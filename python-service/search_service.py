import json
import os
import re
import time
from pathlib import Path

import faiss
import numpy as np
import cn_clip.clip as clip
import requests
import torch
import uvicorn
from deep_translator import BaiduTranslator
from fastapi import FastAPI, File, HTTPException, UploadFile
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import FileResponse
from PIL import Image
from pydantic import BaseModel

import db
from clip_embedding import encode_one
from logger import logger

# ---------- 配置 ----------
MODEL_NAME = "ViT-L-14"
IMAGE_DIR = os.environ.get("IMAGE_DIR", "images")  # 图库目录归 Python 自己管
STORAGE_DIR = os.environ.get("STORAGE_DIR", "storage")  # 导入脚本真正落图的目录
MODEL_DIR = Path(__file__).parent / "models"  # Chinese-CLIP 模型缓存目录
INDEX_PATH = "index/image_embeddings.faiss"
IDS_PATH = "index/image_ids.json"

# 图片代理允许读取的根目录白名单（真实图片可能落在 IMAGE_DIR 或 STORAGE_DIR）
# 用 realpath 归一后存入，校验时做精确路径前缀匹配，杜绝 ../ 目录遍历。
ALLOWED_ROOTS = [os.path.realpath(p) for p in (IMAGE_DIR, STORAGE_DIR) if p]

# 允许跨域访问前端的前端源（Spring Boot 提供 index.html 的地址）
CORS_ALLOW_ORIGINS = os.environ.get(
    "CORS_ALLOW_ORIGINS", "http://localhost:8080,http://127.0.0.1:8080"
).split(",")

# Pexels API 配置
PEXELS_API_KEY = os.environ.get("PEXELS_API_KEY", "")
PEXELS_BASE_URL = os.environ.get("PEXELS_BASE_URL", "https://api.pexels.com")

# Pixabay API 配置
PIXABAY_API_KEY = os.environ.get("PIXABAY_API_KEY", "")
PIXABAY_BASE_URL = os.environ.get("PIXABAY_BASE_URL", "https://pixabay.com/api")

# 百度翻译 API 配置（用于中文查询转英文）
# deep-translator 要求环境变量名为 BAIDU_APPID 和 BAIDU_APPKEY
BAIDU_APP_ID = os.environ.get("BAIDU_APPID", "")
BAIDU_APP_KEY = os.environ.get("BAIDU_APPKEY", "")

# ---------- 加载资源（进程内只加载一次）----------
device = "cuda" if torch.cuda.is_available() else "cpu"
logger.info(f"Loading Chinese-CLIP model on {device} ...")

model, preprocess = clip.load_from_name(
    MODEL_NAME,
    download_root=str(MODEL_DIR),
    device=device,
    use_modelscope=True
)
model.eval()

index = faiss.read_index(INDEX_PATH)
with open(IDS_PATH, "r", encoding="utf-8") as f:
    id_map = json.load(f)

logger.info(f"Index loaded: {index.ntotal} vectors, dimension={index.d}")
logger.info("Image Service started successfully")

# ---------- FastAPI ----------
app = FastAPI(title="Reference Image Search")

# 前端(Sprin Boot 8080)直连本服务(8000)时需要 CORS；图片代理同理。
app.add_middleware(
    CORSMiddleware,
    allow_origins=[o.strip() for o in CORS_ALLOW_ORIGINS if o.strip()],
    allow_credentials=False,
    allow_methods=["*"],
    allow_headers=["*"],
)


class TextSearchRequest(BaseModel):
    query: str
    topK: int = 8
    threshold: float = 0.0


class SearchResult(BaseModel):
    id: str  # FAISS 行号（Java 反查用）
    fileName: str
    filePath: str
    score: float


class SearchResponse(BaseModel):
    items: list[SearchResult]


def _encode_text(text: str) -> np.ndarray:
    tokens = clip.tokenize([text]).to(device)
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
    # 索引为空（未建或被清空）时直接返回空结果，避免 NoneType 崩栈
    if index is None or index.ntotal == 0:
        return SearchResponse(items=[])

    k = min(topK, index.ntotal)
    scores, ids = index.search(vector, k)

    items = []
    for score, idx in zip(scores[0], ids[0]):
        if idx < 0 or score < threshold:
            continue
        sid = str(int(idx))
        meta = id_map.get(sid)
        if not meta:
            # id_map 里没有则回查数据库兜底
            row = db.get_by_faiss_id(int(idx))
            if not row:
                continue
            meta = {"fileName": row["file_name"], "filePath": row["file_path"]}
        items.append(SearchResult(
            id=sid,
            fileName=meta["fileName"],
            filePath=meta["filePath"],
            score=float(score),
        ))
    return SearchResponse(items=items)


