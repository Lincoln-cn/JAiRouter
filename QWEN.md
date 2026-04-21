# JAiRouter - AI Model Service Routing Gateway

## Project Overview

JAiRouter is an AI model service routing and load balancing gateway built with **Spring Boot 3.5.5** (WebFlux). It provides centralized management and routing for various AI model services including Chat, Embedding, Rerank, TTS, and more.

### Core Technologies

| Layer | Technology |
|-------|-----------|
| Backend | Java 17, Spring Boot 3.5.5 (WebFlux/Reactive) |
| Frontend | Vue 3 + TypeScript + Element Plus + Vite |
| Database | H2 (embedded, default), R2DBC reactive access |
| Cache | Redis (optional) |
| Security | JWT + API Key dual authentication, Spring Security |
| Observability | OpenTelemetry, Prometheus, Micrometer |
| Build Tool | Maven 3.x |

### Project Structure

```
/home/ubuntu/jairouter/modelrouter/
├── src/main/java/org/unreal/modelrouter/    # Java source code
│   ├── adapter/                              # AI service adapters (GPUStack, Ollama, vLLM, etc.)
│   ├── circuitbreaker/                       # Circuit breaker implementation
│   ├── config/                               # Configuration management
│   ├── controller/                           # REST API controllers
│   ├── service/                              # Business services
│   ├── security/                             # Security filters and handlers
│   ├── tracing/                              # Distributed tracing
│   └── ...
├── src/main/resources/                       # Application configs, static resources
├── src/test/                                 # Unit and integration tests
├── frontend/                                 # Vue3 frontend admin console
├── config/                                   # Runtime configuration files
├── docs/                                     # Documentation
├── scripts/                                  # Utility scripts
├── Dockerfile*                               # Docker build files
├── docker-compose*.yml                       # Docker Compose configurations
├── Makefile                                  # Build shortcuts
└── pom.xml                                   # Maven configuration
```

### Key Features

- **Multi-Model Support**: GPUStack, Ollama, vLLM, Xinference, LocalAI adapters
- **Load Balancing**: Multiple strategies (round-robin, weighted, least-connections)
- **Rate Limiting**: Service-level and instance-level rate limiting
- **Circuit Breaker**: Automatic failover and recovery
- **Security**: JWT + API Key authentication, audit logging
- **Tracing**: OpenTelemetry integration for distributed tracing
- **Web Console**: Vue3-based admin interface for configuration management
- **Version Control**: Configuration versioning and rollback

---

## Building and Running

### Prerequisites

- JDK 17 or higher
- Maven 3.8+ (or use `./mvnw` wrapper)
- Node.js 18+ (for frontend development)
- Docker (optional, for containerized deployment)

### Maven Commands

```bash
# Compile
mvn compile

# Run tests
mvn test

# Package JAR (skip frontend)
mvn clean package

# Package with frontend (production)
mvn clean package -Pprod

# Fast build (skip tests, quality checks)
mvn clean package -Pfast

# China mirror build (accelerated)
mvn clean package -Pchina

# Run application (development)
mvn spring-boot:run -Dspring-boot.run.profiles=dev
```

### Makefile Shortcuts

```bash
# Show all available commands
make help

# Build and run locally
make compile
make test
make package

# Docker operations
make docker-build          # Build production image
make docker-build-dev      # Build development image
make docker-run            # Run production container
make docker-run-dev        # Run with debug port (5005)
make compose-up            # Start with docker-compose (includes Redis)
make compose-down          # Stop docker-compose services

# Quality and coverage
make quality-check         # Run checkstyle and spotbugs
make coverage              # Generate test coverage report
```

### Frontend Development

```bash
cd frontend/

# Install dependencies
npm install

# Development server (hot reload)
npm run dev

# Build for production
npm run build

# Type check
npm run type-check

# Lint
npm run lint
```

### Docker Deployment

```bash
# Quick start with official image
docker pull sodlinken/jairouter:latest
docker run -d \
  --name jairouter \
  -p 8080:8080 \
  -e SPRING_PROFILES_ACTIVE=prod \
  -e JWT_SECRET="your-secret-key" \
  sodlinken/jairouter:latest

# Or use docker-compose (includes Redis)
docker-compose up -d

# With monitoring stack (Prometheus + Grafana)
docker-compose --profile monitoring up -d
```

### Access Points

| Service | URL |
|---------|-----|
| Application | http://localhost:8080 |
| Admin Console | http://localhost:8080/admin |
| Swagger UI | http://localhost:8080/swagger-ui/index.html |
| Health Check | http://localhost:8080/actuator/health |
| Prometheus | http://localhost:8080/actuator/prometheus |

### Default Credentials

