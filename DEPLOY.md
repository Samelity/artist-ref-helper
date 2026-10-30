# 画师参考图助手 · Windows Docker 部署手册

适用场景：在局域网内的一台 Windows 电脑上用 Docker Desktop 部署，浏览器只访问宿主机 `8080`，PostgreSQL 和 Python 检索服务都不对外暴露。

> 本手册假设：
> - 项目目录：`D:\artist-ref-helper`
> - 数据目录：`D:\artist-data`
> - 部署机为 Windows 10/11 64 位，已开启虚拟化
>
> 路径不同时，请同步修改 `docker-compose.yml` 里的 volumes。
>
> 仓库已提供 `docker-compose.yml`；第 3 章其余 Docker 文件（两个 Dockerfile、`.dockerignore`、`.env.example`）仍需按内容创建。代码本身无需修改，直接构建即可。

---

## 0. 部署架构

| 组件 | 容器名 | 镜像 | 容器内端口 | 宿主端口 | 持久化 |
|------|--------|------|-----------|----------|--------|
| PostgreSQL | `artist-ref-postgres` | `postgres:16` | 5432 | **不发布** | 命名卷 `pgdata` |
| Python 检索服务 | `artist-ref-python` | 自建 `artist-ref-python:latest` | 8000 | **不发布** | `D:\artist-data\{storage,index,models}` |
| Spring Boot + 前端 | `artist-ref-app` | 自建 `artist-ref-app:latest` | 8080 | `8080` | 无（图片在 Python 侧目录） |
| pgAdmin（维护用） | `artist-ref-pgadmin` | `dpage/pgadmin4` | 80 | `127.0.0.1:5050` | 命名卷 `pgadmin-data` |

请求链路：

```
浏览器 ──> app:8080 ──/api/**──> PostgreSQL
                └────/py/**───> python:8000 ──> PostgreSQL
                                      └────────> /data/storage（图片文件）
```

要点：

- 浏览器只接触 `8080`，`/py/**` 由 Spring Boot 反向代理，因此不需要给 Python 开端口，也不需要配置 CORS。
- Python 和 PostgreSQL 都不要发布端口（调试时可临时绑定 `127.0.0.1`）。
- pgAdmin 只绑定宿主机回环 `127.0.0.1:5050`，只有部署机本机能打开；它通过 compose 网络用服务名 `postgres` 连数据库，所以同样不需要发布 5432。
- 图片、索引、模型全部放在宿主目录，重建容器不会丢；PostgreSQL 和 pgAdmin 的数据放在命名卷。

---

## 1. 部署机准备

### 1.1 硬件与系统

| 项目 | 最低 | 建议 |
|------|------|------|
| 系统 | Windows 10 2004+ / Windows 11 64 位 | Windows 11 |
| 内存 | 8 GB | 16 GB |
| 磁盘 | 20 GB 可用 | 30 GB 以上（镜像约 4–5 GB，模型 1.6 GB） |
| 网络 | 能访问 Docker Hub / npm / Maven / ModelScope | 首次构建和首次启动都需要联网 |

Chinese-CLIP ViT-L-14 在 CPU 上编码图片比较吃内存，批量导入时尤其明显，内存不要卡着 8 GB。

### 1.2 安装 WSL2 与 Docker Desktop

以**管理员身份**打开 PowerShell：

```powershell
wsl --install
wsl --set-default-version 2
wsl --status
```

按提示重启。然后安装 Docker Desktop（官网下载），安装时勾选使用 WSL 2 后端。

安装完成后确认：

```powershell
docker version
docker compose version
docker run --rm hello-world
```

Docker Desktop 设置建议：

- **General → Use the WSL 2 based engine**：保持勾选（默认跑 Linux 容器）。
- **General → Start Docker Desktop when you sign in**：勾选，否则重启后容器不会自动起来。
- 确认当前是 **Linux containers** 模式，不是 Windows containers。

### 1.3 调整 WSL2 资源（可选但推荐）

编辑 `C:\Users\<用户名>\.wslconfig`（没有就新建）：

```ini
[wsl2]
memory=10GB
processors=4
swap=2GB
```

然后执行 `wsl --shutdown`，重启 Docker Desktop 生效。

> 注意：不要把 `memory` 设得接近物理内存上限，Windows 本身还要用。机器只有 8 GB 内存时设 `6GB`，并尽量不要同时跑大型程序。

### 1.4 创建数据目录

在 PowerShell 中执行：

```powershell
New-Item -ItemType Directory -Force -Path D:\artist-data\storage,D:\artist-data\index,D:\artist-data\models,D:\artist-data\import
```

| 目录 | 用途 | 说明 |
|------|------|------|
| `storage` | 图片文件 | 数据库里只存**相对该目录**的路径，换机时整个目录拷走即可 |
| `index` | `image_embeddings.faiss`、`image_ids.json` | 必须和数据库内容保持一致 |
| `models` | Chinese-CLIP 模型缓存 | 不挂载的话，每次重建容器都会重新下载约 1.6 GB |
| `import` | 局域网共享图片源（可选） | 容器内只读挂载，供「共享目录导入」使用 |

> 注意：目录建议提前创建。Docker 也能自动创建缺失的 bind mount 目录，但会归 root 所有，排查问题时容易混淆。

### 1.5 防火墙与电源

以管理员身份放行 8080（只对专用/域网络开放）：

```powershell
New-NetFirewallRule -DisplayName "artist-ref 8080" -Direction Inbound -Protocol TCP -LocalPort 8080 -Action Allow -Profile Private,Domain
```

