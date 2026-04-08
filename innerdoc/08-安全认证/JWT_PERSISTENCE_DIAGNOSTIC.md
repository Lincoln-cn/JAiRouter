# JWT持久化诊断指南

## 问题描述
`/api/auth/jwt/tokens` 端点返回空数据，需要检查token颁发和移除操作是否正确持久化到H2数据库。

## 已添加的诊断日志

### 1. 服务初始化日志
启动应用时，应该看到以下日志：

```
=== JwtTokenPersistenceServiceImpl initialized with StoreManager: H2StoreManager ===
JWT Token persistence is ENABLED and using H2 database storage

=== JwtBlacklistServiceImpl initialized with StoreManager: H2StoreManager ===
JWT Blacklist persistence is ENABLED and using H2 database storage

=== JwtTokenController initialized ===
JwtPersistenceService available: true
JwtCleanupService available: true
JwtBlacklistService available: true
JwtPersistenceService class: JwtTokenPersistenceServiceImpl
```

### 2. Token颁发日志
用户登录时，应该看到：

```
保存令牌元数据到H2数据库: username=admin, ip=127.0.0.1
✓ 令牌元数据已成功保存到H2数据库: username=admin
```

### 3. Token撤销日志
撤销token时，应该看到：

```
更新令牌状态到H2: tokenHash=abc123..., status=REVOKED, reason=手动撤销
✓ 令牌状态已更新到H2: status=REVOKED
```

## 诊断步骤

### 步骤1: 检查服务是否正确初始化

启动应用并查看日志：

```bash
java -jar target/model-router.jar --spring.profiles.active=h2 2>&1 | grep -E "JwtTokenPersistenceServiceImpl|JwtBlacklistServiceImpl|JwtTokenController"
```

**预期输出**:
- ✅ 看到 "JwtTokenPersistenceServiceImpl initialized"
- ✅ 看到 "JwtBlacklistServiceImpl initialized"
- ✅ 看到 "JwtPersistenceService available: true"

**如果看到**:
- ❌ "JwtPersistenceService available: false"
- ❌ "JwtPersistenceService is NULL"

**原因**: 配置问题，服务未被创建

**解决方案**:
1. 检查 `application-h2.yml` 中的配置
2. 确认 `jairouter.security.jwt.persistence.enabled=true`
3. 确认使用了正确的profile: `--spring.profiles.active=h2`

### 步骤2: 测试Token颁发

```bash
# 登录获取token
curl -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"UqfpTm2Zw7ff2BNnZb8AQo8t"}' \
  -v 2>&1 | tee login.log
```

**查看应用日志**:
```bash
tail -f logs/application.log | grep -E "保存令牌元数据|令牌元数据已成功保存"
```

**预期日志**:
```
保存令牌元数据到H2数据库: username=admin, ip=127.0.0.1
✓ 令牌元数据已成功保存到H2数据库: username=admin
```

**如果看到**:
```
!!! JwtPersistenceService不可用，令牌未持久化 !!!
```

**原因**: 持久化服务未注入

**解决方案**: 检查步骤1的配置

### 步骤3: 验证Token已保存到H2

```bash
# 查询token列表
TOKEN="<从步骤2获取的token>"
curl -X GET "http://localhost:8080/api/auth/jwt/tokens?page=0&size=20" \
  -H "Authorization: Bearer $TOKEN"
```

**预期响应**:
```json
{
  "success": true,
  "data": {
    "items": [
      {
        "id": "...",
        "userId": "admin",
        "tokenHash": "...",
        "status": "ACTIVE",
        "issuedAt": "2025-11-21T...",
        "expiresAt": "2025-11-21T...",
        "ipAddress": "127.0.0.1"
      }
    ],
    "page": 0,
    "size": 20,
    "total": 1
  }
}
```

**如果返回空列表**:
```json
{
  "success": true,
  "data": {
    "items": [],
    "total": 0
  }
}
```

**可能原因**:
1. Token未保存（检查步骤2的日志）
2. 数据库查询失败
3. StoreManager配置错误

### 步骤4: 直接检查H2数据库

访问 H2 控制台: http://localhost:8080/h2-console

**连接信息**:
- JDBC URL: `jdbc:h2:file:./data/config`
- User Name: `sa`
- Password: (留空)

**查询JWT Token数据**:
```sql
-- 查看所有JWT相关的配置键
SELECT config_key, version, is_latest, created_at 
FROM config 
WHERE config_key LIKE 'jwt_%' 
  AND is_latest = true
ORDER BY created_at DESC;

-- 查看具体的token数据
SELECT config_key, config_value, created_at 
FROM config 
WHERE config_key LIKE 'jwt_token_%' 
  AND is_latest = true
LIMIT 5;

-- 统计token数量
SELECT COUNT(*) as token_count
FROM config 
WHERE config_key LIKE 'jwt_token_%' 
  AND is_latest = true;
```

