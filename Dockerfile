# JAiRouter 优化版 Dockerfile
# 多阶段构建 + Spring Boot 分层 JAR + Alpine 基础镜像
# 镜像大小: ~280MB（比标准版减少 36%）

# =============================================================================
# 阶段 1: 提取 Spring Boot 分层内容
# =============================================================================
FROM eclipse-temurin:17-jdk-alpine AS extract-layer

WORKDIR /app

# 复制 JAR 文件
COPY target/model-router-*.jar app.jar

# 使用 Spring Boot 工具提取分层（利用 Docker 缓存）
RUN java -Djarmode=tools -jar app.jar extract --layers --launcher --destination extracted

# =============================================================================
# 阶段 2: 最终运行镜像
# =============================================================================
FROM eclipse-temurin:17-jre-alpine

# 设置元数据
LABEL maintainer="JAiRouter Team"
LABEL description="JAiRouter - AI Model Service Routing and Load Balancing Gateway"
LABEL version="2.7.34"

# 安装必要工具（wget 用于健康检查，tzdata 用于时区支持）
RUN apk add --no-cache wget tzdata

# 创建非 root 用户（安全最佳实践）
RUN addgroup -g 10010 -S jairouter && \
    adduser -u 10010 -S jairouter -G jairouter

WORKDIR /app

# 创建数据目录
RUN mkdir -p /app/logs /app/config /app/config-store /app/data && \
    chown -R jairouter:jairouter /app && \
    chmod -R 755 /app

# 复制 Spring Boot 分层内容（按层复制以利用缓存）
COPY --from=extract-layer --chown=jairouter:jairouter /app/extracted/dependencies/ ./
COPY --from=extract-layer --chown=jairouter:jairouter /app/extracted/spring-boot-loader/ ./
COPY --from=extract-layer --chown=jairouter:jairouter /app/extracted/snapshot-dependencies/ ./
COPY --from=extract-layer --chown=jairouter:jairouter /app/extracted/application/ ./

# JVM 优化参数（容器感知）
ENV JAVA_OPTS="-Xms256m -Xmx512m -XX:+UseG1GC -XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0"

# 应用配置
ENV SPRING_PROFILES_ACTIVE=prod
ENV SERVER_PORT=8080

# 安全功能配置
ENV JAIROUTER_SECURITY_ENABLED=true
ENV JAIROUTER_SECURITY_API_KEY_ENABLED=true
ENV JAIROUTER_SECURITY_JWT_ENABLED=true
ENV JAIROUTER_SECURITY_SANITIZATION_REQUEST_ENABLED=true
ENV JAIROUTER_SECURITY_SANITIZATION_RESPONSE_ENABLED=true
ENV JAIROUTER_SECURITY_AUDIT_ENABLED=true

# API Key 配置
ENV ADMIN_API_KEY=""
ENV USER_API_KEY=""

# JWT 配置
ENV JWT_SECRET=""
ENV JWT_ALGORITHM=HS256
ENV JWT_EXPIRATION_MINUTES=60

# Redis 配置
ENV REDIS_HOST=localhost
ENV REDIS_PORT=6379
ENV REDIS_PASSWORD=""
ENV REDIS_DATABASE=0
ENV REDIS_TIMEOUT=2000

EXPOSE 8080

USER jairouter

# 健康检查
HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
    CMD wget --no-verbose --tries=1 --spider http://localhost:8080/actuator/health || exit 1

# 启动应用（使用 Spring Boot Launcher）
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS org.springframework.boot.loader.launch.JarLauncher"]
