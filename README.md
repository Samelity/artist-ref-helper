# 画师参考图助手 (Artist Reference Helper)

面向画师的 AI 参考图管理工具，基于 Chinese-CLIP 向量检索，支持**文搜图**、**图搜图**、**网络搜图**和**灵感板导出**。

## 功能概览

| 功能 | 说明 |
|------|------|
| 文搜图 | 输入中/英文文字描述，从本地图库中检索语义相似的参考图 |
| 图搜图 | 上传或拖入一张图片，检索风格/内容相似的参考图 |
| 网络搜图 | 从 Pixabay / Pexels 搜索远程图片，一键保存到本地并自动建立索引 |
| 灵感板 | 创建画板、收集图片、导出为 ZIP 压缩包 |

## 技术架构

```
┌─────────────────────────────────────────────────────┐
│  浏览器 (index.html)          port 8080 / 8000      │
└──────────┬──────────────────────────┬────────────────┘
           │ REST API                 │ 图片代理 / 网络搜图
           ▼                          ▼
┌─────────────────────┐    HTTP    ┌──────────────────────┐
│  Spring Boot 3.3.5  │◄──────────►│  Python FastAPI       │
│  (Java 17, 8080)    │           │  (8000)               │
│  - JPA / Hibernate  │           │  - Chinese-CLIP ViT-L  │
│  - WebClient        │           │  - FAISS 向量检索      │
│  - StreamingBody    │           │  - 图片编码 / 代理      │
└──────────┬──────────┘           └──────────┬─────────────┘
           │                                  │
           ▼                                  ▼
     ┌───────────┐                     ┌───────────┐
     │ PostgreSQL │◄───────────────────│  psycopg   │
     │  (5432)    │                     │  (raw SQL) │
     └───────────┘                     └───────────┘
```

- **Java 后端**：Spring Boot 提供 REST API、静态页面托管、灵感板 CRUD 和 ZIP 流式导出
- **Python 微服务**：FastAPI 加载 Chinese-CLIP 模型 + FAISS 索引，提供向量检索、图片代理、网络搜图和批量导入
- **数据库**：PostgreSQL 存储图片元数据和灵感板关系，Java 通过 JPA、Python 通过 psycopg 共同访问
- **前端**：原生 HTML/CSS/JS 单页面，无框架依赖

## 环境要求

| 依赖 | 版本 |
|------|------|
| JDK | 17+ |
| Python | 3.11+ |
| PostgreSQL | 12+ |
| Maven | 3.8+ |

## 快速开始

### 1. 配置环境变量

```bash
# 必需
set DB_PASSWORD=your_postgres_password
set SOURCE_DIR=C:\path\to\your\images    # 本地图片源目录（import_images.py 使用）

# 可选 - 网络搜图
set PIXABAY_API_KEY=your_pixabay_key
set PEXELS_API_KEY=your_pexels_key

# 可选 - 百度翻译（提升中文查询 Pixabay 搜索效果）
set BAIDU_APPID=your_baidu_appid
set BAIDU_APPKEY=your_baidu_appkey
```

### 2. 启动 PostgreSQL

确保 PostgreSQL 在 `localhost:5432` 运行，数据库名为 `postgres`。表结构由 Hibernate 自动创建（`ddl-auto: update`）。

### 3. 启动 Python 微服务

```bash
cd python-service

# 创建虚拟环境（首次）
python -m venv .venv
.venv\Scripts\activate

# 安装依赖
pip install -r requirements.txt

# 启动服务（端口 8000）
python search_service.py
```