@app.get("/health")
def health():
    return {
        "status": "ok",
        "vectors": 0 if index is None else index.ntotal,
    }


@app.post("/search/text", response_model=SearchResponse)
def search_by_text(req: TextSearchRequest):
    t0 = time.time()
    vec = _encode_text(req.query)
    resp = _search(vec, req.topK, req.threshold)
    logger.info(f"[text] query={req.query!r} topK={req.topK} "
                f"results={len(resp.items)} elapsed={time.time() - t0:.3f}s")
    return resp


@app.post("/search/image", response_model=SearchResponse)
def search_by_image(file: UploadFile = File(...),  # noqa: B008
                    topK: int = 20,
                    threshold: float = 0.0):
    t0 = time.time()
    try:
        img = Image.open(file.file)
        img.load()  # 提前解码，坏图在这里就抛错，不会污染后续编码
    except Exception as e:  # noqa: BLE001
        raise HTTPException(status_code=400, detail=f"Invalid image file: {e}")
    vec = _encode_image(img)
    resp = _search(vec, topK, threshold)
    logger.info(f"[image] file={file.filename!r} topK={topK} "
                f"results={len(resp.items)} elapsed={time.time() - t0:.3f}s")
    return resp


@app.post("/index")
def rebuild_index():
    """
    以 PG 为唯一事实源重建 FAISS。
    自动处理 PG 与磁盘的不一致：缺失文件跳过并解绑 faiss_id，孤儿文件忽略。
    """
    global index, id_map

    # 1. 从 PG 拉取全量记录（按 id 升序，决定 FAISS 行号）
    records = db.list_all_ordered()
    if not records:
        logger.warning("PG 中无图片记录，清空索引")
        index = None
        id_map = {}
        return {"indexedCount": 0, "skipped": 0, "unbound": 0}

    # 2. 逐条编码，收集有效记录与缺失记录
    vectors, valid_records, missing_ids = [], [], []
    for rec in records:
        try:
            img = Image.open(rec["file_path"])
            img.load()
            vec = encode_one(model, preprocess, img, device)
            vectors.append(vec)
            valid_records.append(rec)
        except FileNotFoundError:
            missing_ids.append(rec["id"])
            logger.warning(f"[自愈] 文件缺失，跳过并解绑: id={rec['id']} path={rec['file_path']}")
        except Exception as e:  # noqa: BLE001
            missing_ids.append(rec["id"])
            logger.warning(f"[自愈] 文件损坏/读取失败，跳过并解绑: id={rec['id']} err={e}")

    # 3. 将缺失文件的 faiss_id 置 NULL，避免 Java 侧用旧行号查到错误结果
    unbound_count = 0
    if missing_ids:
        unbound_count = db.unbind_faiss_ids(missing_ids)
        logger.info(f"[自愈] 已解绑 {unbound_count} 条缺失文件的 faiss_id")

    # 4. 无有效向量时清空索引
    if not vectors:
        logger.warning("所有文件均不可用，清空索引")
        index = None
        id_map = {}
        return {"indexedCount": 0, "skipped": len(missing_ids), "unbound": unbound_count}

    # 5. 重建 FAISS（numpy 已在顶部导入，无需重复 import）
    new_vectors = np.vstack(vectors)
    new_index = faiss.IndexFlatIP(new_vectors.shape[1])
    new_index.add(new_vectors)
    faiss.write_index(new_index, INDEX_PATH)

    # 6. 回填有效记录的新行号
    pairs = [(row_idx, rec["id"]) for row_idx, rec in enumerate(valid_records)]
    db.bind_faiss_ids(pairs)

    # 7. 更新内存 id_map（结构化兜底）
    id_map = {
        str(row_idx): {
            "id": rec["id"],
            "fileName": rec["file_name"],
            "filePath": rec["file_path"],
        }
        for row_idx, rec in enumerate(valid_records)
    }
    with open(IDS_PATH, "w", encoding="utf-8") as f:
        json.dump(id_map, f, ensure_ascii=False, indent=2)

    # 8. 热替换全局索引
    index = new_index
    logger.info(
        f"Index rebuilt: total={new_index.ntotal}, "
        f"skipped={len(missing_ids)}, unbound={unbound_count}"
    )
    return {
        "indexedCount": new_index.ntotal,
        "skipped": len(missing_ids),
        "unbound": unbound_count,
    }


def _is_allowed(abs_path: str) -> bool:
    """判断绝对路径是否落在任一允许的根目录内（精确边界，防 ../ 与前缀绕过）。"""
    for root in ALLOWED_ROOTS:
        try:
            # commonpath 只有在 abs_path 真处于 root 内部(或就是 root)时才等于 root
            if os.path.commonpath([abs_path, root]) == root:
                return True
        except ValueError:
            # 不同盘符/无公共根，直接判否
            continue
    return False


