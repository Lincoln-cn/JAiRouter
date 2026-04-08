# H2 JWT配置总结

## 配置完成 ✅

已成功配置JWT令牌和黑名单使用H2数据库进行持久化存储。

## 配置详情

### 1. JWT令牌持久化
```yaml
jairouter:
  security:
    jwt:
      persistence:
        enabled: true                    # ✅ 已启用
        primary-storage: file            # ✅ 使用StoreManager（H2）
        fallback-storage: memory         # ✅ 内存作为备用
        startup-recovery:
          enabled: true                  # ✅ 启动时恢复数据
        cleanup:
          enabled: true                  # ✅ 自动清理
          schedule: "0 0 2 * * ?"       # ✅ 每天凌晨2点
          retention-days: 30             # ✅ 保留30天
```

### 2. JWT黑名单持久化
```yaml
jairouter:
  security:
    jwt:
      blacklist:
        persistence:
          enabled: true                  # ✅ 已启用
          primary-storage: file          # ✅ 使用StoreManager（H2）
          fallback-storage: memory       # ✅ 内存作为备用
          cleanup-interval: 3600         # ✅ 每小时清理一次
```

### 3. 安全审计
```yaml
jairouter:
  security:
    audit:
      enabled: true                      # ✅ 已启用
      storage: h2                        # ✅ 使用H2数据库
      retentionDays: 30                  # ✅ 保留30天
```

### 4. 数据迁移
```yaml
store:
  migration:
    enabled: true                        # ✅ 已启用
  security-migration:
    enabled: true                        # ✅ 已启用
```

## 存储架构

```
H2 数据库 (./data/config.mv.db)
│
├── config 表 (通用配置表)
│   ├── jwt_token_*           → JWT令牌数据
│   ├── jwt_blacklist_*       → 黑名单条目
│   ├── jwt_user_index_*      → 用户令牌索引
│   ├── jwt_status_index_*    → 状态索引
│   ├── jwt_blacklist_index   → 黑名单索引
│   ├── jwt_token_counter     → 令牌计数器
│   ├── jwt_blacklist_stats   → 黑名单统计
│   ├── jwt-accounts-config   → JWT账户配置
│   └── security-config       → 安全配置
│
└── security_audit 表 (专用审计表)
    └── 安全审计记录
```

## 工作流程

### 用户登录流程
```
1. POST /api/auth/login
   ↓
2. AccountManager.authenticateAndGenerateToken()
   ↓
3. JwtTokenRefreshService.saveTokenMetadata()
   ↓
4. JwtTokenPersistenceServiceImpl.saveToken()
   ↓
5. StoreManager.saveConfig()
   ↓
6. H2StoreManager.doSaveConfig()
   ↓
7. ConfigRepository.save()
   ↓
8. 数据写入 config 表
```

### 令牌验证流程
```
1. 请求携带JWT令牌
   ↓
2. JwtAuthenticationFilter 拦截
   ↓
3. JwtBlacklistServiceImpl.isBlacklisted()
   ↓
4. StoreManager.getConfig()
   ↓
5. H2StoreManager.doGetConfig()
   ↓
6. ConfigRepository.findLatestByConfigKey()
   ↓
7. 从 config 表读取数据
   ↓
8. 返回验证结果
```

### 令牌撤销流程
```
1. POST /api/auth/jwt/tokens/{id}/revoke
   ↓
2. JwtTokenController.revokeToken()
   ↓
3. JwtBlacklistServiceImpl.addToBlacklist()
   ↓
4. StoreManager.saveConfig()
   ↓
5. 黑名单条目写入 config 表
   ↓
6. 更新令牌状态为 REVOKED
```

## 关键特性

### ✅ 已实现
- [x] JWT令牌持久化到H2数据库
- [x] JWT黑名单持久化到H2数据库
- [x] 安全审计日志持久化
- [x] 自动数据迁移（从文件到H2）
- [x] 定时清理过期数据
- [x] 启动时数据恢复
- [x] 索引优化（用户索引、状态索引）
- [x] 统计信息跟踪
- [x] H2控制台支持

