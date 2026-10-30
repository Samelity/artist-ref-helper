import asyncio
import json
import os
import re
import shutil
import threading
import time
from pathlib import Path
from urllib.parse import urlparse

import faiss
import numpy as np
import cn_clip.clip as clip
import requests
import torch
import uvicorn
from deep_translator import BaiduTranslator
from fastapi import BackgroundTasks, FastAPI, File, Form, HTTPException, UploadFile
from fastapi.responses import FileResponse
from PIL import Image
from pydantic import BaseModel

import db
from clip_embedding import IMAGE_EXTS, encode_one
from logger import logger
from paths import (
    IDS_PATH,
    IMPORT_DIR,
    INDEX_PATH,
    MODEL_DIR,
    STORAGE_DIR,
    resolve_storage_path,
)

# ---------- 配置 ----------
MODEL_NAME = "ViT-L-14"

# 图片代理允许读取的根目录白名单
# 用 realpath 归一后存入，校验时做精确路径前缀匹配，杜绝 ../ 目录遍历。
ALLOWED_ROOTS = [os.path.realpath(STORAGE_DIR)] if STORAGE_DIR else []

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

# 加载 FAISS 索引，如果不存在则创建空索引
if os.path.exists(INDEX_PATH) and os.path.exists(IDS_PATH):
    index = faiss.read_index(INDEX_PATH)
    with open(IDS_PATH, "r", encoding="utf-8") as f:
        id_map = json.load(f)
    logger.info(f"Index loaded: {index.ntotal} vectors, dimension={index.d}")
else:
    logger.warning(f"FAISS index not found at {INDEX_PATH}, creating empty index. "
                   f"Please run import_images.py first to populate the index.")
    # 创建空的 FAISS 索引（维度 768 对应 ViT-L-14）
    index = faiss.IndexFlatIP(768)
    id_map = {}

logger.info("Image Service started successfully")

# ---------- FastAPI ----------
app = FastAPI(title="Reference Image Search")


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
                    topK: int = Form(20),
                    threshold: float = Form(0.0)):
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
            img = Image.open(resolve_storage_path(rec["file_path"]))
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


@app.get("/images/view")
def view_image(path: str):
    """
    图片代理接口：解决浏览器无法直接访问本地绝对路径的安全限制。
    合法图片必然登记在 PG，因此白名单 = 允许的根目录 + 真实路径归一化校验。
    """
    if not path:
        raise HTTPException(status_code=400, detail="Missing path")

    # realpath 会解析 ../ 与软链，得到规范绝对路径后再比对白名单
    abs_path = resolve_storage_path(path)

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


# 只允许从图库官方 CDN 下载，避免 /remote/save 被改造成任意 URL 抓取入口
ALLOWED_IMAGE_HOSTS = ("pixabay.com", "pexels.com")