@app.get("/api/images/view")
def view_image(path: str):
    """
    图片代理接口：解决浏览器无法直接访问本地绝对路径的安全限制。
    合法图片必然登记在 PG，因此白名单 = 允许的根目录 + 真实路径归一化校验。
    """
    if not path:
        raise HTTPException(status_code=400, detail="Missing path")

    # realpath 会解析 ../ 与软链，得到规范绝对路径后再比对白名单
    abs_path = os.path.realpath(path)

    if not _is_allowed(abs_path):
        logger.warning(f"[view] 拒绝越权访问: {path} -> {abs_path}")
        raise HTTPException(status_code=403, detail="Access denied: path outside allowed roots")

    if not os.path.isfile(abs_path):
        raise HTTPException(status_code=404, detail="Image not found")

    # 带缓存头，前端二次加载直接走浏览器缓存，不重复读盘
    return FileResponse(abs_path, headers={"Cache-Control": "public, max-age=86400"})


# ---------- 网络搜图：通用模型 ----------

def _is_chinese(text: str) -> bool:
    """检测文本是否包含中文字符。"""
    return bool(re.search(r'[\u4e00-\u9fff]', text))


def _translate_to_en(text: str) -> str:
    """中文查询转英文，提升 Pixabay 搜索效果。失败时返回原文。"""
    if not _is_chinese(text):
        return text
    if not BAIDU_APP_ID or not BAIDU_APP_KEY:
        logger.warning("[translate] 百度翻译未配置，使用原文")
        return text
    try:
        translator = BaiduTranslator(
            api_key=BAIDU_APP_KEY,
            app_id=BAIDU_APP_ID,
            source="zh",
            target="en"
        )
        result = translator.translate(text)
        if result:
            logger.info(f"[translate] {text!r} -> {result!r}")
            return result
    except Exception as e:  # noqa: BLE001
        logger.warning(f"[translate] 翻译失败，使用原文: {e}")
    return text


class RemotePhoto(BaseModel):
    """统一的远程图片模型，兼容 Pixabay / Pexels 响应。"""
    id: str
    url: str
    thumbnail: str
    photographer: str | None = None
    source: str  # "pixabay" | "pexels"


class RemoteSearchResponse(BaseModel):
    photos: list[RemotePhoto]
    total_results: int
    page: int
    per_page: int


class SaveItem(BaseModel):
    id: str
    url: str
    photographer: str | None = None
    source: str  # "pixabay" | "pexels"


class SaveRequest(BaseModel):
    photos: list[SaveItem]


class SaveResponse(BaseModel):
    saved: int
    skipped: int
    errors: list[str]


# ---------- Pixabay 网络搜图 ----------

class PixabayHit(BaseModel):
    id: int
    webformatURL: str
    largeImageURL: str
    user: str
    tags: str | None = None


class PixabaySearchResponse(BaseModel):
    photos: list[RemotePhoto]
    total_results: int
    page: int
    per_page: int


@app.get("/pixabay/search", response_model=PixabaySearchResponse)
def pixabay_search(
    query: str,
    per_page: int = 20,
    page: int = 1,
    lang: str = "zh",
):
    """搜索 Pixabay 图片，返回缩略图列表供前端展示（不下载、不入库）。"""
    if not PIXABAY_API_KEY:
        raise HTTPException(status_code=500, detail="PIXABAY_API_KEY not configured")
    if not query or not query.strip():
        raise HTTPException(status_code=400, detail="搜索关键词不能为空")

    # 中文查询自动翻译为英文，提升搜索效果
    search_query = _translate_to_en(query.strip())

    t0 = time.time()
    try:
        resp = requests.get(
            PIXABAY_BASE_URL,
            params={
                "key": PIXABAY_API_KEY,
                "q": search_query,
                "lang": "en" if search_query != query.strip() else lang,
                "image_type": "photo",
                "per_page": per_page,
                "page": page,
                "safesearch": "true",
            },
            timeout=15,
        )
        resp.raise_for_status()
        data = resp.json()
    except requests.RequestException as e:
        logger.error(f"[pixabay] 搜索失败: {e}")
        raise HTTPException(status_code=502, detail=f"Pixabay API 请求失败: {e}")

    hits = data.get("hits", [])
    photos = [
        RemotePhoto(
            id=f"pixabay_{h['id']}",
            url=h["largeImageURL"],
            thumbnail=h["webformatURL"],
            photographer=h.get("user"),
            source="pixabay",
        )
        for h in hits
    ]

    logger.info(f"[pixabay] search query={query!r},{search_query!r} results={data.get('totalHits', 0)} "
                f"elapsed={time.time() - t0:.3f}s")
    return PixabaySearchResponse(
        photos=photos,
        total_results=data.get("totalHits", 0),
        page=page,
        per_page=per_page,
    )