> 注意：
> - 这是**局域网内**使用的个人工具，不要做路由器端口映射，不要暴露到公网。
> - 如果部署机使用「公用网络」配置，防火墙规则不会生效，需要把网络改为专用，或临时把 `-Profile` 改成 `Any`。
> - 长期运行的机器建议把电源计划里的「睡眠」设为「从不」。
> - 本项目定位为个人/局域网非商业使用。网络搜图和导入的图片请遵守来源网站的版权与使用条款。

---

## 2. 代码准备（无需修改）

### 2.1 数据库地址已参数化

[src/main/resources/application.yml](src/main/resources/application.yml) 里的 datasource URL 已经是：

```yaml
url: jdbc:postgresql://${DB_HOST:localhost}:${DB_PORT:5432}/${DB_NAME:postgres}?stringtype=unspecified
```

- 本机开发不设 `DB_HOST`/`DB_PORT` 时，仍然连 `localhost:5432`，行为不变；
- Docker 里由 compose 注入 `DB_HOST=postgres`、`DB_PORT=5432`，直接连容器名；
- 不需要在部署前手改任何代码；
- `stringtype=unspecified` 不要丢，TIMESTAMPTZ 列依赖它。

> 注意：`spring.datasource.password` 是 `${DB_PASSWORD}`，**没有默认值**。这是故意的，缺密码时应用启动即失败，避免连到错误数据库。

### 2.2 其余配置已支持环境变量（确认过）

- Python 的 `DB_HOST` / `DB_PORT` / `DB_NAME` / `DB_USER` / `DB_PASSWORD` 已经支持环境变量（[python-service/db.py](python-service/db.py)）。
- Python 的路径配置已统一到 [python-service/paths.py](python-service/paths.py)：`STORAGE_DIR`、`MODEL_DIR`、`INDEX_DIR` 均由环境变量指定，启动时解析为绝对路径；`INDEX_PATH` / `IDS_PATH` 由 `INDEX_DIR` 派生；`IMPORT_DIR` 可选。
- Python 里的 `uvicorn.run(..., host="127.0.0.1")` 不会被用到：容器用 `uvicorn` 命令行启动并指定 `--host 0.0.0.0`（见 3.3）。
- Java 的 `clip.service.base-url` 通过环境变量 `CLIP_SERVICE_BASE_URL` 覆盖，不需要改 yml。

---

## 3. 新增 Docker 文件

在项目根目录创建以下文件。文件内容可以直接使用，注意保持 YAML 缩进。

### 3.1 `.dockerignore`

位置：`D:\artist-ref-helper\.dockerignore`

```dockerignore
# 版本与 IDE
.git
.gitignore
.idea
.vscode
**/*.iml

# 构建产物
target
src/main/resources/static

# 运行期数据（绝不能进镜像）
logs
docs
vibe_images
storage
models
index
python-service/storage
python-service/index
python-service/models
python-service/logs

# Python
**/__pycache__
**/*.pyc
**/.venv
**/venv

# 前端依赖
frontend/node_modules

# 密钥
.env

# 日志
*.log
```

> 注意：`.gitignore` 对 Docker 构建上下文**无效**。如果不写 `.dockerignore`，`python-service\models`（1.6 GB）、`frontend\node_modules`、`storage` 等都会被发送给 Docker，构建会非常慢。

### 3.2 根目录 `Dockerfile`

位置：`D:\artist-ref-helper\Dockerfile`

```dockerfile
# syntax=docker/dockerfile:1

# ---------- 1. 构建前端 ----------
FROM node:22-slim AS frontend
WORKDIR /build/frontend
COPY frontend/package.json frontend/package-lock.json ./
RUN npm ci
COPY frontend/ ./
RUN npm run build && test -f /build/src/main/resources/static/index.html

# ---------- 2. 构建 Spring Boot jar ----------
FROM maven:3.9-eclipse-temurin-17 AS backend
WORKDIR /build
COPY pom.xml ./
COPY src ./src
COPY --from=frontend /build/src/main/resources/static ./src/main/resources/static
RUN mvn -B clean package -DskipTests

# ---------- 3. 运行 ----------
FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=backend /build/target/artist-ref-helper-*.jar ./app.jar
ENV TZ=Asia/Shanghai
EXPOSE 8080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "/app/app.jar"]
```

说明：

- 前端构建产物由 Vite 输出到 `src/main/resources/static`，再复制进 Maven 构建目录，最终打进 jar，所以镜像里只有一个 Java 进程。
- `test -f .../index.html` 是为了在前端构建异常时尽早失败，避免打出一个没有页面的 jar。
- 运行镜像不需要 Node 和 Maven，最终镜像只包含 JRE 和 jar。

### 3.3 `python-service/Dockerfile`

位置：`D:\artist-ref-helper\python-service\Dockerfile`

```dockerfile
FROM python:3.11-slim

ENV PYTHONUNBUFFERED=1 \
    PYTHONDONTWRITEBYTECODE=1 \
    PIP_NO_CACHE_DIR=1 \
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
# 装完立即卸载编译工具，避免最终镜像多出几百 MB（同一 RUN 内删除不计入镜像层）
RUN pip install --upgrade pip && pip install -r requirements.txt \
    && apt-get purge -y --auto-remove build-essential patch \
    && rm -rf /var/lib/apt/lists/*

COPY python-service/ ./

EXPOSE 8000

# 必须只开 1 个 worker：FAISS 索引和 id_map 是进程内内存对象，多 worker 会各存一份且不同步
CMD ["uvicorn", "search_service:app", "--host", "0.0.0.0", "--port", "8000", "--workers", "1"]
```

说明：