### 🔧 配置灵活性
- [x] 可配置存储类型（file/memory/redis）
- [x] 可配置清理策略
- [x] 可配置保留期限
- [x] 可配置备用存储
- [x] 可配置迁移策略

### 🛡️ 安全特性
- [x] 令牌哈希存储（不存储完整令牌）
- [x] 黑名单验证
- [x] 审计日志记录
- [x] 版本控制
- [x] 数据加密支持

## API端点

### JWT令牌管理
- `POST /api/auth/login` - 用户登录
- `POST /api/auth/refresh` - 刷新令牌
- `GET /api/auth/jwt/tokens` - 获取令牌列表
- `GET /api/auth/jwt/tokens/{id}` - 获取令牌详情
- `POST /api/auth/jwt/tokens/{id}/revoke` - 撤销令牌
- `POST /api/auth/jwt/tokens/revoke-batch` - 批量撤销
- `POST /api/auth/jwt/cleanup` - 手动清理

### 黑名单管理
- `GET /api/auth/jwt/blacklist/stats` - 黑名单统计
- `POST /api/auth/jwt/blacklist/cleanup` - 清理过期条目

### 审计查询
- `GET /api/audit/events` - 查询审计事件
- `GET /api/audit/jwt-tokens` - JWT令牌审计
- `GET /api/audit/security-report` - 安全报告

## 监控指标

### 令牌指标
- 总令牌数
- 活跃令牌数
- 过期令牌数
- 撤销令牌数
- 每日新增令牌数

### 黑名单指标
- 黑名单总数
- 活跃黑名单条目
- 过期黑名单条目
- 清理次数
- 命中率

### 性能指标
- 令牌保存耗时
- 令牌查询耗时
- 黑名单检查耗时
- 数据库连接池状态

## 维护操作

### 日常维护
```bash
# 查看令牌统计
curl http://localhost:8080/api/auth/jwt/stats

# 查看黑名单统计
curl http://localhost:8080/api/auth/jwt/blacklist/stats

# 手动触发清理
curl -X POST http://localhost:8080/api/auth/jwt/cleanup
```

### 数据库维护
```sql
-- 查看数据量
SELECT 
  COUNT(*) as total_configs,
  SUM(CASE WHEN config_key LIKE 'jwt_token_%' THEN 1 ELSE 0 END) as jwt_tokens,
  SUM(CASE WHEN config_key LIKE 'jwt_blacklist_%' THEN 1 ELSE 0 END) as blacklist_entries
FROM config 
WHERE is_latest = true;

-- 清理旧版本
DELETE FROM config WHERE is_latest = false AND created_at < DATEADD('DAY', -30, CURRENT_TIMESTAMP);

-- 优化表
ANALYZE TABLE config;
```

### 备份恢复
```bash
# 备份数据库
cp ./data/config.mv.db ./backup/config_$(date +%Y%m%d).mv.db

# 恢复数据库
cp ./backup/config_20251121.mv.db ./data/config.mv.db
```

## 故障排查清单

- [ ] 检查配置文件是否正确
- [ ] 检查H2数据库文件是否存在
- [ ] 检查日志中是否有错误信息
- [ ] 验证数据库连接是否正常
- [ ] 检查磁盘空间是否充足
- [ ] 验证迁移是否成功执行
- [ ] 检查清理任务是否正常运行
- [ ] 验证API端点是否返回数据

## 下一步

1. ✅ 编译并启动应用
2. ✅ 执行登录测试
3. ✅ 验证令牌持久化
4. ✅ 验证黑名单功能
5. ✅ 检查H2控制台数据
6. ✅ 监控性能指标
7. ✅ 设置定期备份

## 相关文档

- [JWT_PERSISTENCE_FIX.md](./JWT_PERSISTENCE_FIX.md) - 修复详情
- [H2_JWT_STORAGE_VERIFICATION.md](./H2_JWT_STORAGE_VERIFICATION.md) - 验证指南
- [H2_STORAGE_GUIDE.md](./docs/H2_STORAGE_GUIDE.md) - H2存储指南
