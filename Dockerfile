# 多阶段构建 Dockerfile for JAiRouter
# 阶段1: 构建阶段
FROM maven:3.9.6-eclipse-temurin-17 AS builder

# 设置工作目录
WORKDIR /app

# 复制 pom.xml 和源代码
COPY pom.xml .
COPY src ./src
COPY checkstyle.xml .
COPY spotbugs-security-include.xml .
COPY spotbugs-security-exclude.xml .

# 构建应用程序（跳过测试以加快构建速度）
RUN mvn clean package -DskipTests

# 阶段2: 运行阶段
FROM eclipse-temurin:17-jre-alpine

# 设置维护者信息
LABEL maintainer="JAiRouter Team"
LABEL description="JAiRouter - AI Model Service Routing and Load Balancing Gateway"
LABEL version="1.0-SNAPSHOT"

# 创建应用用户（安全最佳实践）
RUN addgroup -g 1001 jairouter && \
    adduser -D -s /bin/sh -u 1001 -G jairouter jairouter

# 设置工作目录
WORKDIR /app

# 创建必要的目录
RUN mkdir -p /app/logs /app/config /app/config-store && \
    chown -R jairouter:jairouter /app

# 从构建阶段复制 JAR 文件
COPY --from=builder /app/target/model-router-*.jar app.jar

# 复制配置文件（如果存在）
RUN if [ -d "config" ]; then cp -r config/ ./config/ && chown -R jairouter:jairouter ./config/; fi

# 设置 JVM 参数
ENV JAVA_OPTS="-Xms512m -Xmx1024m -XX:+UseG1GC -XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0"

# 设置应用参数
ENV SPRING_PROFILES_ACTIVE=prod
ENV SERVER_PORT=8080

# 暴露端口
EXPOSE 8080

# 切换到应用用户
USER jairouter

# 健康检查
HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
    CMD wget --no-verbose --tries=1 --spider http://localhost:8080/actuator/health || exit 1

# 启动应用
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]