import os
import shutil
from pathlib import Path

import faiss
import cn_clip.clip as clip
import torch

torch.set_num_threads(os.cpu_count())

import db
from clip_embedding import IMAGE_EXTS, encode_one
from logger import logger
from paths import (
    IDS_PATH,
    INDEX_DIR,
    INDEX_PATH,
    MODEL_DIR,
    STORAGE_DIR,
    resolve_storage_path,
)

# ---------- 配置 ----------
SOURCE_DIR = os.environ.get("SOURCE_DIR")  # 原始图片待导入目录（必须通过环境变量设置）
MODEL_NAME = "ViT-L-14"


def import_images():
    if not SOURCE_DIR:
        raise ValueError("SOURCE_DIR environment variable must be set")
    
    os.makedirs(STORAGE_DIR, exist_ok=True)
    os.makedirs(INDEX_DIR, exist_ok=True)

    # 1) 扫描源目录，复制图片到固定存储目录并落库
    source_dir = Path(SOURCE_DIR)
    source_files = [
        p for p in sorted(source_dir.iterdir())
        if p.is_file() and p.suffix.lower() in IMAGE_EXTS
    ]
    logger.info(f"Found {len(source_files)} images to import from {SOURCE_DIR}")

    for src in source_files:
        dst = Path(resolve_storage_path(src.name))
        if not dst.exists():
            shutil.copy2(src, dst)
        # 同一文件名重复导入时去重，避免库里堆积
        existing = db.query_one("SELECT id FROM reference_image WHERE file_name = %s", (src.name,))
        if existing:
            logger.info(f"Skip duplicated {src.name}")
            continue
        new_id = db.insert_image(src.name, src.name)
        logger.info(f"Imported id={new_id} file={src.name}")

    build_index_from_db()


def build_index_from_db():
    """以数据库记录顺序为准建 FAISS，行号即 faiss_id。"""
    device = "cuda" if torch.cuda.is_available() else "cpu"
    model, preprocess = clip.load_from_name(
        MODEL_NAME,
        download_root=str(MODEL_DIR),
        device=device,
        use_modelscope=True
    )
    model.eval()

    records = db.list_all_ordered()  # 按 id 升序，行号顺序由此确定
    if not records:
        logger.info("数据库中没有图片记录，终止建索引。")
        return

    embeddings = []
    id_map = {}
    for row_idx, rec in enumerate(records):
        try:
            from PIL import Image
            img = Image.open(resolve_storage_path(rec["file_path"]))
            vec = encode_one(model, preprocess, img, device)
            embeddings.append(vec)
            id_map[str(row_idx)] = {
                "id": rec["id"],
                "fileName": rec["file_name"],
                "filePath": rec["file_path"],
            }
        except Exception as e:  # noqa: BLE001
            logger.exception(f"Skip {rec['file_path']}: {e}")

    if not embeddings:
        logger.info("没有成功生成任何向量，终止建索引。")
        return

    import numpy as np
    vectors = np.vstack(embeddings)  # (N, D)
    index = faiss.IndexFlatIP(vectors.shape[1])
    index.add(vectors)
    faiss.write_index(index, INDEX_PATH)

    # 回填 faiss_id（行号 = 在该有序列表中的位置）
    pairs = [(row_idx, rec["id"]) for row_idx, rec in enumerate(records)]
    db.bind_faiss_ids(pairs)

    import json
    with open(IDS_PATH, "w", encoding="utf-8") as f:
        json.dump(id_map, f, ensure_ascii=False, indent=2)

    logger.info(f"FAISS index saved to {INDEX_PATH}, total={index.ntotal}")
    logger.info(f"ID map saved to {IDS_PATH}, faiss_id bound to PostgreSQL")


if __name__ == "__main__":
    import_images()
