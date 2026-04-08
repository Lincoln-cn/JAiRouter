# JPA 迁移版本迭代计划 (v1.5.x)

## 目标
逐步将 R2DBC 迁移到 JPA，消除 R2DBC SQL 语法问题，提升系统稳定性。

## 核心原则
1. **向后兼容**：每次迭代保持原有 API 不变
2. **可测试**：每个版本都有完整的测试验证
3. **可回滚**：任何问题可立即回退到上一版本
4. **逐步替换**：不一次性改动过多代码

---

## 版本路线图

### v1.5.0 - 基础准备阶段 【已完成】
- [x] 分析现有 R2DBC 使用情况
- [x] 设计 JPA Entity 结构
- [x] 确定迁移策略

### v1.5.1 - JPA 基础设施 【当前阶段】
**目标**：添加 JPA 依赖，创建并行存储层

**任务清单**：
- [ ] 添加 Spring Data JPA 依赖
- [ ] 配置 JPA 数据源（与 R2DBC 共存）
- [ ] 创建新的 jpa 包结构
- [ ] 迁移 ConfigEntity → JpaConfigEntity
- [ ] 创建 JpaConfigRepository
- [ ] 创建 JpaStoreManager（实现 StoreManager 接口）
- [ ] 配置存储类型切换开关
- [ ] 测试验证（R2DBC 和 JPA 双轨运行）

**验证标准**：
```bash
# 测试 JPA 存储模式
./test_version_api.sh --storage=jpa

# 测试 R2DBC 存储模式（确保向后兼容）
./test_version_api.sh --storage=r2dbc
```

### v1.5.2 - 版本管理服务迁移
**目标**：迁移版本管理核心逻辑

**任务清单**：
- [ ] 创建 JpaVersionManagementService
- [ ] 迁移版本号生成策略（复用现有策略模式）
- [ ] 迁移版本对比功能（VersionDiffService）
- [ ] 添加版本数据迁移工具（R2DBC → JPA）
- [ ] 集成测试

**验证标准**：
```bash
# 测试版本生成
./test_version_api.sh --test-version-generation

# 测试版本对比
./test_version_api.sh --test-version-diff
```

### v1.5.3 - ConfigurationService 迁移
**目标**：核心业务逻辑迁移

**任务清单**：
- [ ] 创建 JpaConfigurationService
- [ ] 迁移配置 CRUD 操作
- [ ] 迁移配置合并逻辑
- [ ] 迁移配置验证逻辑
- [ ] 保持 ConfigurationService 接口不变（内部实现切换）
- [ ] 压力测试

**验证标准**：
```bash
# 完整功能测试
./test_version_api.sh --full-test

# 性能对比测试
./test_performance.sh --compare
```

### v1.5.4 - 其他 Repository 迁移
**目标**：迁移辅助存储模块

**任务清单**：
- [ ] 迁移 ApiKeyRepository
- [ ] 迁移 JwtAccountRepository
- [ ] 迁移 SecurityAuditRepository
- [ ] 迁移 ServiceConfigRepository
- [ ] 统一配置存储路径

**验证标准**：
```bash
# 安全功能测试
./test_security.sh

# 服务配置测试
./test_service_config.sh
```

### v1.5.5 - R2DBC 全面移除
**目标**：清理遗留代码

**任务清单**：
- [ ] 移除 R2DBC 依赖
- [ ] 删除 ReactiveH2StoreManager
- [ ] 删除 R2DBC Repository 接口
- [ ] 删除 R2DBC 配置
- [ ] 更新文档
- [ ] 最终集成测试

**验证标准**：
```bash
# 全量回归测试
./test_all.sh

# 部署验证
./deploy_test.sh
```

---

## 技术细节

### 存储类型切换配置
```yaml
# application.yml
jairouter:
  storage:
    type: jpa  # r2dbc | jpa | file | memory
    jpa:
      ddl-auto: update
      show-sql: true
```

### 双轨运行架构
```
┌─────────────────────────────────────┐
│     ConfigurationService            │
│  (保持不变，业务逻辑层)              │
└──────────────┬──────────────────────┘
               │
      ┌────────┴────────┐
      │                 │
┌─────▼─────┐     ┌─────▼─────┐
│R2DBC Store│     │ JPA Store │
│  Manager  │     │  Manager  │
└───────────┘     └───────────┘
```

### 数据迁移策略
1. **自动迁移**：启动时自动将 R2DBC 数据导入 JPA
2. **增量同步**：双轨运行期间同时写入两种存储
3. **最终切换**：验证无误后关闭 R2DBC

---

## 风险评估与应对

| 风险 | 可能性 | 应对措施 |
|------|--------|----------|
| 数据迁移失败 | 中 | 保留原始数据备份，支持回滚 |
| 性能下降 | 低 | 压力测试，必要时优化索引 |
| API 不兼容 | 低 | 接口层保持不变，只改实现 |
| 依赖冲突 | 中 | 分阶段移除 R2DBC 依赖 |

---

## 回滚方案

每个版本都有回滚脚本：

```bash
# 回滚到 v1.5.0
./scripts/rollback.sh --to=1.5.0

# 回滚数据
./scripts/restore-data.sh --backup=pre-migration
```

---

## 当前状态

- **当前版本**: v1.2.5
- **目标版本**: v1.5.5
- **当前阶段**: v1.5.1 - JPA 基础设施准备
- **预计完成**: 5 个迭代周期

---

## 下一步行动

1. 确认 v1.5.1 任务清单
2. 创建 JPA 分支
3. 开始 v1.5.1 开发