# ---------- Pexels 网络搜图 ----------

class PexelsSearchResponse(BaseModel):
    photos: list[RemotePhoto]
    total_results: int
    page: int
    per_page: int


@app.get("/pexels/search", response_model=PexelsSearchResponse)
def pexels_search(
    query: str,
    per_page: int = 15,
    page: int = 1,
    locale: str = "zh-CN",
):
    """搜索 Pexels 图片，返回缩略图列表供前端展示（不下载、不入库）。"""
    if not PEXELS_API_KEY:
        raise HTTPException(status_code=500, detail="PEXELS_API_KEY not configured")
    if not query or not query.strip():
        raise HTTPException(status_code=400, detail="搜索关键词不能为空")

    t0 = time.time()
    try:
        resp = requests.get(
            f"{PEXELS_BASE_URL}/v1/search",
            headers={"Authorization": PEXELS_API_KEY},
            params={"query": query.strip(), "per_page": per_page, "page": page, "locale": locale},
            timeout=15,
        )
        resp.raise_for_status()
        data = resp.json()
    except requests.RequestException as e:
        logger.error(f"[pexels] 搜索失败: {e}")
        raise HTTPException(status_code=502, detail=f"Pexels API 请求失败: {e}")

    photos = [
        RemotePhoto(
            id=f"pexels_{p['id']}",
            url=p["src"]["original"],
            thumbnail=p["src"]["medium"],
            photographer=p.get("photographer"),
            source="pexels",
        )
        for p in data.get("photos", [])
    ]

    logger.info(f"[pexels] search query={query!r} results={data.get('total_results', 0)} "
                f"elapsed={time.time() - t0:.3f}s")
    return PexelsSearchResponse(
        photos=photos,
        total_results=data.get("total_results", 0),
        page=data.get("page", page),
        per_page=data.get("per_page", per_page),
    )


def _download_image(url: str, save_path: str, timeout: int = 30) -> None:
    """下载图片到本地文件。"""
    resp = requests.get(url, stream=True, timeout=timeout)
    resp.raise_for_status()
    with open(save_path, "wb") as f:
        for chunk in resp.iter_content(chunk_size=8192):
            f.write(chunk)


@app.post("/remote/save", response_model=SaveResponse)
def remote_save(req: SaveRequest):
    """
    下载用户选中的远程图片（Pixabay / Pexels），CLIP 编码后增量追加到 FAISS 索引。
    已存在的 source_id 自动跳过，单张失败不影响其他图片。
    """
    if index is None:
        raise HTTPException(status_code=500, detail="FAISS 索引未初始化，请先重建索引")

    saved, skipped, errors = 0, 0, []
    next_faiss_id = index.ntotal

    for photo in req.photos:
        try:
            if db.exists_by_source_id(photo.id):
                skipped += 1
                logger.info(f"[remote] 跳过已存在: {photo.id}")
                continue

            file_name = f"{photo.id}.jpg"
            file_path = str(Path(STORAGE_DIR) / file_name)
            os.makedirs(STORAGE_DIR, exist_ok=True)

            _download_image(photo.url, file_path)

            img = Image.open(file_path)
            img.load()
            vec = encode_one(model, preprocess, img, device)

            db_id = db.insert_image_with_source(
                file_name=file_name,
                file_path=file_path,
                source=photo.source,
                source_id=photo.id,
                source_url=photo.url,
                photographer=photo.photographer,
            )

            index.add(vec)
            faiss_id = next_faiss_id
            next_faiss_id += 1
            db.bind_faiss_ids([(faiss_id, db_id)])

            sid = str(faiss_id)
            id_map[sid] = {
                "id": db_id,
                "fileName": file_name,
                "filePath": file_path,
                "source": photo.source,
                "photographer": photo.photographer,
            }

            saved += 1
            logger.info(f"[remote] 已保存: id={db_id} faiss_id={faiss_id} file={file_name}")

        except Exception as e:  # noqa: BLE001
            err_msg = f"{photo.id}: {e}"
            errors.append(err_msg)
            logger.error(f"[remote] 保存失败: {err_msg}")

    faiss.write_index(index, INDEX_PATH)
    with open(IDS_PATH, "w", encoding="utf-8") as f:
        json.dump(id_map, f, ensure_ascii=False, indent=2)

    logger.info(f"[remote] save 完成: saved={saved} skipped={skipped} errors={len(errors)}")
    return SaveResponse(saved=saved, skipped=skipped, errors=errors)


if __name__ == "__main__":
    uvicorn.run(app, host="0.0.0.0", port=8000)
