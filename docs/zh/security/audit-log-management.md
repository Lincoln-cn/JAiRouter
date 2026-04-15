# 审计日志管理

<!-- 版本信息 -->
> **文档版本**: 1.7.0
> **最后更新**: 2026-04-10
> **Git 提交**: 2cba097
> **作者**: Lincoln
<!-- /版本信息 -->

## 概述

JAiRouter 审计日志功能记录了系统中所有重要的安全相关操作和配置变更，为安全分析、问题排查和合规审计提供完整的数据支持。

## 功能特性

### 核心功能

- **全面记录**：记录登录、配置变更、API Key 操作等关键事件
- **不可篡改**：审计日志一旦生成不可修改
- **长期保存**：支持日志归档和长期存储
- **查询分析**：提供多维度查询和统计分析
- **合规支持**：满足安全审计合规要求

### 审计日志类型

| 类型 | 说明 | 记录内容 |
|------|-----|---------|
| **安全审计** | 登录、登出、权限变更 | 用户、IP、时间、结果 |
| **配置审计** | 配置项创建、更新、删除 | 配置 Key、变更前后值、操作人 |
| **API Key 审计** | API Key 创建、删除、轮换 | Key 名称、操作类型、操作人 |
| **黑名单审计** | 黑名单添加、删除、清理 | 类型、值、原因、操作人 |

## 审计日志结构

### 日志字段

| 字段 | 类型 | 说明 |
|------|-----|------|
| `id` | string | 日志唯一标识 |
| `operation` | string | 操作类型（LOGIN、CREATE、UPDATE、DELETE） |
| `resource_type` | string | 资源类型（USER、CONFIG、API_KEY、BLACKLIST） |
| `resource_id` | string | 资源标识 |
| `operator` | string | 操作人 |
| `operator_ip` | string | 操作人 IP |
| `request_uri` | string | 请求 URI |
| `request_method` | string | 请求方法 |
| `old_value` | string | 变更前值（JSON） |
| `new_value` | string | 变更后值（JSON） |
| `status` | string | 操作状态（SUCCESS、FAILURE） |
| `message` | string | 操作描述 |
| `created_at` | timestamp | 创建时间 |

### 日志示例

#### 登录审计

```json
{
  "id": "audit-log-001",
  "operation": "LOGIN",
  "resource_type": "USER",
  "resource_id": "admin",
  "operator": "admin",
  "operator_ip": "192.168.1.100",
  "request_uri": "/api/auth/login",
  "request_method": "POST",
  "status": "SUCCESS",
  "message": "用户 admin 登录成功",
  "created_at": "2026-04-10T10:30:00Z"
}
```

#### 配置变更审计

```json
{
  "id": "audit-log-002",
  "operation": "UPDATE",
  "resource_type": "CONFIG",
  "resource_id": "rate-limiter.default",
  "operator": "admin",
  "operator_ip": "192.168.1.100",
  "request_uri": "/api/config/rate-limiter/default",
  "request_method": "PUT",
  "old_value": "{\"rate\":100,\"window\":60}",
  "new_value": "{\"rate\":200,\"window\":60}",
  "status": "SUCCESS",
  "message": "更新限流器配置",
  "created_at": "2026-04-10T11:00:00Z"
}
```

## 查询审计日志

### 通过管理界面

访问管理控制台 `/admin/security/audit-logs` 可以：

- 查看审计日志列表
- 按条件筛选日志
- 导出日志数据
- 查看日志详情

### 通过 API 查询

#### 获取审计日志列表

```http
GET /api/security/audit-logs?operation={operation}&resourceType={type}&operator={user}&page={page}&size={size}
Authorization: Bearer {token}
```

参数说明：
- `operation`：可选，筛选操作类型
- `resourceType`：可选，筛选资源类型
- `operator`：可选，筛选操作人
- `page`：可选，页码，默认 0
- `size`：可选，每页条数，默认 20

响应：
```json
{
  "success": true,
  "data": {
    "content": [
      {
        "id": "audit-log-001",
        "operation": "LOGIN",
        "resource_type": "USER",
        "operator": "admin",
        "operator_ip": "192.168.1.100",
        "status": "SUCCESS",
        "created_at": "2026-04-10T10:30:00Z"
      }
    ],
    "totalElements": 150,
    "totalPages": 8,
    "size": 20,
    "number": 0
  }
}
```

