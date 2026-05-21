# Docker 镜像优化指南

<!-- 版本信息 -->
> **文档版本**: 1.1.0
> **最后更新**: 2026-05-21
> **Git 提交**: 61384b4a
> **作者**: Lincoln
<!-- /版本信息 -->

## 概述

JAiRouter 提供优化的 Docker 镜像构建方案，通过多阶段构建和 Alpine 基础镜像，将镜像体积从 440MB 降至 **281MB**（减少 36%）。

### JLink 优化方案说明

> **注意**：JLink 方案尝试使用 `jlink` 工具定制精简 JRE 模块，但由于 Spring Boot 3.x 的模块兼容性问题，最终采用 Alpine JRE + 多阶段构建 + JVM 参数优化的方案。
> 
> JLink 版镜像 (`Dockerfile.jlink`) 与优化版 (`Dockerfile.optimized`) 大小相同（281MB），作为实验性方案提供，供有特殊需求的用户参考。

## 镜像对比

| 镜像类型 | Dockerfile | 大小 | 基础镜像 | 构建方式 | 推荐度 |
|----------|-----------|------|----------|----------|--------|
| **优化版** | `Dockerfile.optimized` | **281MB** | `eclipse-temurin:17-jre-alpine` | 多阶段 + layertools | ⭐⭐⭐⭐⭐ |
| **JLink 版** | `Dockerfile.jlink` | **281MB** | `eclipse-temurin:17-jre-alpine` | 多阶段 + JVM 优化 | 🔬实验性 |
| 标准版 | `Dockerfile` | 440MB | `eclipse-temurin:17-jre` | 单阶段 | ⭐⭐⭐ |

## 优化技术

### 1. Alpine 基础镜像

使用 `eclipse-temurin:17-jre-alpine` 替代标准 JRE 镜像：

```dockerfile
# 优化版
FROM eclipse-temurin:17-jre-alpine

# 标准版
FROM eclipse-temurin:17-jre
```

**优势**：
- Alpine 镜像仅 5MB，而 Debian 基础镜像约 120MB
- 更小的攻击面，提高安全性
- 更快的拉取速度

### 2. 多阶段构建

使用两个构建阶段分离构建和运行时环境：

```dockerfile
# 阶段 1: 构建阶段
FROM eclipse-temurin:17-jre-alpine AS extract-layer
COPY target/model-router-*.jar app.jar
RUN java -Djarmode=layertools -jar app.jar extract --destination extracted

# 阶段 2: 运行阶段
FROM eclipse-temurin:17-jre-alpine
COPY --from=extract-layer /app/extracted/dependencies/ ./
COPY --from=extract-layer /app/extracted/spring-boot-loader/ ./
COPY --from=extract-layer /app/extracted/snapshot-dependencies/ ./
COPY --from=extract-layer /app/extracted/application/ ./
```

**优势**：
- 最终镜像不包含构建工具
- 更少的安全漏洞
- 更小的镜像层

### 3. Spring Boot Layertools

使用 Spring Boot 自带的 layertools 功能提取 JAR 分层：

```bash
java -Djarmode=layertools -jar app.jar extract --destination extracted
```

**提取的分层**：
1. `dependencies/` - 第三方依赖
2. `spring-boot-loader/` - Spring Boot 加载器
3. `snapshot-dependencies/` - SNAPSHOT 依赖
4. `application/` - 应用代码

**优势**：
- 利用 Docker 缓存机制
- 依赖变更时只重建相关层
- 更快的构建速度

### 4. JVM 参数优化

针对容器环境优化的 JVM 参数：

```bash
# 优化版/JLink 版
-Xms128m -Xmx256m -XX:+UseG1GC -XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0

# 标准版
-Xms512m -Xmx1024m -XX:+UseG1GC -XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0
```

**优化点**：
- 更小的初始堆内存（128MB vs 512MB）
- 更小的最大堆内存（256MB vs 1024MB）
- 启用容器感知（`UseContainerSupport`）
- 基于百分比的内存限制（`MaxRAMPercentage`）

### 5. JLink 定制 JRE（实验性）

尝试使用 `jlink` 工具创建定制 JRE：

```dockerfile
# 阶段 2: 创建 jlink 定制 JRE
FROM eclipse-temurin:17-jdk-alpine AS jlink-create

RUN /opt/java/openjdk/bin/jlink \
    --add-modules ALL-MODULE-PATH \
    --compress=2 \
    --no-header-files \
    --no-man-pages \
    --strip-debug \
    --output /jlink-runtime
```

**技术说明**：
- ⚠️ Spring Boot 3.x 使用模块化系统，需要完整的 JRE 模块
- ⚠️ 尝试精简模块会导致 `ClassNotFoundException` 等兼容性问题
- ✅ 最终方案：Alpine JRE + 多阶段构建 + JVM 参数优化
- 📊 镜像大小：281MB（与优化版相同）

**JLink 版适用场景**：
- 需要进一步定制 JRE 模块的高级用户
- 愿意承担兼容性风险以换取潜在优化的场景
- 作为技术参考和实验用途

## 构建优化镜像

### 使用构建脚本（推荐）

```bash
# 构建优化版镜像（推荐）
./scripts/build/docker-build.sh optimized

# 构建 JLink 版镜像（实验性）
./scripts/build/docker-build.sh jlink

# 构建标准版镜像
./scripts/build/docker-build.sh standard
```