- Username: `admin`
- Password: `UqfpTm2Zw7ff2BNnZb8AQo8t` (check logs on first startup)

---

## Development Conventions

### Code Style

- **Java**: Google Java Style (enforced by Checkstyle)
- **Frontend**: ESLint + Prettier with Vue 3 recommendations
- **Line endings**: LF (Unix-style)
- **Encoding**: UTF-8

### Maven Profiles

| Profile | Purpose |
|---------|---------|
| `dev` | Development environment, skip code quality checks |
| `prod` | Production build, includes frontend |
| `fast` | Quick build, skip all checks and tests |
| `frontend` | Enable frontend build |
| `china` | Use Aliyun Maven mirrors for faster builds in China |
| `jib` | Build Docker image with Jib |

### Testing

- Unit tests: JUnit 5 + Mockito
- Integration tests: Spring Boot Test + TestContainers
- Coverage: JaCoCo (minimum 60% complexity coverage)

```bash
# Run all tests
mvn test

# Run with coverage
mvn clean test jacoco:report

# Skip tests (fast build)
mvn package -DskipTests
```

### Code Quality

```bash
# Run all checks
mvn checkstyle:check spotbugs:check

# Skip checks (development)
mvn package -Dcheckstyle.skip=true -Dspotbugs.skip=true
```

### Git Workflow

1. Feature branches: `feature/description`
2. Bug fixes: `fix/description`
3. Commits should be atomic with clear messages
4. Quality checks run on `validate` and `compile` phases

### Configuration Management

- Configs are modularized under `src/main/resources/config/`
- Environment-specific configs: `application-{dev|prod|staging}.yml`
- Runtime configs can be mounted via Docker volumes to `/app/config/`
- Sensitive values should use environment variables

### Security Notes

- JWT secret must be at least 32 characters in production
- API keys are stored hashed with SHA-256
- Default passwords are generated on first startup - check logs
- Enable HTTPS for production deployments

---

## Documentation

- Full documentation: https://jairouter.com
- English docs: https://jairouter.com/en/
- API docs: Available at `/swagger-ui/index.html` when running

---

## Critical Development Lessons

> ⚠️ **These lessons were learned from multiple development sessions. Always follow these guidelines to avoid repeated issues.**

### Build and Testing

- **ALWAYS run `mvn clean` first** after any Maven build and **verify browser cache is cleared** before testing changes. Browser caching has caused false negatives in multiple sessions.

### Database Queries (R2DBC)

- **ALWAYS prefer `@Query` annotation** over modifying method names or `DatabaseClient` configuration for Spring Data R2DBC queries.
- **Do NOT attempt to modify R2DBC URL patterns** for query fixes. Use the `@Query` annotation approach instead.

### Circuit Breaker Implementation

- **Check for existing class name conflicts** (like `CircuitBreakerState` enum) before creating new classes.
- **Prefer interface-based naming** to avoid collisions with existing implementations.

### Git Operations

- When encountering `'not a git repository'` errors, **prompt the user for the correct project path immediately** rather than waiting.
- **Always verify quote escaping** in git commands before execution to avoid shell parsing errors.

### Vue Frontend APIs

- **Prefer FLAT data structures** over nested formats for Vue admin page APIs unless explicitly specified.
- **Verify props match** between frontend components and API responses before attempting persistence fixes.

### Spring Security

- When Spring Security routing issues persist beyond 20 turns, **STOP and request explicit configuration files** (`WebFluxSecurityConfig`, `application.yml`) rather than trying multiple approaches (`securityMatcher`, `authorizeExchange`, `AdminPageController`).

---

## Project Development Insights

> 📊 **Based on 11 sessions spanning 3 weeks (462+ hours of development)**

### Key Development Patterns

| Pattern | Description |
|---------|-------------|
| **Iterative Debugging** | Average 42+ hours per session; quick identification and redirection when wrong approaches suggested |
| **Multi-layered Refactoring** | Successfully completed 2400+ turn refactoring across P0-P3 priority tasks including security fixes, circuit breaker redesign, i18n, and E2E testing |
| **Full-Stack Coordination** | Systematic handling of Vue frontend + Spring Security + R2DBC + Maven build issues |

### Major Development Areas

| Area | Sessions | Focus |
|------|----------|-------|
| Vue Frontend & Admin Dashboard | 3 | Rate limiter/circuit breaker panels, data persistence, Spring Security routing |
| Backend API & Data Access Layer | 2 | R2DBC H2 SQL fixes, DTO patterns, `@Query` annotation usage |
| Large-Scale Architecture Refactoring | 1 | Circuit breaker state pattern, E2E framework, i18n, build optimization |
| Release Management & DevOps | 2 | Git workflows, version tagging, deployment scripts |
| Microservices Resilience | 2 | Independent circuit breaker/rate limiter configs, flat API structures |

