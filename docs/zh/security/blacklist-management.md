# 安全黑名单管理指南

<!-- 版本信息 -->
> **文档版本**: 1.7.0
> **最后更新**: 2026-05-21
> **Git 提交**: 61384b4a
> **作者**: Lincoln
<!-- /版本信息 -->

## 概述

JAiRouter 安全黑名单功能提供了一种主动防御机制，允许管理员阻止可疑的 IP 地址、用户账户或 JWT 令牌访问系统。通过黑名单管理，可以有效防止恶意攻击、滥用行为和未授权访问。

## 功能特性

### 核心功能

- **多类型支持**：支持 IP、用户、令牌三种黑名单类型
- **过期机制**：支持设置黑名单条目过期时间
- **自动清理**：定期清理过期的黑名单记录
- **统计分析**：提供黑名单统计数据和趋势分析
- **审计日志**：记录所有黑名单操作，便于追溯

### 黑名单类型

| 类型 | 说明 | 使用场景 | 验证时机 |
|------|-----|---------|---------|
| **IP 黑名单** | 阻止特定 IP 地址访问 | 防止 DDoS 攻击、恶意爬虫 | 请求入口 |
| **用户黑名单** | 阻止特定用户登录 | 禁用可疑账户、离职员工 | JWT 验证 |
| **令牌黑名单** | 阻止特定 JWT 令牌 | 撤销被盗令牌、强制登出 | JWT 验证 |

## 快速开始

### 1. 通过管理界面操作

访问管理控制台 `/admin/security/blacklist` 可以：

- 查看当前黑名单列表
- 添加新的黑名单条目
- 编辑或删除现有条目
- 查看黑名单统计数据

### 2. 通过 API 操作

#### 添加 IP 黑名单

```bash
curl -X POST "http://localhost:8080/api/security/blacklist" \
     -H "Authorization: Bearer admin_token" \
     -H "Content-Type: application/json" \
     -d '{
       "type": "IP",
       "value": "192.168.1.100",
       "reason": "DDoS攻击来源",
       "expiresAt": "2026-05-10T00:00:00Z"
     }'
```

## 详细配置

### 黑名单条目属性

| 属性 | 类型 | 必填 | 说明 |
|------|-----|-----|------|
| `type` | string | 是 | 黑名单类型：IP、USER、TOKEN |
| `value` | string | 是 | 黑名单值（IP地址、用户名、令牌ID） |
| `reason` | string | 否 | 加入黑名单的原因 |
| `expiresAt` | timestamp | 否 | 过期时间，不设置则永不过期 |
| `createdBy` | string | 否 | 创建者（系统自动填充） |

### IP 黑名单配置

#### 单个 IP 地址

```json
{
  "type": "IP",
  "value": "192.168.1.100"
}
```

#### CIDR 网段

```json
{
  "type": "IP",
  "value": "10.0.0.0/8",
  "reason": "内部网络不允许外部访问"
}
```

#### IP 范围

```json
{
  "type": "IP",
  "value": "172.16.0.0-172.16.255.255",
  "reason": "可疑IP范围"
}
```

### 用户黑名单配置

```json
{
  "type": "USER",
  "value": "malicious_user",
  "reason": "多次违规操作",
  "expiresAt": "2026-06-10T00:00:00Z"
}
```

### 令牌黑名单配置

```json
{
  "type": "TOKEN",
  "value": "token-uuid-12345",
  "reason": "令牌被盗用",
  "expiresAt": "2026-04-11T00:00:00Z"
}
```

## API 参考

### 添加黑名单条目

```http
POST /api/security/blacklist
Authorization: Bearer {token}
Content-Type: application/json
```

请求体：
```json
{
  "type": "IP",
  "value": "192.168.1.100",
  "reason": "攻击来源",
  "expiresAt": "2026-05-10T00:00:00Z"
}
```

响应：
```json
{
  "success": true,
  "message": "黑名单条目已添加",
  "data": {
    "id": "entry-uuid",
    "type": "IP",
    "value": "192.168.1.100",
    "reason": "攻击来源",
    "createdAt": "2026-04-10T10:30:00Z",
    "expiresAt": "2026-05-10T00:00:00Z"
  }
}
```

### 获取黑名单列表

```http
GET /api/security/blacklist?type={type}&page={page}&size={size}&status={status}
Authorization: Bearer {token}
```

参数说明：
- `type`：可选，筛选类型（IP/USER/TOKEN）
- `page`：可选，页码，默认 0
- `size`：可选，每页条数，默认 20
- `status`：可选，状态筛选（ACTIVE/EXPIRED）

响应：
```json
{
  "success": true,
  "data": {
    "content": [
      {
        "id": "entry-uuid-1",
        "type": "IP",
        "value": "192.168.1.100",
        "reason": "攻击来源",
        "createdAt": "2026-04-10T10:30:00Z",
        "expiresAt": "2026-05-10T00:00:00Z",
        "status": "ACTIVE"
      }
    ],
    "totalElements": 150,
    "totalPages": 8,
    "size": 20,
    "number": 0
  }
}
```

### 获取单个黑名单条目

```http
GET /api/security/blacklist/{entryId}
Authorization: Bearer {token}
```

### 更新黑名单条目

```http
PUT /api/security/blacklist/{entryId}
Authorization: Bearer {token}
Content-Type: application/json
```

请求体：
```json
{
  "reason": "更新后的原因",
  "expiresAt": "2026-06-10T00:00:00Z"
}
```

### 删除黑名单条目

