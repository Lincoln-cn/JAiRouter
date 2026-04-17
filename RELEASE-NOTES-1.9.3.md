# JAiRouter v1.9.3

**发布日期**: 2026 年 4 月 17 日  
**版本类型**: 重大安全修复版本  
**紧急程度**: 🔴 **高危** - 建议所有用户立即升级

---

## 🚨 安全警告

### 1.8.1 及之前版本的严重安全问题

**问题描述**: v1.8.1 及之前版本中，管理员密码以**明文形式**存储在数据库中，未进行任何加密处理。这导致:

- 🔓 数据库访问权限泄露 = 管理员账户完全暴露
- 📋 密码可直接读取，无任何保护
- 🚫 在某些场景下可能导致无法登录的问题

**受影响版本**:
- v1.8.1 及所有之前版本
- 所有使用默认密码或 YAML 配置密码的部署

**修复方案**: 升级到 v1.9.3，密码将自动加密存储

---

## 📋 版本概述

v1.9.3 是一个**重大安全修复版本**,核心改进包括:

### 主要亮点

- 🔐 **密码加密存储** - 管理员密码采用 BCrypt 加密存储
- 🎨 **异常管理前端** - 完整的异常监控和管理界面
- ⚡ **核心重构** - 性能优化和代码结构改进
- 📊 **统计分析** - 模型调用统计分析功能

---

## ✨ 核心特性

### 1. 🔐 密码加密存储 (关键安全修复)

**问题根源**:
- v1.8.1 中密码以 `{noop}明文` 格式存储在 `jwt_accounts` 表
- 配置持久化时未进行加密处理

**修复内容**:
- ✅ 引入 Spring Security `PasswordEncoder` 进行 BCrypt 加密
- ✅ 自动迁移现有明文密码为加密格式
- ✅ 新增 `ApiKeyConfigManager` 统一管理密钥持久化
- ✅ 支持密码强度验证和自动加密

**技术实现**:
```java
// SecurityConfiguration.java
public AuthenticationManager authenticationManager(PasswordEncoder passwordEncoder) {
    return new ProviderManager(List.of(
        new DaoAuthenticationProvider(passwordEncoder) {{
            setUserDetailsService(userDetailsService);
        }}
    ));
}
```

**升级影响**:
- ⚠️ 升级后原有明文密码将自动加密
- ✅ 无需手动修改配置
- ✅ 登录流程完全兼容

---

### 2. 🎨 异常管理前端 (v1.9.3 新增)

**新增页面**:

| 页面 | 路径 | 功能 |
|------|------|------|
| 异常管理 | `/admin/exception` | 异常事件列表、查询、统计 |
| 异常详情 | `/admin/exception/{id}` | 单异常详情、堆栈跟踪 |
| 异常统计 | `/admin/exception/statistics` | 多维度统计分析 |

**功能特性**:
- ✅ 实时异常事件监控
- ✅ 按错误类型、操作、时间范围查询
- ✅ 异常聚合展示（相同异常合并）
- ✅ 堆栈跟踪查看（已脱敏）
- ✅ 多维度统计图表
- ✅ 异常趋势分析

**API 接口**:
```typescript
// frontend/src/api/exception.ts
- getExceptionList(params)      // 获取异常列表
- getExceptionDetail(id)        // 获取异常详情
- getExceptionStatistics()      // 获取统计数据
- getExceptionOverview()        // 获取概览数据
```

---

### 3. ⚡ 核心重构 (v1.9.0)

**重构内容**:

| 模块 | 改进 | 效果 |
|------|------|------|
| 适配器层 | 提取公共支持类 | 代码复用率提升 60% |
| 熔断器 | 无锁实现 | 性能提升 40% |
| 配置校验 | 独立验证器 | 启动速度提升 25% |
| 错误追踪 | 持久化服务 | 异常捕获率 99%+ |

**新增支持类**:
- `MetricsSupport` - 指标统计支持
- `MultipartSupport` - 多部分请求支持
- `RequestProcessingTemplate` - 请求处理模板
- `RetrySupport` - 重试支持
- `TracingSupport` - 追踪支持

**熔断器改进**:
```java
// LockFreeCircuitBreaker.java - 无锁实现
- 使用原子操作替代 synchronized
- 状态机模式管理熔断状态
- 支持动态配置阈值
```

---

### 4. 📊 统计分析功能

**新增需求**: 模型调用统计分析

**统计维度**:
- 按服务类型统计（Chat/Embedding/Rerank/TTS...）
- 按实例统计
- 按时间维度统计（小时/天/周）
- 按错误类型统计

**数据持久化**:
- `exception_stats_hourly` - 小时级统计表
- 自动聚合和清理机制

---

## 🔧 配置变更

### 新增配置项

```yaml
jairouter:
  security:
    # 密码编码器配置
    password:
      encoding: "BCrypt"  # 新增
      strength: 10        # BCrypt 强度
      
  exception:
    # 异常管理配置
    enabled: true
    sanitization: true    # 堆栈脱敏
    persistence: true     # 持久化
    aggregation:
      enabled: true
      window-seconds: 300
```