> **Windows 用户注意**：cn_clip 安装可能需要先安装二进制 wheel，再 `pip install cn_clip --no-deps`。详见 [lmdb 构建问题](https://github.com/OFA-Sys/Chinese-CLIP/issues)。

### 4. 导入本地图片（必需）

**首次启动前必须执行此步骤**，否则 FAISS 索引不存在会导致服务异常。

设置环境变量并运行导入脚本：

```bash
# 设置图片源目录（必需）
set SOURCE_DIR=C:\path\to\your\images

cd python-service
python import_images.py
```

脚本会：
1. 从 `SOURCE_DIR` 复制图片到 `storage/` 目录
2. 写入 PostgreSQL 数据库
3. 用 CLIP 编码图片并构建 FAISS 索引

> **提示**：如果已有 `storage/` 目录和索引，可跳过此步骤。

### 5. 启动 Spring Boot

```bash
# 项目根目录
mvn spring-boot:run
```

### 6. 访问应用

浏览器打开 **http://localhost:8080** 即可使用。

## 项目结构

```
artist-ref-helper/
├── pom.xml                              # Maven 构建配置
├── src/main/java/com/example/artist/
│   ├── ArtistRefApplication.java        # Spring Boot 入口
│   ├── config/ClipConfig.java           # WebClient 配置
│   ├── controller/
│   │   ├── ImageController.java         # 图片搜索 API
│   │   └── BoardController.java         # 灵感板 API
│   ├── model/                           # JPA 实体
│   ├── repository/                      # Spring Data JPA
│   ├── service/
│   │   ├── ImageService.java            # 图片搜索业务逻辑
│   │   └── InspirationBoardService.java # 灵感板业务逻辑
│   └── dto/                             # 响应 DTO
├── src/main/resources/
│   ├── application.yml                  # 应用配置
│   ├── logback-spring.xml               # 日志配置
│   └── static/index.html               # 前端单页面
├── python-service/
│   ├── search_service.py                # FastAPI 主服务
│   ├── clip_embedding.py                # CLIP 模型加载与编码
│   ├── db.py                            # PostgreSQL 直连（psycopg）
│   ├── import_images.py                 # 批量图片导入脚本（首次启动前必需运行）
│   ├── requirements.txt                 # Python 依赖
│   ├── models/                          # Chinese-CLIP 模型权重
│   ├── storage/                         # 本地图片存储（统一目录）
│   └── index/                           # FAISS 索引文件
└── docs/                                # 设计文档
```

## API 接口

### 图片搜索（Spring Boot :8080）

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/images/search/text` | 文搜图（参数：`query`, `topK`, `threshold`） |
| POST | `/api/images/search/similar` | 图搜图（multipart 上传图片，`topK`, `threshold`） |
| POST | `/api/images/index` | 重建 FAISS 索引 |

### 灵感板（Spring Boot :8080）

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/boards` | 创建灵感板 |
| GET | `/api/boards` | 获取所有灵感板 |
| GET | `/api/boards/{boardId}` | 获取灵感板详情（含图片） |
| PUT | `/api/boards/{boardId}` | 更新灵感板 |
| DELETE | `/api/boards/{boardId}` | 删除灵感板 |
| POST | `/api/boards/{boardId}/images/{imageId}` | 添加图片到灵感板 |
| DELETE | `/api/boards/{boardId}/images/{imageId}` | 从灵感板移除图片 |
| GET | `/api/boards/{boardId}/export` | 导出灵感板为 ZIP |

### Python 微服务（:8000）

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/health` | 健康检查（返回索引向量数） |
| POST | `/search/text` | CLIP 文字检索 |
| POST | `/search/image` | CLIP 图片检索 |
| GET | `/api/images/view` | 图片文件代理 |
| GET | `/pixabay/search` | Pixabay 网络搜图 |
| GET | `/pexels/search` | Pexels 网络搜图 |
| POST | `/remote/save` | 保存远程图片并编码入库 |

## 配置说明

所有配置集中在 `src/main/resources/application.yml`，敏感信息通过环境变量注入：

| 配置项 | 环境变量 | 默认值 | 说明 |
|--------|----------|--------|------|
| 数据库密码 | `DB_PASSWORD` | - | PostgreSQL 密码（必需） |
| 数据库用户 | `DB_USER` | `postgres` | PostgreSQL 用户名 |
| Pixabay Key | `PIXABAY_API_KEY` | - | Pixabay API 密钥 |
| Pexels Key | `PEXELS_API_KEY` | - | Pexels API 密钥 |
| 百度翻译 ID | `BAIDU_APPID` | - | 中译英 App ID |
| 百度翻译密钥 | `BAIDU_APPKEY` | - | 中译英密钥 |

关键应用参数：

| 参数 | 默认值 | 说明 |
|------|--------|------|
| `clip.service.base-url` | `http://localhost:8000` | Python 服务地址 |
| `image.search.top-k` | `20` | 默认返回结果数 |
| `image.search.threshold` | `0.2` | 相似度阈值 |
| `image.search.timeout-seconds` | `30` | 搜索超时 |
| `image.search.index-timeout-seconds` | `300` | 索引重建超时 |
| `board.export.timeout-seconds` | `180` | ZIP 导出超时 |

## 核心模型

- **Chinese-CLIP ViT-L/14**：中英双语 CLIP 模型，支持跨语言图文语义匹配
- **FAISS IndexFlatIP**：基于内积（余弦相似度）的平铺向量索引，由 Python 服务管理
- **增量索引**：网络导入的新图片自动编码并追加到 FAISS，无需全量重建
