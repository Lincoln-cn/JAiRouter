# JWT 持久化部署检查清单

<!-- 版本信息 -->
> **文档版本**: 1.0.2
> **最后更新**: 2026-05-21
> **Git 提交**: 61384b4a-
> **作者**: System
<!-- /版本信息 -->

本检查清单确保 JAiRouter 中 JWT 令牌持久化功能的正确部署。

## 部署前检查清单

### 环境设置

- [ ] **Java 环境**
  - [ ] Java 17 或更高版本已安装
  - [ ] JAVA_HOME 环境变量已设置
  - [ ] 已分配足够的堆内存（生产环境最少 2GB）

- [ ] **Docker 环境**
  - [ ] Docker Engine 20.10+ 已安装
  - [ ] Docker Compose 2.0+ 已安装
  - [ ] Docker daemon 正在运行
  - [ ] 有足够的磁盘空间用于容器和卷

- [ ] **网络配置**
  - [ ] 所需端口可用（8080, 6379, 9090, 3000）
  - [ ] 防火墙规则已配置
  - [ ] DNS 解析正常

### 配置验证

- [ ] **环境变量**
  - [ ] `JWT_SECRET` 已设置（最少 32 字符）
  - [ ] `REDIS_PASSWORD` 已设置（强密码）
  - [ ] `JWT_EXPIRATION_MINUTES` 已配置（推荐：生产环境 15）
  - [ ] `JWT_REFRESH_EXPIRATION_DAYS` 已配置（推荐：生产环境 30）

- [ ] **配置文件**
  - [ ] `config/redis.conf` 存在且有效
  - [ ] `src/main/resources/config/security/persistence-base.yml` 已配置
  - [ ] 环境特定配置文件已更新
  - [ ] YAML 语法已验证

- [ ] **Docker 配置**
  - [ ] `docker-compose.yml` 已更新 Redis 服务
  - [ ] `docker-compose.prod.yml` 已配置生产环境
  - [ ] 卷挂载配置正确
  - [ ] 网络配置已验证

### 安全配置

- [ ] **JWT 安全**
  - [ ] 已生成强 JWT 密钥
  - [ ] 已设置合适的令牌过期时间
  - [ ] 黑名单持久化已启用
  - [ ] 审计日志已配置

- [ ] **Redis 安全**
  - [ ] Redis 密码认证已启用
  - [ ] Redis 配置文件已保护
  - [ ] 危险 Redis 命令已禁用
  - [ ] 网络访问已限制

- [ ] **应用安全**
  - [ ] 安全审计日志已启用
  - [ ] API 访问控制已配置
  - [ ] HTTPS/TLS 已配置（生产环境）
  - [ ] 安全头已配置

## 部署步骤

### 步骤 1：配置验证

```bash
# 运行配置验证脚本
./scripts/tools/validate-jwt-persistence-config.sh

# 检查 Docker Compose 配置
docker-compose config
docker-compose -f docker-compose.prod.yml config
```

### 步骤 2：启动基础设施服务

```bash
# 首先启动 Redis
docker-compose up -d redis

# 等待 Redis 健康
docker-compose ps redis
docker-compose logs redis

# 验证 Redis 连接
docker-compose exec redis redis-cli ping
```

### 步骤 3：启动应用

```bash
# 启动 JAiRouter 应用
docker-compose up -d jairouter

# 监控启动日志
docker-compose logs -f jairouter

# 等待应用就绪
curl -f http://localhost:8080/actuator/health
```

### 步骤 4：验证 JWT 持久化

```bash
# 检查 JWT 持久化健康状态
curl http://localhost:8080/actuator/health/jwt-persistence

# 测试 JWT 令牌创建
curl -X POST http://localhost:8080/api/auth/jwt/login \
     -H "Content-Type: application/json" \
     -d '{"username":"admin","password":"admin123"}'

# 测试令牌管理 API
curl -H "Authorization: Bearer <token>" \
     http://localhost:8080/api/auth/jwt/tokens
```

