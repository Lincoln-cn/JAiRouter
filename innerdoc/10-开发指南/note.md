# 当前开发环境
使用Windows 系统进行开发，开发中使用到的各类脚本及运行时，均需要考虑windows平台的适配性。

当前项目状态：
- 主要开发环境：Windows 10/11
- 构建工具：Maven Wrapper (./mvnw)
- 代码质量检查：SpotBugs + Checkstyle（当前配置较严格，影响正常测试执行）
- 测试框架：JUnit + Spring Boot Test
- 开发IDE：主要使用IntelliJ IDEA

存在的问题：
1. 代码质量检查工具配置过于严格，导致 `mvn test` 无法正常执行
2. 部分脚本和命令需要同时兼容Windows和Linux环境
3. Maven Wrapper脚本在不同平台下的执行方式不同（Windows: mvnw.cmd, Linux: ./mvnw）

# 需适配的运行环境
需要考虑 windows 平台 和 类Linux 平台

具体适配要求：
- **Windows平台**：支持Windows 10及以上版本，使用PowerShell或CMD执行脚本
- **Linux平台**：支持主流Linux发行版（Ubuntu、CentOS、Alpine等）
- **macOS平台**：作为类Unix系统，与Linux平台保持一致的适配策略
- **容器环境**：支持Docker容器部署，主要基于Linux镜像
跨平台注意事项：
- 路径分隔符差异（Windows: `\`, Linux/macOS: `/`）
- 脚本执行权限设置（Linux/macOS需要chmod +x）
- 环境变量设置方式不同
- 换行符差异（CRLF vs LF）

# 快速编译说明

## 问题描述
项目中使用了 SpotBugs 和 Checkstyle 代码质量检查工具，直接执行 `compiler:compile compiler:testCompile` 命令时会因为代码质量检查失败而导致测试无法正常运行。

## 解决方案
使用以下命令跳过代码质量检查，直接执行测试：

```bash
./mvnw compiler:compile compiler:testCompile -Pfast
```

# 测试执行说明

## 问题描述
项目中使用了 SpotBugs 和 Checkstyle 代码质量检查工具，直接执行 `mvn test` 命令时会因为代码质量检查失败而导致测试无法正常运行。

## 解决方案
使用以下命令跳过代码质量检查，直接执行测试：

```bash
./mvnw surefire:test -Dtest=UniversalControllerMetricsTest
```
# 文档结构说明
## 目录组织
- **docs 目录**：存放面向第三方用户的公开文档，包括API文档、使用指南等
- **innerdoc 目录**：存放面向开发团队的内部文档，包括设计文档、开发规范等

## 使用建议
- 新增用户文档请放入 `docs` 目录
- 开发相关的技术文档请放入 `innerdoc` 目录
- 文档格式建议使用 Markdown (.md) 格式，便于版本控制和协作

# 项目开源地址
https://github.com/Lincoln-cn/JAiRouter