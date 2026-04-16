# JAiRouter v1.8.1 快速开始指南

> **文档版本**: 1.0.1  
> **最后更新**: 2026-04-16  
> **适用版本**: v1.8.0+

---

## 📋 概述

本指南介绍如何快速部署和启动 JAiRouter v1.8.0+ 版本，重点介绍如何使用密钥生成工具来生成安全的 JWT 密钥和管理员密码。

### v1.8.0 新特性

- ✅ **密钥生成工具** - 命令行生成安全的 JWT 密钥和密码
- ✅ **密钥强度检查** - 启动时自动检查密钥强度
- ✅ **配置验证机制** - 启动时验证关键配置
- ✅ **增强审计日志** - 备用日志通道，确保数据不丢失

---

## 🚀 快速开始

### 步骤 1: 拉取镜像

```bash
# 拉取最新镜像
docker pull sodlinken/jairouter:latest

# 或拉取指定版本
docker pull sodlinken/jairouter:v1.8.1
```

### 步骤 2: 生成安全密钥（v1.8.0+ 推荐）

**v1.8.0+ 新增密钥生成工具**，支持自动生成安全的 JWT 密钥和管理员密码。

#### 方式一：使用 Docker 运行密钥生成工具（推荐）

```bash
# 生成 JWT 密钥（Base64 编码）
docker run --rm sodlinken/jairouter:latest java -jar /app/modelrouter.jar --generate-key

# 示例输出：
# ╔══════════════════════════════════════════════════════════════════════════════╗
# ║                         JAiRouter 密钥生成工具                               ║
# ╚══════════════════════════════════════════════════════════════════════════════╝
#
# 【JWT 密钥生成】
# Base64 编码（推荐，适用于 JWT HS256）:
#   cGFzc3dvcmQtdGVzdC1rZXktZm9yLWphb3V0ZXItMjAyNg==
# 密钥强度：非常强
# 使用建议：
#   export JWT_SECRET="cGFzc3dvcmQtdGVzdC1rZXktZm9yLWphb3V0ZXItMjAyNg=="

# 生成管理员密码
docker run --rm sodlinken/jairouter:latest java -jar /app/modelrouter.jar --generate-password

# 示例输出：
# 【随机密码生成】
# 16 字符密码：aB3dEfGhIjKlMnOp
#   密码强度：强
#
# 20 字符密码：xYz123AbC456DeF789Gh
#   密码强度：非常强
```

#### 方式二：使用在线工具生成（无需 jar 包）

如果没有本地 Java 环境，可以使用在线工具或系统命令生成：

```bash
# 生成 Base64 编码的 JWT 密钥（至少 32 字节）
# 使用 OpenSSL
openssl rand -base64 32

# 或使用 Python
python3 -c "import secrets; print(secrets.token_urlsafe(32))"

# 生成随机密码（16 字符，包含字母数字）
openssl rand -base64 24 | tr -dc 'A-Za-z0-9' | head -c 16

# 生成随机密码（包含特殊字符）
openssl rand -base64 24 | tr -dc 'A-Za-z0-9!@#$%^&*' | head -c 16
```

#### 方式三：使用本地 Java 环境运行

如果你有本地 Java 环境，可以下载 jar 包运行：

```bash
# 从 GitHub Release 下载 jar 包
wget https://github.com/Lincoln-cn/JAiRouter/releases/download/v1.8.1/model-router-1.8.1.jar

# 生成 JWT 密钥
java -jar model-router-1.8.1.jar --generate-key

# 生成管理员密码
java -jar model-router-1.8.1.jar --generate-password
```

### 步骤 3: 设置环境变量

```bash
# 设置 JWT 密钥（使用生成的密钥）
export JWT_SECRET="cGFzc3dvcmQtdGVzdC1rZXktZm9yLWphb3V0ZXItMjAyNg=="

# 设置管理员密码（使用生成的密码）
export INITIAL_ADMIN_PASSWORD="MyStr0ng!Pass#2026"
```

### 步骤 4: 运行容器

#### 生产环境（推荐配置）

```bash
docker run -d \
  --name jairouter \
  -p 8080:8080 \
  -e SPRING_PROFILES_ACTIVE=prod \
  -e JWT_SECRET="$JWT_SECRET" \
  -e INITIAL_ADMIN_PASSWORD="$INITIAL_ADMIN_PASSWORD" \
  -v $(pwd)/config:/app/config:ro \
  -v $(pwd)/logs:/app/logs \
  -v $(pwd)/data:/app/data \
  --restart unless-stopped \
  sodlinken/jairouter:latest
```

#### 开发环境

```bash
docker run -d \
  --name jairouter-dev \
  -p 8080:8080 \
  -p 5005:5005 \
  -e SPRING_PROFILES_ACTIVE=dev \
  -e JWT_SECRET="your-very-strong-jwt-secret-key-at-least-32-characters-long" \
  -e JAVA_OPTS="-Xms256m -Xmx512m -agentlib:jdwp=transport=dt_socket,server=y,suspend=n" \
  sodlinken/jairouter:dev
```

