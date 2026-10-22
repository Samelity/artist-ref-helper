import json
import os

import time
from typing import List

import faiss
import numpy as np
import open_clip
import torch
import uvicorn
from fastapi import FastAPI, File, UploadFile
from PIL import Image
from pydantic import BaseModel

from clip_embedding import encode_dir

# ---------- 配置 ----------
MODEL_NAME = "ViT-B-32"
PRETRAINED = "laion2b_s34b_b79k"
IMAGE_DIR = os.environ.get("IMAGE_DIR", "images")  # 图库目录归 Python 自己管
INDEX_PATH = "index/image_embeddings.faiss"
IDS_PATH = "index/image_ids.json"

# ---------- 加载资源（进程内只加载一次）----------
device = "cuda" if torch.cuda.is_available() else "cpu"
print(f"Loading model on {device} ...")

model, preprocess, _ = open_clip.create_model_and_transforms(
    MODEL_NAME, pretrained=PRETRAINED, device=device
)
model.eval()
tokenizer = open_clip.get_tokenizer(MODEL_NAME)

index = faiss.read_index(INDEX_PATH)
with open(IDS_PATH, "r", encoding="utf-8") as f:
    id_map = json.load(f)

print(f"Index loaded: {index.ntotal} vectors, dimension={index.d}")

# ---------- FastAPI ----------
app = FastAPI(title="Reference Image Search")


class TextSearchRequest(BaseModel):
    query: str
    topK: int = 8
    threshold: float = 0.0


class SearchResult(BaseModel):
    id: str
    path: str
    score: float


class SearchResponse(BaseModel):
    items: List[SearchResult]


def _encode_text(text: str) -> np.ndarray:
    tokens = tokenizer([text]).to(device)
    with torch.no_grad():
        feat = model.encode_text(tokens)
    feat = feat / feat.norm(dim=-1, keepdim=True)
    return feat.cpu().numpy().astype(np.float32)


def _encode_image(image: Image.Image) -> np.ndarray:
    tensor = preprocess(image.convert("RGB")).unsqueeze(0).to(device)
    with torch.no_grad():
        feat = model.encode_image(tensor)
    feat = feat / feat.norm(dim=-1, keepdim=True)
    return feat.cpu().numpy().astype(np.float32)


def _search(vector: np.ndarray, topK: int, threshold: float) -> SearchResponse:
    k = min(topK, index.ntotal)
    scores, ids = index.search(vector, k)

    items = []
    for score, idx in zip(scores[0], ids[0]):
        if idx < 0 or score < threshold:
            continue
        sid = str(int(idx))
        path = id_map.get(sid, f"unknown/{sid}")
        items.append(SearchResult(id=sid, path=path, score=float(score)))

    return SearchResponse(items=items)


@app.get("/health")
def health():
    return {"status": "ok", "vectors": index.ntotal}


@app.post("/search/text", response_model=SearchResponse)
def search_by_text(req: TextSearchRequest):
    t0 = time.time()
    vec = _encode_text(req.query)
    resp = _search(vec, req.topK, req.threshold)
    print(f"[text] query={req.query!r} topK={req.topK} "
          f"results={len(resp.items)} elapsed={time.time() - t0:.3f}s")
    return resp


@app.post("/search/image", response_model=SearchResponse)
def search_by_image(file: UploadFile = File(...),
                    topK: int = 8,
                    threshold: float = 0.0):
    t0 = time.time()
    img = Image.open(file.file)
    vec = _encode_image(img)
    resp = _search(vec, topK, threshold)
    print(f"[image] file={file.filename!r} topK={topK} "
          f"results={len(resp.items)} elapsed={time.time() - t0:.3f}s")
    return resp


@app.post("/index")
def rebuild_index():
    """供 Spring Boot 触发：用 Python 自己的 IMAGE_DIR 重建 FAISS 索引。"""
    global index, id_map
    vectors, paths = encode_dir(model, preprocess, IMAGE_DIR, device)
    if vectors.shape[0] == 0:
        return {"indexedCount": 0}

    new_index = faiss.IndexFlatIP(vectors.shape[1])
    new_index.add(vectors)
    faiss.write_index(new_index, INDEX_PATH)

    id_map = {str(i): p for i, p in enumerate(paths)}
    with open(IDS_PATH, "w", encoding="utf-8") as f:
        json.dump(id_map, f, ensure_ascii=False, indent=2)

    index = new_index
    return {"indexedCount": new_index.ntotal}


if __name__ == "__main__":
    uvicorn.run(app, host="0.0.0.0", port=8000)
