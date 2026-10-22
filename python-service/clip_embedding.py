import numpy as np
import torch
from pathlib import Path
from PIL import Image

IMAGE_EXTS = {".jpg", ".jpeg", ".png", ".webp"}


def list_images(image_dir: str):
    """列出目录下所有受支持格式的图片，按名称排序。"""
    image_dir = Path(image_dir)
    return [
        p for p in sorted(image_dir.iterdir())
        if p.is_file() and p.suffix.lower() in IMAGE_EXTS
    ]


def encode_one(model, preprocess, image: Image.Image, device: str) -> np.ndarray:
    """单张图片 -> L2 归一化后的向量，形状 (1, D)。"""
    tensor = preprocess(image.convert("RGB")).unsqueeze(0).to(device)
    with torch.no_grad():
        feat = model.encode_image(tensor)
    feat = feat / feat.norm(dim=-1, keepdim=True)
    return feat.cpu().numpy().astype(np.float32)


def encode_dir(model, preprocess, image_dir: str, device: str = "cpu"):
    """
    扫描目录，把所有图片向量化（模型由外部传入，不在这里加载）。
    返回:
        vectors: np.ndarray, shape=(N, D), 已 L2 归一化
        paths:   list[str], 与 vectors 行号一一对应的图片路径
    """
    image_paths = list_images(image_dir)
    if not image_paths:
        print(f"{image_dir} 下没有找到图片")
        return np.zeros((0, 0), dtype=np.float32), []

    embeddings = []
    valid_paths = []
    for path in image_paths:
        try:
            img = Image.open(path)
            vec = encode_one(model, preprocess, img, device)
            embeddings.append(vec)
            valid_paths.append(str(path))
        except Exception as e:
            print(f"Skip {path}: {e}")

    if not embeddings:
        return np.zeros((0, 0), dtype=np.float32), []

    vectors = np.vstack(embeddings)  # (N, D)
    print(f"Encoded {len(valid_paths)} images, dim={vectors.shape[1]}")
    return vectors, valid_paths
