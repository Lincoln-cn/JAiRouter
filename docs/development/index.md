# 开发指南

欢迎参与 JAiRouter 的开发！本节提供了开发相关的指南和规范。

## 开发概述

JAiRouter 是一个基于 Spring Boot 的 Java 项目，采用响应式编程模型。

## 技术栈

- **Java 17+** - 主要开发语言
- **Spring Boot 3.5.x** - 应用框架
- **Spring WebFlux** - 响应式 Web 框架
- **Reactor Core** - 响应式编程支持
- **Maven** - 构建工具

## 开发环境

- **IDE**: IntelliJ IDEA（推荐）
- **JDK**: OpenJDK 17 或更高版本
- **Maven**: 3.6 或更高版本
- **Git**: 版本控制

## 开发章节

- [架构说明](architecture.md) - 系统架构和设计原理
- [贡献指南](contributing.md) - 如何参与项目开发
- [测试指南](testing.md) - 测试策略和规范
- [代码质量](code-quality.md) - 代码规范和质量标准

## 快速开始

1. 克隆项目：`git clone https://github.com/your-org/jairouter.git`
2. 构建项目：`./mvnw clean package`
3. 运行测试：`./mvnw test`
4. 启动应用：`./mvnw spring-boot:run`