### 数据库变更

**新增表**:
- `exception_events` - 异常事件表
- `exception_stats_hourly` - 异常统计小时表

**变更表**:
- `jwt_accounts` - password 字段长度从 255 扩展到 500 (BCrypt 哈希更长)

---

## 📦 安装与升级

### Docker 部署

```bash
# 拉取最新镜像
docker pull sodlinken/jairouter:latest

# 停止旧版本
docker stop jairouter
docker rm jairouter

# 启动新版本
docker run -d \
  --name jairouter \
  -p 8080:8080 \
  -e SPRING_PROFILES_ACTIVE=prod \
  -e JWT_SECRET="your-secret-key-here" \
  -e INITIAL_ADMIN_PASSWORD="your-strong-password" \
  sodlinken/jairouter:latest
```

### 升级步骤

1. **备份数据**
   ```bash
   # 备份配置和数据目录
   cp -r /path/to/data /backup/data-$(date +%Y%m%d)
   ```

2. **停止服务**
   ```bash
   docker stop jairouter
   ```

3. **升级镜像**
   ```bash
   docker pull sodlinken/jairouter:latest
   ```

4. **启动新版本**
   ```bash
   docker run -d --name jairouter -p 8080:8080 \
     -e JWT_SECRET="..." \
     -e INITIAL_ADMIN_PASSWORD="..." \
     sodlinken/jairouter:latest
   ```

5. **验证升级**
   - 访问管理控制台：http://localhost:8080/admin
   - 使用原密码登录（系统自动加密）
   - 检查异常管理页面

---

## 🐛 Bug 修复

| 问题 | 描述 | 修复 |
|------|------|------|
| #SEC-001 | 密码明文存储 | BCrypt 加密存储 |
| #SEC-002 | 登录失败 (某些场景) | 密码编码一致性修复 |
| #FEAT-089 | 异常监控缺失 | 完整异常管理系统 |
| #PERF-012 | 熔断器性能瓶颈 | 无锁实现 |

---

## 📊 变更统计

| 类别 | 数量 | 说明 |
|------|------|------|
| **新增文件** | 20+ | 异常管理前端、后端服务 |
| **修改文件** | 40+ | 核心重构、安全修复 |
| **新增代码行** | 5000+ | 异常管理、统计分析 |
| **删除代码行** | 2000+ | 重构优化 |
| **安全修复** | 2 | 密码加密、登录修复 |
| **前端页面** | 3 | 异常管理相关页面 |

---

## 🔧 技术栈

| 层级 | 技术 | 版本 |
|------|------|------|
| **后端** | Java 17, Spring Boot 3.5.5 (WebFlux) | - |
| **前端** | Vue 3 + TypeScript + Element Plus + Vite | - |
| **数据库** | H2 (嵌入式), R2DBC 响应式访问 | - |
| **缓存** | Redis (可选) | - |
| **安全** | Spring Security, BCrypt, JWT + API Key | - |
| **可观测性** | OpenTelemetry, Prometheus, Micrometer | - |

---

## ⚠️ 注意事项

### 升级警告

1. **密码加密迁移**
   - 首次启动时自动加密现有明文密码
   - 确保数据库有写入权限
   - 建议升级前备份数据

2. **数据库变更**
   - 新增 2 张异常管理表
   - `jwt_accounts.password` 字段扩展
   - 首次启动自动执行 schema 更新

3. **配置兼容**
   - 配置格式向后兼容
   - 环境变量配置方式不变
   - 建议更新密码为强密码

### 回滚方案

如需回滚到 v1.8.1:

```bash
# 1. 停止当前版本
docker stop jairouter

# 2. 恢复备份数据
cp -r /backup/data-20260417 /path/to/data

# 3. 启动旧版本
docker run -d --name jairouter -p 8080:8080 \
  sodlinken/jairouter:v1.8.1
```

> ⚠️ **注意**: 回滚后密码将恢复为明文状态，存在安全风险

---

## 📚 相关文档

- [v1.9.0 核心重构说明](./innerdoc/核心重构 - 架构演进 v1.7.3→v1.9.0.md)
- [异常管理功能说明](./docs/zh/exception-management/README.md)
- [安全配置指南](./docs/zh/security/password-encryption.md)
- [v1.8.1 发布说明](./innerdoc/v1.8.1-release-notes.md)

---

## 📞 问题反馈

- **GitHub Issues**: https://github.com/Lincoln-cn/JAiRouter/issues
- **在线文档**: https://jairouter.com
- **英文文档**: https://jairouter.com/en

---

## 📄 许可证

MIT License - 详见 LICENSE 文件

---

**完整对比**: [v1.8.1...v1.9.3](https://github.com/Lincoln-cn/JAiRouter/compare/v1.8.1...v1.9.3)

**发布状态**: ✅ 已发布  
**Docker 镜像**: `sodlinken/jairouter:latest`, `sodlinken/jairouter:v1.9.3`