- 基础镜像用 Python 3.11，与 `requirements.txt` 的注释一致。
- `libgomp1` 是 torch/faiss 需要的 OpenMP 运行库；`libgl1`/`libglib2.0-0` 给 Pillow/torchvision 用。
- `build-essential` 和 `patch` 只在 `pip install` 阶段需要（编译 lmdb），装完就 `purge`，不会被带进最终镜像；不装会报 `sh: 1: /usr/bin/patch: not found`。
- 不使用 `python search_service.py` 启动，因为代码里绑定的是 `127.0.0.1`；命令行参数覆盖为 `0.0.0.0`。
- `COPY python-service/ ./` 只决定代码位置（`/app`）。图片、索引、模型目录全部由 compose 的 `STORAGE_DIR` / `INDEX_DIR` / `MODEL_DIR` 指定为 `/data/**`，与代码目录、工作目录都解耦。

> 注意：首次 `pip install` 会下载 CPU 版 torch，耗时较长；国内网络慢时可以先在开发机把镜像构建好再 `docker save` 过去（见附录 B）。

### 3.4 `docker-compose.yml`

位置：`D:\artist-ref-helper\docker-compose.yml`（仓库已提供，内容如下）

```yaml
name: artist-ref

services:
  postgres:
    image: postgres:16
    container_name: artist-ref-postgres
    restart: unless-stopped
    environment:
      POSTGRES_DB: ${DB_NAME}
      POSTGRES_USER: ${DB_USER}
      POSTGRES_PASSWORD: ${DB_PASSWORD}
      TZ: Asia/Shanghai
    volumes:
      - pgdata:/var/lib/postgresql/data
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U $$POSTGRES_USER -d $$POSTGRES_DB"]
      interval: 10s
      timeout: 5s
      retries: 12

  python:
    build:
      context: .
      dockerfile: python-service/Dockerfile
    image: artist-ref-python:latest
    container_name: artist-ref-python
    restart: unless-stopped
    environment:
      DB_HOST: postgres
      DB_PORT: "5432"
      DB_NAME: ${DB_NAME}
      DB_USER: ${DB_USER}
      DB_PASSWORD: ${DB_PASSWORD}
      STORAGE_DIR: /data/storage
      MODEL_DIR: /data/models
      INDEX_DIR: /data/index
      IMPORT_DIR: /import
      PIXABAY_API_KEY: ${PIXABAY_API_KEY:-}
      PEXELS_API_KEY: ${PEXELS_API_KEY:-}
      BAIDU_APPID: ${BAIDU_APPID:-}
      BAIDU_APPKEY: ${BAIDU_APPKEY:-}
      TZ: Asia/Shanghai
    volumes:
      - "D:/artist-data/storage:/data/storage"
      - "D:/artist-data/index:/data/index"
      - "D:/artist-data/models:/data/models"
      - "D:/artist-data/import:/import:ro"
    depends_on:
      postgres:
        condition: service_healthy
    healthcheck:
      test: ["CMD", "python", "-c", "import urllib.request; urllib.request.urlopen('http://127.0.0.1:8000/docs', timeout=3)"]
      interval: 15s
      timeout: 5s
      retries: 20
      start_period: 300s

  app:
    build:
      context: .
      dockerfile: Dockerfile
    image: artist-ref-app:latest
    container_name: artist-ref-app
    restart: unless-stopped
    environment:
      DB_HOST: postgres
      DB_PORT: "5432"
      DB_NAME: ${DB_NAME}
      DB_USER: ${DB_USER}
      DB_PASSWORD: ${DB_PASSWORD}
      CLIP_SERVICE_BASE_URL: http://python:8000
      TZ: Asia/Shanghai
    ports:
      - "8080:8080"
    depends_on:
      postgres:
        condition: service_healthy

  pgadmin:
    image: dpage/pgadmin4:latest
    container_name: artist-ref-pgadmin
    restart: unless-stopped
    environment:
      PGADMIN_DEFAULT_EMAIL: ${PGADMIN_EMAIL}
      PGADMIN_DEFAULT_PASSWORD: ${PGADMIN_PASSWORD}
      PGADMIN_CONFIG_UPGRADE_CHECK_ENABLED: "False"
      TZ: Asia/Shanghai
    volumes:
      - pgadmin-data:/var/lib/pgadmin
    ports:
      # 只绑定部署机回环地址，局域网其他设备访问不到
      - "127.0.0.1:5050:80"
    depends_on:
      postgres:
        condition: service_healthy

volumes:
  pgdata:
  pgadmin-data:
```

几个关键点：

- 四个服务在同一个默认网络里，用服务名 `postgres`、`python` 互相访问。
- Python 的数据路径统一由环境变量指定：`STORAGE_DIR=/data/storage`、`MODEL_DIR=/data/models`、`INDEX_DIR=/data/index`，与代码目录 `/app` 解耦。
- 只有 `app` 对局域网发布端口；Python、PostgreSQL 不发布，局域网访问不到 8000/5432；pgAdmin 只绑定 `127.0.0.1:5050`，仅部署机本机可访问。
- Python 的 `start_period` 给了 300 秒，是因为首次启动要下载/加载模型；期间状态是 `starting`，不是故障。
- `pgdata` 和 `pgadmin-data` 是命名卷，`docker compose down` 不会删；只有 `down -v` 会删。
- 平时不需要 pgAdmin 时执行 `docker compose stop pgadmin`，可省下几百 MB 内存；下次 `docker compose up -d` 会重新启动它。
- 调试时如需用本机其他数据库工具直连，可临时给 postgres 加 `ports: ["127.0.0.1:5432:5432"]`，用完删掉。

### 3.5 `.env`

位置：`D:\artist-ref-helper\.env.example`（模板，可入库），实际运行用同目录的 `.env`（不可入库）。

新建 `.env.example`，内容如下：

