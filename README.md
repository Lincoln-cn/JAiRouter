# JAiRouter

<p align="center">
  <img src="logo/JAiRouterLogo.png" alt="JAiRouter Logo" width="400">
</p>

[![Ask DeepWiki](https://deepwiki.com/badge.svg)](https://deepwiki.com/Lincoln-cn/JAiRouter)
![Docker Pulls](https://img.shields.io/docker/pulls/sodlinken/jairouter)

JAiRouter is a Spring Boot-based model service routing and load balancing gateway designed to centrally manage and route various AI model services (such as Chat, Embedding, Rerank, TTS, etc.), supporting multiple load balancing strategies, rate limiting, circuit breaking, health checks, dynamic configuration updates, and more.

[中文介绍](README-ZH.md)

---


## 🧭 Feature Overview (Web Console)

| Module Category     | Menu             | Description                                                                                      |
|---------------------|------------------|--------------------------------------------------------------------------------------------------|
| 🔍 **Overview**      | Dashboard        | Real-time display of system status, service health, request trends, exception statistics, with chart visualization and dynamic refresh support. |
| ⚙️ **Configuration Management** | Service Management   | Supports dynamic configuration of AI service types, adapters, load balancing policies, and service-level rate limiting and circuit breaker rules. |
|                     | Instance Management  | Provides instance creation, editing, deletion, status management, and supports instance-level rate limiting, circuit breaking, health checks, and weight configuration. |
|                     | Version Management   | Full lifecycle management of configuration versions: create, apply, rollback, delete; metadata recording and version comparison supported. |
| 🔐 **Security Management** | API Key Management   | Supports API key creation, enable/disable, permission assignment, usage statistics, expiration reminders, and field desensitization for sensitive data. |
|                     | JWT Token Management | Lifecycle management of JWT tokens: query, revoke, refresh, blacklist mechanism; supports Redis and file persistence. |
|                     | Audit Logs           | Comprehensive logging of user login, configuration changes, token operations, key management events, with event type filtering and tracking. |
| 👤 **System Management** | Account Management   | Supports admin account creation, permission allocation, status management, and action log tracking. |
| 📊 **Trace Management** | Trace Overview       | Real-time display of trace data health status, sampling rate, service statistics, and trend charts. |
|                     | Trace Search         | Multi-condition combined queries for trace records, supporting filtering by service, time, status, tags, etc. |
|                     | Performance Analysis | Service-level performance analysis including latency distribution, error rates, throughput, bottleneck diagnosis, and optimization suggestions. |
|                     | Trace Management     | Configurable sampling strategies (global/service-level), performance settings, exporter configuration, real-time refresh of trace data. |

---

## 🚀 Core Highlights

- ✅ **Full-featured Web Console**: Built from scratch covering complete management chains such as configuration, security, tracing, and auditing.
- ✅ **Frontend-backend Separation Architecture**: Based on Vue3 + Element Plus with responsive design and friendly interaction.
- ✅ **Configuration Version Control**: Supports multi-version configuration management and rollback to ensure traceability of changes.
- ✅ **Tracing & Performance Monitoring**: Integrated distributed tracing and performance analysis to enhance system observability.
- ✅ **Enterprise-grade Security Mechanism**: Dual authentication system with JWT + API Key, built-in audit and desensitization mechanisms.
- ✅ **High Availability & Scalability**: Supports high availability deployment via Redis, multi-tier storage strategy for configurations and tokens.
- ✅ **Multi-backend Adapter Support**: Supports latest API features for GPUStack, Ollama, VLLM, Xinference, LocalAI and other backend services.

---

## 🧩 Use Cases

- Centralized internal AI service gateway management
- Multi-model service routing and load balancing
- API security authentication and access control
- Distributed system tracing and performance analysis
- Configuration change audit and version rollback

---

## 📚 Online Documentation

Complete project documentation has been migrated to GitHub Pages and can be accessed online:

- [Chinese Documentation](https://jairouter.com/)
- [English Documentation](https://jairouter.com/en/)

Documentation includes:

- Quick Start Guide
- Detailed Configuration Instructions
- API Reference
- Deployment Guide
- Monitoring Configuration
- Development Guide
- Troubleshooting

---

## 🚀 Quick Start

### 1. Pull Image

```bash
# Pull the latest image
docker pull sodlinken/jairouter:latest
```

### 2. Generate Secure Keys (Recommended)

**New in v1.8.0**: Key generation tool for automatic generation of secure JWT secrets and admin passwords.

**Option 1: Using Docker (Recommended)**

```bash
# Generate JWT secret (Base64 encoded, at least 32 characters)
docker run --rm sodlinken/jairouter:latest java -jar /app/modelrouter.jar --generate-key

# Example output:
# Base64 Encoded (recommended for JWT HS256):
#   cGFzc3dvcmQtdGVzdC1rZXktZm9yLWphb3V0ZXItMjAyNg==
# Key Strength: Very Strong

# Generate random password
docker run --rm sodlinken/jairouter:latest java -jar /app/modelrouter.jar --generate-password

# Example output:
# 16 character password: aB3dEfGhIjKlMnOp
# Password Strength: Strong
```

**Option 2: Using OpenSSL (No jar needed)**

```bash
# Generate JWT secret
openssl rand -base64 32

# Generate random password
openssl rand -base64 24 | tr -dc 'A-Za-z0-9!@#$%^&*' | head -c 16
```

### 3. Run Container

**Option 1: Using generated keys (Recommended)**

```bash
# Set environment variables
export JWT_SECRET="cGFzc3dvcmQtdGVzdC1rZXktZm9yLWphb3V0ZXItMjAyNg=="
export INITIAL_ADMIN_PASSWORD="MyStr0ng!Pass#2026"

# Run container
docker run -d \
  --name jairouter \
  -p 8080:8080 \
  -e SPRING_PROFILES_ACTIVE=prod \
  -e JWT_SECRET="$JWT_SECRET" \
  -e INITIAL_ADMIN_PASSWORD="$INITIAL_ADMIN_PASSWORD" \
  sodlinken/jairouter:latest
```

**Option 2: Using default password (Development only)**

```bash
docker run -d \
  --name jairouter-dev \
  -p 8080:8080 \
  -e SPRING_PROFILES_ACTIVE=dev \
  -e JWT_SECRET="your-very-strong-jwt-secret-key-at-least-32-characters-long" \
  -e JAVA_OPTS="-Xms256m -Xmx512m -agentlib:jdwp=transport=dt_socket,server=y,suspend=n" \
  sodlinken/jairouter:dev
```

### 4. Access Service

```bash
curl http://localhost:8080/admin/login
```

![](./docs/capture/login.png)

**Default Login Credentials**:
- Username: `admin`
- Password: The value of `INITIAL_ADMIN_PASSWORD` environment variable set at startup
  - **Development environment default**: `ChangeMeOnFirstStartup123456` (when not set)
  - **Production environment**: Must be set via environment variable, otherwise security warnings will be issued during startup
  - **Recommended**: Generate a strong password using the key generation tool:
    ```bash
    docker run --rm sodlinken/jairouter:latest java -jar /app/modelrouter.jar --generate-password
    ```

After successful login, you can enter the web interface to perform service configuration, management, tracing, and performance analysis operations.

![](./docs/capture/dashboard.png)


## 📘 API Documentation

After starting the project, you can access the automatically generated API documentation at the following addresses:

- **Swagger UI**: http://127.0.0.1:8080/swagger-ui/index.html
- **OpenAPI JSON**: http://127.0.0.1:8080/v3/api-docs

## 📌 Development Roadmap (Status Update)

### Phase 1: Foundation (v0.x - v1.0.x) ✅

| Version | Status | Content |
|---------|--------|---------|
| 0.1.0 | ✅ | Basic gateway, adapter, load balancing, health check |
| 0.2.0 | ✅ | Rate limiting, circuit breaking, degradation, config persistence |
| 0.2.1 | ✅ | Scheduled cleanup, memory optimization, client IP rate limiting |
| 0.3.0 | ✅ | Docker containerization, multi-environment deployment |
| 0.3.1 | ✅ | Alibaba Maven mirror for China acceleration |
| 0.4.0 | ✅ | Monitoring metrics, Prometheus integration |
| 0.5.0 | ✅ | GitHub Pages documentation |
| 0.6.0 | ✅ | Authentication and authorization |
| 0.7.0 | ✅ | Log tracing |
| 0.8.0 | ✅ | Automated Docker Hub publishing |
| 0.9.0 | ✅ | Enhanced dashboard and user management |
| 1.0.0 | ✅ | Enterprise deployment guide |
| 1.1.0 | ✅ | API playground feature |
| 1.2.5 | ✅ | H2 database support (default persistence) |

### Phase 2: Security & Management (v1.5.x - v1.9.x) ✅

| Version | Status | Content |
|---------|--------|---------|
| 1.5.6 | ✅ | Instance-level rate limiter & circuit breaker config |
| 1.5.7 | ✅ | JWT account initialization |
| 1.6.0 | ✅ | Config version management optimization |
| 1.6.1 | ✅ | API Key security enhancement (P0 fixes) |
| 1.6.2 | ✅ | API Key management enhancement (P1/P2) |
| 1.7.0 | ✅ | JWT account management optimization |
| 1.7.2 | ✅ | Playground component refactoring |
| 1.8.0 | ✅ | Security hardening version |
| 1.8.1 | ✅ | Quick start guide |
| 1.9.0 | ✅ | Core refactoring - performance optimization |
| 1.9.3 | ✅ | Exception management frontend |
| 1.9.4 | ✅ | Prometheus integration for exception monitoring |
| 1.9.5 | ✅ | Token usage statistics feature |
| 1.9.6 | ✅ | Complete monitoring system |

### Phase 3: Refactoring (v2.0.x - v2.3.x) ✅

| Version | Status | Content |
|---------|--------|---------|
| 2.0.0 | ✅ | Concurrent performance optimization, model call statistics |
| 2.1.2 | ✅ | Magic string cleanup - Adapter support classes |
| 2.1.3 | ✅ | Magic string cleanup - Monitoring & tracing classes |
| 2.1.4 | ✅ | Magic string cleanup - Full scan and fixes |
| 2.2.0 | ✅ | ConfigurationService split - ConfigVersionManager & ConfigValidator |
| 2.2.1 | ✅ | BaseAdapter split - RequestBuilder & ResponseHandler |
| 2.2.4 | ✅ | BaseAdapter further split - InstanceSelector & ResponseTransformer |
| 2.2.5 | ✅ | Enhanced instance health check - HTTP connectivity |
| 2.2.6 | ✅ | ServiceConfigManager refactoring |
| 2.2.7 | ✅ | Adapter capability check - CapabilityChecker |
| 2.2.8 | ✅ | Integration tests for new components |
| 2.2.9 | ✅ | Quality improvement and documentation |
| 2.3.0 | ✅ | Error handling & retry components |
| 2.3.1 | ✅ | HttpRequestProcessor & ResponseMapper |
| 2.3.2 | ✅ | Monitoring & tracing split |
| 2.3.3 | ✅ | Health check display fix |
| 2.4.0 | ✅ | Consistent hashing load balancer, weight overflow fix |
| 2.4.1 | ✅ | Load balancer management page, configuration persistence |
| 2.4.5 | ✅ | State persistence infrastructure |
| 2.4.7 | ✅ | Circuit breaker state management page |

### Phase 4: State Persistence (v2.5.x) ✅

| Version | Status | Content |
|---------|--------|---------|
| 2.5.0 | ✅ | Circuit breaker state persistence (Redis + File) |
| 2.5.1 | ✅ | Rate limiter state persistence |
| 2.5.2 | ✅ | Unified state manager API |
| 2.5.3-2.5.15 | ✅ | State persistence optimization & bug fixes |

### Phase 5: Code Quality (v2.6.x) ✅

| Version | Status | Content |
|---------|--------|---------|
| 2.6.1-2.6.9 | ✅ | Checkstyle FinalParameters cleanup (5,413 → 0) |
| 2.6.10 | ✅ | WhitespaceAfter fixes (1,655 → 29, 98.2% reduction) |
| 2.6.11 | ✅ | HiddenField & OperatorWrap suppression config |
| **Total** | ✅ | **Warnings: 10,413 → 3,424 (67% reduction)** |

### Phase 6: Microservices Preparation (v2.7-v2.9) 🚧

| Version | Status | Content | Duration |
|---------|--------|---------|----------|
| **v2.7.x** | 🚧 | **Package Structure Reorganization** | 10 days |
| 2.7.0 | 🚧 | - Service boundary definition & base structure |
| 2.7.1 | 🚧 | - auth module migration (security/audit) |
| 2.7.2 | 🚧 | - config module migration (config/version) |
| 2.7.3 | 🚧 | - router module migration (adapter/loadbalancer) |
| 2.7.4 | 🚧 | - router module migration (circuitbreaker/ratelimit) |
| 2.7.5 | 🚧 | - monitor module migration (tracing/metrics) |
| 2.7.6 | 🚧 | - persistence module migration (store/jpa) |
| 2.7.7 | 🚧 | - common module migration (constants/dto/util) |
| 2.7.8 | 🚧 | - controller grouping by service |
| 2.7.9 | 🚧 | - test adjustment & dependency fix |
| **v2.8.x** | 📋 | **Configuration Integration** | 10 days |
| 2.8.0 | 📋 | - Configuration structure analysis |
| 2.8.1 | 📋 | - Configuration file split by module |
| 2.8.2 | 📋 | - Service module config separation |
| 2.8.3 | 📋 | - Multi-environment config completion |
| 2.8.4 | 📋 | - External config file support |
| 2.8.5 | 📋 | - Sensitive config separation |
| 2.8.6 | 📋 | - Config loading priority |
| 2.8.7 | 📋 | - Config validation mechanism |
| 2.8.8 | 📋 | - Configuration documentation |
| 2.8.9 | 📋 | - Config migration test & summary |
| **v2.9.0** | 📋 | **Issue Fix & Review** | 7 days |
| | 📋 | - Fix v2.7/v2.8 leftover issues |
| | 📋 | - Update all documentation |
| | 📋 | - Code quality check & performance test |

### Phase 7: Microservices Architecture (v3.0.x) 📋

| Version | Status | Content | Duration |
|---------|--------|---------|----------|
| 3.0.0 | 📋 | **Microservices Architecture Transformation** (2026-06-05 ~ 2026-07-15) |
| | 📋 | - Authentication/Authorization service separation |
| | 📋 | - Nacos configuration center integration |
| | 📋 | - Monitoring/Tracing service separation |
| | 📋 | - Service discovery mechanism |
| | 📋 | - Inter-service communication stabilization |

---

📖 **Full Documentation & Deployment Guide**: [Click Here](https://jairouter.com)
🐙 **Source Code Repository**: [GitHub - JAiRouter](https://github.com/Lincoln-cn/jairouter)

---

💬 Welcome feedback and collaboration—let's make JAiRouter better together!