### 步骤 5：启动监控（可选）

```bash
# 启动监控栈
docker-compose -f docker-compose-monitoring.yml up -d

# 验证 Prometheus
curl http://localhost:9090/api/v1/targets

# 验证 Grafana
curl http://localhost:3000/api/health
```

## 部署后验证

### 功能测试

- [ ] **JWT 令牌操作**
  - [ ] 令牌创建正常
  - [ ] 令牌验证正常
  - [ ] 令牌刷新正常
  - [ ] 令牌撤销正常
  - [ ] 黑名单功能正常

- [ ] **持久化验证**
  - [ ] 令牌存储到 Redis
  - [ ] 黑名单条目已持久化
  - [ ] 内存存储备选正常
  - [ ] 清理操作正常

- [ ] **API 端点**
  - [ ] `/api/auth/jwt/tokens` 返回令牌列表
  - [ ] `/api/auth/jwt/tokens/{id}` 返回令牌详情
  - [ ] `/api/auth/jwt/tokens/{id}/revoke` 撤销令牌
  - [ ] `/api/auth/jwt/cleanup` 触发清理
  - [ ] `/api/auth/jwt/blacklist/stats` 返回统计

### 性能测试

- [ ] **负载测试**
  - [ ] 负载下令牌创建
  - [ ] 令牌验证性能
  - [ ] Redis 连接池性能
  - [ ] 负载下内存使用

- [ ] **压力测试**
  - [ ] 高并发令牌操作
  - [ ] 大黑名单性能
  - [ ] 内存清理效果
  - [ ] Redis 故障转移行为

### 安全测试

- [ ] **认证测试**
  - [ ] 无效令牌拒绝
  - [ ] 过期令牌处理
  - [ ] 撤销令牌阻止
  - [ ] 黑名单执行

- [ ] **审计测试**
  - [ ] 安全事件已记录
  - [ ] 审计日志轮转正常
  - [ ] 可疑活动检测
  - [ ] 日志完整性维护

### 监控验证

- [ ] **指标收集**
  - [ ] JWT 操作指标可用
  - [ ] Redis 指标已收集
  - [ ] 应用健康指标
  - [ ] 安全审计指标

- [ ] **告警**
  - [ ] JWT 持久化告警已配置
  - [ ] Redis 连接告警
  - [ ] 安全违规告警
  - [ ] 性能降级告警

## 回滚程序

### 紧急回滚

如果 JWT 持久化导致问题：

```bash
# 1. 禁用 JWT 持久化
export JWT_PERSISTENCE_ENABLED=false
docker-compose restart jairouter

# 2. 或回滚到之前的配置
git checkout HEAD~1 -- src/main/resources/config/security/
docker-compose restart jairouter

# 3. 或停止 Redis 使用内存模式
docker-compose stop redis
# 应用会自动回退到内存存储
```

### 渐进回滚

计划性回滚：

```bash
# 1. 停止新令牌创建（维护模式）
# 2. 等待现有令牌过期
# 3. 在配置中禁用持久化
# 4. 重启应用
# 5. 停止 Redis 服务
```

## 故障排除

### 常见问题

1. **Redis 连接失败**
   ```bash
   # 检查 Redis 状态
   docker-compose ps redis
   docker-compose logs redis

   # 测试连接
   docker-compose exec redis redis-cli ping
   ```

2. **JWT 持久化不工作**
   ```bash
   # 检查应用日志
   docker-compose logs jairouter | grep -i jwt

   # 检查健康端点
   curl http://localhost:8080/actuator/health/jwt-persistence
   ```

3. **高内存使用**
   ```bash
   # 检查内存指标
   curl http://localhost:8080/actuator/metrics/jvm.memory.used

   # 触发手动清理
   curl -X POST http://localhost:8080/api/auth/jwt/cleanup
   ```

