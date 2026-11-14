# JAiRouter 优化版 Dockerfile
# 多阶段构建 + Spring Boot 分层 JAR + 多架构支持 (amd64/arm64)
# 镜像大小: ~320MB

# =============================================================================
# 阶段 1: 提取 Spring Boot 分层内容
# =============================================================================
FROM eclipse-temurin:17-jdk AS extract-layer

WORKDIR /app

# 复制 JAR 文件
# target 下可能同时存在多个版本的 jar（旧版本未清理），`*.jar` 就会命中多个源，
# 而 COPY 把多个源复制到文件目标时的报错非常抽象。先复制到目录、再断言恰好一个，
# 让「选错了 jar」变成一条能直接读懂的失败信息。
COPY target/model-router-*.jar /tmp/jar/
RUN set -eux; \
    set -- /tmp/jar/*.jar; \
    [ "$#" -eq 1 ] || { echo "expected exactly one model-router jar under target/, found $#:" >&2; ls -l /tmp/jar/ >&2; exit 1; }; \
    cp /tmp/jar/*.jar app.jar

# 使用 Spring Boot 工具提取分层（利用 Docker 缓存）
RUN java -Djarmode=tools -jar app.jar extract --layers --launcher --destination extracted

# =============================================================================
# 阶段 2: 最终运行镜像
# =============================================================================
FROM eclipse-temurin:17-jre

# 设置元数据
LABEL maintainer="JAiRouter Team"
LABEL description="JAiRouter - AI Model Service Routing and Load Balancing Gateway"
# 版本号由构建参数注入：默认 dev，发布流程传 --build-arg APP_VERSION=<版本>
ARG APP_VERSION=dev
LABEL version="${APP_VERSION}"

# 安装必要工具（curl 用于健康检查，tzdata 用于时区支持）
RUN apt-get update && apt-get install -y --no-install-recommends \
    curl \
    tzdata \
    && rm -rf /var/lib/apt/lists/*

# 创建非 root 用户（安全最佳实践）
RUN groupadd -g 10010 jairouter && \
    useradd -u 10010 -g jairouter -m -s /bin/bash jairouter

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
ENV JAVA_OPTS="-Xms256m -XX:+UseG1GC -XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0"

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
# #677：ADMIN_API_KEY / USER_API_KEY 只被 config-external 与迁移脚本生成的配置以 ${…} 形式引用，
# 随包配置里没有任何位置读它们，导出空值只会误导运维 —— 需要时由部署侧显式传（脚本会写进生成的 yml）。

# JWT 配置（不要设置默认空值，让 Spring 使用配置文件中的默认值）
# ENV JWT_SECRET=""
# #677：这两个名字由 config/auth/jwt.yml 的 ${JWT_ALGORITHM:HS256} / ${JWT_EXPIRATION_MINUTES:60} 读取，
# 与 config-external/auth/jwt.yml 保持一致。
ENV JWT_ALGORITHM=HS256
ENV JWT_EXPIRATION_MINUTES=60

# Redis 配置
ENV REDIS_HOST=localhost
ENV REDIS_PORT=6379
ENV REDIS_PASSWORD=""
ENV REDIS_DATABASE=0
# #677：不再导出 REDIS_TIMEOUT —— 随包配置里没有任何键读它（真实键是 spring.data.redis.timeout，
# 由 config/security/persistence-base.yml 固定为 5000ms）。

EXPOSE 8080

USER jairouter

# 健康检查
HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
    CMD curl -f http://localhost:8080/actuator/health/liveness || exit 1

# 启动应用（使用 Spring Boot Launcher）
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS org.springframework.boot.loader.launch.JarLauncher \"$@\"", "--"]
