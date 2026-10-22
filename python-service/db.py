import os

import psycopg
from psycopg.rows import dict_row

DATABASE_URL = os.environ.get(
    "DATABASE_URL",
    "postgresql://postgres:postgres@localhost:5432/postgres",
)


def connect():
    """返回一个 autocommit 的连接，row 用 dict 方便按列名取值。"""
    return psycopg.connect(DATABASE_URL, row_factory=dict_row, autocommit=True)


def query_one(sql, params=None):
    """通用单行查询，返回第一个 dict_row 或 None"""
    with connect() as conn, conn.cursor() as cur:
        cur.execute(sql, params)
        return cur.fetchone()


def insert_image(file_name: str, file_path: str) -> int:
    """插入一条图片元数据，返回自增主键 id。"""
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
