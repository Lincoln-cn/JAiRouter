# Project Summary - JAiRouter API Key Management

## Overall Goal
实现 API 密钥管理功能的安全增强，包括 P0 安全问题修复和功能增强。

## Version
**Current Version: v1.6.1**

## Key Knowledge

### 项目技术栈
| 层 | 技术 |
|---|---|
| 后端 | Java 17, Spring Boot 3.5.5 (WebFlux/Reactive), Spring Security |
| 前端 | Vue 3 + TypeScript + Element Plus + Vite |
| 数据库 | H2 (embedded), R2DBC reactive access |
| 安全 | JWT + API Key 双重认证 |

### API Key 管理核心文件
- `ApiKeyManagementController.java` - REST API 控制器，路径 `/api/auth/api-keys`
- `ApiKeyService.java` - 业务服务，支持哈希存储和缓存管理
- `ApiKey.java` - 数据模型，包含 `verifyKey()` 和 `createSecureCopy()` 方法
- `ApiKeyHashUtil.java` - SHA-256 + 盐值哈希工具类
- `AdminApiRateLimiter.java` - 管理接口速率限制过滤器
- `ApiKeyManagement.vue` - 前端管理页面组件

### v1.6.1 安全增强要点

#### 密钥存储安全
- **哈希存储**: `keyValue` 使用 SHA-256 + 随机盐值哈希存储
- **一次性显示**: 仅创建时返回原始密钥值，之后永不返回
- **常量时间比较**: 使用 `MessageDigest.isEqual()` 防止时序攻击
- **自动迁移**: 旧明文密钥自动迁移到哈希存储

#### 速率限制
| 限制类型 | 阈值 |
|----------|------|
| 每分钟请求 | 30 次/IP |
| 每小时请求 | 100 次/IP |
| 创建操作 | 10 次/小时/IP |

#### 新增功能
- **IP 白名单**: `allowedIpAddresses` 字段，支持多个 IP 地址
- **每日请求限制**: `dailyRequestLimit` 字段，限制单密钥每日请求次数
- **密钥重置**: `/api/auth/api-keys/{keyId}/reset` 端点，生成新密钥值

#### 强类型 DTO/VO
- `ApiKeyVO` - 列表/详情响应（不含 keyValue）
- `ApiKeyCreationVO` - 创建响应（含 keyValue，仅此一次）
- `ApiKeyListVO` - 列表响应，含统计数据
- `ApiKeyCreateRequest` - 创建请求
- `ApiKeyUpdateRequest` - 更新请求

## Recent Actions

### v1.6.1 完成的工作
| 级别 | 问题 | 解决方案 | 状态 |
|------|------|----------|------|
| **P0** | API Key 明文存储 | SHA-256 + 盐值哈希 (`ApiKeyHashUtil`) | ✅ 完成 |
| **P0** | 管理接口缺少速率限制 | `AdminApiRateLimiter` | ✅ 完成 |
| **P1** | 缺少 IP 白名单功能 | `allowedIpAddresses` + `isIpAllowed()` | ✅ 完成 |
| **P2** | 单密钥使用次数无上限 | `dailyRequestLimit` + `isDailyLimitExceeded()` | ✅ 完成 |
| - | 前端使用 Map | 强类型 DTO/VO | ✅ 完成 |
| - | 表格布局优化 | 描述栏 200px，操作栏 260px，支持横向滚动 | ✅ 完成 |

### API 端点列表
| 方法 | 路径 | 功能 |
|------|------|------|
| GET | `/api/auth/api-keys` | 获取密钥列表 |
| POST | `/api/auth/api-keys` | 创建密钥 |
| GET | `/api/auth/api-keys/{keyId}` | 获取密钥详情 |
| PUT | `/api/auth/api-keys/{keyId}` | 更新密钥 |
| DELETE | `/api/auth/api-keys/{keyId}` | 删除密钥 |
| POST | `/api/auth/api-keys/{keyId}/reset` | 重置密钥值 |
| PATCH | `/api/auth/api-keys/{keyId}/enable` | 启用密钥 |
| PATCH | `/api/auth/api-keys/{keyId}/disable` | 禁用密钥 |

## Current Plan

### 剩余 P1 任务
| 任务 | 描述 | 优先级 |
|------|------|--------|
| 密钥轮换机制 | 定期自动轮换 API Key，或在安全事件后强制轮换 | 中 |
| 创建者信息记录 | 创建时记录创建者用户名和 IP 地址 | 中 |
| 过期密钥自动清理 | 定时任务清理或禁用过期密钥 | 中 |

### 剩余 P2 任务
| 任务 | 描述 |
|------|------|
| 密钥使用统计持久化 | usage 统计数据持久化到数据库 |
| JWT Token 密钥轮换 | 支持 JWT 签名密钥定期轮换 |
| API Key 批量导入/导出 | 支持批量管理密钥 |

### 待修复
| 任务 | 说明 |
|------|------|
| 测试用例修复 | `JwtAccountApiTest.java` 和 `ApiKeyTest.java` 因 DTO 变更导致编译失败 |

## Security Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                    API Key 认证流程                          │
├─────────────────────────────────────────────────────────────┤
│  1. 请求携带 X-API-Key 或 Jairouter_Token                    │
│  2. DefaultAuthenticationConverter 提取认证信息              │
│  3. CustomReactiveAuthenticationManager 验证                 │
│     - JWT: 解析验证 Token                                    │
│     - API Key: SHA-256 哈希比对                              │
│  4. 检查: 启用状态、过期时间、IP 白名单、每日限制              │
│  5. 授权访问资源                                              │
└─────────────────────────────────────────────────────────────┘
```

---

## Summary Metadata
**Update time**: 2026-04-09T07:55:00.000Z
**Version**: v1.6.1