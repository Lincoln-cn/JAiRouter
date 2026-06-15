# 开发指南

<!-- 版本信息 -->
> **文档版本**: 2.0.0
> **最后更新**: 2026-06-15
> **Git 提交**: 0f56b957
> **作者**: JAiRouter Team
<!-- /版本信息 -->

欢迎来到 JAiRouter 开发指南！本指南为开发者提供了完整的开发环境搭建、代码规范、测试策略和贡献流程。

## 快速开始

如果您是第一次参与 JAiRouter 开发，建议按以下顺序阅读：

1. **[架构说明](architecture.md)** - 了解系统整体架构和设计原则
2. **[贡献指南](contributing.md)** - 学习如何参与项目开发
3. **[测试指南](testing.md)** - 掌握测试策略和最佳实践
4. **[代码质量标准](code-quality.md)** - 遵循项目代码规范

---

## 开发环境要求

| 工具 | 版本要求 | 说明 |
|------|---------|------|
| **Java** | 17+ | 必须，LTS 版本 |
| **Maven** | 3.8+ | 推荐使用项目内置 Maven Wrapper |
| **Git** | 2.20+ | 版本控制 |
| **IDE** | - | IntelliJ IDEA（推荐）或 Eclipse |

---

## 核心开发流程

### 1. 环境准备

```bash
# 克隆项目
git clone https://github.com/Lincoln-cn/JAiRouter.git
cd jairouter

# 构建项目（中国用户推荐）
./mvnw clean package -Pchina

# 运行测试
./mvnw compiler:compile compiler:testCompile surefire:test
```

### 2. 开发规范

- 遵循 [代码质量标准](code-quality.md) 中的编码规范
- 使用 Checkstyle 和 SpotBugs 进行代码质量检查
- 确保测试覆盖率不低于 80%

### 3. 提交流程

```bash
# 创建功能分支
git checkout -b feature/your-feature-name

# 编写代码和测试
# ...

# 运行测试和质量检查
./mvnw clean verify

# 提交代码
git add .
git commit -m "feat: your feature description"

# 推送分支
git push origin feature/your-feature-name

# 创建 Pull Request
```

---

## 项目架构概览

JAiRouter 采用模块化设计，主要包含以下核心模块：

| 模块 | 包路径 | 职责 |
|------|--------|------|
| **auth** | `org.unreal.modelrouter.auth` | 认证授权、JWT、API Key |
| **config** | `org.unreal.modelrouter.config` | 配置管理、版本控制 |
| **router** | `org.unreal.modelrouter.router` | 路由、负载均衡、限流、熔断 |
| **monitor** | `org.unreal.modelrouter.monitor` | 监控、追踪、指标 |
| **persistence** | `org.unreal.modelrouter.persistence` | 数据持久化、仓库 |
| **common** | `org.unreal.modelrouter.common` | 公共工具、异常、常量 |

详细信息请参考 [架构说明](architecture.md)。

---

## 常用命令

### 构建命令

```bash
# 完整构建（包含所有检查）
./mvnw clean package

# 快速构建（跳过检查）
./mvnw clean package -Pfast

# 中国镜像加速
./mvnw clean package -Pchina
```

### 测试命令

```bash
# 运行所有测试
./mvnw test

# 运行特定测试
./mvnw test -Dtest=LoadBalancerTest

# 生成覆盖率报告
./mvnw clean test jacoco:report
```

### 质量检查

```bash
# 代码风格检查
./mvnw checkstyle:check

# 静态分析
./mvnw spotbugs:check

# 完整质量检查
./mvnw verify
```

---

## 开发工具集成

### 代码质量工具

| 工具 | 用途 | 配置文件 |
|------|------|---------|
| **Checkstyle** | 代码风格检查 | `checkstyle.xml` |
| **SpotBugs** | 静态代码分析 | `spotbugs-security-*.xml` |
| **JaCoCo** | 代码覆盖率分析 | `pom.xml` |

### 测试框架

| 框架 | 用途 |
|------|------|
| **JUnit 5** | 主要测试框架 |
| **Mockito** | Mock 框架 |
| **Spring Boot Test** | 集成测试支持 |
| **Reactor Test** | 响应式流测试 |

---

## 扩展开发

### 添加新适配器

1. 创建适配器类继承 `BaseAdapter`
2. 实现请求转换和响应映射逻辑
3. 注册到 `AdapterRegistry`
4. 编写单元测试

详细说明请参考 [贡献指南](contributing.md)。

### 添加新负载均衡策略

1. 实现 `LoadBalancer` 接口
2. 在 `LoadBalancerFactory` 中注册
3. 添加配置支持
4. 编写测试用例

### 添加新限流算法

1. 实现 `RateLimiter` 接口
2. 在 `RateLimiterFactory` 中注册
3. 添加 YAML 配置支持
4. 编写并发测试

---

## 获得帮助

如果在开发过程中遇到问题，可以通过以下方式获得帮助：

- 查看项目文档和 FAQ
- 搜索已有的 GitHub Issues
- 创建新的 Issue 描述问题
- 参与社区讨论

---

## 贡献方式

我们欢迎各种形式的贡献：

| 类型 | 说明 |
|------|------|
| 🐛 **Bug 报告** | 发现问题请及时报告 |
| ✨ **功能开发** | 实现新功能或改进现有功能 |
| 📚 **文档改进** | 完善文档内容和示例 |
| 🧪 **测试补充** | 增加测试用例提高覆盖率 |
| 💡 **建议讨论** | 提出改进建议和想法 |

详细的贡献流程请参考 [贡献指南](contributing.md)。

---

感谢您对 JAiRouter 项目的贡献！
