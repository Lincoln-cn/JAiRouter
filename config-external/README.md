# 外部配置使用指南

## 目录结构

```
config-external/
├── application.yml      # 主配置覆盖文件
├── auth/
│   └── jwt.yml          # JWT 配置（敏感）
├── router/
│   └── services.yml     # 服务实例配置（API Token）
└── README.md            # 本文件
```

## Docker 部署使用方法

### 1. 复制配置目录

```bash
# 将配置目录复制到生产服务器
scp -r config-external/ production-server:/path/to/jairouter/config
```

### 2. 配置环境变量

创建 `.env` 文件（参考 `.env.example`）：

```bash
# 必须配置的环境变量
JWT_SECRET=your-very-secure-jwt-secret-key-at-least-32-characters-long
GPUSTACK_API_TOKEN=your-gpustack-api-token
INITIAL_ADMIN_PASSWORD=your-initial-admin-password
```

### 3. Docker Compose 挂载

```yaml
volumes:
  # 外部配置挂载（只读）
  - ./config:/app/config:ro
  # 其他挂载
  - ./logs:/app/logs
  - ./data:/app/data
```

## 配置加载优先级

配置按以下优先级加载（高优先级覆盖低优先级）：

1. **环境变量** - 最高优先级
2. **外部配置文件** (`/app/config/*.yml`)
3. **环境配置文件** (`application-{env}.yml`)
4. **模块配置文件** (`classpath:config/*.yml`)
5. **默认配置** - 最低优先级

## 敏感配置处理规则

### 必须使用环境变量的配置

| 配置项 | 环境变量 | 说明 |
|--------|----------|------|
| JWT 密钥 | `JWT_SECRET` | ≥32 字符，不能硬编码 |
| 管理员密码 | `INITIAL_ADMIN_PASSWORD` | 初始化密码 |
| API Token | `GPUSTACK_API_TOKEN` | 第三方服务 Token |
| Redis 密码 | `REDIS_PASSWORD` | Redis 连接密码 |

### 配置文件中的写法

```yaml
jairouter:
  security:
    jwt:
      # 使用环境变量，如果未设置则为空（启动时校验）
      secret: "${JWT_SECRET:}"
      
      # 可选环境变量，有默认值
      expiration-minutes: ${JWT_EXPIRATION_MINUTES:60}
```

## 配置验证

应用启动时会自动校验：

- JWT_SECRET 必须存在且 ≥32 字符
- INITIAL_ADMIN_PASSWORD 必须存在（首次启动）
- 服务实例 URL 必须有效（如果配置了实例）

## 配置热更新

部分配置支持热更新（无需重启）：

- 限流阈值
- 熔断器阈值
- 服务实例权重

通过 Admin Console 或 API 更新：
- `/api/services/{serviceType}/ratelimit`
- `/api/services/{serviceType}/circuitbreaker`
- `/api/services/{serviceType}/instances/{instanceId}/weight`

## 配置备份

定期备份配置：

```bash
# 备份配置目录
tar -czf config-backup-$(date +%Y%m%d).tar.gz config/

# 备份环境变量（加密存储）
# 注意：不要将 .env 文件提交到 Git
```

## 配置迁移

从旧版本迁移配置：

1. 对比新旧配置文件结构
2. 将旧配置值迁移到新的模块化配置
3. 敏感配置迁移到环境变量
4. 测试启动验证配置正确性

详见：`docs/configuration-migration-guide.md`