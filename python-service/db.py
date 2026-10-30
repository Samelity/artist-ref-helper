import os

import psycopg
from psycopg.rows import dict_row

DB_HOST = os.environ.get("DB_HOST", "localhost")
DB_PORT = os.environ.get("DB_PORT", "5432")
DB_USER = os.environ.get("DB_USER", "postgres")
DB_PASSWORD = os.environ.get("DB_PASSWORD", "")
DB_NAME = os.environ.get("DB_NAME", "postgres")

DATABASE_URL = f"postgresql://{DB_USER}:{DB_PASSWORD}@{DB_HOST}:{DB_PORT}/{DB_NAME}"


def connect():
    """返回一个 autocommit 的连接，row 用 dict 方便按列名取值。"""
    return psycopg.connect(DATABASE_URL, row_factory=dict_row, autocommit=True)


def query_one(sql, params=None):
    """通用单行查询，返回第一个 dict_row 或 None"""
    with connect() as conn, conn.cursor() as cur:
        cur.execute(sql, params)
        return cur.fetchone()


def insert_image(file_name: str, file_path: str) -> int:
    """插入一条图片元数据，返回自增主键 id。file_path 为相对 STORAGE_DIR 的路径。"""
    sql = (
        "INSERT INTO reference_image (file_name, file_path) "
        "VALUES (%s, %s) RETURNING id"
    )
    with connect() as conn, conn.cursor() as cur:
        cur.execute(sql, (file_name, file_path))
        return cur.fetchone()["id"]


def list_all_ordered():
    """按主键升序返回全部记录，用于建索引时确定 FAISS 行号顺序。"""
    sql = "SELECT id, file_name, file_path FROM reference_image ORDER BY id"
    with connect() as conn, conn.cursor() as cur:
        cur.execute(sql)
        return cur.fetchall()


def bind_faiss_ids(pairs):
    """批量回填 faiss_id（行号）。pairs = [(faiss_row, db_id), ...]"""
    sql = "UPDATE reference_image SET faiss_id = %s WHERE id = %s"
    with connect() as conn, conn.cursor() as cur:
        cur.executemany(sql, pairs)


def get_by_faiss_id(faiss_id: int):
    """Spring Boot 或 Python 反查用：FAISS 行号 -> 图片元数据。"""
    sql = (
        "SELECT id, file_name, file_path "
        "FROM reference_image WHERE faiss_id = %s"
    )
    with connect() as conn, conn.cursor() as cur:
        cur.execute(sql, (faiss_id,))
        return cur.fetchone()


def unbind_faiss_ids(ids: list[int]) -> int:
    """将指定 id 列表的 faiss_id 置为 NULL，返回影响行数。"""
    if not ids:
        return 0
    sql = "UPDATE reference_image SET faiss_id = NULL WHERE id = ANY(%s)"
    with connect() as conn, conn.cursor() as cur:
        cur.execute(sql, (ids,))
        return cur.rowcount


def insert_image_with_source(
    file_name: str,
    file_path: str,
    source: str = "local",
    source_id: str | None = None,
    source_url: str | None = None,
    photographer: str | None = None,
) -> int:
    """插入一条带来源信息的图片记录，返回自增主键 id。file_path 为相对 STORAGE_DIR 的路径。"""
    sql = (
        "INSERT INTO reference_image (file_name, file_path, source, source_id, source_url, photographer) "
        "VALUES (%s, %s, %s, %s, %s, %s) RETURNING id"
    )
    with connect() as conn, conn.cursor() as cur:
        cur.execute(sql, (file_name, file_path, source, source_id, source_url, photographer))
        return cur.fetchone()["id"]


def exists_by_source_id(source_id: str) -> bool:
    """检查 source_id 是否已存在（用于 Pexels 去重）。"""
    sql = "SELECT 1 FROM reference_image WHERE source_id = %s LIMIT 1"
    with connect() as conn, conn.cursor() as cur:
        cur.execute(sql, (source_id,))
        return cur.fetchone() is not None


def get_max_faiss_id() -> int | None:
    """返回当前最大的 faiss_id，用于增量追加时确定新行号起点。"""
    sql = "SELECT MAX(faiss_id) AS max_id FROM reference_image"
    with connect() as conn, conn.cursor() as cur:
        cur.execute(sql)
        row = cur.fetchone()
        return row["max_id"] if row else None


# ---------- Board 相关 ----------

def list_boards() -> list[dict]:
    """查询所有灵感板，按创建时间倒序。"""
    sql = "SELECT id, name, description, created_at FROM boards ORDER BY created_at DESC"
    with connect() as conn, conn.cursor() as cur:
        cur.execute(sql)
        return cur.fetchall()


def get_board_with_images(board_id: int) -> dict | None:
    """查询单个灵感板及其关联图片。"""
    sql = "SELECT id, name, description, created_at FROM boards WHERE id = %s"
    with connect() as conn, conn.cursor() as cur:
        cur.execute(sql, (board_id,))
        board = cur.fetchone()
        if not board:
            return None

        cur.execute(
            "SELECT ri.id, ri.faiss_id, ri.file_name, ri.file_path, "
            "ri.source, ri.source_id, ri.source_url, ri.photographer "
            "FROM reference_image ri "
            "JOIN board_images bi ON bi.image_id = ri.id "
            "WHERE bi.board_id = %s",
            (board_id,),
        )
        board["images"] = cur.fetchall()
        return board
