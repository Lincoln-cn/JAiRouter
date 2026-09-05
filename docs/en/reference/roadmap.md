# Roadmap

<!-- 版本信息 -->
> **Doc Version**: 1.6.0
> **Last Updated**: 2026-09-05
> **Git 标签**: v2.10.0
> **Author**: Lincoln
<!-- /版本信息 -->



This document outlines the future development plans and feature roadmap for the JAiRouter project.

## Project Vision

JAiRouter aims to become the best open-source AI model service routing gateway, providing users with:

- **Unified Access**: One gateway to access all AI model services
- **Intelligent Routing**: Smart load balancing based on multiple strategies
- **High Availability**: Robust fault tolerance and recovery mechanisms
- **Ease of Use**: Simple configuration and comprehensive documentation
- **High Performance**: Support for large-scale concurrency and low-latency responses
- **Observability**: Comprehensive monitoring, logging, and distributed tracing

## Current Version Status

### ✅ v2.10.0 (Current Stable)

**Release Status**: Released (2026-09-05)
**Git Tag**: v2.10.0

#### Key Features (v2.9.6 → v2.10.0 evolution)
| Version | Date | Highlights |
|---------|------|------------|
| v2.9.6 | 2026-08-30 | Routing Intelligence-2: request-level failover (switch-instance retry + per-request blacklist) |
| v2.9.7 | 2026-09-01 | Routing Intelligence-3: tag routing (instance tags + TARGET_TAGS action + header selection) |
| v2.9.8 | 2026-09-02 | Web menu RBAC management (43 permission codes + 4 role templates + data-driven URL authorization + permission UI + login DB fallback) |
| v2.9.9 | 2026-09-03 | Routing Intelligence-4: response cache P0 (non-streaming exact-match cache + Caffeine + tenant-isolated keys + metrics) |
| v2.9.10 | 2026-09-05 | Routing Intelligence-5: response cache P1 (streaming SSE concatenation cache + invalidation API + three-part cache key + service-level rate-limit early short-circuit + 44th permission code) |
| v2.9.11 | 2026-09-05 | Docs & UI quality: README screenshot/content refresh (light/dark + 44 codes + response cache P1) + hallmark web-console design audit (input for v2.10.x) + frontend fixes (44-code sync etc.) |
| v2.10.0 | 2026-09-05 | Web frontend foundation: dead-code cleanup (-13.5k lines) + PageSkeleton/StatCard/useChartTheme shared components + ~20 pages tokenized (~150 hex→token) + Element Plus dark-mode fixes + Layout/console cleanup |

#### Statistics
- Test count: 3,225 (all green)
- Java source files: 750+
- Codebase: ~135k LOC

---

### 🎯 v2.10.x - Web Console Refactor Series (Planned)

Features are complete but the console does not yet form a full system flow; one theme per version:
- **v2.10.0 Frontend foundation ✅ (released 2026-09-05)**: dead-code cleanup (13 .vue + common ≈13.5k lines) + PageSkeleton/StatCard/useChartTheme + Dashboard·Service·Rule pilot + ~20 pages tokenized + Element Plus dark-mode fixes + Layout/console cleanup
- **v2.10.1 Config & onboarding**: services/instances/adapters/pools/rules page refactor + end-to-end onboarding guide + response-cache management UI
- **v2.10.2 Governance & security**: pages for headless capabilities (monitoring ops / dynamic metrics / slow queries / tracing security / model stats) + security page refactor wrap-up
- **v2.10.3 Web bilingual edition**: full zh/en i18n + language switcher + Element Plus locale + date/number localization
- **v3.0** (Web complete-flow milestone) after the series; high-availability foundation (multi-node/Redis distributed/config rollback) re-assessed after v3.0; semantic cache evaluation only, separate project; microservice migration separate

---

### ✅ v2.6.11 LTS (Long-Term Support)

**Release Status**: Released (2026-04-17)
**Maintenance Period**: Until 2028-05

#### Completed Features
- ✅ Multi-tenancy support
- ✅ API Key authentication
- ✅ JWT Token support
- ✅ OAuth 2.0 integration
- ✅ Role-Based Access Control (RBAC)
- ✅ Request/response data obfuscation
- ✅ Security audit logging
- ✅ H2 embedded database
- ✅ PostgreSQL/MySQL support
- ✅ Redis cache integration
- ✅ Prometheus metrics collection
- ✅ Grafana dashboard templates
- ✅ Distributed tracing (Zipkin/OpenTelemetry)
- ✅ Complete Docker deployment
- ✅ Kubernetes deployment support