```dotenv
# ===== 数据库（必填）=====
# 首次启动时 PostgreSQL 会用它初始化；之后改这里不会自动改已有库
DB_NAME=artist_ref
DB_USER=artist
DB_PASSWORD=ArtistRef_2026_ChangeMe

# ===== pgAdmin 登录（必填，仅部署机本机访问 127.0.0.1:5050）=====
PGADMIN_EMAIL=admin@example.com
PGADMIN_PASSWORD=ChangeMe_2026

# ===== 网络搜图（可选）=====
PIXABAY_API_KEY=
PEXELS_API_KEY=
BAIDU_APPID=
BAIDU_APPKEY=
```

然后：

```powershell
Copy-Item .env.example .env
notepad .env
```

> 注意：
> - `.env` 已在 `.gitignore` 里，不要提交，也不要打进镜像。
> - **数据库密码只使用字母、数字、下划线**。Python 的 `db.py` 是把密码直接拼进连接串的，密码里出现 `@`、`:`、`/`、`%`、`#`、空格会导致连接失败。
> - `.env` 保存为 UTF-8（不要带 BOM）。如果出现「密码明明正确却认证失败」，检查值末尾是否混入了不可见字符（如 CRLF 换行残留、多余空格）。
> - `.env` 必须和 `docker-compose.yml` 放在同一目录。所有 compose 命令都在项目根目录执行。
> - `PGADMIN_EMAIL` / `PGADMIN_PASSWORD` 是 pgAdmin 的登录账号，和数据库账号无关。它们只在 `pgadmin-data` 卷为空时生效；后续改 `.env` 不会改已有账号，需要在 pgAdmin 页面里改密码，或删掉该卷重来（只丢 pgAdmin 的服务器配置，不影响数据库）。

---

## 4. 数据库初始化

> 本章说明数据库的初始化规则。实际的自动创建发生在第 5 章第一次 `docker compose up -d` 时；4.3 的验证命令请在首次启动之后执行。

### 4.1 方式一：容器自动初始化（推荐）

compose 里的 `POSTGRES_DB` / `POSTGRES_USER` / `POSTGRES_PASSWORD` 会在 **数据卷为空时** 自动创建：

- 数据库：`artist_ref`
- 用户：`artist`（该实例的超级用户）
- 密码：`.env` 里的 `DB_PASSWORD`

不需要手动执行任何 SQL。第一次 `docker compose up -d` 时自动完成。

### 4.2 方式二：手动创建（已有 PostgreSQL 或想改库名时）

如果数据库实例已经存在（比如宿主机上另装了 PostgreSQL），用管理员连接后执行：

```sql
CREATE ROLE artist LOGIN PASSWORD 'ArtistRef_2026_ChangeMe';
CREATE DATABASE artist_ref OWNER artist;
```

然后把 `.env` 的 `DB_NAME` / `DB_USER` / `DB_PASSWORD` 改成对应值。

### 4.3 验证（首次启动后执行）

```powershell
docker compose ps
docker compose exec postgres psql -U artist -d artist_ref -c "\l"
```

应用启动后，再确认表已由 Hibernate 创建：

```powershell
docker compose exec postgres psql -U artist -d artist_ref -c "\dt"
```

应能看到 `reference_image`、`boards`、`board_images` 等表。

> 注意：
> - Python 侧**不会建表**，表由 Spring Boot 的 `ddl-auto: update` 创建。空库第一次必须先让 `app` 启动一次。
> - PostgreSQL 的初始化变量只在 `pgdata` 卷为空时生效。以后修改 `.env` 里的 `DB_NAME` 不会自动新建库，改 `DB_PASSWORD` 也不会改已有用户的密码，需要手动 `CREATE DATABASE` / `ALTER USER`，或者删卷重来（会丢数据）。
> - 不要随意升级 `postgres` 大版本（如 16 → 17）后继续用旧 `pgdata`，数据目录不兼容。

---

## 5. 构建与启动

### 5.1 构建镜像

在项目根目录执行：

```powershell
cd D:\artist-ref-helper
docker compose build
```

首次构建通常需要 10–30 分钟，主要耗时在：

- 拉取 `node:22-slim`、`maven:3.9-eclipse-temurin-17`、`python:3.11-slim`；
- `npm ci` 安装前端依赖；
- Maven 下载依赖；
- pip 下载 torch 等依赖。

`postgres:16` 和 `dpage/pgadmin4` 不在构建阶段拉取，而是在第一次 `docker compose up` 时获取。

构建完成后可以用 `docker images` 看到 `artist-ref-app` 和 `artist-ref-python`。

### 5.2 启动

```powershell
docker compose up -d
docker compose ps
```

启动顺序：

1. `postgres` 先启动并通过 `pg_isready` 健康检查；
2. `app`、`python`、`pgadmin` 随后并行启动：`app` 由 Hibernate 自动建表，`python` 加载/下载 Chinese-CLIP 模型并读取 FAISS 索引（启动阶段不访问数据库），`pgadmin` 初始化自己的配置。三者互不阻塞。

因此正常情况下先打开页面、再等 python 变成 `healthy` 即可；`app`、`python`、`pgadmin` 三者互不阻塞。

查看日志：

```powershell
docker compose logs -f --tail=100 app python
```

看到 Spring Boot 的 `Started ...` 和 Python 的 `Image Service started successfully` 即启动完成。

> 注意：Python 首次启动要下载模型（约 1.6 GB），可能持续几分钟。在这之前通过页面调用搜索/上传会得到 `502 Python 服务不可用`，属正常现象，等 `docker compose ps` 里 python 变成 `healthy` 再操作。

### 5.3 首次使用与索引

浏览器打开 `http://localhost:8080`，或局域网其他电脑打开 `http://<部署机IP>:8080`。

全新部署时 FAISS 索引是空的，必须先产生数据：

