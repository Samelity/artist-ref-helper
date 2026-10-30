# syntax=docker/dockerfile:1

# ---------- 1. 构建前端 ----------
FROM node:22-slim AS frontend
WORKDIR /build/frontend
COPY frontend/package.json frontend/package-lock.json ./
RUN --mount=type=cache,target=/root/.npm npm ci
COPY frontend/ ./
RUN npm run build && test -f /build/src/main/resources/static/index.html

# ---------- 2. 构建 Spring Boot jar ----------
FROM maven:3.9-eclipse-temurin-17 AS backend
WORKDIR /build
# 依赖层只由 pom.xml 决定：改源码或前端不会触发重新下载依赖
COPY pom.xml ./
RUN --mount=type=cache,target=/root/.m2,sharing=locked \
    mvn -B -q dependency:go-offline
COPY src ./src
COPY --from=frontend /build/src/main/resources/static ./src/main/resources/static
RUN --mount=type=cache,target=/root/.m2,sharing=locked \
    mvn -B -DskipTests package

# ---------- 3. 运行 ----------
FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=backend /build/target/artist-ref-helper-*.jar ./app.jar
ENV TZ=Asia/Shanghai
EXPOSE 8080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "/app/app.jar"]