#### Docker Compose（推荐）

```yaml
version: '3.8'
services:
  jairouter:
    image: sodlinken/jairouter:latest
    container_name: jairouter
    ports:
      - "8080:8080"
    environment:
      - SPRING_PROFILES_ACTIVE=prod
      - JWT_SECRET=${JWT_SECRET}
      - INITIAL_ADMIN_PASSWORD=${INITIAL_ADMIN_PASSWORD}
    volumes:
      - ./config:/app/config:ro
      - ./logs:/app/logs
      - ./data:/app/data
    restart: unless-stopped
    healthcheck:
      test: ["CMD", "curl", "-f", "http://localhost:8080/actuator/health"]
      interval: 30s
      timeout: 10s
      retries: 3
```

启动：
```bash
docker-compose up -d
```

### 步骤 5: 验证部署

```bash
# 检查容器状态
docker ps | grep jairouter

# 查看启动日志
docker logs -f jairouter

# 验证健康检查
curl http://localhost:8080/actuator/health

# 预期输出:
# {"status":"UP"}
```

### 步骤 6: 访问 Web 控制台

打开浏览器访问：http://localhost:8080/admin

**登录凭证**:
- 用户名：`admin`
- 密码：`$INITIAL_ADMIN_PASSWORD` 环境变量的值

![](../capture/login.png)

---

## 🔒 安全说明

### 启动时安全检查

v1.8.0+ 版本在启动时会自动检查密钥强度：

```
╔══════════════════════════════════════════════════════════════════════════════╗
║                            JAiRouter 安全密钥检查                             ║
╚══════════════════════════════════════════════════════════════════════════════╝

正在检查 JWT 密钥配置...
✓ JWT 密钥强度：强 - 通过检查

正在检查管理员密码配置...
✓ 管理员密码强度：强 - 通过检查

╔══════════════════════════════════════════════════════════════════════════════╗
║  ✓ 所有密钥和密码检查通过                                                    ║
╚══════════════════════════════════════════════════════════════════════════════╝
```

### 弱密钥警告

如果检测到弱密钥或弱密码，会输出警告：

```
╔══════════════════════════════════════════════════════════════════════════════╗
║  ⚠️  安全警告：检测到弱密钥或弱密码                                          ║
║                                                                              ║
║  在生产环境使用之前，请务必：                                                ║
║  1. 设置强密钥：export JWT_SECRET="<随机生成的 32+ 字节密钥>"                    ║
║  2. 设置强密码：export INITIAL_ADMIN_PASSWORD="<复杂密码>"                     ║
║  3. 使用密钥生成工具：docker run --rm sodlinken/jairouter:latest java -jar /app/modelrouter.jar --generate-key  ║
╚══════════════════════════════════════════════════════════════════════════════╝
```

---

## 📊 配置验证

v1.8.0+ 版本在启动时会自动验证关键配置：

```
╔══════════════════════════════════════════════════════════════════════════════╗
║                            JAiRouter 配置验证                                ║
╚══════════════════════════════════════════════════════════════════════════════╝

✓ [rate-limit-capacity] 限流容量 - 通过
✓ [rate-limit-rate] 限流速率 - 通过
✓ [circuit-breaker-threshold] 熔断器失败阈值 - 通过
✓ [circuit-breaker-timeout] 熔断器超时时间 - 通过
✓ [jwt-expiration] JWT 过期时间 - 通过
✓ [server-port] 服务器端口 - 通过
✓ [thread-pool-size] 线程池大小 - 通过

╔══════════════════════════════════════════════════════════════════════════════╗
║  配置验证汇总                                                                ║
║                                                                              ║
║  总规则数：7                                                                 ║
║  通过：7                                                                     ║
║  警告：0                                                                     ║
║  错误：0                                                                     ║
╚══════════════════════════════════════════════════════════════════════════════╝
```

---

## 🔧 故障排查

### 问题 1: 容器启动失败

**检查日志**:
```bash
docker logs jairouter
```

**常见原因**:
- 端口被占用
- 配置文件路径错误
- 环境变量未设置

### 问题 2: 无法访问 Web 控制台

**检查防火墙**:
```bash
# 检查端口是否监听
netstat -tlnp | grep 8080

# 检查防火墙规则
sudo ufw status
```

### 问题 3: 密钥强度检查失败

**重新生成密钥**:
```bash
# 使用 Docker 生成
docker run --rm sodlinken/jairouter:latest java -jar /app/modelrouter.jar --generate-key

# 或使用 OpenSSL
openssl rand -base64 32
```

**设置环境变量**:
```bash
export JWT_SECRET="生成的密钥"
```

---

## 📚 相关文档

- [Docker 部署完整指南](../deployment/docker.md)
- [配置验证规则](../configuration/validation-rules.md)

---

**文档版本**: 1.0.1  
**最后更新**: 2026-04-16
