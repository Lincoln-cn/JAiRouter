# 更新日志

<!-- 版本信息 -->
> **文档版本**: 1.7.0
> **最后更新**: 2026-04-10
> **Git 提交**: 2cba097
> **作者**: Lincoln
<!-- /版本信息 -->



本文档记录了 JAiRouter 项目的版本更新历史和重要变更。

## 版本规范

JAiRouter 遵循 [语义化版本](https://semver.org/lang/zh-CN/) 规范：

- **主版本号 (MAJOR)**: 不兼容的 API 变更
- **次版本号 (MINOR)**: 向后兼容的功能新增
- **修订号 (PATCH)**: 向后兼容的问题修正

## 版本历史

### [1.7.0] - 2026-04-10

#### 新增功能
- **安全黑名单管理**：新增安全黑名单管理功能，支持 IP/用户/令牌黑名单
- **审计日志增强**：增强审计日志查询和展示功能，支持高级搜索和统计分析
- **JWT账户状态切换**：实现账户启用/禁用状态切换功能

#### 改进优化
- **JWT账户管理**：修复编辑密码验证问题，优化账户管理界面
- **前端表格优化**：表格列使用自适应宽度（min-width），添加统计卡片和搜索功能
- **配置管理简化**：移除未实现的版本管理功能，简化界面

#### 问题修复
- 修复 JWT 账户编辑时的密码验证问题
- 修复账户管理页面数据展示问题

#### 技术改进
- `CreateJwtAccountRequest` 添加 `enabled` 字段
- `JwtAccountService` 实现 `toggleAccountStatus` 方法
- 新增 `SecurityBlacklistController` 和相关实体类
- 清理未使用的前端代码和类型定义

---

### [1.6.2] - 2026-04-08

#### 新增功能
- **API Key 批量导入/导出**：支持批量导入导出 API Key
  - 新增导出端点 `GET /api/auth/api-keys/export`
  - 新增导入端点 `POST /api/auth/api-keys/import`
  - 支持 MERGE/REPLACE 两种导入模式
- **API Key 密钥轮换**：支持密钥自动轮换机制
  - 配置 `rotationPeriodDays` 设置轮换周期
  - 新增 `ApiKeyRotationScheduler` 自动执行轮换
- **过期密钥自动清理**：新增 `ApiKeyExpirationScheduler` 自动禁用过期密钥

#### 改进优化
- **创建者信息记录**：API Key 创建时记录 `createdBy` 和 `creatorIpAddress`
- **密钥使用统计持久化**：使用统计数据通过 `saveApiKeysToStore()` 持久化

#### 新增文件
- `ApiKeyBatchExportVO.java`
- `ApiKeyBatchImportRequest.java`
- `ApiKeyBatchImportResult.java`
- `ApiKeyRotationScheduler.java`
- `ApiKeyExpirationScheduler.java`

---

### [1.6.1] - 2026-04-06

#### 安全修复 (P0)
- **API Key 哈希存储**：API Key 使用 SHA-256 + 盐值哈希存储，替代明文存储
- **管理接口速率限制**：添加速率限制 (30/min, 100/hour, 10 create/hour)

#### 新增功能
- **IP 白名单**：支持 IP 白名单功能 (`allowedIpAddresses`)
- **每日请求限制**：支持每日请求限制功能 (`dailyRequestLimit`)
- **密钥重置接口**：新增密钥重置接口 `/api/auth/api-keys/{keyId}/reset`

#### 改进优化
- **前端强类型**：使用强类型 DTO/VO 替代 Map 数据传递
- **表格布局优化**：优化表格布局和横向滚动支持

#### 新增文件
- `ApiKeyHashUtil.java` - SHA-256 哈希工具类
- `AdminApiRateLimiter.java` - 管理接口速率限制过滤器
- `ApiKeyVO/ApiKeyCreationVO/ApiKeyListVO/ApiKeyCreateRequest/ApiKeyUpdateRequest` - 强类型 DTO

---

### [1.6.0] - 2026-04-04

#### 破坏性变更
- **移除配置合并功能**：移除 AutoMergeService 和 AutoMergeController
- **移除相关实体类**：移除 MergeResult 等 5 个相关实体类
- **移除前端页面**：移除 ConfigMergeManagement.vue 页面和相关 API

#### 改进优化
- **配置版本管理优化**：简化版本管理界面，保留核心版本切换功能
- **日志配置优化**：优化 logback-spring.xml 配置
- **文档更新**：移除配置合并相关内容

#### 保留功能
- `ConfigMergeService`：配置获取和合并核心功能
- `SecurityConfigMergeService`：安全配置合并服务

---

### [1.5.7] - 2026-04-02

#### 新增功能
- **JWT 账户初始化**：JWT 账户从 YAML 配置自动初始化到数据库
- **账户管理 API 优化**：使用标准 RouterResponse 响应格式

#### 问题修复
- 修复系统启动时 JWT 账户未初始化到数据库的问题
- 修复账户管理页面无法显示数据的问题
- 修复 API 路径与前端不匹配的问题（`/api/admin/accounts` -> `/api/security/jwt/accounts`）

#### 新增文件
- `JwtAccountProperties.java` - 映射 YAML 账户配置
- `JwtConfig.accounts` 字段 - 支持账户列表配置

---

### [1.5.6] - 2026-03-30

#### 新增功能
- **实例级别限流器独立存储**：新增 `instance_rate_limit` 表存储实例限流器配置
- **实例级别熔断器独立存储**：新增 `instance_circuit_breaker` 表存储实例熔断器配置
- **独立配置 API**：新增独立的限流器/熔断器配置 API 接口
- **强类型 DTO**：使用强类型 DTO 替代 Map 数据传递

#### API 变更
- `GET/PUT /api/config/instance/{type}/{id}/rate-limit`
- `GET/PUT /api/config/instance/{type}/{id}/circuit-breaker`

#### 新增文件
- `InstanceRateLimitEntity/InstanceCircuitBreakerEntity` - 实体类
- `InstanceRateLimitRepository/InstanceCircuitBreakerRepository` - 仓库
- `InstanceRateLimitDTO/InstanceCircuitBreakerDTO` - DTO 类

#### 改进优化
- `build-and-deploy.sh` 脚本自动清理旧编译文件

---

### [1.5.2] - 2026-03-20

#### 新增功能
- **JPA 迁移完成**：完成 R2DBC 到 JPA 的激进迁移
- **DTO 结构优化**：所有核心功能恢复并优化为 DTO 结构

#### 问题修复
- 修复 JPA 迁移过程中的编译错误
- 修复服务配置功能恢复

---

### [1.4.6] - 2026-03-10

#### 问题修复
- 修复前端独立配置功能
- 修复前端独立限流器和熔断器配置功能
- 修复数据返回完整性问题
- 修复 `buildInstanceMap` 和 `convertToVO` 方法

---

### [未发布] - 开发中

#### 新增功能
- **安全模块**：完整的企业级安全功能，包括API Key认证、JWT令牌支持和双向数据脱敏
- **多租户支持**：租户隔离、资源配额和基于租户的配置管理
- **认证鉴权**：API Key认证机制、JWT Token支持、OAuth 2.0集成和基于角色的访问控制(RBAC)
- **数据保护**：请求/响应数据脱敏、敏感信息加密存储和安全审计日志
- **H2数据库支持**：H2嵌入式数据库作为默认存储，支持配置数据、安全审计、API密钥和JWT账户的自动迁移
- **监控集成**：完整的Prometheus + Grafana监控栈，支持业务指标和基础设施指标收集
- **分布式追踪**：端到端分布式追踪，集成Jaeger/Zipkin实现完整的请求生命周期追踪
- 完善的文档体系和用户指南
- 多语言文档支持（中文/英文）
- 增强的监控和告警功能
- 更多的适配器支持

#### 改进优化
- **Spring Security集成**：与Spring Security框架完全集成，实现强大的认证和授权功能
- **缓存层优化**：Redis缓存优化以提升性能
- **数据库支持**：新增对PostgreSQL和MySQL数据库的支持
- **H2存储性能**：相比文件存储，配置和安全数据操作性能提升5-20倍
- **Docker构建优化**：为中国用户优化的Docker构建，使用阿里云Maven镜像，依赖下载速度提升5-10倍
- 性能优化和内存管理改进
- 更好的错误处理和日志记录
- 配置验证和用户体验提升

#### 问题修复
- 修复已知的内存泄漏问题
- 解决并发场景下的竞态条件
- 改进配置热更新的稳定性
- 安全扫描和漏洞修复
- 修复容器环境下的配置文件路径问题
- 解决Docker容器中的DNS解析问题

---

### [0.3.2] - 2025-08-20

#### 新增功能
- **监控集成**: 完整的 Prometheus + Grafana 监控栈
- **性能指标**: 新增业务指标和基础设施指标收集
- **告警规则**: 预配置的告警规则和通知机制
- **监控仪表板**: 专业的 Grafana 仪表板模板

#### 改进优化
- **指标优化**: 优化指标收集性能，减少对主业务的影响
- **内存管理**: 改进指标数据的内存使用和清理机制
- **配置简化**: 简化监控相关的配置参数

#### 问题修复
- 修复监控指标在高并发下的数据不准确问题
- 解决长时间运行后的内存泄漏问题
- 修复 Prometheus 指标格式兼容性问题

#### 技术债务
- 重构监控模块的代码结构
- 改进单元测试覆盖率
- 优化构建和部署流程

---

### [0.3.1] - 2025-08-15

#### 新增功能
- **中国加速构建**: 专门为中国用户优化的 Docker 构建
- **阿里云镜像**: 使用阿里云 Maven 镜像加速依赖下载
- **构建脚本**: 新增 `docker-build-china.sh` 构建脚本
- **Maven Profile**: 新增 `china` profile 支持

#### 改进优化
- **构建速度**: 中国用户构建速度提升 5-10 倍
- **网络优化**: 优化网络连接和超时配置
- **文档完善**: 添加中国用户专用的构建指南

#### 问题修复
- 修复在中国网络环境下的依赖下载超时问题
- 解决 Maven 仓库连接不稳定的问题
- 修复 Docker 构建过程中的网络错误

---

### [0.3.0] - 2025-08-14

#### 新增功能
- **Docker 容器化**: 完整的 Docker 部署支持
- **多环境部署**: 支持开发、测试、生产环境配置
- **Docker Compose**: 提供完整的容器编排配置
- **健康检查**: 容器级别的健康检查机制
- **监控集成**: 基础的监控指标暴露

#### 改进优化
- **镜像优化**: 多阶段构建，生产镜像约 200MB
- **安全加固**: 非 root 用户运行，最小权限原则
- **性能调优**: 容器环境下的 JVM 参数优化
- **日志管理**: 容器化环境的日志收集和轮转

#### 问题修复
- 修复容器环境下的配置文件路径问题
- 解决容器重启后配置丢失的问题
- 修复网络连接在容器间的通信问题

#### 破坏性变更
- 默认配置文件路径从 `./config` 改为 `/app/config`
- 环境变量命名规范调整

---

### [0.2.1] - 2025-08--12

#### 新增功能
- **定时清理任务**: 自动清理不活跃的限流器，防止内存泄漏
- **内存优化**: 改进内存使用模式，减少 GC 压力
- **客户端 IP 限流增强**: 更精确的客户端 IP 识别和限流
- **配置文件自动合并**: 支持多版本配置文件的智能合并

#### 改进优化
- **性能提升**: 限流器性能优化，减少锁竞争
- **监控增强**: 新增内存使用和清理任务的监控指标
- **日志优化**: 改进日志格式和性能
- **错误处理**: 更好的异常处理和错误恢复机制

#### 问题修复
- 修复长时间运行后的内存泄漏问题
- 解决高并发场景下的限流器竞态条件
- 修复配置热更新时的线程安全问题
- 解决客户端 IP 获取在代理环境下的问题

#### 技术改进
- 重构限流器清理机制
- 改进单元测试覆盖率到 85%
- 优化代码质量检查规则

---

### [0.2.0] - 2025-08-11

#### 新增功能
- **限流机制**: 支持 Token Bucket、Leaky Bucket、Sliding Window、Warm Up 四种限流算法
- **熔断器**: 实现熔断器模式，支持失败阈值、恢复检测、降级策略
- **降级策略**: 支持默认响应和缓存降级
- **配置持久化**: 支持内存存储和文件存储两种后端
- **动态配置更新**: 运行时更新服务实例、权重、限流、熔断等配置

#### 改进优化
- **性能优化**: 响应式编程模型，支持高并发处理
- **配置管理**: 配置文件自动合并和版本管理
- **错误处理**: 完善的异常处理和错误恢复机制
- **监控指标**: 新增限流、熔断相关的监控指标

#### 问题修复
- 修复负载均衡器在实例变更时的线程安全问题
- 解决配置更新时的数据一致性问题
- 修复高并发场景下的内存泄漏问题

#### API 变更
- 新增动态配置管理 API (`/api/config/instance/*`)
- 新增配置版本管理 API (`/api/config/versions/*`)
- 扩展健康检查 API，包含更多状态信息

---

### [0.1.0] - 2025-08-04

#### 新增功能
- **基础网关**: 统一 `/v1/*` API 网关，支持 OpenAI 兼容格式
- **服务类型支持**: Chat、Embedding、Rerank、TTS、STT、Image Generation、Image Editing
- **适配器模式**: 支持 GPUStack、Ollama、VLLM、Xinference、LocalAI、OpenAI 适配器
- **负载均衡**: 实现 Random、Round Robin、Least Connections、IP Hash 四种策略
- **健康检查**: 每服务独立状态接口，自动剔除不可用实例
- **配置管理**: 基于 YAML 的静态配置支持

#### 技术特性
- **Spring Boot 3.5.x**: 基于最新的 Spring Boot 框架
- **响应式编程**: 使用 Spring WebFlux 和 Reactor Core
- **代码质量**: 集成 Checkstyle、SpotBugs、JaCoCo 代码质量工具
- **API 文档**: 使用 SpringDoc OpenAPI 自动生成 API 文档
- **单元测试**: 包含负载均衡、健康检查等核心功能的单元测试

#### 项目结构
- 建立清晰的模块化架构
- 定义统一的编码规范和最佳实践
- 建立完整的构建和测试流程

---

## 升级指南

### 从 0.3.1 升级到 0.3.2

#### 配置变更
```yaml
# 新增监控配置
monitoring:
  metrics:
    enabled: true
    categories:
      - system
      - business
      - infrastructure
```

#### 部署变更
- 新增 Prometheus 和 Grafana 容器
- 更新 `docker-compose.yml` 配置
- 导入新的 Grafana 仪表板

#### 注意事项
- 监控功能默认启用，可能会增加少量性能开销
- 新增的监控端点需要相应的网络配置

### 从 0.2.1 升级到 0.3.0

#### 破坏性变更
- 配置文件路径变更：`./config` → `/app/config`
- 环境变量命名调整

#### 迁移步骤
1. 更新配置文件路径
2. 调整环境变量名称
3. 更新部署脚本和容器配置

### 从 0.1.0 升级到 0.2.0

#### 新增依赖
- 无需额外依赖，所有功能已内置

#### 配置扩展
```yaml
# 新增限流配置
model:
  services:
    chat:
      rate-limit:
        type: token-bucket
        capacity: 100
        refill-rate: 10

# 新增熔断配置
      circuit-breaker:
        failure-threshold: 5
        recovery-timeout: 30s
        success-threshold: 3
```

## 已知问题

### 当前版本 (0.3.2)
- 在极高并发场景下（>10k RPS），监控指标可能出现轻微延迟
- Docker 容器在某些网络环境下可能出现 DNS 解析缓慢

### 历史问题
- ~~0.3.1: 中国网络环境下构建超时~~ (已修复)
- ~~0.2.1: 长时间运行后内存泄漏~~ (已修复)
- ~~0.2.0: 高并发下配置更新竞态条件~~ (已修复)

## 贡献者

感谢所有为 JAiRouter 项目做出贡献的开发者：

- **核心团队**: 负责架构设计和核心功能开发
- **社区贡献者**: 提供功能建议、问题报告和代码贡献
- **文档团队**: 完善项目文档和用户指南
- **测试团队**: 进行功能测试和性能验证

## 反馈和建议

如果您在使用过程中遇到问题或有改进建议，欢迎通过以下方式反馈：

- **GitHub Issues**: [提交问题报告](https://github.com/Lincoln-cn/JAiRouter/issues)
- **GitHub Discussions**: [参与讨论](https://github.com/Lincoln-cn/JAiRouter/discussions)
- **邮件联系**: jairouter@example.com

我们重视每一个反馈，并会及时响应和处理。