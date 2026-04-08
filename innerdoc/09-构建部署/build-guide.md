# JAiRouter 构建指南

## 📋 概述

JAiRouter 提供多种构建方式，包括标准构建和针对中国用户优化的加速构建。

## 🛠️ 构建方式对比

| 构建方式 | 适用用户 | Maven仓库 | 构建速度 | 推荐度 |
|----------|----------|-----------|----------|--------|
| **标准构建** | 国际用户 | Maven Central | 正常 | ⭐⭐⭐ |
| **中国加速** | 中国用户 | 阿里云镜像 | 快速 | ⭐⭐⭐⭐⭐ |

## 🇨🇳 中国用户专用构建

### 优化特性
- **阿里云Maven镜像**: 使用 `https://maven.aliyun.com/repository/public`
- **完整仓库支持**: Central、Spring、Plugin等仓库镜像
- **自动配置**: 内置settings.xml，无需手动配置
- **显著提速**: 依赖下载速度提升5-10倍

### 相关文件
```
├── Dockerfile.china              # 中国优化的Docker构建文件
├── settings-china.xml            # 阿里云Maven镜像配置
├── scripts/docker-build-china.sh # 中国优化构建脚本
└── pom.xml (china profile)       # Maven中国加速配置
```

## 🚀 快速开始

### 方式一：Docker构建（推荐）

#### 中国用户
```bash
# 使用中国优化构建脚本
./scripts/docker-build-china.sh
```

#### 国际用户
```bash
# 使用标准构建脚本
./scripts/docker-build.sh
```

### 方式二：Maven构建

#### 中国用户
```bash
# 使用china profile
mvn clean package -Pchina

# 或直接使用阿里云镜像
mvn clean package -s settings-china.xml
```

#### 国际用户
```bash
# 标准构建
mvn clean package
```

## 📊 构建性能对比

| 构建方式 | 首次构建时间 | 增量构建时间 | 网络要求 |
|----------|-------------|-------------|----------|
| **标准构建** | 5-10分钟 | 2-3分钟 | 国际网络 |
| **中国加速** | 1-2分钟 | 30-60秒 | 中国网络 |

## 🔧 配置详解

### settings-china.xml 配置
```xml
<mirrors>
  <mirror>
    <id>aliyunmaven</id>
    <mirrorOf>*</mirrorOf>
    <name>阿里云公共仓库</name>
    <url>https://maven.aliyun.com/repository/public</url>
  </mirror>
</mirrors>
```

### pom.xml china profile
```xml
<profile>
  <id>china</id>
  <repositories>
    <repository>
      <id>aliyun-central</id>
      <url>https://maven.aliyun.com/repository/central</url>
    </repository>
    <repository>
      <id>aliyun-spring</id>
      <url>https://maven.aliyun.com/repository/spring</url>
    </repository>
  </repositories>
</profile>
```

## 🐳 Docker构建详解

### Dockerfile.china 特性
- 在构建阶段自动配置阿里云Maven镜像
- 使用china profile进行构建
- 优化的多阶段构建流程

### 构建过程
1. **准备阶段**: 复制settings-china.xml到Maven配置目录
2. **依赖下载**: 从阿里云镜像下载依赖
3. **编译构建**: 使用china profile编译应用
4. **镜像打包**: 创建最终运行镜像

## 🔍 故障排查

### 常见问题

#### 1. 依赖下载缓慢
```bash
# 检查是否使用了正确的镜像
mvn help:effective-settings

# 强制使用阿里云镜像
mvn clean package -s settings-china.xml
```

#### 2. Docker构建失败
```bash
# 检查Dockerfile是否存在
ls -la Dockerfile.china

# 检查settings文件
cat settings-china.xml

# 重新构建
docker build -f Dockerfile.china -t jairouter/model-router:latest .
```

#### 3. 配置文件复制错误
```bash
# 如果遇到 "cannot copy a directory into itself" 错误
# 这通常是因为配置文件复制逻辑问题，已在最新版本中修复
# 配置文件现在通过卷挂载提供，不会复制到镜像中

# 正确的运行方式
docker run -v $(pwd)/config:/app/config:ro jairouter/model-router:latest
```

#### 4. 网络连接问题
```bash
# 测试阿里云镜像连接
curl -I https://maven.aliyun.com/repository/public

# 测试Maven Central连接
curl -I https://repo.maven.apache.org/maven2
```

## 📝 最佳实践

### 1. 中国用户建议
- 优先使用 `./scripts/docker-build-china.sh`
- 配置本地Maven使用阿里云镜像
- 使用china profile进行日常开发

### 2. 国际用户建议
- 使用标准构建脚本
- 配置Maven代理（如需要）
- 使用默认的Maven Central仓库

### 3. CI/CD集成
```yaml
# GitHub Actions 示例
- name: Build with China mirrors
  run: |
    if [[ "${{ github.actor }}" == "chinese-user" ]]; then
      ./scripts/docker-build-china.sh
    else
      ./scripts/docker-build.sh
    fi
```

## 📚 相关文档

- [Docker部署指南](../deployment/docker-deployment.md)
- [项目README](../../README.md)
- [英文README](../../README-EN.md)