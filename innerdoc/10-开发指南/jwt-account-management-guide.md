# JWT账户管理指南

## 概述

JWT账户管理功能提供了完整的用户账户动态管理能力，支持持久化配置和版本管理。该功能参考了 `ConfigurationService` 的实现模式，为JWT认证中的 `accounts` 配置提供了完整的生命周期管理。

## 核心特性

### 1. 配置持久化
- **初始化**：应用首次启动时，将YAML中的账户配置保存为版本1
- **版本管理**：每次账户变更都创建新版本，支持版本回滚
- **配置合并**：持久化配置优先于YAML配置

### 2. 完整的CRUD操作
- 创建、查询、更新、删除JWT账户
- 批量操作支持
- 账户启用/禁用管理

### 3. 安全特性
- 密码自动加密存储
- 角色权限验证
- 操作审计日志

## 配置结构

### YAML配置示例
```yaml
jairouter:
  security:
    jwt:
      enabled: false
      jwt-header: "Jairouter_Token"
      secret: ""
      algorithm: "HS256"
      expiration-minutes: 60
      refresh-expiration-days: 7
      issuer: "jairouter"
      blacklist-enabled: true
      blacklist-cache:
        expiration-seconds: 86400
        max-size: 10000
      # 用户账户配置
      accounts:
        - username: "admin"
          password: "{noop}admin123"  # 开发环境明文密码，生产环境应使用加密
          roles: [ "ADMIN", "USER" ]
          enabled: true
        - username: "user"
          password: "{noop}user123"
          roles: [ "USER" ]
          enabled: true
```

### 持久化存储结构
```
config-store/
├── jwt-accounts-config.json              # 当前活跃的JWT账户配置
├── jwt-accounts-config.metadata.json     # JWT账户配置元数据
└── versions/
    ├── jwt-accounts-config.v1.json       # 版本1（初始化）
    ├── jwt-accounts-config.v2.json       # 版本2（第一次更新）
    └── jwt-accounts-config.v3.json       # 版本3（当前最新）
```

## API接口

### 1. 账户查询接口

#### 获取所有JWT账户
```http
GET /api/security/jwt/accounts
Authorization: Bearer <admin-token>
```

响应示例：
```json
[
  {
    "username": "admin",
    "roles": ["ADMIN", "USER"],
    "enabled": true
  },
  {
    "username": "user",
    "roles": ["USER"],
    "enabled": true
  }
]
```

#### 根据用户名获取账户
```http
GET /api/security/jwt/accounts/{username}
Authorization: Bearer <admin-token>
```

### 2. 账户管理接口

#### 创建JWT账户
```http
POST /api/security/jwt/accounts
Authorization: Bearer <admin-token>
Content-Type: application/json

{
  "username": "newuser",
  "password": "password123",
  "roles": ["USER"],
  "enabled": true
}
```

#### 更新JWT账户
```http
PUT /api/security/jwt/accounts/{username}
Authorization: Bearer <admin-token>
Content-Type: application/json

{
  "username": "newuser",
  "password": "newpassword123",
  "roles": ["USER", "EDITOR"],
  "enabled": true
}
```

#### 删除JWT账户
```http
DELETE /api/security/jwt/accounts/{username}
Authorization: Bearer <admin-token>
```

#### 启用/禁用账户
```http
PATCH /api/security/jwt/accounts/{username}/status?enabled=false
Authorization: Bearer <admin-token>
```

#### 批量更新账户
```http
PUT /api/security/jwt/accounts/batch
Authorization: Bearer <admin-token>
Content-Type: application/json

[
  {
    "username": "user1",
    "password": "password1",
    "roles": ["USER"],
    "enabled": true
  },
  {
    "username": "user2",
    "password": "password2",
    "roles": ["ADMIN"],
    "enabled": true
  }
]
```

### 3. 版本管理接口

#### 获取所有版本
```http
GET /api/security/jwt/accounts/versions
Authorization: Bearer <admin-token>
```

#### 获取指定版本配置
```http
GET /api/security/jwt/accounts/versions/{version}
Authorization: Bearer <admin-token>
```

