# 单阶段构建 Dockerfile for JAiRouter
FROM eclipse-temurin:17-jre

# 设置维护者信息
LABEL maintainer="JAiRouter Team"
LABEL description="JAiRouter - AI Model Service Routing and Load Balancing Gateway"
LABEL version="1.0-SNAPSHOT"

# 创建应用用户（安全最佳实践）
RUN groupadd -r -g 10010 jairouter && \
    useradd -r -u 10010 -g 10010 jairouter

# 设置工作目录
WORKDIR /app

# 创建必要的目录
RUN mkdir -p /app/logs /app/config /app/config-store /app/config-dev /app/r2dbc:h2:file/data && \
    chown -R jairouter:jairouter /app  && \
    chmod -R 766 /app

# 直接复制外部构建好的 JAR 文件
COPY target/model-router-*.jar app.jar
RUN chown jairouter:jairouter app.jar

# 配置文件通过卷挂载提供，无需复制到镜像中

# 设置 JVM 参数
ENV JAVA_OPTS="-Xms512m -Xmx1024m -XX:+UseG1GC -XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0"

# 设置应用参数
ENV SPRING_PROFILES_ACTIVE=prod
ENV SERVER_PORT=8080

# 安全功能相关环境变量
ENV JAIROUTER_SECURITY_ENABLED=true
ENV JAIROUTER_SECURITY_API_KEY_ENABLED=true
ENV JAIROUTER_SECURITY_JWT_ENABLED=true
ENV JAIROUTER_SECURITY_SANITIZATION_REQUEST_ENABLED=true
ENV JAIROUTER_SECURITY_SANITIZATION_RESPONSE_ENABLED=true
ENV JAIROUTER_SECURITY_AUDIT_ENABLED=true

# API Key 配置环境变量
ENV ADMIN_API_KEY=""
ENV USER_API_KEY=""

# JWT 配置环境变量
ENV JWT_SECRET=""
ENV JWT_ALGORITHM=HS256
ENV JWT_EXPIRATION_MINUTES=60

# Redis 缓存配置环境变量
ENV REDIS_HOST=localhost
ENV REDIS_PORT=6379
ENV REDIS_PASSWORD=""
ENV REDIS_DATABASE=0
ENV REDIS_TIMEOUT=2000

# 暴露端口
EXPOSE 8080

# 切换到应用用户
USER jairouter

# 健康检查
HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
    CMD wget --no-verbose --tries=1 --spider http://localhost:8080/actuator/health || exit 1

# 启动应用
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]