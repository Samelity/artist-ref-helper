import logging
import os
from logging.handlers import RotatingFileHandler


def setup_logger(
        name: str = "image_service",
        log_dir: str = "logs",
        level: int = logging.INFO,
        max_bytes: int = 10 * 1024 * 1024,  # 单文件最大 10MB
        backup_count: int = 5  # 保留最近 5 个历史文件
) -> logging.Logger:
    """
    初始化并返回一个带本地文件轮转功能的 Logger
    """
    logger = logging.getLogger(name)

    # 防止重复添加 handler（在 reload 或多处调用时很关键）
    if logger.handlers:
        return logger

    logger.setLevel(level)

    # 自动创建日志目录
    os.makedirs(log_dir, exist_ok=True)

    # 统一日志格式：时间 | 级别 | 模块:行号 | 消息
    formatter = logging.Formatter(
        "%(asctime)s | %(levelname)-8s | %(name)s:%(lineno)d | %(message)s",
        datefmt="%Y-%m-%d %H:%M:%S"
    )

    # 1. 文件 Handler（带轮转）
    log_file = os.path.join(log_dir, f"{name}.log")
    file_handler = RotatingFileHandler(
        log_file,
        maxBytes=max_bytes,
        backupCount=backup_count,
        encoding="utf-8"  # 防止中文乱码
    )
    file_handler.setLevel(level)
    file_handler.setFormatter(formatter)

    # 2. 控制台 Handler（开发/运维实时查看）
    console_handler = logging.StreamHandler()
    console_handler.setLevel(level)
    console_handler.setFormatter(formatter)

    logger.addHandler(file_handler)
    logger.addHandler(console_handler)

    return logger


# 全局单例，其他模块直接 from logger import logger 即可
logger = setup_logger()