#### Code Quality
- ✅ Checkstyle code standards
- ✅ SpotBugs static analysis
- ✅ JaCoCo test coverage reports
- ✅ 700+ unit tests
- ✅ E2E integration tests

---

### 🎯 v2.7.x - Performance Optimization Series (Q2-Q3 2026)

#### Completed
| Version | Date | Main Content |
|---------|------|--------------|
| v2.7.0 | 2026-04-20 | Package structure refactoring, 6 service modules |
| v2.7.1 | 2026-04-21 | auth module independence (116 files) |
| v2.7.2 | 2026-04-22 | config module independence (~50 files) |
| v2.7.3 | 2026-04-23 | router module part 1 (adapter/loadbalancer) |
| v2.7.4 | 2026-04-24 | router module part 2 (circuit breaker/rate limit) |
| v2.7.5 | 2026-04-25 | monitor module independence (98 files) |
| v2.7.6 | 2026-04-26 | persistence module independence (49 files) |
| v2.7.7 | 2026-04-27 | common module independence (96 files) |
| v2.7.8 | 2026-04-28 | controller grouping optimization |
| v2.7.9 | 2026-04-29 | package structure completion |
| v2.7.10 | 2026-07-13 | Technical debt cleanup - large file splitting + deprecated code removal |
| v2.7.11 | 2026-07-14 | RBAC permission control + UI optimization |

#### Performance Improvements
- Route flow optimization: 20-50%
- Memory usage reduction: 15%
- Startup time reduction: 30%

---

### ⏸️ v3.0 - Microservices Architecture (Indefinitely Postponed)

**Status**: ⏸️ Postponed

The v3.0 microservices architecture transformation has been indefinitely postponed as the current monolithic architecture meets all requirements.

---

## Feature Roadmap

### Adapter Extensions

#### Supported ✅
| Adapter | Type | Status |
|---------|------|--------|
| GPUStack | Chat/Embedding/Rerank | ✅ |
| Ollama | Chat/Embedding | ✅ |
| vLLM | Chat | ✅ |
| Xinference | Chat/Embedding/Rerank | ✅ |
| LocalAI | Chat/Embedding | ✅ |
| OpenAI | Chat/Embedding | ✅ |
| Azure OpenAI | Chat/Embedding | ✅ |
| Anthropic Claude | Chat | ✅ |
| Alibaba Bailian | Chat/Embedding | ✅ |
| Tencent Hunyuan | Chat | ✅ |
| Baidu Cloud | Chat | ✅ |

#### Planned 📋
- 📋 Google Gemini (v2.8.x)
- 📋 Cohere API (v2.8.x)
- 📋 AWS Bedrock (v2.9.x)

### Load Balancing Strategies

#### Implemented ✅
- ✅ Random
- ✅ Round Robin
- ✅ Weighted Round Robin
- ✅ Least Connections
- ✅ IP Hash
- ✅ Consistent Hash

#### Planned 📋
- 📋 Latency-based
- 📋 Cost-based
- 📋 Model Capability-based

### Rate Limiting Algorithms

#### Implemented ✅
- ✅ Token Bucket
- ✅ Leaky Bucket
- ✅ Sliding Window
- ✅ Warm Up
- ✅ Adaptive Rate Limiting

#### Planned 📋
- 📋 Distributed Rate Limiting
- 📋 User-based Rate Limiting
- 📋 API Key-level Rate Limiting

### Monitoring and Observability

#### Implemented ✅
- ✅ Prometheus metrics
- ✅ Grafana dashboards
- ✅ Health check endpoints
- ✅ Basic alert rules
- ✅ Distributed tracing (Zipkin/OpenTelemetry)
- ✅ Structured logging

#### Planned 📋
- 📋 Business metrics analysis
- 📋 Cost analysis
- 📋 Custom alert rules

## Technical Architecture

### Current Architecture (v2.7.x)
```
Monolithic App → Modular Design → Reactive Programming
```

### Architecture Evolution

