# H2 JWT存储配置验证指南

## 配置概览

### JWT令牌持久化 ✅
- **状态**: 已启用
- **存储方式**: H2数据库（通过StoreManager）
- **表**: `config` 表
- **键前缀**: `jwt_token_*`

### JWT黑名单持久化 ✅
- **状态**: 已启用
- **存储方式**: H2数据库（通过StoreManager）
- **表**: `config` 表
- **键前缀**: `jwt_blacklist_*`

### 安全审计 ✅
- **状态**: 已启用
- **存储方式**: H2数据库（专用表）
- **表**: `security_audit` 表

## 验证步骤

### 1. 启动应用
```bash
mvn clean package
java -jar target/model-router.jar --spring.profiles.active=h2
```

### 2. 登录获取令牌
```bash
curl -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{
    "username": "admin",
    "password": "UqfpTm2Zw7ff2BNnZb8AQo8t"
  }'
```

响应示例：
```json
{
  "success": true,
  "data": {
    "token": "eyJhbGciOiJIUzI1NiJ9...",
    "tokenType": "Bearer",
    "expiresIn": 43200
  },
  "message": "登录成功"
}
```

### 3. 验证令牌已持久化
```bash
# 使用获取的token查询令牌列表
curl -X GET "http://localhost:8080/api/auth/jwt/tokens?page=0&size=20" \
  -H "Authorization: Bearer <your-token>"
```

预期响应：
```json
{
  "success": true,
  "data": {
    "items": [
      {
        "id": "uuid",
        "userId": "admin",
        "tokenHash": "hash-value",
        "status": "ACTIVE",
        "issuedAt": "2025-11-21T...",
        "expiresAt": "2025-11-21T...",
        "ipAddress": "127.0.0.1",
        "userAgent": "curl/..."
      }
    ],
    "page": 0,
    "size": 20,
    "total": 1
  }
}
```

### 4. 验证黑名单功能
```bash
# 撤销令牌（将其加入黑名单）
curl -X POST "http://localhost:8080/api/auth/jwt/tokens/<token-id>/revoke" \
  -H "Authorization: Bearer <your-token>" \
  -H "Content-Type: application/json" \
  -d '{
    "reason": "测试黑名单功能"
  }'
```

### 5. 查看黑名单统计
```bash
curl -X GET "http://localhost:8080/api/auth/jwt/blacklist/stats" \
  -H "Authorization: Bearer <your-token>"
```

### 6. 通过H2控制台验证数据

访问 http://localhost:8080/h2-console

**连接信息**:
- JDBC URL: `jdbc:h2:file:./data/config`
- User Name: `sa`
- Password: (留空)

**查询JWT令牌**:
```sql
-- 查看所有JWT相关配置
SELECT * FROM config WHERE config_key LIKE 'jwt_%' AND is_latest = true;

-- 查看JWT令牌
SELECT * FROM config WHERE config_key LIKE 'jwt_token_%' AND is_latest = true;

-- 查看黑名单
SELECT * FROM config WHERE config_key LIKE 'jwt_blacklist_%' AND is_latest = true;

-- 查看安全审计
SELECT * FROM security_audit ORDER BY timestamp DESC LIMIT 10;
```

## 数据存储结构

### JWT令牌数据示例
```json
{
  "id": "550e8400-e29b-41d4-a716-446655440000",
  "userId": "admin",
  "tokenHash": "abc123...",
  "tokenType": "Bearer",
  "status": "ACTIVE",
  "issuedAt": "2025-11-21T10:00:00",
  "expiresAt": "2025-11-21T22:00:00",
  "deviceInfo": null,
  "ipAddress": "127.0.0.1",
  "userAgent": "curl/7.68.0",
  "createdAt": "2025-11-21T10:00:00",
  "updatedAt": "2025-11-21T10:00:00"
}
```

### 黑名单条目示例
```json
{
  "tokenHash": "xyz789...",
  "expiresAt": "2025-12-21T10:00:00",
  "reason": "用户主动撤销",
  "addedBy": "admin",
  "addedAt": "2025-11-21T10:00:00"
}
```

## 故障排查

### 问题1: 令牌列表为空
**可能原因**:
- JWT持久化未启用
- 用户还未登录过
- 数据库连接失败

**解决方案**:
1. 检查配置: `jairouter.security.jwt.persistence.enabled=true`
2. 查看日志中是否有"令牌元数据保存成功"的消息
3. 检查H2数据库连接

### 问题2: 黑名单不生效
**可能原因**:
- 黑名单持久化未启用
- 令牌哈希计算不一致

**解决方案**:
1. 检查配置: `jairouter.security.jwt.blacklist.persistence.enabled=true`
2. 查看日志中黑名单相关的DEBUG信息
3. 验证H2数据库中是否有黑名单记录

### 问题3: 数据未迁移
**可能原因**:
- 迁移未启用
- 源文件不存在

**解决方案**:
1. 检查配置: `store.migration.enabled=true`
2. 确认 `config/` 目录下有配置文件
3. 查看启动日志中的迁移信息

## 性能监控

### 关键指标
- JWT令牌总数
- 活跃令牌数
- 黑名单条目数
- 过期条目数
- 清理操作频率

### 监控端点
```bash
# 令牌统计
curl -X GET "http://localhost:8080/api/auth/jwt/stats" \
  -H "Authorization: Bearer <your-token>"

# 黑名单统计
curl -X GET "http://localhost:8080/api/auth/jwt/blacklist/stats" \
  -H "Authorization: Bearer <your-token>"
```

## 维护建议

1. **定期清理**: 自动清理任务每天凌晨2点运行
2. **监控大小**: 定期检查数据库大小，避免无限增长
3. **备份策略**: 定期备份 `./data/config.mv.db` 文件
4. **日志审查**: 定期查看安全审计日志
5. **性能优化**: 如果令牌数量超过10万，考虑增加清理频率或缩短保留期
