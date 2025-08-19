# 安装指南

<!-- 版本信息 -->
> **文档版本**: 1.0.0  
> **最后更新**: 2025-08-19  
> **Git 提交**: c1aa5b0f  
> **作者**: Lincoln
<!-- /版本信息 -->



本指南将详细介绍如何在不同环境中安装和构建 JAiRouter。

## 环境要求

### 系统要求

| 组件 | 最低版本 | 推荐版本 | 说明 |
|------|----------|----------|------|
| **JDK** | 17 | 21+ | 支持 OpenJDK 和 Oracle JDK |
| **Maven** | 3.6.0 | 3.9+ | 可选，项目包含 Maven Wrapper |
| **Docker** | 20.10 | 24+ | 用于容器化部署 |
| **内存** | 512MB | 1GB+ | 运行时内存要求 |
| **磁盘空间** | 1GB | 2GB+ | 包含依赖和日志空间 |

### 支持的操作系统

- **Windows**: Windows 10/11, Windows Server 2019+
- **Linux**: Ubuntu 18.04+, CentOS 7+, RHEL 7+, Debian 10+
- **macOS**: macOS 10.15+

## 安装方式选择

JAiRouter 提供多种安装方式，请根据您的需求选择：

| 安装方式 | 适用场景 | 优势 | 劣势 |
|----------|----------|------|------|
| **Docker 部署** | 生产环境、快速体验 | 环境隔离、易于部署 | 需要 Docker 环境 |
| **传统部署** | 开发环境、系统集成 | 直接运行、易于调试 | 需要配置 Java 环境 |
| **源码构建** | 开发贡献、定制化 | 完全控制、可定制 | 需要开发环境 |

## Docker 安装（推荐）

Docker 安装是最简单快捷的方式，适合大多数用户。

### 1. 安装 Docker

