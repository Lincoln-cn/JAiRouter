# 开发指南

<!-- 版本信息 -->
> **文档版本**: 1.0.0  
> **最后更新**: 2025-08-18  
> **Git 提交**: 3418d3f6  
> **作者**: Lincoln
<!-- /版本信息 -->


欢迎来到 JAiRouter 开发指南！本指南为开发者提供了完整的开发环境搭建、代码规范、测试策略和贡献流程。

## 快速开始

如果您是第一次参与 JAiRouter 开发，建议按以下顺序阅读：

1. **[架构说明](architecture.md)** - 了解系统整体架构和设计原则
2. **[贡献指南](contributing.md)** - 学习如何参与项目开发
3. **[测试指南](testing.md)** - 掌握测试策略和最佳实践
4. **[代码质量标准](code-quality.md)** - 遵循项目代码规范

## 开发环境要求

- **Java**: 17 或更高版本
- **Maven**: 3.8+ (推荐使用项目内置的 Maven Wrapper)
- **Git**: 2.20+
- **IDE**: IntelliJ IDEA (推荐) 或 Eclipse

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
- 创建功能分支进行开发
- 编写单元测试和集成测试
- 提交 Pull Request 进行代码审查
- 合并到主分支

## 项目架构概览

JAiRouter 采用模块化设计，主要包含以下核心模块：

- **控制器层**: 统一 API 入口和配置管理
- **适配器层**: 不同后端服务的协议适配
- **负载均衡层**: 多种负载均衡策略实现
- **限流层**: 多种限流算法支持
- **熔断层**: 服务容错和故障恢复
- **存储层**: 配置持久化和版本管理

详细信息请参考 [架构说明](architecture.md)。

## 开发工具集成

### 代码质量工具
- **Checkstyle**: 代码风格检查
- **SpotBugs**: 静态代码分析
- **JaCoCo**: 代码覆盖率分析

### 测试框架
- **JUnit 5**: 主要测试框架
- **Mockito**: Mock 框架
- **Spring Boot Test**: 集成测试支持
- **Reactor Test**: 响应式流测试

### 构建和部署
- **Maven**: 项目构建和依赖管理
- **Docker**: 容器化部署
- **GitHub Actions**: 持续集成和部署

## 常用命令

```bash
# 完整构建（包含所有检查）
./mvnw clean package

# 快速构建（跳过检查）
./mvnw clean package -Pfast

# 运行特定测试
./mvnw test -Dtest=LoadBalancerTest

# 生成覆盖率报告
./mvnw clean test jacoco:report

# 代码质量检查
./mvnw checkstyle:check spotbugs:check
```

## 获得帮助

如果在开发过程中遇到问题，可以通过以下方式获得帮助：

- 查看项目文档和 FAQ
- 搜索已有的 GitHub Issues
- 创建新的 Issue 描述问题
- 参与社区讨论

## 贡献方式

我们欢迎各种形式的贡献：

- 🐛 **Bug 报告**: 发现问题请及时报告
- ✨ **功能开发**: 实现新功能或改进现有功能
- 📚 **文档改进**: 完善文档内容和示例
- 🧪 **测试补充**: 增加测试用例提高覆盖率
- 💡 **建议讨论**: 提出改进建议和想法

详细的贡献流程请参考 [贡献指南](contributing.md)。

感谢您对 JAiRouter 项目的贡献！