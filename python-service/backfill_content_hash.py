"""为存量图片补齐 content_hash（SHA-256）。

可重复执行：只处理 content_hash 为 NULL 的行。
用法（部署机）：
    docker compose exec python python backfill_content_hash.py
"""
import os

import psycopg

import db
from hash_utils import sha256_file
from logger import logger
from paths import resolve_storage_path


def backfill() -> int:
    try:
        rows = db.list_images_missing_hash()
    except psycopg.errors.UndefinedColumn:
        logger.error(
            "reference_image.content_hash 列不存在：请先用新镜像启动一次 app，"
            "等 Hibernate 自动加列后再运行本脚本。"
        )
        return 1

    if not rows:
        logger.info("[backfill] 没有需要回填的记录。")
    else:
        updates = []
        missing = 0
        for row in rows:
            abs_path = resolve_storage_path(row["file_path"])
            if not os.path.isfile(abs_path):
                missing += 1
                logger.warning(f"[backfill] 文件不存在，保持 NULL: id={row['id']} {row['file_path']}")
                continue
            updates.append((sha256_file(abs_path), row["id"]))

        if updates:
            db.update_content_hashes(updates)
        logger.info(f"[backfill] 完成: 回填 {len(updates)} 条, 缺文件 {missing} 条")

    groups = db.list_duplicate_hashes()
    if groups:
        logger.warning(f"[backfill] 发现 {len(groups)} 组内容完全相同的图片（未删除，请自行核对）：")
        for group in groups:
            logger.warning(f"  {group['content_hash'][:12]}…  {group['cnt']} 张")
    else:
        logger.info("[backfill] 未发现内容完全相同的图片。")

    return 0


if __name__ == "__main__":
    raise SystemExit(backfill())