- **方式 A（推荐）**：左侧「导入图片」→ 批量上传本地图片，系统会自动编码并追加索引；
- **方式 B**：确认数据库里已有图片记录后，点左侧栏底部的「重建索引」；
- **方式 C**：把图片源目录挂到 `/import`，使用「共享目录导入」。

索引文件会写入 `D:\artist-data\index`，重启容器后自动加载。

> 注意：
> - 如果数据库里有记录但索引文件丢失，搜索会返回空或不准确的结果，点一次「重建索引」即可。CPU 上重建较慢，图片多时请耐心等待。
> - 批量上传经过 Spring Boot 的 `/py` 代理，等待响应头的超时是 300 秒（`clip.service.proxy-timeout-seconds`）。一次不要传太多，建议分批。
> - 索引和数据库必须来自同一时间点。只恢复数据库、不恢复索引时，务必重建索引。

### 5.4 可选：在容器里跑导入脚本

正常情况下用页面导入即可。如果需要沿用旧的 `import_images.py` 流程：

```powershell
docker compose stop python
docker compose run --rm -e SOURCE_DIR=/import python python import_images.py
docker compose start python
```

`/import` 对应 `D:\artist-data\import`（只读挂载），脚本会把图片复制到 `/data/storage` 并写入数据库和索引。

> 注意：必须在 `app` 已经启动过一次（表已创建）之后执行。脚本运行期间不要同时让 Python 服务写索引，所以先 `stop python`。

---

## 6. 验收清单

| # | 操作 | 期望结果 |
|---|------|----------|
| 1 | 打开 `http://localhost:8080` | 页面正常显示，无白屏 |
| 2 | 「导入图片」上传 1–2 张 | 上传成功，进度正常结束 |
| 3 | 「图库浏览」 | 出现刚上传的图片，最新在前，每页 20 张 |
| 4 | 点击图片缩略图 | 大图能正常加载（走 `/py/images/view`） |
| 5 | 文搜图 / 图搜图 | 返回结果，缩略图正常 |
| 6 | 「重建索引」 | 提示完成，数量与图库一致 |
| 7 | 重启容器：`docker compose restart` | 图片、索引、数据库记录都还在 |
| 8 | 局域网另一台电脑访问 `http://<IP>:8080` | 能打开页面并使用 |
| 9 | 局域网访问 `http://<IP>:8000` | 无法访问（符合预期，Python 未发布端口） |
| 10 | `docker compose ps` | app 为 `running`，python 为 `healthy`，postgres 为 `healthy` |

---

## 7. 日常运维

### 7.1 查看日志

```powershell
docker compose logs -f --tail=200 app
docker compose logs -f --tail=200 python
```

Python 还会在容器内写轮转文件日志，可单独查看：

```powershell
docker compose exec python tail -n 200 /app/logs/image_service.log
```

> 文件日志在容器内，重建容器会丢失；需要长期保留可给 python 增加挂载 `D:/artist-data/logs:/app/logs`。

### 7.2 更新版本（代码变更后重新部署）

容器的代码是打进镜像里的，改了代码必须重建镜像再替换容器；图片、索引、模型在宿主目录，数据库在命名卷，重建都不会丢。

先按改动范围决定重建谁：

| 改动内容 | 操作 |
|----------|------|
| Java / 前端 | 重建 `app` |
| Python（含 `requirements.txt`） | 重建 `python` |
| 只改 `docker-compose.yml` / `.env` | **不用 build**，直接 `docker compose up -d` |
| 只改 `DEPLOY.md` 等文档 | 不用操作 |
| CLIP 模型、向量维度或相似度逻辑 | 重建镜像后再点一次「重建索引」 |

```powershell
cd D:\artist-ref-helper

# 1. 可选：给当前镜像留回滚标签
docker tag artist-ref-app:latest    artist-ref-app:backup-20261030
docker tag artist-ref-python:latest artist-ref-python:backup-20261030

# 2. 更新源码：clone 的仓库用 git pull；
#    拷文件部署的用 robocopy 同步源码，排除 .env、storage、index、models、logs、target
git pull

# 3. 按范围重建（不确定就 docker compose build 全量）
docker compose build app
docker compose build python

# 4. 应用新镜像，只会重建镜像变了的容器
docker compose up -d

# 5. 验证
docker compose ps
docker compose logs --tail=200 app python
```

`up -d` 重建容器时服务会中断几秒，局域网个人使用不用追求零停机。

> 注意：
> - **不要执行 `docker compose down -v`**，`-v` 会把 `pgdata` / `pgadmin-data` 卷一起删掉，数据库直接丢。
> - 依赖和 Dockerfile 没变时不要加 `--no-cache`，否则 torch 那层会重新下载；只有改了 `requirements.txt` 或 Dockerfile 才需要。
> - 模型缓存在 `D:\artist-data\models`，重建容器不会重新下载。
> - 表结构由 Hibernate `ddl-auto: update` 自动加表、加列；删列、改类型、重命名不会自动处理，这类改动要自己写 SQL。
> - 回滚：把备份标签重新打回 `latest`（如 `docker tag artist-ref-app:backup-20261030 artist-ref-app:latest`），再 `docker compose up -d`。
> - 更新前建议先做一次备份（见 7.3）。

### 7.3 备份

备份 = 数据库导出 + 三个数据目录。为保证索引和数据库一致，先停掉写入方：

```powershell
New-Item -ItemType Directory -Force -Path D:\artist-backup | Out-Null
docker compose stop app python

# 数据库（用容器内文件再拷出来，避免 PowerShell 重定向破坏二进制）
docker compose exec postgres pg_dump -U artist -d artist_ref -Fc -f /tmp/artist_ref.dump
docker cp artist-ref-postgres:/tmp/artist_ref.dump D:\artist-backup\artist_ref.dump

# 图片、索引、模型
robocopy D:\artist-data\storage D:\artist-backup\storage /E
robocopy D:\artist-data\index   D:\artist-backup\index   /E
robocopy D:\artist-data\models  D:\artist-backup\models  /E

docker compose start app python
```