**预期结果**:
- 应该看到 `jwt_token_*` 开头的记录
- `config_value` 字段包含JSON格式的token信息

**如果没有记录**:
- Token确实没有保存到数据库
- 需要检查 StoreManager 的实现

### 步骤5: 检查StoreManager配置

```bash
# 查看StoreManager相关日志
tail -f logs/application.log | grep -E "StoreManager|H2StoreManager"
```

**预期日志**:
```
StoreManagerConfiguration: Creating H2StoreManager
H2StoreManager initialized
```

**检查配置**:
```bash
# 查看store配置
grep -A 10 "^store:" src/main/resources/application-h2.yml
```

**预期配置**:
```yaml
store:
  type: h2
  h2:
    url: file:./data/config
  migration:
    enabled: true
```

### 步骤6: 测试Token撤销

```bash
# 获取token ID
TOKEN_ID="<从步骤3获取>"

# 撤销token
curl -X POST "http://localhost:8080/api/auth/jwt/tokens/${TOKEN_ID}/revoke" \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"reason":"测试撤销"}'
```

**查看应用日志**:
```bash
tail -f logs/application.log | grep -E "更新令牌状态|令牌状态已更新"
```

**预期日志**:
```
更新令牌状态到H2: tokenHash=abc123..., status=REVOKED, reason=测试撤销
✓ 令牌状态已更新到H2: status=REVOKED
```

## 常见问题和解决方案

### 问题1: 服务未初始化
**症状**: 日志中没有 "JwtTokenPersistenceServiceImpl initialized"

**原因**: 
- `@ConditionalOnProperty` 条件不满足
- 配置文件中 `jairouter.security.jwt.persistence.enabled` 不是 `true`

**解决方案**:
```yaml
# 在 application-h2.yml 中确认
jairouter:
  security:
    jwt:
      persistence:
        enabled: true  # 必须是 true
```

### 问题2: StoreManager为null
**症状**: NullPointerException 或 "StoreManager is null"

**原因**: 
- StoreManager bean未创建
- H2数据库配置错误

**解决方案**:
1. 检查 `store.type=h2`
2. 检查 R2DBC 配置
3. 检查数据库文件路径

### 问题3: Token保存失败
**症状**: 日志显示 "保存令牌元数据失败"

**原因**:
- 数据库连接失败
- 序列化错误
- 磁盘空间不足

**解决方案**:
1. 检查数据库连接
2. 检查日志中的详细错误信息
3. 检查磁盘空间: `df -h`

### 问题4: 查询返回空列表但数据库有数据
**症状**: H2控制台能看到数据，但API返回空

**原因**:
- 查询逻辑错误
- 索引问题
- 数据格式不匹配

**解决方案**:
1. 检查 `findAllTokens` 方法的实现
2. 查看详细的查询日志
3. 验证数据格式

## 完整测试脚本

```bash
#!/bin/bash

echo "=== JWT持久化诊断测试 ==="

# 1. 启动应用（假设已启动）
echo "1. 检查服务初始化..."
curl -s http://localhost:8080/actuator/health > /dev/null
if [ $? -eq 0 ]; then
    echo "✓ 应用已启动"
else
    echo "✗ 应用未启动，请先启动应用"
    exit 1
fi

# 2. 登录获取token
echo "2. 测试登录..."
LOGIN_RESPONSE=$(curl -s -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"UqfpTm2Zw7ff2BNnZb8AQo8t"}')

TOKEN=$(echo $LOGIN_RESPONSE | jq -r '.data.token')
if [ "$TOKEN" != "null" ] && [ -n "$TOKEN" ]; then
    echo "✓ 登录成功，获取到token"
else
    echo "✗ 登录失败"
    echo $LOGIN_RESPONSE
    exit 1
fi

# 3. 等待持久化完成
echo "3. 等待持久化..."
sleep 2

# 4. 查询token列表
echo "4. 查询token列表..."
TOKENS_RESPONSE=$(curl -s -X GET "http://localhost:8080/api/auth/jwt/tokens?page=0&size=20" \
  -H "Authorization: Bearer $TOKEN")

TOKEN_COUNT=$(echo $TOKENS_RESPONSE | jq -r '.data.total')
if [ "$TOKEN_COUNT" -gt 0 ]; then
    echo "✓ 查询成功，找到 $TOKEN_COUNT 个token"
    echo $TOKENS_RESPONSE | jq '.data.items[0]'
else
    echo "✗ 查询失败或没有token"
    echo $TOKENS_RESPONSE
fi

echo "=== 测试完成 ==="
```

## 下一步

如果所有诊断步骤都通过，但仍然有问题：

1. 收集完整的应用日志
2. 导出H2数据库内容
3. 检查是否有其他服务实现被意外启用（如Redis）
4. 验证配置文件的加载顺序

## 联系支持

提供以下信息：
- 应用启动日志（包含初始化部分）
- 登录和查询的完整日志
- H2数据库查询结果
- 配置文件内容
