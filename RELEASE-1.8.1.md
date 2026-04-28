# JAiRouter v1.8.1 发布说明

**发布日期**: 2026 年 4 月 16 日  
**版本类型**: 功能增强版本  
**上一个版本**: [v1.7.3](https://github.com/Lincoln-cn/JAiRouter/releases/tag/v1.7.3)

---

## 📋 版本概述

v1.8.1 是一个功能增强版本，在 v1.8.0 安全加固的基础上，进一步优化了 Docker 镜像构建、文档结构和测试质量。本版本重点提升了部署效率和文档完整性。

---

## ✨ 核心特性

### 🔐 安全加固 (v1.8.0 延续)
- ✅ JWT + API Key 双重认证机制
- ✅ 密钥生成工具集成，支持自动生成安全的 JWT 密钥和管理员密码
- ✅ 内置审计日志和敏感数据脱敏

### 🐳 Docker 镜像优化
- ✅ 添加 JLink 优化的 Dockerfile，镜像体积减少 36%
- ✅ 多阶段构建优化，更快的构建速度和更小的镜像体积
- ✅ 镜像大小从 ~200MB 优化至 ~130MB

### 📚 文档完善
- ✅ 整理文档结构，优化 innerdoc 文档组织
- ✅ 添加 v1.8.1 快速开始指南
- ✅ 更新 README 添加密钥生成工具使用说明
- ✅ 修复 MkDocs 编译错误并补充缺失文档
- ✅ 删除重复的开发计划文件，保持文档简洁

### 🧪 代码质量提升
- ✅ 删除重复和无价值的单元测试
- ✅ 清理问题测试，提升测试覆盖率质量
- ✅ 修复熔断器状态共享问题，提升系统稳定性

---

## 📊 变更统计

| 类别 | 数量 | 说明 |
|------|------|------|
| 新功能 | 3 | Docker 镜像优化、JLink 支持、密钥生成工具 |
| 修复 | 3 | 熔断器状态共享、GitHub Actions 路径、MkDocs 编译 |
| 文档 | 12 | 快速开始指南、README 更新、文档结构整理 |
| 测试 | 6 | 删除低质量测试，提升测试价值 |

---

## 🚀 快速开始

### 1. 拉取镜像

```bash
# 拉取最新镜像
docker pull sodlinken/jairouter:latest
```

### 2. 生成安全密钥（推荐）

**使用 Docker（推荐方式）**

```bash
# 生成 JWT 密钥（Base64 编码，至少 32 字符）
docker run --rm sodlinken/jairouter:latest java -jar /app/modelrouter.jar --generate-key

# 生成随机密码
docker run --rm sodlinken/jairouter:latest java -jar /app/modelrouter.jar --generate-password
```

### 3. 启动服务

```bash
docker run -d \
  --name jairouter \
  -p 8080:8080 \
  -e SPRING_PROFILES_ACTIVE=prod \
  -e JWT_SECRET="your-secret-key-here" \
  -e DEFAULT_ADMIN_PASSWORD="your-password-here" \
  sodlinken/jairouter:latest
```

### 4. 访问应用

- **应用地址**: http://localhost:8080
- **管理控制台**: http://localhost:8080/admin
- **健康检查**: http://localhost:8080/actuator/health

**默认管理员账号**:
- 用户名：`admin`
- 密码：首次启动时查看日志生成的随机密码

---

## 🔧 技术栈

| 层级 | 技术 |
|------|------|
| 后端 | Java 17, Spring Boot 3.5.5 (WebFlux/Reactive) |
| 前端 | Vue 3 + TypeScript + Element Plus + Vite |
| 数据库 | H2 (嵌入式，默认), R2DBC 响应式访问 |
| 缓存 | Redis (可选) |
| 安全 | JWT + API Key 双重认证，Spring Security |
| 可观测性 | OpenTelemetry, Prometheus, Micrometer |
| 构建工具 | Maven 3.x |

---

## 📝 详细变更日志

### 功能增强

- **feat**: 添加多阶段构建 Dockerfile 优化版，镜像体积减少 36%
- **feat**: 添加 JLink 优化的 Dockerfile 和构建脚本
- **feat(v1.8.0)**: 安全加固版本，集成密钥生成工具

### Bug 修复

- **fix(circuitbreaker)**: 修复熔断器状态共享问题
- **fix**: 修复 GitHub Actions 工作流中的脚本路径错误
- **docs**: 修复 MkDocs 编译错误并补充缺失文档

### 文档更新

- 添加 v1.8.1 快速开始指南
- 更新 README 添加密钥生成工具使用说明
- 整理 innerdoc 文档结构（第二版）
- 将文档移动到对应子文件夹
- 更新开发计划 -2026 为 v1.8.1 完成状态
- 删除重复的开发计划文件
- 添加英文文档：优化的 Docker 镜像说明
- 更新 Docker 部署文档和优化版镜像说明

### 测试优化

- 删除重复和无价值的单元测试
- 删除 WebAdmin 和资源测试
- 删除 ModelManager 和 Tracing 测试
- 删除更多无价值的测试
- 删除剩余的问题测试
- 添加测试清理总结文档

---

## 📦 Docker 镜像

### 镜像信息

| 属性 | 值 |
|------|-----|
| 镜像仓库 | `sodlinken/jairouter` |
| 版本标签 | `latest`, `v1.8.1` |
| 镜像大小 | ~130MB (优化后) |
| 基础镜像 | Eclipse Temurin 17 JRE |

### 镜像拉取

```bash
# 拉取最新版
docker pull sodlinken/jairouter:latest

# 拉取指定版本
docker pull sodlinken/jairouter:v1.8.1
```

---

## 🆙 升级指南

### 从 v1.7.x 升级

1. 备份现有配置和数据
2. 停止旧版本容器
3. 拉取新镜像：`docker pull sodlinken/jairouter:latest`
4. 使用相同配置启动新版本容器
5. 验证功能正常

### 注意事项

- 首次启动会生成默认管理员密码，请查看日志
- 建议使用密钥生成工具生成新的 JWT 密钥
- 配置版本管理支持回滚，升级前可创建配置版本快照

---

## 📞 问题反馈

如遇到问题，请通过以下方式反馈：

- **GitHub Issues**: https://github.com/Lincoln-cn/JAiRouter/issues
- **在线文档**: https://jairouter.com
- **英文文档**: https://jairouter.com/en

---

## 👏 致谢

感谢所有贡献者和用户的支持！

---

## 📄 许可证

本项目采用 MIT 许可证 - 详见 [LICENSE](LICENSE) 文件

---

**完整提交记录**: [v1.7.3...v1.8.1](https://github.com/Lincoln-cn/JAiRouter/compare/v1.7.3...v1.8.1)
