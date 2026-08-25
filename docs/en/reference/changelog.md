# Changelog

<!-- 版本信息 -->
> **Document Version**: 2.8.7
> **Last Updated**: 2026-08-25
> **Git Commit**: 4d3e084d
> **Author**: Lincoln
<!-- /版本信息 -->



This document records the version update history and important changes of the JAiRouter project.

## Versioning Scheme

JAiRouter follows the [Semantic Versioning](https://semver.org/) specification:

- **Major Version**: Incompatible API changes
- **Minor Version**: Backward-compatible new features
- **Patch Version**: Backward-compatible bug fixes

## Version History

### [2.8.7] - 2026-08-25 - Feature Release

#### Rule Engine Trio

- **Rule hit statistics**: hits are counted at the decision-application point (no double counting; dry-run excluded); new Prometheus metric `jairouter_rule_hits_total` (tags: ruleId/actionType) + `GET /api/config/rules/stats` aggregation endpoint; the rule list page adds a "hits" column with refresh
- **Priority drag-and-drop**: rules can be reordered by drag (sortablejs), committing priorities in row order; `PUT /priority` now skips unknown ids (YAML rules no longer 404) and returns `{updated, skipped}`; rules expose a `source` field (YAML/PERSISTED), YAML rules show a badge and cannot be dragged
- **Scenario templates**: 6 built-in rule templates (canary release / tenant isolation / model rewrite / weight split / adapter switch / VIP instance pin); `GET /api/config/rules/templates` + `POST /{id}/create`; a "Create from template" wizard pre-fills the rule form as a draft before saving

#### Tests

- RuleConfigControllerTest extended (RULEC-026~028 for stats); new RuleStatsServiceTest, RuleTemplateServiceTest, RuleTemplateControllerTest; all rule-related tests pass; Checkstyle 0 violations

---

### [2.8.6] - 2026-08-25 - Enhancement Release

#### Rule Engine Usability

- **Action/condition value dropdowns**: action targets are selected from type-aware dropdowns (model name / instance / adapter, searchable with custom input; LB strategy is a fixed 5-value select); condition values support model-name / service-type / common-header dropdowns with autocomplete; options refresh when the service type changes
- **Rule simulation test (dry-run)**: `POST /api/config/rules/validate` verifies rule matching with a sample request (modelName/IP/headers), read-only; the Web form includes a built-in 「Simulate Test」 panel
- **Fix**: case-insensitive `serviceType` matching (`imgGen`/`imgEdit` camelCase enum values now resolve correctly)

#### Tests

- RuleConfigControllerTest extended (incl. RULEC-025); all rule-engine tests pass; Checkstyle 0 violations

---

### [2.8.5] - 2026-08-23 - Feature Release

#### Rule Engine (Visual Conditional Routing)

Implement visual routing rule configuration so users can configure condition-based smart routing from the Web console **without writing code**:

- **Conditional routing rules**: 5 match condition types (model name / service type / request header / client IP / weight) and 5 operators (equals / contains / starts-with / regex / CIDR match); matched requests execute a preset action
- **Action types**: TARGET_MODEL (model rewrite), TARGET_INSTANCE (pin instance), TARGET_ADAPTER (switch adapter), LB_STRATEGY (load-balancing strategy override)
- **Rule management API**: `/api/config/rules` full CRUD + enable/disable + priority batch update
- **Rule persistence**: StoreManager-backed persistence, auto-recovery on restart, YAML-first merge
- **Frontend visual configuration**: rule management page + rule form dialog (condition editing / action config)
- **Docs overhaul**: new routing-rule configuration doc, docs structure reorganization

#### Tests

- All rule-engine related test cases pass; Checkstyle 0 violations

---

### [2.8.4] - 2026-08-15 - Feature Release

#### Plugin System (Custom Adapter Framework)

Implement the "plugin system" so users can create, test, and manage custom adapters from the Web console **without writing code**:

- **Adapter Template System**: 13 built-in templates (DeepSeek/Zhipu GLM/Moonshot Kimi/Baichuan/Qwen/MiniMax/01.AI Yi/StepFun/SiliconFlow/Groq/OpenRouter/Together AI/Local Ollama)
- **Adapter Test API**: PING connectivity test + CHAT conversation test (independent 10s timeout, API Key never persisted)
- **Enhanced Frontend Wizard**: 4-step configuration wizard (template selection/basic config/advanced config/test connection) + template cards + test panel
- **Adapter Definition Persistence**: StoreManager-backed persistence, auto-recovery on restart, YAML-first merge strategy

#### New Components

- `AdapterTemplateService`: template service (13 built-in templates)
- `AdapterTestService`: adapter connectivity test service
- `AdapterDefinitionPersistenceService`: adapter definition persistence service
- `AdapterTemplateController` / `AdapterTestController`: template & test REST APIs

#### Tests

- 49 new test cases (TDD-first), all passing; Checkstyle 0 violations

---

### [2.8.3] - 2026-07-15 - Feature Release

#### Gemini Adapter + Adapter Config Enhancements

- **Gemini Adapter**: added Google Gemini adapter support
- **adapter-config phases 1-3**: OpenAI-compatible adapter config, Ollama-compatible adapter config, inheritance + override mode
- **Test coverage improvements**: ErrorResponseBuilder (15 cases), StreamingRequestProcessor (14 cases), NonStreamingRequestProcessor (11 cases), Health module (0% → 68-71%)

---

### [2.8.2] - 2026-07-17 - Code Quality

- Checkstyle cleanup: fixed 11 warnings

---

### [2.8.1] - 2026-07-16 - Code Quality

- Checkstyle cleanup: fixed 66 warnings

---

### [2.8.0] - 2026-07-15 - Feature Release

#### Anthropic Claude Adapter

Added Anthropic Claude adapter support, enabling JAiRouter to serve as a unified gateway for Claude API workloads alongside existing OpenAI-compatible backends.

- **ClaudeAdapter**: Claude adapter implementation (+295 lines)
- **ClaudeAdapterTest**: adapter tests (+404 lines, covering request transformation, response parsing, error handling)
- **Documentation cleanup & config examples**: added Claude adapter configuration examples

---

### [2.7.6] - 2026-06-30 - Feature Release

#### API Key Quota Management

Added comprehensive quota management features for API Keys:

- **Daily Token Limit**: Track and limit daily Token usage per API Key
- **Rate Limiting**: Sliding window algorithm for per-minute request limiting
- **Alert Thresholds**: Configurable usage percentage thresholds for alerts
- **Automatic Reset**: Quota counters reset daily at midnight

#### Bug Fixes

- **API Key Serialization**: Fixed `@JsonProperty(access = WRITE_ONLY)` preventing `keyHash` from being serialized to database
- **Token Usage Tracking**: Improved Token usage tracking for streaming and non-streaming requests

#### New Components

- `ApiKeyQuotaService`: Quota checking and alert service
- `TokenBucketRateLimiter`: Sliding window rate limiter
- `ApiKeyQuotaCleanupScheduler`: Scheduled cleanup of expired quota data

#### Database Changes

Added quota-related fields to `api_keys` table:
- `daily_token_limit`: Daily Token usage limit
- `rate_limit_per_minute`: Requests per minute limit
- `quota_alert_threshold`: Alert threshold percentage
- `today_token_usage`: Current day Token usage
- `today_request_count`: Current day request count
- `last_reset_time`: Last quota reset time

---

### [2.7.7] - 2026-07-10 - Feature Release

#### ExceptionEvent Collection Fixes

Fixed multiple issues in the exception event collection pipeline:

- **Bean conflict fix**: Resolved `ExceptionEventService` bean initialization conflict
- **Error degradation optimization**: Exception event collection failure no longer affects the main flow
- **Persistence chain fix**: Ensured exception events are correctly written to the database

#### API Key Service-Level Permission Checks

- **Service-level permissions**: API Keys now support fine-grained permission control by service type (Chat/Embedding/Rerank, etc.)
- **Frontend permission fixes**: Fixed permission management page display and interaction issues

---

### [2.7.8] - 2026-07-10 - Feature Release

#### Request Call History Persistence

Added a complete API call history recording feature:

- **Call record storage**: Records detailed information for each API call (model, instance, duration, status, etc.)
- **Query and statistics**: Supports querying and statistics by time range, model, service type, and other dimensions
- **Frontend dashboard**: New call history visualization page with trend charts and detail tables
- **Exception monitoring enhancement**: Exception management page adds business fields and frontend filtering

#### New Components

- `ApiCallHistoryService`: Call history recording service
- `ApiCallHistoryController`: Call history REST API
- `CallHistoryDashboard`: Frontend call history dashboard

---

### [2.5.15] - 2026-05-11 - Stable Release

#### Large Class Refactoring Complete

This release completed refactoring of 4 oversized classes, reducing 2011 lines of code (-62%).

| File | Original Lines | Final Lines | Reduction | Target |
|------|----------------|-------------|-----------|--------|
| BaseAdapter | 1386 | 416 | -70% | 600 ✅ |
| TracingService | 764 | 483 | -37% | 400 ✅ |
| DefaultStructuredLogger | 945 | 365 | -61% | 400 ✅ |
| ConfigVersionManager | 746 | 387 | -48% | 400 ✅ |

#### New Components (12)

- ConfigComparator, SpanAttributeHelper, ServiceNameResolver
- RequestLogBuilder, ResponseLogBuilder, BackendCallLogBuilder
- ErrorLogBuilder, SystemEventLogBuilder, VersionValidator
- VersionMetadataManager, VersionSyncService, ModelUtils

#### Quality Checks

- Checkstyle: ✅ Passed
- SpotBugs: ✅ Passed
- Tests: 971 passed ✅

---

### [1.7.3] - 2026-04-14

#### Bug Fixes
- **Playground Chat Streaming Response Fix**: Fixed duplicate AI response messages during streaming
  - Issue: AI responses appeared twice during streaming
  - Cause: Incorrect `displayMessages` filtering logic in `MessageList.vue`
  - Fix: Always filter the last assistant message during streaming, displayed by additional `MessageBubble` component

#### Improvements
- **Port Configuration Restoration**: Server port restored to default `8080`, consistent with documentation

#### Technical Improvements
- Updated frontend static resource build artifacts

---

### [1.7.2] - 2026-04-14

#### New Features
- **Playground Component Refactoring**: Major refactoring of Playground module with component-based architecture
  - Chat Module: `ChatContainer`, `ChatConfigPanel`, `MessageInput`, `MessageList`
  - Audio Module: `AudioContainer`, `TtsPanel`, `SttPanel`
  - Image Module: `ImageContainer`, `ImageGeneratePanel`, `ImageEditPanel`
  - Embedding Module: `EmbeddingContainer`
  - Rerank Module: `RerankContainer`
  - Common Components: `MessageBubble`, `MarkdownRenderer`, `CodeBlock`, `ModelSelector`, `ServiceLayout`, `LoadingIndicator`
- **New Composables**:
  - `useChatSession`: Chat session management (localStorage persistence)
  - `useMarkdown`: Markdown rendering handling
  - `useStreaming`: SSE streaming response processing

#### Improvements & Optimizations
- **Health Check SSE Controller Optimization**: Optimized `HealthStatusSseController` implementation
- **Instance Management Extension**: Added field support, extended `ServiceInstanceDTO` and entity classes
- **Adapter Base Class Adjustment**: Unified adapter base class handling logic
- **Frontend Routing and Layout Optimization**: Optimized routing configuration and Layout component

#### New Files
- `frontend/src/views/playground/components/` - 18 component files
- `frontend/src/views/playground/composables/` - 4 composable files
- `src/main/resources/db/migration/V3__add_adapter_headers_fields.sql`

---

### [1.7.1] - 2026-04-13

#### Bug Fixes
- **Tracing Fixes**:
  - Fixed `TracingWebFilter` duplicate `traceId` creation issue
  - Fixed `TraceQueryService` `spanCount` display error in `recentTraces` merge
  - Fixed `TracingService` `serviceName` classification, frontend route correctly identified as 'front'
- **Frontend Routing Fix**: Fixed frontend routing path error (`/admin/admin/tracing` -> `/admin/tracing`)

#### Improvements & Optimizations
- **ControllerTracingInterceptor Optimization**: Optimized child Span synchronous recording logic
- **Table Layout Optimization**: Table column width using `min-width` for adaptive filling

#### New Features
- **TraceDetail Component**: Added trace detail display component
- **Tracing Dashboard Page**: Added tracing dashboard page

#### New Files
- `frontend/src/views/tracing/Dashboard.vue`
- `frontend/src/views/tracing/components/TraceDetail.vue`
- `docs/zh/development/tracing-full-chain-design.md`

---

### [1.7.0] - 2026-04-10

#### New Features
- **Security Blacklist Management**: New security blacklist management feature, supporting IP/user/token blacklists
- **Enhanced Audit Logs**: Enhanced audit log query and display functionality with advanced search and statistics
- **JWT Account Status Toggle**: Implement account enable/disable status toggle functionality

#### Improvements & Optimizations
- **JWT Account Management**: Fixed password validation issue during editing, optimized account management interface
- **Frontend Table Optimization**: Table columns use adaptive width (min-width), added statistics cards and search functionality
- **Configuration Management Simplification**: Removed unimplemented version management features, simplified interface

#### Bug Fixes
- Fixed password validation issue when editing JWT accounts
- Fixed data display issue on account management page

#### Technical Improvements
- Added `enabled` field to `CreateJwtAccountRequest`
- Implemented `toggleAccountStatus` method in `JwtAccountService`
- Added `SecurityBlacklistController` and related entity classes
- Cleaned up unused frontend code and type definitions

---

### [1.6.2] - 2026-04-08

#### New Features
- **API Key Batch Import/Export**: Support batch import/export of API Keys
  - Added export endpoint `GET /api/auth/api-keys/export`
  - Added import endpoint `POST /api/auth/api-keys/import`
  - Support MERGE/REPLACE import modes
- **API Key Rotation**: Support automatic key rotation mechanism
  - Configure `rotationPeriodDays` to set rotation period
  - Added `ApiKeyRotationScheduler` for automatic rotation execution
- **Expired Key Auto Cleanup**: Added `ApiKeyExpirationScheduler` to automatically disable expired keys

#### Improvements & Optimizations
- **Creator Information Recording**: Record `createdBy` and `creatorIpAddress` when creating API Keys
- **Key Usage Statistics Persistence**: Usage statistics persisted via `saveApiKeysToStore()`

#### New Files
- `ApiKeyBatchExportVO.java`
- `ApiKeyBatchImportRequest.java`
- `ApiKeyBatchImportResult.java`
- `ApiKeyRotationScheduler.java`
- `ApiKeyExpirationScheduler.java`

---

### [1.6.1] - 2026-04-06

#### Security Fixes (P0)
- **API Key Hashed Storage**: API Keys stored using SHA-256 + salt hashing, replacing plaintext storage
- **Admin API Rate Limiting**: Added rate limiting (30/min, 100/hour, 10 create/hour)

#### New Features
- **IP Whitelist**: Support IP whitelist functionality (`allowedIpAddresses`)
- **Daily Request Limit**: Support daily request limit functionality (`dailyRequestLimit`)
- **Key Reset Interface**: Added key reset interface `/api/auth/api-keys/{keyId}/reset`

#### Improvements & Optimizations
- **Frontend Strong Typing**: Use strongly typed DTO/VO instead of Map data passing
- **Table Layout Optimization**: Optimized table layout and horizontal scroll support

#### New Files
- `ApiKeyHashUtil.java` - SHA-256 hash utility class
- `AdminApiRateLimiter.java` - Admin API rate limiting filter
- `ApiKeyVO/ApiKeyCreationVO/ApiKeyListVO/ApiKeyCreateRequest/ApiKeyUpdateRequest` - Strongly typed DTOs

---

### [1.6.0] - 2026-04-04

#### Breaking Changes
- **Removed Configuration Merge Feature**: Removed AutoMergeService and AutoMergeController
- **Removed Related Entity Classes**: Removed MergeResult and 5 related entity classes
- **Removed Frontend Page**: Removed ConfigMergeManagement.vue page and related API

#### Improvements & Optimizations
- **Configuration Version Management Optimization**: Simplified version management interface, retained core version switching functionality
- **Log Configuration Optimization**: Optimized logback-spring.xml configuration
- **Documentation Update**: Removed configuration merge related content

#### Retained Features
- `ConfigMergeService`: Core configuration retrieval and merge functionality
- `SecurityConfigMergeService`: Security configuration merge service

---

### [1.5.7] - 2026-04-02

#### New Features
- **JWT Account Initialization**: JWT accounts auto-initialized from YAML configuration to database
- **Account Management API Optimization**: Use standard RouterResponse response format

#### Bug Fixes
- Fixed JWT accounts not being initialized to database on system startup
- Fixed account management page unable to display data
- Fixed API path mismatch with frontend (`/api/admin/accounts` -> `/api/security/jwt/accounts`)

#### New Files
- `JwtAccountProperties.java` - Maps YAML account configuration
- `JwtConfig.accounts` field - Supports account list configuration

---

### [1.5.6] - 2026-03-30

#### New Features
- **Instance-level Rate Limiter Independent Storage**: Added `instance_rate_limit` table for instance rate limiter configuration
- **Instance-level Circuit Breaker Independent Storage**: Added `instance_circuit_breaker` table for instance circuit breaker configuration
- **Independent Configuration API**: Added independent rate limiter/circuit breaker configuration API endpoints
- **Strongly Typed DTO**: Use strongly typed DTO instead of Map data passing

#### API Changes
- `GET/PUT /api/config/instance/{type}/{id}/rate-limit`
- `GET/PUT /api/config/instance/{type}/{id}/circuit-breaker`

#### New Files
- `InstanceRateLimitEntity/InstanceCircuitBreakerEntity` - Entity classes
- `InstanceRateLimitRepository/InstanceCircuitBreakerRepository` - Repositories
- `InstanceRateLimitDTO/InstanceCircuitBreakerDTO` - DTO classes

#### Improvements & Optimizations
- `build-and-deploy.sh` script automatically cleans old compiled files

---

### [1.5.2] - 2026-03-20

#### New Features
- **JPA Migration Complete**: Completed aggressive migration from R2DBC to JPA
- **DTO Structure Optimization**: All core functions restored and optimized to DTO structure

#### Bug Fixes
- Fixed compilation errors during JPA migration
- Fixed service configuration function restoration

---

### [1.4.6] - 2026-03-10

#### Bug Fixes
- Fixed frontend independent configuration functionality
- Fixed frontend independent rate limiter and circuit breaker configuration functionality
- Fixed data return completeness issue
- Fixed `buildInstanceMap` and `convertToVO` methods

---

### [1.4.4] - 2026-03-31

#### Bug Fixes
- Fixed frontend instance management page data format issue
- Optimized data display logic

---

### [1.4.3] - 2026-03-25

#### Bug Fixes
- Fixed service type validation and exception handling logic
- Improved error messages

---

### [1.4.2] - 2026-03-25

#### New Features
- **Adapter Refactoring Plan**: Created adapter refactoring plan documentation for future architecture optimization

---

### [1.4.1] - 2026-03-24

#### New Features
- **Value Object Pattern**: Introduced InstanceId value object for improved code type safety

---

### [1.4.0] - 2026-03-24

#### Bug Fixes
- Gracefully fixed ConfigMergeService blocking call warning
- Optimized reactive programming model

---

### [1.2.5] - 2025-11-26

#### Improvements
- Merged remote branch updates
- Code synchronization and stability improvements

---

### [1.1.2] - 2025-10-30

#### Improvements
- Merged remote branch updates
- Code stability improvements

---

### [1.1.1] - 2025-10-28

#### Improvements
- **Frontend Routing Optimization**: Improved routing and authentication flow
- Code refactoring and cleanup

---

### [1.1.0] - 2025-10-28

#### Improvements
- Merged remote branch updates
- Feature stability improvements

---

### [1.0.0] - 2025-10-16

#### New Features
- **First Official Release**: JAiRouter project first official release version
- Basic gateway functionality
- Core adapter support

---

### [0.9.2] - 2025-09-30

#### New Features
- **ApiKey Model Unification**: Merged ApiKeyInfo and ApiKeyProperties into unified ApiKey model

---

### [0.9.1] - 2025-09-12

#### Bug Fixes
- Fixed merge errors
- Code stability improvements

---

### [0.9.0] - 2025-09-10

#### New Features
- **Web Console Architecture**: Added Web Console architecture design documentation
- Frontend management console planning

---

### [0.8.2] - 2025-09-05

#### Improvements
- Merged remote branch updates
- Code synchronization

---

### [0.8.1] - 2025-09-03

#### Improvements
- Updated project version number
- Version management standardization

---

### [0.7.3] - 2025-08-27

#### New Features
- **JWT Authentication**: Implemented JWT authentication and user management functionality
- Security module basic functionality

---

### [0.7.2] - 2025-08-27

#### New Features
- **Distributed Tracing Documentation**: Added distributed tracing system documentation

---

### [0.7.1] - 2025-08-27

#### New Features
- **Documentation Optimization**: Integrated Google Ads and optimized documentation styling

---

### [0.7.0] - 2025-08-22

#### New Features
- **Slow Query Alerts**: Added slow query alert functionality
- Monitoring module enhancement

---

### [0.6.1] - 2025-08-19

#### New Features
- **Internationalization Support**: Added internationalization and code compression support
- Documentation system enhancement

---

### [0.6.0] - 2025-08-18

#### New Features
- **Security Authentication**: Implemented API Key and JWT authentication functionality
- Security module core functionality

---

### [0.5.0] - 2025-08-18

#### New Features
- **Documentation Management**: Refactored documentation management workflow with unified management script
- Documentation system refactoring

---

### [0.4.0] - 2025-08-15

#### New Features
- **Prometheus Alert Rules**: Added Prometheus alert rules guide and configuration
- Added ALERT_RULES_GUIDE.md file
- Added alertmanager.yml configuration
- Created docker-compose-monitoring.yml monitoring stack configuration

---

### [Unreleased] - In Development

#### New Features
- **Security Module**: Complete enterprise-grade security features including API Key authentication, JWT token support, and bidirectional data sanitization
- **Multi-tenancy Support**: Tenant isolation, resource quotas, and tenant-based configuration management
- **Authentication and Authorization**: API Key authentication mechanism, JWT Token support, OAuth 2.0 integration, and Role-Based Access Control (RBAC)
- **Data Protection**: Request/response data obfuscation, encrypted storage of sensitive information, and security audit logs
- **H2 Database Support**: H2 embedded database as default storage with automatic data migration for configuration, security audit, API keys, and JWT accounts
- **Monitoring Integration**: Complete Prometheus + Grafana monitoring stack with business and infrastructure metrics collection
- **Distributed Tracing**: End-to-end distributed tracing with Jaeger/Zipkin integration for full request lifecycle tracking
- Complete documentation system and user guides
- Multi-language documentation support (Chinese/English)
- Enhanced monitoring and alerting capabilities
- More adapter support

#### Improvements & Optimizations
- **Spring Security Integration**: Full integration with Spring Security framework for robust authentication and authorization
- **Cache Layer Optimization**: Redis cache optimization for improved performance
- **Database Support**: Added support for PostgreSQL and MySQL databases
- **H2 Storage Performance**: 5-20x performance improvement over file storage for configuration and security data operations
- **Docker Build Optimization**: China-optimized Docker builds with Alibaba Cloud Maven mirror, 5-10x faster dependency downloads
- Performance optimization and memory management improvements
- Better error handling and logging
- Configuration validation and user experience enhancements

#### Bug Fixes
- Fixed known memory leak issues
- Resolved race conditions in concurrent scenarios
- Improved stability of configuration hot updates
- Security scanning and vulnerability fixes
- Fixed configuration file path issues in container environments
- Resolved DNS resolution issues in Docker containers

---

### [0.3.2] - 2025-08-20

#### New Features
- **Monitoring Integration**: Complete Prometheus + Grafana monitoring stack
- **Performance Metrics**: Added collection of business and infrastructure metrics
- **Alert Rules**: Pre-configured alert rules and notification mechanisms
- **Monitoring Dashboard**: Professional Grafana dashboard templates

#### Improvements & Optimizations
- **Metrics Optimization**: Optimized metrics collection performance to reduce impact on main business
- **Memory Management**: Improved memory usage and cleanup mechanisms for metric data
- **Configuration Simplification**: Simplified monitoring-related configuration parameters

#### Bug Fixes
- Fixed inaccurate monitoring metrics under high concurrency
- Resolved memory leak issues after long-term operation
- Fixed Prometheus metrics format compatibility issues

#### Technical Debt
- Refactored monitoring module code structure
- Improved unit test coverage
- Optimized build and deployment processes

---

### [0.3.1] - 2025-08-15

#### New Features
- **China Accelerated Build**: Docker builds optimized specifically for Chinese users
- **Alibaba Cloud Mirror**: Using Alibaba Cloud Maven mirror to accelerate dependency downloads
- **Build Script**: Added `docker-build-china.sh` build script
- **Maven Profile**: Added china profile support

#### Improvements & Optimizations
- **Build Speed**: Build speed for Chinese users improved by 5-10 times
- **Network Optimization**: Optimized network connections and timeout configurations
- **Documentation Enhancement**: Added China-specific build guides

#### Bug Fixes
- Fixed dependency download timeout issues in Chinese network environments
- Resolved unstable Maven repository connection issues
- Fixed network errors during Docker builds

---

### [0.3.0] - 2025-08-14

#### New Features
- **Docker Containerization**: Complete Docker deployment support
- **Multi-environment Deployment**: Support for development, testing, and production environment configurations
- **Docker Compose**: Provided complete container orchestration configuration
- **Health Check**: Container-level health check mechanism
- **Monitoring Integration**: Basic monitoring metrics exposure

#### Improvements & Optimizations
- **Image Optimization**: Multi-stage builds, production image approximately 200MB
- **Security Hardening**: Running as non-root user, principle of least privilege
- **Performance Tuning**: JVM parameter optimization in container environments
- **Log Management**: Log collection and rotation in containerized environments

#### Bug Fixes
- Fixed configuration file path issues in container environments
- Resolved configuration loss after container restarts
- Fixed network communication issues between containers

#### Breaking Changes
- Default configuration file path changed from `./config` to `/app/config`
- Environment variable naming convention adjusted

---

### [0.2.1] - 2025-08-12

#### New Features
- **Scheduled Cleanup Task**: Automatic cleanup of inactive rate limiters to prevent memory leaks
- **Memory Optimization**: Improved memory usage patterns to reduce GC pressure
- **Enhanced Client IP Rate Limiting**: More precise client IP identification and rate limiting
- **Automatic Configuration File Merging**: Support for intelligent merging of multi-version configuration files

#### Improvements & Optimizations
- **Performance Improvement**: Rate limiter performance optimization to reduce lock contention
- **Enhanced Monitoring**: Added monitoring metrics for memory usage and cleanup tasks
- **Log Optimization**: Improved log format and performance
- **Error Handling**: Better exception handling and error recovery mechanisms

#### Bug Fixes
- Fixed memory leak issues after long-term operation
- Resolved rate limiter race conditions in high-concurrency scenarios
- Fixed thread safety issues during configuration hot updates
- Resolved client IP acquisition issues in proxy environments

#### Technical Improvements
- Refactored rate limiter cleanup mechanism
- Improved unit test coverage to 85%
- Optimized code quality check rules

---

### [0.2.0] - 2024-08-11

#### New Features
- **Rate Limiting Mechanism**: Support for Token Bucket, Leaky Bucket, Sliding Window, and Warm Up rate limiting algorithms
- **Circuit Breaker**: Implemented circuit breaker pattern with support for failure thresholds, recovery detection, and fallback strategies
- **Fallback Strategies**: Support for default responses and cache fallback
- **Configuration Persistence**: Support for both in-memory and file storage backends
- **Dynamic Configuration Updates**: Runtime updates for service instances, weights, rate limiting, and circuit breaking configurations

#### Improvements & Optimizations
- **Performance Optimization**: Reactive programming model supporting high-concurrency processing
- **Configuration Management**: Automatic configuration file merging and version management
- **Error Handling**: Comprehensive exception handling and error recovery mechanisms
- **Monitoring Metrics**: Added rate limiting and circuit breaking related monitoring metrics

#### Bug Fixes
- Fixed thread safety issues in load balancer during instance changes
- Resolved data consistency issues during configuration updates
- Fixed memory leak issues in high-concurrency scenarios

#### API Changes
- Added dynamic configuration management API (`/api/config/instance/*`)
- Added configuration file merging API (`/api/config/merge/*`)
- Extended health check API to include more status information

---

### [0.1.0] - 2025-08-04

#### New Features
- **Basic Gateway**: Unified `/v1/*` API gateway supporting OpenAI-compatible format
- **Service Type Support**: Chat, Embedding, Rerank, TTS, STT, Image Generation, Image Editing
- **Adapter Pattern**: Support for GPUStack, Ollama, VLLM, Xinference, LocalAI, and OpenAI adapters
- **Load Balancing**: Implemented Random, Round Robin, Least Connections, and IP Hash strategies
- **Health Check**: Independent status interface per service, automatic removal of unavailable instances
- **Configuration Management**: Static configuration support based on YAML

#### Technical Features
- **Spring Boot 3.5.x**: Based on the latest Spring Boot framework
- **Reactive Programming**: Using Spring WebFlux and Reactor Core
- **Code Quality**: Integrated Checkstyle, SpotBugs, JaCoCo code quality tools
- **API Documentation**: Automatically generated API documentation using SpringDoc OpenAPI
- **Unit Testing**: Unit tests for core functions including load balancing and health checks

#### Project Structure
- Established clear modular architecture
- Defined unified coding standards and best practices
- Established complete build and testing processes

---

## Upgrade Guide

### Upgrading from 0.3.1 to 0.3.2

#### Configuration Changes
```yaml
# New monitoring configuration
monitoring:
  metrics:
    enabled: true
    categories:
      - system
      - business
      - infrastructure
```

#### Deployment Changes
- Added Prometheus and Grafana containers
- Updated `docker-compose.yml` configuration
- Imported new Grafana dashboards

#### Notes
- Monitoring functionality is enabled by default, which may add slight performance overhead
- New monitoring endpoints require corresponding network configuration

### Upgrading from 0.2.1 to 0.3.0

#### Breaking Changes
- Configuration file path change: `./config` → `/app/config`
- Environment variable naming adjustment

#### Migration Steps
1. Update configuration file paths
2. Adjust environment variable names
3. Update deployment scripts and container configurations

### Upgrading from 0.1.0 to 0.2.0

#### New Dependencies
- No additional dependencies required, all features are built-in

#### Configuration Extensions
```yaml
# New rate limiting configuration
model:
  services:
    chat:
      rate-limit:
        type: token-bucket
        capacity: 100
        refill-rate: 10

# New circuit breaker configuration
      circuit-breaker:
        failure-threshold: 5
        recovery-timeout: 30s
        success-threshold: 3
```

## Known Issues

### Current Version (0.3.2)
- In extremely high concurrency scenarios (>10k RPS), monitoring metrics may experience slight delays
- Docker containers may experience slow DNS resolution in certain network environments

### Historical Issues
- ~~0.3.1: Build timeouts in Chinese network environments~~ (Fixed)
- ~~0.2.1: Memory leaks after long-term operation~~ (Fixed)
- ~~0.2.0: Race conditions during configuration updates in high-concurrency scenarios~~ (Fixed)

## Contributors

Thank you to all developers who have contributed to the JAiRouter project:

- **Core Team**: Responsible for architecture design and core feature development
- **Community Contributors**: Provided feature suggestions, bug reports, and code contributions
- **Documentation Team**: Improved project documentation and user guides
- **Testing Team**: Conducted functional testing and performance verification

## Feedback and Suggestions

If you encounter issues or have improvement suggestions during usage, please feel free to provide feedback through the following channels:

- **GitHub Issues**: [Submit issue report](https://github.com/Lincoln-cn/JAiRouter/issues)
- **GitHub Discussions**: [Participate in discussions](https://github.com/Lincoln-cn/JAiRouter/discussions)
- **Email Contact**: jairouter@example.com

We value every piece of feedback and will respond and address them promptly.
