# JWT持久化快速检查清单

## 启动检查 ✓

启动应用后，在日志中查找以下内容：

- [ ] `JwtTokenPersistenceServiceImpl initialized with StoreManager: H2StoreManager`
- [ ] `JWT Token persistence is ENABLED`
- [ ] `JwtBlacklistServiceImpl initialized with StoreManager: H2StoreManager`
- [ ] `JwtTokenController initialized`
- [ ] `JwtPersistenceService available: true`
- [ ] `JwtPersistenceService class: JwtTokenPersistenceServiceImpl`

**如果看到 `JwtPersistenceService available: false`**:
→ 配置问题，检查 `application-h2.yml`

## 登录检查 ✓

执行登录后，在日志中查找：

- [ ] `保存令牌元数据到H2数据库: username=admin`
- [ ] `✓ 令牌元数据已成功保存到H2数据库`

**如果看到 `JwtPersistenceService不可用，令牌未持久化`**:
→ 服务未注入，检查启动日志

**如果看到 `✗ 保存令牌元数据失败`**:
→ 数据库问题，检查错误详情

## 查询检查 ✓

查询token列表：

```bash
curl -X GET "http://localhost:8080/api/auth/jwt/tokens?page=0&size=20" \
  -H "Authorization: Bearer <token>"
```

- [ ] 返回状态码 200
- [ ] `success: true`
- [ ] `data.total > 0`
- [ ] `data.items` 包含token信息

**如果返回空列表**:
→ 检查H2数据库

## H2数据库检查 ✓

访问 http://localhost:8080/h2-console

```sql
SELECT COUNT(*) FROM config WHERE config_key LIKE 'jwt_token_%' AND is_latest = true;
```

- [ ] 返回数量 > 0

**如果数量为0**:
→ Token确实未保存，检查保存日志

**如果数量>0但API返回空**:
→ 查询逻辑问题，启用DEBUG日志

## 配置检查 ✓

检查 `application-h2.yml`:

```yaml
jairouter:
  security:
    jwt:
      persistence:
        enabled: true  # ← 必须是 true
```

- [ ] `persistence.enabled: true`
- [ ] `blacklist.persistence.enabled: true`
- [ ] `store.type: h2`
- [ ] `store.migration.enabled: true`

## 快速测试脚本

```bash
#!/bin/bash
echo "=== JWT持久化快速检查 ==="

# 1. 检查服务状态
echo "1. 检查应用状态..."
curl -s http://localhost:8080/actuator/health | jq .

# 2. 登录
echo "2. 登录..."
TOKEN=$(curl -s -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"UqfpTm2Zw7ff2BNnZb8AQo8t"}' | jq -r '.data.token')

echo "Token: ${TOKEN:0:20}..."

# 3. 等待持久化
sleep 2

# 4. 查询
echo "3. 查询token列表..."
curl -s -X GET "http://localhost:8080/api/auth/jwt/tokens?page=0&size=20" \
  -H "Authorization: Bearer $TOKEN" | jq '.data.total'

echo "=== 检查完成 ==="
```

## 常见问题速查

| 症状 | 原因 | 解决方案 |
|------|------|----------|
| 服务未初始化 | 配置未启用 | 设置 `persistence.enabled: true` |
| Token未保存 | 服务未注入 | 检查启动日志 |
| 保存失败 | 数据库错误 | 检查H2连接和权限 |
| 查询返回空 | 数据未保存或查询错误 | 检查H2数据库 |
| 撤销失败 | 服务不可用 | 同上 |

## 日志关键字

搜索这些关键字快速定位问题：

- `initialized` - 服务初始化
- `available` - 服务可用性
- `保存令牌` - Token保存操作
- `✓` - 成功操作
- `✗` - 失败操作
- `!!!` - 严重问题警告

## 支持信息收集

如果问题仍未解决，收集以下信息：

```bash
# 1. 启动日志
grep -E "initialized|available" logs/application.log > startup.log

# 2. 操作日志
grep -E "保存令牌|更新令牌|✓|✗" logs/application.log > operations.log

# 3. 错误日志
grep -E "ERROR|WARN|!!!" logs/application.log > errors.log

# 4. H2数据
# 在H2控制台执行并导出结果
SELECT * FROM config WHERE config_key LIKE 'jwt_%' AND is_latest = true;

# 5. 配置文件
cat src/main/resources/application-h2.yml > config.yml
```