```http
DELETE /api/security/blacklist/{entryId}
Authorization: Bearer {token}
```

### 批量添加黑名单

```http
POST /api/security/blacklist/batch
Authorization: Bearer {token}
Content-Type: application/json
```

请求体：
```json
{
  "entries": [
    {"type": "IP", "value": "192.168.1.100", "reason": "攻击来源"},
    {"type": "IP", "value": "192.168.1.101", "reason": "攻击来源"},
    {"type": "USER", "value": "malicious_user", "reason": "违规操作"}
  ]
}
```

响应：
```json
{
  "success": true,
  "data": {
    "addedCount": 3,
    "skippedCount": 0,
    "errorCount": 0,
    "details": [
      {"value": "192.168.1.100", "status": "ADDED"},
      {"value": "192.168.1.101", "status": "ADDED"},
      {"value": "malicious_user", "status": "ADDED"}
    ]
  }
}
```

### 获取黑名单统计

```http
GET /api/security/blacklist/stats
Authorization: Bearer {token}
```

响应：
```json
{
  "success": true,
  "data": {
    "totalEntries": 150,
    "activeEntries": 120,
    "expiredEntries": 30,
    "byType": {
      "IP": 80,
      "USER": 30,
      "TOKEN": 40
    },
    "recentlyAdded": 15,
    "recentlyExpired": 5,
    "topReasons": [
      {"reason": "攻击来源", "count": 50},
      {"reason": "违规操作", "count": 30},
      {"reason": "令牌被盗", "count": 20}
    ]
  }
}
```

### 清理过期条目

```http
POST /api/security/blacklist/cleanup
Authorization: Bearer {token}
```

响应：
```json
{
  "success": true,
  "data": {
    "cleanedCount": 30,
    "cleanedAt": "2026-04-10T10:30:00Z"
  }
}
```

## 黑名单验证机制

### IP 黑名单验证

每个请求进入系统时，首先检查客户端 IP 是否在黑名单中：

1. 提取客户端真实 IP（考虑代理场景）
2. 检查 IP 是否匹配黑名单中的条目
3. 支持单个 IP、CIDR 网段、IP 范围匹配
4. 如果匹配，返回 403 Forbidden

### 用户黑名单验证

JWT 令牌验证时检查用户是否在黑名单中：

1. 解析 JWT 令牌获取用户名
2. 检查用户是否在黑名单中
3. 如果匹配，返回 401 Unauthorized

### 令牌黑名单验证

JWT 令牌验证时检查令牌是否在黑名单中：

1. 获取令牌 ID（jti claim）
2. 检查令牌 ID 是否在黑名单中
3. 如果匹配，返回 401 Unauthorized

## 自动清理机制

系统自动清理过期的黑名单记录：

### 清理配置

```yaml
jairouter:
  security:
    blacklist:
      cleanup:
        enabled: true
        schedule: "0 0 3 * * ?"  # 每天凌晨3点
        retention-days: 30       # 过期后保留30天用于审计
```

### 清理策略

- **过期条目**：`expiresAt` 时间已过的条目
- **保留策略**：可配置保留已过期记录用于审计分析
- **批量处理**：批量清理，避免一次性大量删除

## 监控和告警

### 监控指标

- `jairouter_security_blacklist_total`：黑名单总条目数
- `jairouter_security_blacklist_active`：活跃黑名单条目数
- `jairouter_security_blacklist_blocked_requests`：被黑名单阻止的请求数
- `jairouter_security_blacklist_by_type`：按类型统计的黑名单数量

### 告警配置

```yaml
jairouter:
  security:
    blacklist:
      alerts:
        enabled: true
        # 黑名单条目过多告警
        max-entries-threshold: 10000
        # 单一 IP 短时间内大量请求告警
        ip-request-rate-threshold: 1000
```

## 最佳实践

### 1. 合理设置过期时间

- **临时封禁**：设置短期过期时间（几小时到几天）
- **长期封禁**：不设置过期时间或设置较长时间
- **定期审查**：定期审查黑名单，移除不再需要的条目

### 2. 使用批量操作

- 大量条目使用批量添加 API
- 定期导出黑名单用于备份
- 使用导入功能迁移配置

### 3. 配合其他安全功能

- 结合审计日志分析可疑行为
- 与限流器配合防止暴力攻击
- 与 JWT 黑名单功能协同

### 4. 记录清晰原因

- 添加黑名单时记录清晰的原因
- 便于后续审查和申诉处理
- 支持自动化审计报告生成

## 故障排除

### 常见问题

#### 1. IP 黑名单不生效

**可能原因**：
- 代理环境下 IP 获取不正确
- CIDR 格式配置错误

**解决方案**：
1. 检查 `X-Forwarded-For` 或 `X-Real-IP` 头配置
2. 验证 IP 格式是否正确

#### 2. 用户仍能登录

**可能原因**：
- 用户名拼写错误
- 黑名单未同步到验证层

**解决方案**：
1. 检查黑名单条目值是否正确
2. 清除认证缓存

#### 3. 黑名单条目过多

**可能原因**：
- 自动化攻击导致大量条目添加
- 未设置过期时间

**解决方案**：
1. 配置自动清理
2. 设置合理的过期时间
3. 使用 CIDR 网段代替单个 IP

## 相关文档

- [API Key 管理指南](api-key-management.md)
- [JWT 认证配置说明](jwt-authentication.md)
- [安全功能故障排除指南](troubleshooting.md)
- [审计日志管理](audit-log-management.md)
- [数据脱敏配置](data-sanitization.md)