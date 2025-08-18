# JAiRouter 安全模块

## 概述

JAiRouter安全模块为AI模型路由网关提供企业级的安全保护能力，包括API Key认证、JWT令牌支持以及请求/响应数据脱敏功能。

## 模块结构

```
security/
├── audit/                    # 安全审计服务
├── authentication/           # 认证相关组件
├── config/                   # 安全配置管理
├── constants/                # 安全常量定义
├── exception/                # 安全异常处理
├── model/                    # 数据模型
├── sanitization/             # 数据脱敏功能
└── util/                     # 安全工具类
```

## 核心功能

### 1. API Key认证
- 支持多个API Key同时有效
- 可配置API Key过期时间
- 支持权限控制
- 提供使用统计功能
- 支持Redis缓存提升性能

### 2. JWT令牌支持
- 标准JWT令牌验证
- 支持令牌刷新机制
- 令牌黑名单功能
- 可配置签名算法和过期时间

### 3. 数据脱敏
- 请求数据脱敏
- 响应数据脱敏
- 支持敏感词和PII数据识别
- 可配置脱敏规则和策略
- 白名单用户跳过机制

### 4. 安全审计
- 完整的安全事件记录
- 可配置的审计日志级别
- 支持实时告警
- 长期存储和归档

## 配置说明

安全功能默认关闭，需要在配置文件中显式启用：

```yaml
jairouter:
  security:
    enabled: true  # 启用安全功能
```

详细配置示例请参考 `application-security-example.yml` 文件。

## 使用方式

### 1. 启用安全功能

在 `application.yml` 中添加：

```yaml
jairouter:
  security:
    enabled: true
```

### 2. 配置API Key

```yaml
jairouter:
  security:
    api-key:
      enabled: true
      keys:
        - key-id: "admin-key"
          key-value: "your-api-key"
          permissions: ["admin", "read", "write"]
```

### 3. 配置数据脱敏

```yaml
jairouter:
  security:
    sanitization:
      request:
        enabled: true
        sensitive-words: ["password", "secret"]
        pii-patterns: ["\\d{11}"]  # 手机号
```

## 依赖项

安全模块依赖以下组件：

- Spring Security WebFlux
- JWT库 (jjwt)
- Redis (可选，用于缓存)
- Lombok (用于减少样板代码)

## 扩展性

安全模块采用模块化设计，支持：

- 自定义认证提供者
- 自定义脱敏规则
- 自定义审计事件处理器
- 插件式配置管理

## 性能考虑

- API Key缓存机制减少数据库查询
- 编译后正则表达式缓存
- 异步审计日志处理
- 流式数据脱敏处理

## 安全最佳实践

1. 定期轮换API Key
2. 使用强密码作为JWT密钥
3. 启用审计日志
4. 配置适当的告警阈值
5. 定期检查安全配置

## 测试

运行安全模块基础测试：

```bash
./mvnw test -Dtest=SecurityModuleFoundationTest
```

## 后续开发

当前已完成基础架构，后续任务包括：

1. 实现API Key认证核心功能
2. 集成Spring Security框架
3. 实现JWT令牌支持
4. 开发数据脱敏功能
5. 实现安全配置管理
6. 开发安全审计和监控
7. 性能优化和缓存实现
8. 集成测试和端到端测试

详细的实施计划请参考 `.kiro/specs/security-enhancement/tasks.md` 文件。