### Common Friction Points (Learned from Experience)

| Issue | Frequency | Mitigation Strategy |
|-------|-----------|---------------------|
| Wrong approach suggestions | 17 instances | User quickly redirects to correct solution |
| Buggy code generation | 14 instances | Immediate correction and re-generation |
| Browser caching false negatives | Multiple | Always `mvn clean` + clear cache before testing |
| R2DBC method name approaches | Several | **Always use `@Query` annotation** |
| Class name conflicts | Occasional | Check existing enums before creating new classes |
| Git quote escaping | Multiple | Verify shell command escaping before execution |
| User rejected actions | 7 instances | Clarify requirements and retry |

### Development Achievements

- ✅ **2400+ turn refactoring** completed across 21 of 22 planned tasks
- ✅ **Circuit breaker state pattern** successfully redesigned with state machine implementation
- ✅ **E2E testing framework** implemented with comprehensive coverage
- ✅ **i18n support** added for internationalization
- ✅ **Build optimization** achieved with Maven profile improvements
- ✅ **Security fixes** deployed for JWT and API key management
- ✅ **Full-stack debugging** mastered across Vue + Spring + R2DBC stack
- ✅ **5-day development streak** maintained with consistent progress

---

## Troubleshooting

| Issue | Solution |
|-------|----------|
| Port 8080 in use | Change `SERVER_PORT` env var or use `-Dserver.port=8081` |
| Frontend not showing | Ensure `-Pfrontend` or `-Pprod` profile was used during build |
| H2 database locked | Stop application and delete `data/*.lock.db` files |
| Redis connection failed | Check `REDIS_HOST` and `REDIS_PORT` environment variables |
| Out of memory | Increase `JAVA_OPTS` `-Xmx` value (default: 1024m) |

---

## Related Files

- `README.md` - Project overview and quick start
- `README-ZH.md` - Chinese documentation
- `Makefile` - Build shortcuts
- `pom.xml` - Maven configuration
- `docker-compose.yml` - Multi-service orchestration
- `checkstyle.xml` - Code style rules
- `REFACTORING_SUMMARY.md` - Recent refactoring notes

## Qwen Added Memories
- innerdoc 目录结构规范：根目录只保留《开发计划 -2026.md》和《任务跟踪表.md》两个核心文档。所有其他文档必须按内容归档到对应的子目录中：
- 01-项目概述：需求文档、开发路线图、子版本计划
- 03-重构记录：重构方案、设计文档
- 05-测试报告：测试文档、检查报告
- 16-版本发布：所有版本的开发总结（v1.x.x-开发总结.md）和 release notes
后续新生成的开发总结文档也必须放置到 16-版本发布 目录下。
- 每次启动新对话时，需要读取 README-INNERDOC.md 文件作为内部文档索引，了解项目内部文档结构
- 每次启动新对话时，需要读取 innerdoc/README-INNERDOC.md 文件作为内部文档索引，了解项目内部文档结构
- 每次启动新对话时，需要读取 innerdoc/README-INNERDOC.md 文件作为内部文档索引，该文档包含完整的 145 个文档分类清单，用于 AI 助手快速定位项目内部文档
- 项目开发计划和任务跟踪信息存储在 innerdoc/01-项目概述/开发计划 -2026.md 中。当前版本路线：v1.9.0 已完成（2026-04-17），正在进行 v1.9.1-v1.9.4 异常管理功能开发（2026-04-19~04-25），后续规划 v2.0.0 核心重构（2026-06-01）和 v2.1.0 测试与质量（2026-07-16）。共 17 个任务：P0 高优先级 4 个（安全与核心质量）、P1 中优先级 7 个（性能与可维护性）、P2 低优先级 6 个（用户体验与质量），总工作量约 35 天
- v2.0.0 文档整合工作流：1) 读取 v2.x-分版本重构开发计划.md 和开发计划2026.md 了解现有计划；2) 更新 v2.x 计划：标记 v2.0.0 为已完成，添加提交哈希 (62a0fdf,b51758d,1385ca5)、性能数据 (20-50% 提升)、新增 API(6 个端点)、代码统计 (+2,200 行)；3) 更新开发计划2026：标记 v1.9.0/v1.9.x/v2.0.0 为已完成，更新任务跟踪表 (6 个任务完成)，更新迭代计划；4) 更新 16-版本发布/v2.0.0-开发总结：添加 Git 标签、提交信息、实际代码统计；5) 清理冗余文件 (开发计划-v2.0.0.md、任务跟踪表-v2.0.0.md)；6) 所有文档版本号为 v1.1，添加变更记录。