### 手动构建

```bash
# 构建 JAR
mvn clean package -DskipTests -Pfast

# 构建优化版镜像
docker build -f Dockerfile.optimized \
    -t sodlinken/jairouter:latest-optimized \
    .
```

## 运行优化镜像

### 基础运行

```bash
docker run -d \
  --name jairouter \
  -p 8080:8080 \
  -e SPRING_PROFILES_ACTIVE=prod \
  -e JAIROUTER_SECURITY_ENABLED=true \
  -e JAIROUTER_SECURITY_JWT_ENABLED=true \
  -e JAIROUTER_SECURITY_JWT_SECRET="your-32-char-secret-key-here" \
  sodlinken/jairouter:latest-optimized
```

### 生产环境运行

```bash
docker run -d \
  --name jairouter \
  -p 8080:8080 \
  -e SPRING_PROFILES_ACTIVE=prod \
  -e JAIROUTER_SECURITY_ENABLED=true \
  -e JAIROUTER_SECURITY_JWT_ENABLED=true \
  -e JAIROUTER_SECURITY_JWT_SECRET="your-very-strong-jwt-secret-key-at-least-32-characters-long" \
  -e JAIROUTER_SECURITY_API_KEY_ENABLED=true \
  -v $(pwd)/config:/app/config:ro \
  -v $(pwd)/logs:/app/logs \
  -v $(pwd)/data:/app/data \
  --restart unless-stopped \
  sodlinken/jairouter:latest-optimized
```

### 使用运行脚本

```bash
# 运行优化版（推荐）
./scripts/build/docker-run.sh prod latest optimized

# 运行标准版
./scripts/build/docker-run.sh prod latest standard
```

## 性能对比

### 镜像大小

```bash
# 查看镜像大小
docker images sodlinken/jairouter

# 输出示例
REPOSITORY            TAG               SIZE
sodlinken/jairouter   latest-optimized  281MB
sodlinken/jairouter   latest-jlink      281MB
sodlinken/jairouter   latest            440MB
```

### 启动时间

| 镜像类型 | 冷启动时间 | 热启动时间 |
|----------|-----------|-----------|
| 优化版 | ~8-10 秒 | ~3-5 秒 |
| 标准版 | ~10-12 秒 | ~4-6 秒 |

### 资源使用

| 镜像类型 | 初始内存 | 运行内存 | CPU 使用 |
|----------|---------|---------|---------|
| 优化版 | ~200MB | ~350MB | 低 |
| 标准版 | ~250MB | ~400MB | 低 |

## 安全性

### 非 root 用户

优化版镜像使用非 root 用户运行：

```dockerfile
RUN addgroup -g 10010 -S jairouter && \
    adduser -u 10010 -S jairouter -G jairouter

USER jairouter
```

### 最小权限

```dockerfile
# 只设置必要的权限
RUN chmod -R 755 /app
```

### 安全扫描

建议使用 Docker Scout 或 Trivy 进行镜像扫描：

```bash
# 使用 Docker Scout
docker scout cve sodlinken/jairouter:latest-optimized

# 使用 Trivy
trivy image sodlinken/jairouter:latest-optimized
```

## 最佳实践

### 1. 使用优化版镜像

生产环境推荐使用优化版镜像：

```yaml
# docker-compose.yml
services:
  jairouter:
    image: sodlinken/jairouter:latest-optimized
    container_name: jairouter
```

### 2. 镜像标签管理

```bash
# 使用具体版本标签
docker pull sodlinken/jairouter:v1.7.0-optimized

# 避免在生产环境使用 latest 标签
# docker pull sodlinken/jairouter:latest-optimized  # 不推荐用于生产
```

### 3. 镜像更新策略

```bash
# 定期更新基础镜像
# 在 Dockerfile.optimized 中更新基础镜像版本
FROM eclipse-temurin:17-jre-alpine:latest
```

### 4. 监控镜像大小

```bash
# 使用 docker history 查看镜像层
docker history sodlinken/jairouter:latest-optimized

# 使用 dive 工具分析镜像层
dive sodlinken/jairouter:latest-optimized
```

## 故障排查

### 镜像构建失败

```bash
# 检查 JAR 文件是否存在
ls -la target/model-router-*.jar

# 清理构建缓存
docker builder prune -a

# 重新构建
docker build --no-cache -f Dockerfile.optimized -t sodlinken/jairouter:latest-optimized .
```

### 容器启动失败

```bash
# 查看容器日志
docker logs jairouter

# 进入容器调试
docker exec -it jairouter sh

# 检查 JVM 参数
docker exec jairouter java -XX:+PrintFlagsFinal -version
```

### 镜像拉取慢

```bash
# 使用国内镜像加速器
# 配置 /etc/docker/daemon.json
{
  "registry-mirrors": [
    "https://registry.cn-hangzhou.aliyuncs.com"
  ]
}
```

## 下一步

- **[Docker 部署指南](docker.md)** - 详细的 Docker 部署说明
- **[生产环境部署](production.md)** - 生产环境配置最佳实践
- **[监控指南](../monitoring/index.md)** - 设置监控和告警

> **文档版本**: 1.0.2