> 注意：
> - 不要用 `pg_dump ... > backup.sql` 的方式在 PowerShell 里导出**二进制格式**，重定向会破坏文件。上面的 `-f` + `docker cp` 是安全的。
> - `robocopy` 返回码 `1` 表示「成功复制了文件」，不是错误，不要被它吓到。

### 7.4 恢复

> 以下操作会覆盖现有数据库，执行前确认备份文件存在。

```powershell
docker compose stop app python

docker compose exec postgres psql -U artist -d postgres -c "DROP DATABASE IF EXISTS artist_ref WITH (FORCE);"
docker compose exec postgres psql -U artist -d postgres -c "CREATE DATABASE artist_ref OWNER artist;"

docker cp D:\artist-backup\artist_ref.dump artist-ref-postgres:/tmp/artist_ref.dump
docker compose exec postgres pg_restore -U artist -d artist_ref --no-owner /tmp/artist_ref.dump

robocopy D:\artist-backup\storage D:\artist-data\storage /E
robocopy D:\artist-backup\index   D:\artist-data\index   /E

docker compose start app python
```

如果数据库和索引不是同一时间点的备份，恢复后点一次「重建索引」。

### 7.5 停止与清理

```powershell
# 停止并删除容器，保留 pgdata 卷和宿主数据
docker compose down

# 停止但保留容器
docker compose stop

# 危险：连数据库卷一起删除，数据全部丢失
docker compose down -v
```

清理无用镜像（不影响运行中的容器）：

```powershell
docker image prune -f
```

查看资源占用：

```powershell
docker stats
```

### 7.6 用 pgAdmin 维护数据库

pgAdmin 已在 compose 里，不需要给 PostgreSQL 发布 5432 端口。在**部署机**上打开 `http://127.0.0.1:5050`（局域网其他设备打不开，这是故意的），用 `.env` 里的 `PGADMIN_EMAIL` / `PGADMIN_PASSWORD` 登录。

首次使用需要注册一次服务器，之后配置会保存在 `pgadmin-data` 卷里：

| 字段 | 值 |
|------|-----|
| Name | `artist-ref`（随意） |
| Host name/address | `postgres`（compose 服务名，不要填 localhost） |
| Port | `5432` |
| Maintenance database | `postgres` |
| Username | `.env` 里的 `DB_USER`（手册示例是 `artist`） |
| Password | `.env` 里的 `DB_PASSWORD` |

常见操作：

- 表数据浏览 / 修改：`artist_ref` → Schemas → public → Tables → `reference_image`；
- 执行 SQL：右键 `artist_ref` → Query Tool；
- 备份：右键 `artist_ref` → Backup，格式选 Custom，等价于 7.3 的 `pg_dump`。

> 注意：
> - 不需要时执行 `docker compose stop pgadmin` 释放内存，下次 `docker compose up -d` 会重新启动。
> - 登录失败时先确认 `.env` 里的两个 pgAdmin 变量非空，并 `docker compose up -d pgadmin` 让容器按新环境变量重建；如果之前已经初始化过卷，旧密码优先，见 3.5 的说明。
> - pgAdmin 只是数据库客户端，删掉 `pgadmin-data` 卷只丢保存的服务器配置，不影响 `artist_ref` 数据。

---

## 8. 常见问题

### 8.1 页面提示「502 Python 服务不可用」

依次检查：

```powershell
docker compose ps
docker compose logs --tail=200 python
```

- python 还在 `starting`：在加载模型，等几分钟；
- 启动即退出：看日志里的下载失败、OOM、依赖错误；
- 健康检查用的是 Python 自带的 `urllib`，镜像里不需要额外安装 `curl`；
- 确认 app 的环境变量 `CLIP_SERVICE_BASE_URL=http://python:8000`，且 python 容器确实监听 `0.0.0.0:8000`。

### 8.2 Python 容器反复重启或退出码 137

多为内存不足（OOM）。增大 WSL2 内存（1.3），避免同时跑大批量导入，或减少单次上传数量。

### 8.3 数据库连接失败

- `password authentication failed`：`pgdata` 卷是用旧密码初始化的。修改 `.env` 不会改已有密码。要么：

  ```powershell
  docker compose exec postgres psql -U artist -d artist_ref -c "ALTER USER artist WITH PASSWORD '新密码';"
  ```

  改完后同步更新 `.env` 里的 `DB_PASSWORD`，再 `docker compose up -d` 重建应用容器；两边不一致会继续报认证失败。要么删卷重建（丢数据）。

- `database "artist_ref" does not exist`：`POSTGRES_DB` 只在空卷首次初始化时生效。手动 `CREATE DATABASE artist_ref OWNER artist;`，或删卷重建。
- `Connection refused`：确认 `DB_HOST=postgres`，且 postgres 处于 `healthy`。

### 8.4 图片打不开（404/403）

- 404：数据库里的 `file_path` 必须能拼到 `STORAGE_DIR` 下。检查 `D:\artist-data\storage` 里是否有对应文件，compose 是否把该目录挂到 `/data/storage`。
- 403：请求路径超出了 `STORAGE_DIR` 白名单，通常是数据库里存了绝对路径或带 `..` 的路径。确认数据来自「相对路径」版本。
- 旧数据迁移时如果数据库记录里是绝对路径，需要清空重来（当前版本不考虑旧数据兼容）。

### 8.5 搜索没有结果

- 索引为空：上传图片或点「重建索引」；
- 数据库有数据但 `D:\artist-data\index` 为空：点「重建索引」；
- 相似度阈值偏高：默认 `image.search.threshold: 0.2`，按需调整。

