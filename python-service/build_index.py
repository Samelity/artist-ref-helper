import json
import os

import faiss
import open_clip
import torch

from clip_embedding import encode_dir
from logger import logger

# ---------- 配置 ----------
IMAGE_DIR = "images"
INDEX_DIR = "index"
MODEL_NAME = "ViT-B-32"
PRETRAINED = "laion2b_s34b_b79k"

INDEX_PATH = os.path.join(INDEX_DIR, "image_embeddings.faiss")
IDS_PATH = os.path.join(INDEX_DIR, "image_ids.json")


def build_index():
    device = "cuda" if torch.cuda.is_available() else "cpu"
    logger.info(f"Loading OpenCLIP model {MODEL_NAME} on {device} ...")

    model, preprocess, _ = open_clip.create_model_and_transforms(
        MODEL_NAME, pretrained=PRETRAINED, device=device
    )
    model.eval()

    vectors, paths = encode_dir(model, preprocess, IMAGE_DIR, device)
    if vectors.shape[0] == 0:
        logger.info("没有成功生成任何向量，终止建索引。")
        return

    index = faiss.IndexFlatIP(vectors.shape[1])  # 维度用实际值，换模型不用改代码
    index.add(vectors)

    os.makedirs(INDEX_DIR, exist_ok=True)
    faiss.write_index(index, INDEX_PATH)

    id_map = {str(i): p for i, p in enumerate(paths)}
    with open(IDS_PATH, "w", encoding="utf-8") as f:
        json.dump(id_map, f, ensure_ascii=False, indent=2)

    logger.info(f"FAISS index saved to {INDEX_PATH}")
    logger.info(f"Image ID map saved to {IDS_PATH}")
    logger.info(f"Total indexed images: {index.ntotal}")


if __name__ == "__main__":
    build_index()
