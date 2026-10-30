import os
from pathlib import Path

# python-service 代码目录；未显式配置时，数据目录仍放在它下面
BASE_DIR = Path(__file__).resolve().parent


def _resolve_dir(env_name: str, default: Path) -> Path:
    """环境变量优先，统一解析成绝对路径，避免依赖启动时的工作目录。"""
    return Path(os.environ.get(env_name) or default).expanduser().resolve()


# 图片实际存放目录；数据库中只保存相对该目录的路径
STORAGE_DIR = str(_resolve_dir("STORAGE_DIR", BASE_DIR / "storage"))
# Chinese-CLIP 模型缓存目录
MODEL_DIR = _resolve_dir("MODEL_DIR", BASE_DIR / "models")
# FAISS 索引及 id 映射
INDEX_DIR = _resolve_dir("INDEX_DIR", BASE_DIR / "index")
INDEX_PATH = str(INDEX_DIR / "image_embeddings.faiss")
IDS_PATH = str(INDEX_DIR / "image_ids.json")

# 局域网共享导入目录（可选）
_import_dir = os.environ.get("IMPORT_DIR")
IMPORT_DIR = str(Path(_import_dir).expanduser().resolve()) if _import_dir else None


def resolve_storage_path(stored_path: str) -> str:
    """把数据库里的相对路径拼成实际磁盘路径。

    os.path.join 遇到绝对路径会原样返回，调用方仍需做白名单校验。
    """
    return os.path.realpath(os.path.join(STORAGE_DIR, stored_path))