def _is_allowed_image_url(url: str) -> bool:
    parsed = urlparse(url)
    if parsed.scheme not in ("http", "https"):
        return False
    host = (parsed.hostname or "").lower()
    return any(host == d or host.endswith("." + d) for d in ALLOWED_IMAGE_HOSTS)


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
            if not _is_allowed_image_url(photo.url):
                errors.append(f"{photo.id}: 不支持的图片来源域名")
                logger.warning(f"[remote] 拒绝非图库域名: {photo.url}")
                continue

            if db.exists_by_source_id(photo.id):
                skipped += 1
                logger.info(f"[remote] 跳过已存在: {photo.id}")
                continue

            file_name = f"{photo.id}.jpg"
            abs_path = resolve_storage_path(file_name)
            os.makedirs(STORAGE_DIR, exist_ok=True)

            _download_image(photo.url, abs_path)

            img = Image.open(abs_path)
            img.load()
            vec = encode_one(model, preprocess, img, device)

            db_id = db.insert_image_with_source(
                file_name=file_name,
                file_path=file_name,
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
                "filePath": file_name,
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


# ---------- 本地图片上传 ----------

_upload_progress = {
    "running": False,
    "current": 0,
    "total": 0,
    "current_file": "",
    "saved": 0,
    "skipped": 0,
    "errors": [],
}
_upload_lock = threading.Lock()


class UploadResponse(BaseModel):
    total: int
    message: str


@app.post("/upload", response_model=UploadResponse)
async def upload_images(files: list[UploadFile] = File(...), bg: BackgroundTasks = None):  # noqa: B008
    """
    批量上传本地图片（后台处理）。
    先读取所有文件数据，再交给后台线程保存、编码、入库。
    前端通过 GET /upload/status 轮询进度。
    """
    if index is None:
        raise HTTPException(status_code=500, detail="FAISS 索引未初始化，请先重建索引")

    with _upload_lock:
        if _upload_progress["running"]:
            raise HTTPException(status_code=409, detail="上传任务正在执行中")

    file_data: list[tuple[str, bytes]] = []
    for f in files:
        data = await f.read()
        file_data.append((f.filename or "unknown.jpg", data))

    if not file_data:
        return UploadResponse(total=0, message="没有收到文件")

    with _upload_lock:
        _upload_progress.update(
            running=True, current=0, total=len(file_data),
            current_file="", saved=0, skipped=0, errors=[],
        )

    bg.add_task(_run_upload, file_data)
    return UploadResponse(total=len(file_data), message=f"开始上传 {len(file_data)} 张图片")


async def _run_upload(file_data: list[tuple[str, bytes]]):
    """后台线程处理上传文件：保存 → CLIP 编码 → 入库 → 追加 FAISS。"""
    global index, id_map

    def _do_upload():
        global index, id_map

        try:
            saved, skipped, errors = 0, 0, []
            next_faiss_id = index.ntotal
            os.makedirs(STORAGE_DIR, exist_ok=True)

            for i, (original_name, content) in enumerate(file_data):
                with _upload_lock:
                    _upload_progress["current"] = i + 1
                    _upload_progress["current_file"] = original_name

                suffix = Path(original_name).suffix.lower()
                if suffix not in IMAGE_EXTS:
                    with _upload_lock:
                        _upload_progress["errors"] = _upload_progress["errors"] + [f"{original_name}: 不支持的格式"]
                    continue

                try:
                    stem = Path(original_name).stem
                    file_name = f"{stem}{suffix}"
                    abs_path = resolve_storage_path(file_name)

                    if os.path.exists(abs_path):
                        skipped += 1
                        with _upload_lock:
                            _upload_progress["skipped"] = skipped
                        logger.info(f"[upload] 跳过已存在: {file_name}")
                        continue

                    with open(abs_path, "wb") as f:
                        f.write(content)

                    img = Image.open(abs_path)
                    img.load()
                    vec = encode_one(model, preprocess, img, device)

                    db_id = db.insert_image(file_name=file_name, file_path=file_name)

                    index.add(vec)
                    faiss_id = next_faiss_id
                    next_faiss_id += 1
                    db.bind_faiss_ids([(faiss_id, db_id)])

                    id_map[str(faiss_id)] = {
                        "id": db_id,
                        "fileName": file_name,
                        "filePath": file_name,
                    }

                    saved += 1
                    with _upload_lock:
                        _upload_progress["saved"] = saved
                    logger.info(f"[upload] 已导入: id={db_id} faiss_id={faiss_id} file={file_name}")

                except Exception as e:  # noqa: BLE001
                    errors.append(f"{original_name}: {e}")
                    with _upload_lock:
                        _upload_progress["errors"] = list(errors)
                    logger.error(f"[upload] 导入失败: {original_name}: {e}")

            if saved > 0:
                faiss.write_index(index, INDEX_PATH)
                with open(IDS_PATH, "w", encoding="utf-8") as f:
                    json.dump(id_map, f, ensure_ascii=False, indent=2)

            logger.info(f"[upload] 完成: saved={saved} skipped={skipped} errors={len(errors)}")
        finally:
            with _upload_lock:
                _upload_progress["running"] = False

    await asyncio.to_thread(_do_upload)


@app.get("/upload/status")
def upload_status():
    """返回当前上传进度，前端轮询此接口。"""
    with _upload_lock:
        return dict(_upload_progress)


# ---------- 共享目录批量导入 ----------

_import_progress = {
    "running": False,
    "current": 0,
    "total": 0,
    "current_file": "",
    "imported": 0,
    "skipped": 0,
    "errors": [],
}
_import_lock = threading.Lock()


class ImportResponse(BaseModel):
    total: int
    message: str


@app.post("/import", response_model=ImportResponse)
def import_from_directory(bg: BackgroundTasks):
    """
    从 IMPORT_DIR 共享目录递归导入所有子文件夹中的图片（后台执行）。
    前端通过 GET /import/status 轮询进度。
    """
    if not IMPORT_DIR:
        raise HTTPException(status_code=500, detail="IMPORT_DIR 未配置")
    if not os.path.isdir(IMPORT_DIR):
        raise HTTPException(status_code=400, detail=f"导入目录不存在: {IMPORT_DIR}")

    with _import_lock:
        if _import_progress["running"]:
            raise HTTPException(status_code=409, detail="导入任务正在执行中")

    source_dir = Path(IMPORT_DIR)
    # 递归扫描所有子目录；用相对路径作为入库文件名，避免不同子目录中的同名文件互相覆盖
    relative_files = sorted(
        (
            p.relative_to(source_dir)
            for p in source_dir.rglob("*")
            if p.is_file() and p.suffix.lower() in IMAGE_EXTS
        ),
        key=lambda p: p.as_posix().lower(),
    )
    if not relative_files:
        return ImportResponse(total=0, message="导入目录（含子文件夹）中没有找到图片文件")

    with _import_lock:
        _import_progress.update(
            running=True, current=0, total=len(relative_files),
            current_file="", imported=0, skipped=0, errors=[],
        )

    bg.add_task(_run_import, source_dir, relative_files)
    return ImportResponse(total=len(relative_files), message=f"开始导入 {len(relative_files)} 张图片")


async def _run_import(source_dir: Path, relative_files: list[Path]):
    """后台线程执行实际导入，逐文件更新进度。"""
    global index, id_map

    def _do_import():
        global index, id_map

        try:
            if index is None:
                index = faiss.IndexFlatIP(768)
                id_map = {}

            imported, skipped, errors = 0, 0, []
            next_faiss_id = index.ntotal
            os.makedirs(STORAGE_DIR, exist_ok=True)

            for i, rel in enumerate(relative_files):
                file_name = rel.as_posix()
                with _import_lock:
                    _import_progress["current"] = i + 1
                    _import_progress["current_file"] = file_name

                try:
                    abs_path = resolve_storage_path(file_name)

                    if os.path.exists(abs_path):
                        skipped += 1
                        with _import_lock:
                            _import_progress["skipped"] = skipped
                        logger.info(f"[import] 跳过已存在: {file_name}")
                        continue

                    os.makedirs(os.path.dirname(abs_path), exist_ok=True)
                    shutil.copy2(str(source_dir / rel), abs_path)

                    img = Image.open(abs_path)
                    img.load()
                    vec = encode_one(model, preprocess, img, device)

                    db_id = db.insert_image(file_name=file_name, file_path=file_name)

                    index.add(vec)
                    faiss_id = next_faiss_id
                    next_faiss_id += 1
                    db.bind_faiss_ids([(faiss_id, db_id)])

                    id_map[str(faiss_id)] = {
                        "id": db_id,
                        "fileName": file_name,
                        "filePath": file_name,
                    }

                    imported += 1
                    with _import_lock:
                        _import_progress["imported"] = imported
                    logger.info(f"[import] 已导入: id={db_id} faiss_id={faiss_id} file={file_name}")

                except Exception as e:  # noqa: BLE001
                    errors.append(f"{file_name}: {e}")
                    with _import_lock:
                        _import_progress["errors"] = list(errors)
                    logger.error(f"[import] 导入失败: {file_name}: {e}")

            if imported > 0:
                faiss.write_index(index, INDEX_PATH)
                with open(IDS_PATH, "w", encoding="utf-8") as f:
                    json.dump(id_map, f, ensure_ascii=False, indent=2)

            logger.info(f"[import] 完成: imported={imported} skipped={skipped} errors={len(errors)}")
        finally:
            with _import_lock:
                _import_progress["running"] = False

    await asyncio.to_thread(_do_import)


@app.get("/import/status")
def import_status():
    """返回当前导入进度，前端轮询此接口。"""
    with _import_lock:
        return dict(_import_progress)


# ---------- 灵感板 ----------

@app.get("/boards")
def list_boards():
    """查询所有灵感板及其图片。"""
    boards = db.list_boards()
    for board in boards:
        board_images = db.get_board_with_images(board["id"])
        board["images"] = board_images["images"] if board_images else []
    return boards


if __name__ == "__main__":
    uvicorn.run(app, host="127.0.0.1", port=8000)