### 8.6 图库浏览为空

`/api/images/browse` 直接查数据库。用 4.3 的 `\dt` 和 `SELECT count(*) FROM reference_image;` 确认表和数据是否存在。表不存在说明 app 没成功启动过。

### 8.7 上传中途失败或超时

- 大批量上传建议分批；
- 代理等待响应头超时由 `clip.service.proxy-timeout-seconds` 控制，默认 300 秒；
- Python 在 CPU 上逐张编码，速度取决于图片数量和机器性能；
- 查看 `docker compose logs python` 是否有编码失败的文件。

### 8.8 8080 端口被占用

```powershell
netstat -ano | findstr :8080
```

换一个宿主端口即可：

```yaml
ports:
  - "18080:8080"
```

### 8.9 前端页面白屏或 404

- 确认构建日志里有 `npm run build` 成功，且 Dockerfile 里 `test -f .../static/index.html` 没有失败；
- 确认访问的是 `http://IP:8080/`，不要直接访问子路径；
- 前端使用 hash 路由，不需要服务端 history fallback。

### 8.10 前端构建在 `vue-tsc` 阶段失败

`frontend/src/components.d.ts` 由 `unplugin-vue-components` 在构建时生成，仓库里不提交它；已实测缺失时 `vue-tsc` 仍能通过。若遇到与自动导入组件相关的类型错误，先删掉 `frontend/node_modules/.vite` 和 `frontend/src/components.d.ts` 重新构建；实在卡住可把根目录 Dockerfile 里的 `npm run build` 临时改成 `npx vite build`（跳过类型检查，仅影响构建，不影响运行）。

### 8.11 部署机重启后服务没起来

Docker Desktop 默认要登录 Windows 后才启动。打开 Docker Desktop 设置里的「登录时启动」，或登录后手动启动 Docker Desktop，容器会按 `restart: unless-stopped` 自动恢复。

### 8.12 局域网访问不了

- 确认用的是部署机的 IPv4 地址，不是 `localhost`；
- 检查 1.5 的防火墙规则和网络配置文件（专用/公用）；
- 确认 `docker compose ps` 里 app 的端口映射是 `0.0.0.0:8080->8080/tcp`。

### 8.13 仍然看到 `WARNING: Unsupported upgrade request`

说明运行的是旧 jar。当前代码已强制 Java 代理使用 HTTP/1.1，重新 `docker compose build app` 并 `up -d`。

### 8.14 拉取基础镜像失败或超时

- 在 Docker Desktop → Settings → Docker Engine 里配置你所在网络可用的 `registry-mirrors`，Apply & Restart 后重试；
- 或者在能正常拉取的机器上按附录 B 用 `docker save` / `docker load` 搬运镜像。

### 8.15 网络搜图失败，但本地搜索正常

- Python 容器需要能访问外网（Pixabay / Pexels / 百度翻译）；
- 没有配置 `PIXABAY_API_KEY` 时网络搜图不可用，本地图库检索不受影响；
- Pexels 是备用源，Key 失效不影响 Pixabay 和本地功能。

### 8.16 Python 构建失败：`/usr/bin/patch: not found`

`cn_clip` 固定依赖 `lmdb==1.3.0`，该版本在 PyPI 上没有适用于 Python 3.11 的 Linux wheel，`pip` 只能下载源码包现场编译。`python:3.11-slim` 不含编译工具，于是报：

```
sh: 1: /usr/bin/patch: not found
Exception: Applying patch failed
```

按 3.3 的 Dockerfile 安装 `build-essential` 和 `patch` 即可。不要尝试升级 lmdb：`cn_clip` 对其是 `lmdb==1.3.0` 精确锁定。

---

## 附录 A：环境变量总表

| 变量 | 使用方 | 默认值 | 是否必填 | 说明 |
|------|--------|--------|----------|------|
| `DB_NAME` | Java / Python | `postgres`（yml、db.py） | 是（compose 中传 `artist_ref`） | 数据库名 |
| `DB_USER` | Java / Python | `postgres` | 是 | 数据库用户 |
| `DB_PASSWORD` | Java / Python | 无 | 是 | 数据库密码，只含字母数字下划线 |
| `DB_HOST` | Java / Python | `localhost` | compose 传 `postgres` | 数据库主机 |
| `DB_PORT` | Java / Python | `5432` | 否 | 数据库端口 |
| `STORAGE_DIR` | Python | `<python-service>/storage` | compose 传 `/data/storage` | 图片根目录；数据库只存相对路径 |
| `MODEL_DIR` | Python | `<python-service>/models` | compose 传 `/data/models` | Chinese-CLIP 模型缓存目录 |
| `INDEX_DIR` | Python | `<python-service>/index` | compose 传 `/data/index` | FAISS 索引和 id 映射目录；`INDEX_PATH`/`IDS_PATH` 由它派生 |
| `IMPORT_DIR` | Python | 无 | 否 | 共享目录导入的来源目录 |
| `CLIP_SERVICE_BASE_URL` | Java | `http://127.0.0.1:8000` | compose 传 `http://python:8000` | Python 服务地址 |
| `PGADMIN_EMAIL` / `PGADMIN_PASSWORD` | pgAdmin | 无 | 是 | pgAdmin 登录账号，仅首次初始化 `pgadmin-data` 卷时生效 |
| `PIXABAY_API_KEY` | Python | 空 | 否 | Pixabay 网络搜图 |
| `PEXELS_API_KEY` | Python | 空 | 否 | Pexels 备用源（Key 失效不影响本地检索） |
| `BAIDU_APPID` / `BAIDU_APPKEY` | Python | 空 | 否 | 中文查询转英文 |
| `SOURCE_DIR` | Python（仅 import_images.py） | 无 | 否 | 一次性导入脚本的源目录 |