#### 获取单个日志详情

```http
GET /api/security/audit-logs/{logId}
Authorization: Bearer {token}
```

#### 导出审计日志

```http
POST /api/security/audit-logs/export
Authorization: Bearer {token}
Content-Type: application/json
```

请求体：
```json
{
  "filters": {
    "operation": "UPDATE",
    "resourceType": "CONFIG",
    "startTime": "2026-04-01T00:00:00Z",
    "endTime": "2026-04-10T23:59:59Z"
  },
  "format": "CSV"
}
```

## 配置变更审计

### 自动记录

系统自动记录以下配置变更：

- 限流器配置变更
- 熔断器配置变更
- 负载均衡配置变更
- 服务实例配置变更

### 审计内容

每次配置变更会记录：

1. **变更操作**：创建、更新、删除
2. **配置 Key**：被修改的配置项
3. **变更前后值**：完整的配置对比
4. **操作人信息**：用户名和 IP 地址
5. **操作时间**：精确到秒级

### 配置审计表结构

```sql
CREATE TABLE config_change_audit_log (
    id VARCHAR(64) PRIMARY KEY,
    config_key VARCHAR(256) NOT NULL,
    operation VARCHAR(32) NOT NULL,
    old_value TEXT,
    new_value TEXT,
    operator VARCHAR(64),
    operated_at TIMESTAMP NOT NULL,
    ip_address VARCHAR(45),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
```

## 日志管理

### 日志清理

定期清理过期的审计日志：

```yaml
jairouter:
  security:
    audit-log:
      cleanup:
        enabled: true
        schedule: "0 0 2 * * ?"  # 每天凌晨 2 点
        retention-days: 180      # 保留 180 天
```

### 日志归档

支持将历史日志归档到外部存储：

```yaml
jairouter:
  security:
    audit-log:
      archive:
        enabled: true
        schedule: "0 0 3 * * 0"  # 每周日凌晨 3 点
        storage: "s3"            # s3、oss、local
        path: "/archive/audit-logs"
```

### 日志备份

建议定期备份审计日志用于合规审计：

```bash
# 导出最近 30 天的审计日志
curl -X POST "http://localhost:8080/api/security/audit-logs/export" \
     -H "Authorization: Bearer admin_token" \
     -H "Content-Type: application/json" \
     -d '{
       "filters": {
         "startTime": "2026-03-10T00:00:00Z",
         "endTime": "2026-04-10T23:59:59Z"
       },
       "format": "JSON"
     }' > audit-logs-backup.json
```

## 监控和告警

### 监控指标

- `jairouter_audit_logs_total`：审计日志总数
- `jairouter_audit_logs_by_operation`：按操作类型统计
- `jairouter_audit_logs_by_status`：按状态统计
- `jairouter_audit_logs_failed`：失败的操作数

### 告警配置

```yaml
jairouter:
  security:
    audit-log:
      alerts:
        enabled: true
        # 登录失败次数过多
        login-failure-threshold: 5
        # 配置变更频率过高
        config-change-rate-threshold: 10
```

## 最佳实践

### 1. 定期审计分析

- 每周审查关键配置变更
- 每月生成审计报告
- 季度合规审计检查

### 2. 日志保护

- 审计日志只读权限
- 防止日志篡改
- 异地备份存储

### 3. 关联分析

- 结合登录日志和操作日志
- 分析异常操作模式
- 发现潜在安全风险

### 4. 合规要求

- 满足等保 2.0 要求
- 符合 GDPR 数据保护
- 支持 SOX 审计要求

## 故障排除

### 常见问题

#### 1. 审计日志未记录

**可能原因**：
- 审计功能未启用
- 日志表空间不足

**解决方案**：
1. 检查配置 `jairouter.security.audit-log.enabled`
2. 检查数据库空间和连接

#### 2. 日志查询缓慢

**可能原因**：
- 日志数据量过大
- 缺少索引

**解决方案**：
1. 定期清理历史日志
2. 为查询字段添加索引
3. 使用日志归档

## 相关文档

- [API Key 管理指南](api-key-management.md)
- [JWT 认证配置说明](jwt-authentication.md)
- [安全黑名单管理](blacklist-management.md)
- [数据脱敏配置](data-sanitization.md)