#### 应用指定版本
```http
POST /api/security/jwt/accounts/versions/{version}/apply
Authorization: Bearer <admin-token>
```

#### 获取当前版本号
```http
GET /api/security/jwt/accounts/versions/current
Authorization: Bearer <admin-token>
```

### 4. 配置管理接口

#### 重置为默认配置
```http
POST /api/security/jwt/accounts/reset
Authorization: Bearer <admin-token>
```

#### 获取配置状态
```http
GET /api/security/jwt/accounts/config/status
Authorization: Bearer <admin-token>
```

响应示例：
```json
{
  "hasPersistedConfig": true,
  "currentVersion": 3,
  "totalVersions": 3
}
```

## 使用场景

### 1. 开发环境账户管理
```bash
# 创建开发用户
curl -X POST http://localhost:8080/api/security/jwt/accounts \
  -H "Authorization: Bearer $ADMIN_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "username": "developer",
    "password": "dev123",
    "roles": ["USER", "DEVELOPER"],
    "enabled": true
  }'
```

### 2. 生产环境账户维护
```bash
# 禁用临时账户
curl -X PATCH "http://localhost:8080/api/security/jwt/accounts/temp-user/status?enabled=false" \
  -H "Authorization: Bearer $ADMIN_TOKEN"

# 更新账户角色
curl -X PUT http://localhost:8080/api/security/jwt/accounts/user1 \
  -H "Authorization: Bearer $ADMIN_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "username": "user1",
    "password": "newpassword",
    "roles": ["USER", "MANAGER"],
    "enabled": true
  }'
```

### 3. 配置版本管理
```bash
# 查看所有版本
curl -X GET http://localhost:8080/api/security/jwt/accounts/versions \
  -H "Authorization: Bearer $ADMIN_TOKEN"

# 回滚到版本2
curl -X POST http://localhost:8080/api/security/jwt/accounts/versions/2/apply \
  -H "Authorization: Bearer $ADMIN_TOKEN"
```

## 安全考虑

### 1. 权限控制
- 所有账户管理操作需要 `ADMIN` 角色
- 用户只能查看自己的账户信息
- 密码信息不会在API响应中返回

### 2. 密码安全
- 密码自动使用 `PasswordEncoder` 加密
- 支持多种加密格式（bcrypt, pbkdf2等）
- 明文密码仅在开发环境使用 `{noop}` 前缀

### 3. 操作审计
- 所有账户变更操作都会记录审计日志
- 包含操作类型、时间戳、操作用户等信息
- 支持配置变更事件发布

## 最佳实践

### 1. 密码策略
```yaml
# 生产环境使用强密码
accounts:
  - username: "admin"
    password: "{bcrypt}$2a$10$..."  # 使用bcrypt加密
    roles: ["ADMIN"]
    enabled: true
```

### 2. 角色设计
```yaml
# 清晰的角色层次
accounts:
  - username: "superadmin"
    roles: ["SUPER_ADMIN", "ADMIN", "USER"]
  - username: "admin"
    roles: ["ADMIN", "USER"]
  - username: "user"
    roles: ["USER"]
```

### 3. 版本管理
- 定期清理旧版本配置
- 重要变更前先备份当前版本
- 使用有意义的变更描述

### 4. 监控告警
- 监控账户创建/删除操作
- 设置异常登录告警
- 跟踪权限变更记录

## 故障排除

### 1. 常见问题

#### 密码加密问题
```
问题：创建账户时密码加密失败
解决：检查 PasswordEncoder Bean 是否正确配置
```

#### 权限验证失败
```
问题：API调用返回403 Forbidden
解决：确保请求token包含ADMIN角色
```

#### 版本回滚失败
```
问题：应用指定版本时配置验证失败
解决：检查目标版本的配置完整性
```

### 2. 日志分析
```bash
# 查看JWT账户相关日志
grep "JWT账户" application.log

# 查看配置变更审计日志
grep "jwt-account" application.log
```

这个JWT账户管理功能完全遵循了现有的配置管理模式，提供了完整的持久化配置和对外服务能力。