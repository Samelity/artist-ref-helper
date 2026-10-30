# syntax=docker/dockerfile:1

FROM python:3.11-slim

ENV PYTHONUNBUFFERED=1 \
    PYTHONDONTWRITEBYTECODE=1 \
    TZ=Asia/Shanghai

# libgomp1 / libgl1 / libglib2.0-0：torch、faiss、Pillow 的运行库
# build-essential + patch：cn_clip 固定依赖 lmdb==1.3.0，该版本没有 cp311 的 Linux wheel，
# 必须从源码编译，py-lmdb 的打包脚本需要 patch 和 C 编译器
RUN apt-get update \
    && DEBIAN_FRONTEND=noninteractive apt-get install -y --no-install-recommends \
        libgomp1 \
        libgl1 \
        libglib2.0-0 \
        tzdata \
        build-essential \
        patch \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /app
COPY python-service/requirements.txt ./
# pip 缓存挂在构建缓存里，不进镜像层；requirements.txt 变更时不必重新下载 torch
# 装完立即卸载编译工具，避免最终镜像多出几百 MB（同一 RUN 内删除不计入镜像层）
RUN --mount=type=cache,target=/root/.cache/pip \
    pip install --upgrade pip && pip install -r requirements.txt \
    && apt-get purge -y --auto-remove build-essential patch \
    && rm -rf /var/lib/apt/lists/*

COPY python-service/ ./

EXPOSE 8000

# 必须只开 1 个 worker：FAISS 索引和 id_map 是进程内内存对象，多 worker 会各存一份且不同步
CMD ["uvicorn", "search_service:app", "--host", "0.0.0.0", "--port", "8000", "--workers", "1"]