`STORAGE_DIR` / `MODEL_DIR` / `INDEX_DIR` / `IMPORT_DIR` 统一在 [python-service/paths.py](python-service/paths.py) 中解析为绝对路径，开发环境不设置时落在 `python-service/` 下。

关键应用参数（在 `application.yml` 中，不需要环境变量）：

| 参数 | 默认值 | 说明 |
|------|--------|------|
| `clip.service.proxy-timeout-seconds` | 300 | `/py/**` 代理等待 Python 响应头 |
| `image.search.index-timeout-seconds` | 300 | 重建索引超时 |
| `image.browse.page-size` | 20 | 图库每页数量 |

---

## 附录 B：离线 / 慢网络部署

如果部署机拉取镜像或依赖很慢：

1. 在开发机（能联网）上先构建：

   ```powershell
   docker compose build
   docker save artist-ref-app:latest artist-ref-python:latest -o D:\artist-images.tar
   docker pull postgres:16
   docker save postgres:16 -o D:\postgres-16.tar
   ```

2. 把两个 tar 和项目目录拷到部署机，导入：

   ```powershell
   docker load -i D:\artist-images.tar
   docker load -i D:\postgres-16.tar
   cd D:\artist-ref-helper
   docker compose up -d --no-build
   ```

3. 模型：开发机的 `python-service\models`（约 1.6 GB）可以整体拷到 `D:\artist-data\models`：

   ```powershell
   robocopy C:\Develop\artist-ref-helper\python-service\models D:\artist-data\models /E
   ```

   这样首次启动不需要再下载。注意模型目录里应直接是 `clip_cn_vit-l-14.pt` 等文件/目录结构。

如果只是慢、还能联网，可以在 Dockerfile 里临时换国内源：

- npm：`RUN npm ci --registry=https://registry.npmmirror.com`
- pip：`RUN pip install -i https://pypi.tuna.tsinghua.edu.cn/simple -r requirements.txt`（`requirements.txt` 里 PyTorch 的 `--extra-index-url` 仍然有效）
- Maven：在 `maven` 构建阶段挂载或用 `settings.xml` 配置阿里云镜像

> 注意：`docker save` 导出的镜像包含 CPU 架构信息，开发机和部署机都跑 Linux 容器（Windows Docker Desktop 的默认模式）时没有问题。不要跨 ARM/x86 混用。

---

## 附录 C：迁移已有数据

数据库里存的是相对于 `STORAGE_DIR` 的路径，所以**不需要改写任何路径**，整个目录拷过去即可：

1. 停服务：

   ```powershell
   docker compose stop app python
   ```

2. 从开发机拷贝数据：

   ```powershell
   robocopy C:\Develop\artist-ref-helper\python-service\storage D:\artist-data\storage /E
   robocopy C:\Develop\artist-ref-helper\python-service\index   D:\artist-data\index   /E
   robocopy C:\Develop\artist-ref-helper\python-service\models  D:\artist-data\models  /E
   ```

3. 导出/导入数据库（参考 7.3、7.4）。

4. 启动后检查：

   ```powershell
   docker compose up -d
   docker compose exec postgres psql -U artist -d artist_ref -c "SELECT count(*) FROM reference_image;"
   ```

> 注意：数据库和索引必须来自同一时间点。只拷了数据库和图片、没拷索引时，启动后点「重建索引」；只拷索引不拷数据库会导致搜索结果与图片对不上。

---

## 附录 D：数据库放在 Docker 外（可选）

如果不想把 PostgreSQL 放进 Docker，而是装在部署机上：

1. 安装 PostgreSQL 16，创建用户和库（同 4.2）。
2. 修改 `docker-compose.yml`：
   - 删除 `postgres` 服务；
   - 删除 `python` 和 `app` 的 `depends_on`；
   - 把两处 `DB_HOST` 改成 `host.docker.internal`（Docker Desktop 提供的宿主机别名）。
3. 确保 PostgreSQL 的 `pg_hba.conf` 允许来自 Docker 网段的连接，并在 Windows 防火墙放行 5432（只对局域网）。
4. `.env` 保持 `DB_NAME` / `DB_USER` / `DB_PASSWORD` 不变。

> 注意：这种模式下数据库多了一个对外监听的 5432 端口，安全面更大。个人局域网使用可以接受，但不要把 5432 暴露到公网。

---

## 附录 E：部署检查清单（可打印）

- [ ] 部署机已安装 Docker Desktop（WSL2 后端，Linux 容器）
- [ ] `docker version`、`docker compose version`、`hello-world` 正常
- [ ] WSL2 内存已按机器配置调整（建议 ≥ 8 GB 给 Docker）
- [ ] `D:\artist-data\{storage,index,models,import}` 已创建
- [ ] 防火墙已放行 8080（只限专用网络）
- [ ] 已确认无需修改代码（datasource URL 已参数化，`DB_HOST` / `DB_PORT` 由环境变量注入）
- [ ] 已按 3.1–3.3、3.5 创建 `.dockerignore`、`Dockerfile`、`python-service/Dockerfile`、`.env.example`
- [ ] 已从 `.env.example` 创建 `.env`，数据库密码只含字母数字下划线
- [ ] `docker compose build` 成功
- [ ] `docker compose up -d` 后四个容器状态正常（python healthy）
- [ ] 8080 页面可打开，局域网另一台机器也能访问
- [ ] 部署机上 `http://127.0.0.1:5050` 能登录 pgAdmin 并看到 `artist_ref`（可选）
- [ ] 已上传/导入至少一张图片并完成索引
- [ ] 图库浏览、搜索、图片查看均正常
- [ ] 已做一次数据库 + storage + index 备份