4. **安全审计问题**
   ```bash
   # 检查审计日志
   tail -f logs/security-audit.log

   # 检查审计配置
   curl http://localhost:8080/actuator/configprops | grep audit
   ```

### 性能问题

1. **令牌操作慢**
   - 检查 Redis 性能指标
   - 验证网络连接
   - 检查连接池设置
   - 考虑 Redis 集群设置

2. **内存泄漏**
   - 监控 JVM 堆使用
   - 检查清理计划
   - 验证 LRU 淘汰策略
   - 检查令牌保留设置

### 安全问题

1. **未授权访问**
   - 检查 JWT 密钥配置
   - 检查令牌验证逻辑
   - 验证黑名单功能
   - 审计安全日志

2. **审计日志问题**
   - 检查日志文件权限
   - 验证日志轮转设置
   - 检查审计配置
   - 监控磁盘空间

## 维护程序

### 定期维护

- [ ] **每周**
  - [ ] 检查安全审计日志
  - [ ] 检查系统性能指标
  - [ ] 验证备份程序
  - [ ] 更新安全配置

- [ ] **每月**
  - [ ] 轮换 JWT 密钥（如需要）
  - [ ] 检查和更新告警阈值
  - [ ] 性能优化检查
  - [ ] 安全漏洞评估

- [ ] **每季度**
  - [ ] 全面安全审计
  - [ ] 灾难恢复测试
  - [ ] 配置检查和更新
  - [ ] 文档更新

### 备份程序

```bash
# 备份 Redis 数据
docker-compose exec redis redis-cli BGSAVE
docker cp $(docker-compose ps -q redis):/data/dump.rdb ./backup/

# 备份配置
tar -czf backup/config-$(date +%Y%m%d).tar.gz config/

# 备份审计日志
tar -czf backup/audit-logs-$(date +%Y%m%d).tar.gz logs/security-audit.log*
```

### 恢复程序

```bash
# 恢复 Redis 数据
docker-compose stop redis
docker cp ./backup/dump.rdb $(docker-compose ps -q redis):/data/
docker-compose start redis

# 恢复配置
tar -xzf backup/config-20250115.tar.gz

# 重启服务
docker-compose restart
```

## 联系方式

JWT 持久化部署相关问题：

- **文档**: [JWT 持久化配置指南](../configuration/jwt-persistence.md)
- **故障排除**: [常见问题指南](../troubleshooting/common-issues.md)
- **安全**: [安全配置指南](../security/jwt-authentication.md)
- **监控**: [监控设置指南](../monitoring/index.md)

## 附录

### 环境变量参考

| 变量 | 必需 | 默认值 | 描述 |
|------|------|--------|------|
| `JWT_SECRET` | 是 | - | JWT 签名密钥（32+字符） |
| `REDIS_PASSWORD` | 是 | - | Redis 认证密码 |
| `JWT_EXPIRATION_MINUTES` | 否 | 15 | 访问令牌过期时间 |
| `JWT_REFRESH_EXPIRATION_DAYS` | 否 | 30 | 刷新令牌过期时间 |
| `REDIS_HOST` | 否 | redis | Redis 服务器主机名 |
| `REDIS_PORT` | 否 | 6379 | Redis 服务器端口 |

### 端口参考

| 端口 | 服务 | 描述 |
|------|------|------|
| 8080 | JAiRouter | 主应用端口 |
| 6379 | Redis | Redis 服务器端口 |
| 9090 | Prometheus | 监控指标 |
| 3000 | Grafana | 监控仪表板 |
| 9093 | AlertManager | 告警管理 |
| 9121 | Redis Exporter | Redis 指标 |

### 健康检查端点

| 端点 | 描述 |
|------|------|
| `/actuator/health` | 整体应用健康 |
| `/actuator/health/jwt-persistence` | JWT 持久化健康 |
| `/actuator/health/redis` | Redis 连接健康 |
| `/actuator/metrics` | 应用指标 |
| `/actuator/prometheus` | Prometheus 指标 |