如果您还没有安装 Docker，请参考 [Docker 官方安装指南](https://docs.docker.com/get-docker/)。

### 2. 拉取镜像

```bash
# 拉取最新版本
docker pull jairouter/model-router:latest

# 或拉取指定版本
docker pull jairouter/model-router:v0.3.1
```

### 3. 运行容器

```bash
# 基本运行
docker run -d \
  --name jairouter \
  -p 8080:8080 \
  jairouter/model-router:latest

# 带配置文件运行
docker run -d \
  --name jairouter \
  -p 8080:8080 \
  -v $(pwd)/config:/app/config:ro \
  -v $(pwd)/logs:/app/logs \
  jairouter/model-router:latest
```

### 4. 验证安装

```bash
# 检查容器状态
docker ps --filter "name=jairouter"

# 查看日志
docker logs jairouter

# 测试 API
curl http://localhost:8080/actuator/health
```

## 传统安装

传统安装方式直接在系统上运行 JAR 文件，适合开发和调试。

### 1. 安装 Java

确保系统已安装 JDK 17 或更高版本：

```bash
# 检查 Java 版本
java -version

# 应该显示类似输出：
# openjdk version "17.0.2" 2022-01-18
```

如果没有安装 Java，请从以下渠道下载：
- [OpenJDK](https://openjdk.org/install/)
- [Oracle JDK](https://www.oracle.com/java/technologies/downloads/)
- [Amazon Corretto](https://aws.amazon.com/corretto/)

### 2. 下载 JAR 文件

从 [GitHub Releases](https://github.com/Lincoln-cn/JAiRouter/releases) 下载最新版本的 JAR 文件：

```bash
# 下载最新版本
wget https://github.com/Lincoln-cn/JAiRouter/releases/latest/download/model-router.jar

# 或使用 curl
curl -L -o model-router.jar https://github.com/Lincoln-cn/JAiRouter/releases/latest/download/model-router.jar
```

### 3. 运行应用

```bash
# 基本运行
java -jar model-router.jar

# 指定配置文件
java -jar model-router.jar --spring.config.location=classpath:/application.yml

# 指定 JVM 参数
java -Xmx1g -Xms512m -jar model-router.jar

# 后台运行
nohup java -jar model-router.jar > jairouter.log 2>&1 &
```

### 4. 验证安装

```bash
# 检查进程
ps aux | grep model-router

# 测试 API
curl http://localhost:8080/actuator/health
```

## 源码构建

从源码构建适合开发者和需要定制化的用户。

### 1. 克隆代码

```bash
git clone https://github.com/Lincoln-cn/JAiRouter.git
cd jairouter
```

### 2. 构建方式选择

JAiRouter 提供多种构建方式，针对不同用户群体和网络环境优化：

| 构建方式 | 适用用户 | Maven仓库 | 构建速度 | 推荐度 |
|----------|----------|-----------|----------|--------|
| **中国加速** | 中国用户 | 阿里云镜像 | 快速 | ⭐⭐⭐⭐⭐ |
| **标准构建** | 国际用户 | Maven Central | 正常 | ⭐⭐⭐ |
| **快速构建** | 开发调试 | 跳过测试 | 最快 | ⭐⭐⭐⭐ |

### 3. 中国用户专用构建（推荐）

#### 优化特性
- **阿里云Maven镜像**: 使用 `https://maven.aliyun.com/repository/public`
- **完整仓库支持**: Central、Spring、Plugin等仓库镜像
- **自动配置**: 内置settings.xml，无需手动配置
- **显著提速**: 依赖下载速度提升5-10倍

#### 构建命令

```bash
# 使用 Maven Wrapper（推荐）
./mvnw clean package -Pchina

# 或使用系统 Maven
mvn clean package -Pchina

# 使用专用配置文件
mvn clean package -s settings-china.xml
```

#### 相关配置文件
```
├── Dockerfile.china              # 中国优化的Docker构建文件
├── settings-china.xml            # 阿里云Maven镜像配置
├── scripts/docker-build-china.sh # 中国优化构建脚本
└── pom.xml (china profile)       # Maven中国加速配置
```

### 4. 国际用户标准构建

使用标准 Maven Central 仓库：

```bash
# 使用 Maven Wrapper（推荐）
./mvnw clean package

# 或使用系统 Maven
mvn clean package
```

### 5. 快速构建（开发调试）

适合开发环境和快速测试：

```bash
# 跳过所有检查和测试
./mvnw clean package -Pfast

# 仅跳过测试
./mvnw clean package -DskipTests

# 跳过代码质量检查
./mvnw compiler:compile compiler:testCompile surefire:test
```

### 6. 构建性能对比

| 构建方式 | 首次构建时间 | 增量构建时间 | 网络要求 | 适用场景 |
|----------|-------------|-------------|----------|----------|
| **中国加速** | 1-2分钟 | 30-60秒 | 中国网络 | 中国用户日常开发 |
| **标准构建** | 5-10分钟 | 2-3分钟 | 国际网络 | 国际用户开发 |
| **快速构建** | 30-60秒 | 10-20秒 | 任意 | 开发调试 |

### 7. 运行构建结果

```bash
# 运行构建的 JAR 文件
java -jar target/model-router-*.jar

# 指定配置文件运行
java -jar target/model-router-*.jar --spring.config.location=classpath:/application.yml

# 指定 JVM 参数
java -Xmx1g -Xms512m -jar target/model-router-*.jar
```

## Docker 镜像构建

如果您需要构建自定义的 Docker 镜像：

### 1. 使用构建脚本（推荐）

JAiRouter 提供了针对不同用户群体优化的构建脚本：

```bash
# 中国用户（使用阿里云镜像，构建速度快）
./scripts/docker-build-china.sh

# 国际用户（使用标准镜像）
./scripts/docker-build.sh

# Windows 用户
.\scripts\docker-build.ps1
```

### 2. 手动构建

#### 中国用户优化构建

```bash
# 使用中国优化的 Dockerfile
docker build -f Dockerfile.china -t jairouter/model-router:latest .
```

**Dockerfile.china 特性**：
- 在构建阶段自动配置阿里云Maven镜像
- 使用china profile进行构建
- 优化的多阶段构建流程

#### 国际用户标准构建

```bash
# 使用标准 Dockerfile
docker build -t jairouter/model-router:latest .
```

### 3. 使用 Maven 插件

```bash
# 使用 Dockerfile 插件
mvn clean package dockerfile:build -Pdocker

# 使用 Jib 插件（无需 Docker）
mvn clean package jib:dockerBuild -Pjib

# 中国用户使用 Jib 插件
mvn clean package jib:dockerBuild -Pjib,china
```

### 4. 构建过程详解

Docker 构建包含以下阶段：

1. **准备阶段**: 复制源码和配置文件
2. **依赖下载**: 从配置的Maven仓库下载依赖
3. **编译构建**: 编译Java代码并打包
4. **镜像打包**: 创建最终的运行镜像

### 5. 构建优化建议

#### 中国用户
- 优先使用 `./scripts/docker-build-china.sh`
- 配置本地Docker使用国内镜像加速
- 使用多阶段构建减少镜像大小

#### 国际用户
- 使用标准构建脚本
- 配置Docker代理（如需要）
- 利用Docker层缓存提升构建速度

## 开发环境安装

开发环境需要额外的工具和配置。

### 1. IDE 配置

推荐使用以下 IDE：
- **IntelliJ IDEA**: 推荐，内置 Spring Boot 支持
- **Eclipse**: 需要安装 Spring Tools Suite
- **VS Code**: 需要安装 Java 和 Spring Boot 扩展

### 2. 开发工具

```bash
# 安装 Maven（如果不使用 Wrapper）
# Ubuntu/Debian
sudo apt install maven

# CentOS/RHEL
sudo yum install maven

# macOS
brew install maven

# Windows
# 下载并配置环境变量
```

### 3. 代码质量工具

项目集成了多种代码质量工具：

```bash
# 运行代码检查
./mvnw checkstyle:check

# 运行静态分析
./mvnw spotbugs:check

# 生成覆盖率报告
./mvnw jacoco:report
```

### 4. 开发模式运行

```bash
# 使用 Spring Boot Maven 插件
./mvnw spring-boot:run

# 启用调试模式
./mvnw spring-boot:run -Dspring-boot.run.jvmArguments="-Xdebug -Xrunjdwp:transport=dt_socket,server=y,suspend=n,address=5005"

# 使用开发配置
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
```

## 故障排查

### 常见问题

#### 1. Java 版本不兼容

```bash
# 错误信息：UnsupportedClassVersionError
# 解决方案：升级到 JDK 17+

# 检查 Java 版本
java -version
javac -version

# 设置 JAVA_HOME
export JAVA_HOME=/path/to/jdk17
```

#### 2. 端口被占用

```bash
# 错误信息：Port 8080 was already in use
# 解决方案：更改端口或停止占用进程

# 查找占用进程
netstat -tulpn | grep 8080
lsof -i :8080

# 更改端口
java -jar model-router.jar --server.port=8081
```

#### 3. 内存不足

```bash
# 错误信息：OutOfMemoryError
# 解决方案：增加 JVM 内存

# 设置内存参数
java -Xmx2g -Xms1g -jar model-router.jar
```

#### 4. 依赖下载失败

```bash
# 中国用户使用阿里云镜像
./mvnw clean package -Pchina

# 或配置代理
./mvnw clean package -Dhttp.proxyHost=proxy.example.com -Dhttp.proxyPort=8080
```

#### 5. Docker 构建失败

```bash
# 检查 Docker 版本
docker --version

# 清理 Docker 缓存
docker system prune -a

# 重新构建
docker build --no-cache -t jairouter/model-router:latest .
```

### 获取帮助

如果遇到其他问题，请：

1. 查看 [故障排查文档](../troubleshooting/index.md)
2. 搜索 [GitHub Issues](https://github.com/Lincoln-cn/JAiRouter/issues)
3. 提交新的 [Issue](https://github.com/Lincoln-cn/JAiRouter/issues/new)

## 下一步

安装完成后，您可以：

1. **[快速开始](quick-start.md)** - 5分钟快速体验 JAiRouter
2. **[第一步](first-steps.md)** - 配置您的第一个 AI 服务
3. **[配置指南](../configuration/index.md)** - 详细的配置说明