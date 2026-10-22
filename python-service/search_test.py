import requests

BASE_URL = "http://localhost:8000"
IMAGE_PATH = "images/0.png"  # 替换成你实际存在的图片路径


def search_by_text(query: str, topK: int = 5, threshold: float = 0.2):
    """文搜图"""
    print(f"\n=== 文搜图: {query} ===")
    resp = requests.post(
        f"{BASE_URL}/search/text",
        json={
            "query": query,
            "topK": topK,
            "threshold": threshold,
        },
    )
    resp.raise_for_status()
    for item in resp.json()["items"]:
        print(f"  id={item['id']}  score={item['score']:.4f}  path={item['path']}")


def search_by_image(image_path: str, topK: int = 5, threshold: float = 0.2):
    """图搜图"""
    print(f"\n=== 图搜图: {image_path} ===")
    with open(image_path, "rb") as f:
        resp = requests.post(
            f"{BASE_URL}/search/image",
            files={"file": (image_path, f, "image/jpeg")},
            data={"topK": topK, "threshold": threshold},
        )
    resp.raise_for_status()
    for item in resp.json()["items"]:
        print(f"  id={item['id']}  score={item['score']:.4f}  path={item['path']}")


if __name__ == "__main__":
    search_by_text("赛博朋克")
    search_by_image(IMAGE_PATH)