| Phase | Version | Status |
|-------|---------|--------|
| Foundation | v0.1 - v0.3 | ✅ Complete |
| Security | v0.4 | ✅ Complete |
| Monitoring | v0.5 | ✅ Complete |
| Performance | v2.7.x | ✅ Complete |
| Configuration | v2.8.x | 📋 Planned |
| Maintainability | v2.9.x | 📋 Planned |
| Microservices | v3.0 | ⏸️ Postponed |

### Tech Stack

#### Core Technology
- **Backend**: Spring Boot 3.5.5 + WebFlux (Reactive)
- **Frontend**: Vue 3 + TypeScript + Element Plus
- **Database**: H2 (embedded) + R2DBC
- **Cache**: Redis (optional)
- **Monitoring**: Prometheus + Grafana
- **Tracing**: OpenTelemetry + Zipkin

#### Data Storage
- **Default**: H2 embedded database
- **Production**: PostgreSQL / MySQL
- **Cache**: Redis (optional)

## Performance Targets

| Version | RPS | Latency (P95) | Availability | Connections |
|---------|-----|---------------|--------------|-------------|
| v2.6.x | 5k | < 50ms | 99.95% | 5k |
| v2.7.x | 10k | < 30ms | 99.95% | 10k |
| v2.8.x | 20k | < 20ms | 99.99% | 20k |
| Target | 100k+ | < 10ms | 99.99% | 100k+ |

## Community Development

### Current Status
- GitHub / Gitee dual platform hosting
- Complete user and API documentation
- Chinese/English documentation support
- MkDocs static website

### Development Goals
- Active developer community
- Regular version releases
- More adapter support
- Complete plugin system

## Release Cycle

| Version Type | Cycle | Description |
|--------------|-------|-------------|
| LTS | 24 months | Long-term stable support |
| Feature | 1-2 months | New feature iteration |
| Patch | As needed | Bug fixes and security updates |

---

## Contributing

### Code Contributions
- Feature development and bug fixes
- Performance optimization and refactoring
- Test case writing
- Code review

### Documentation Contributions
- User documentation improvements
- API documentation updates
- Tutorial and example writing
- Multi-language translation

### Feedback Channels

- **GitHub Issues**: [https://github.com/Lincoln-cn/JAiRouter/issues](https://github.com/Lincoln-cn/JAiRouter/issues)
- **GitHub Discussions**: [https://github.com/Lincoln-cn/JAiRouter/discussions](https://github.com/Lincoln-cn/JAiRouter/discussions)
- **Documentation Feedback**: Submit via GitHub PR

---

## Risks and Challenges

### Technical Risks
- **Performance bottlenecks**: Performance challenges at scale
- **Compatibility**: Multi-version API compatibility maintenance
- **Security**: Security vulnerabilities and attack protection

### Market Risks
- **Increased competition**: Competition from similar products
- **Technology changes**: Challenges from rapid AI technology development
- **User needs**: Rapidly changing user requirements

### Mitigation Strategies
- Continuous technical innovation and optimization
- Active community building and maintenance
- Flexible product strategy adjustments
- Comprehensive quality assurance system

---

## Summary

JAiRouter will continue to uphold the open-source spirit and is committed to providing users with the best AI model service routing solution. We welcome community participation and contributions to jointly promote the project's development and progress.

### Recent Focus (2026)
1. ✅ Complete v2.7.x performance optimization series + RBAC permission control
2. ✅ Advance v2.8.x configuration management + adapters + plugin system/rule engine/resource pools
3. ✅ v2.9.x intelligent routing series (failover/tag routing/response cache P0)
4. ✅ v2.9.10 response cache P1 (streaming cache + invalidation API + rate-limit short-circuit)
5. ✅ v2.9.11 README/screenshot refresh + hallmark web-console design audit + frontend fixes
6. 🎯 v2.10.x Web console refactor series (v2.10.0 foundation ✅ 2026-09-05; v2.10.1 governance hub / v2.10.2 config & capabilities / v2.10.3 bilingual 📋) → v3.0
7. 📋 Semantic cache evaluation (vector-similarity reuse, separate project); high-availability foundation (multi-node/Redis) re-assessed after v3.0

### Long-term Vision
1. Become the standard in AI model routing
2. Build a complete ecosystem
3. Achieve enterprise-level commercial success
4. Drive industry technology development

---

**Last Updated**: September 5, 2026

For any suggestions or ideas, feel free to communicate with us via [GitHub Discussions](https://github.com/Lincoln-cn/JAiRouter/discussions).
