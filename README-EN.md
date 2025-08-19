# JAiRouter

<!-- 版本信息 -->
> **文档版本**: 1.0.0  
> **最后更新**: 2025-08-19  
> **Git 提交**: f47f2607  
> **作者**: Lincoln
<!-- /版本信息 -->



JAiRouter is a Spring Boot-based model service routing and load balancing gateway designed to unify the management and routing of various AI model services (such as Chat, Embedding, Rerank, TTS, etc.). It supports multiple load balancing strategies, rate limiting, circuit breaking, health checks, dynamic configuration updates, and more.

[Chinese Introduction](README.md)

---

[![Ask DeepWiki](https://deepwiki.com/badge.svg)](https://deepwiki.com/Lincoln-cn/JAiRouter)

## ✨ Core Features

| Feature Category        | Supported Content                                                                 |
|-------------------------|-----------------------------------------------------------------------------------|
| **Unified API Gateway** | Supports OpenAI-compatible format, unified `/v1/*` interface                      |
| **Service Types**       | Chat, Embedding, Rerank, TTS, STT, Image Generation, Image Editing                |
| **Load Balancing Strategies** | Random, Round Robin, Least Connections, IP Hash                          |
| **Rate Limiting Algorithms** | Token Bucket, Leaky Bucket, Sliding Window, Warm Up                     |
| **Circuit Breaker**     | Supports failure thresholds, recovery detection, fallback strategies              |
| **Health Checks**       | Independent status interfaces per service, automatic removal of unavailable instances, periodic cleanup of inactive rate limiters |
| **Adapter Support**     | GPUStack, Ollama, VLLM, Xinference, LocalAI, OpenAI                               |
| **Dynamic Configuration Updates** | Supports runtime updates of service instances, weights, rate limits, circuit breakers |
| **Configuration Persistence** | Supports memory and file storage backends, automatic configuration file merging |
| **Test Coverage**       | Includes unit tests for load balancing, rate limiting, circuit breaking, controllers |

---

## 🧱 Project Structure

```
src/main/java/org/unreal/modelrouter
├── adapter              # Adapter module: unifying invocation methods for different backend services
│   ├── impl             # Adapter implementations: GpuStackAdapter, OllamaAdapter, etc.
├── checker              # Health check module: service status monitoring, removal, scheduled cleanup tasks
├── circuitbreaker       # Circuit breaker module: failure protection mechanisms
├── config               # Configuration module: loading, merging, dynamically updating configurations
├── controller           # Web controller: unified request entry and status interface
├── dto                  # Request/response data structure definitions
├── exception            # Global exception handling
├── factory              # Component factory: dynamic creation of load balancers, rate limiters, etc.
├── fallback             # Fallback strategies: default responses, caching, etc.
├── loadbalancer         # Load balancing module: implementation of four strategies
├── model                # Configuration models and registry center
├── ratelimit            # Rate limiting module: implementation of multiple algorithms
├── store                # Configuration storage module: memory and file persistence support, automatic configuration file merging
├── util                 # Utility classes: IP retrieval, network tools, etc.
└── ModelRouterApplication.java  # Application startup class

src/main/resources
├── application.yml      # Main configuration file
└── logback.xml          # Logging configuration

src/test/java/org/unreal/moduler
├── AutoMergeControllerTest.java
├── AutoMergeServiceTest.java
├── CircuitBreakerTest.java
├── LoadBalancerTest.java
├── ModelManagerControllerTest.java
├── ModelServiceRegistryTest.java
├── RateLimiterTest.java
├── RateLimiterCleanupCheckerTest.java
├── UniversalControllerTest.java
```

---

## 🧪 Test Module Description

| Test Class                        | Function Coverage                                                  |
|----------------------------------|--------------------------------------------------------------------|
| [AutoMergeControllerTest](file://D:\IdeaProjects\model-router\src\test\java\org\unreal\moduler\AutoMergeControllerTest.java#L21-L315)        | Controller interface testing for automatic configuration merging |
| [AutoMergeServiceTest](file://D:\IdeaProjects\model-router\src\test\java\org\unreal\moduler\AutoMergeServiceTest.java#L21-L131)           | Functional testing for configuration file auto-merge, backup, and cleanup |
| [CircuitBreakerTest](file://D:\IdeaProjects\model-router\src\test\java\org\unreal\moduler\CircuitBreakerTest.java#L14-L294)             | Circuit breaker state switching, failure recovery, fallback strategy testing |
| [LoadBalancerTest](file://D:\IdeaProjects\model-router\src\test\java\org\unreal\moduler\LoadBalancerTest.java#L20-L256)               | Verification of load balancing strategies (Random, Round Robin, Least Connections, IP Hash) |
| [ModelManagerControllerTest](file://D:\IdeaProjects\model-router\src\test\java\org\unreal\moduler\ModelManagerControllerTest.java#L20-L112)     | Dynamic configuration update interface testing                   |
| [RateLimiterTest](file://D:\IdeaProjects\model-router\src\test\java\org\unreal\moduler\RateLimiterTest.java#L25-L295)                | Rate limiting algorithm correctness, concurrent rate limiting behavior testing |
| [RateLimiterCleanupCheckerTest](file://D:\IdeaProjects\model-router\src\test\java\org\unreal\moduler\RateLimiterCleanupCheckerTest.java#L15-L48)  | Scheduled cleanup task functionality testing for rate limiters   |
| [UniversalControllerTest](file://D:\IdeaProjects\model-router\src\test\java\org\unreal\moduler\UniversalControllerTest.java#L31-L442)        | Service interface forwarding and response format validation      |

---

## ⚙️ Configuration Instructions

JAiRouter supports two configuration methods:

- **Static Configuration**: Define services, instances, rate limits, circuit breakers, etc., through the [application.yml](file://D:\IdeaProjects\model-router\target\classes\application.yml) file.
- **Dynamic Configuration**: Dynamically add, delete, or modify service instances via REST API at runtime without restarting the service.

---

### ✅ Method 1: Configuration File [application.yml](file://D:\IdeaProjects\model-router\target\classes\application.yml)

| Configuration Item       | Description                                              | Example                             |
|--------------------------|----------------------------------------------------------|-------------------------------------|
| `model.services.<type>`  | Defines global behavior for a service type               | [chat](file://D:\IdeaProjects\model-router\src\main\java\org\unreal\modelrouter\model\ModelServiceRegistry.java#L44-L44), [embedding](file://D:\IdeaProjects\model-router\src\main\java\org\unreal\modelrouter\model\ModelServiceRegistry.java#L44-L44), [tts](file://D:\IdeaProjects\model-router\src\main\java\org\unreal\modelrouter\model\ModelServiceRegistry.java#L44-L44), etc.    |
| [instances](file://D:\IdeaProjects\model-router\src\test\java\org\unreal\moduler\LoadBalancerTest.java#L22-L22)              | List of model instances under each service               | Supports weights, paths, rate limits |
| `load-balance.type`      | Load balancing strategy                                  | [random](file://D:\IdeaProjects\model-router\src\main\java\org\unreal\modelrouter\loadbalancer\impl\RandomLoadBalancer.java#L22-L22), `round-robin`, `least-connections`, `ip-hash` |
| `rate-limit`             | Rate limiting configuration                              | Supports `token-bucket`, `leaky-bucket`, `sliding-window` |
| `client-ip-enable`       | Whether to enable independent rate limiting based on client IP | `true`/`false`                      |
| `circuit-breaker`        | Circuit breaker configuration                            | Failure threshold, recovery time, success threshold |
| [fallback](file://D:\IdeaProjects\model-router\src\main\java\org\unreal\modelrouter\model\ModelRouterProperties.java#L14-L14)               | Fallback strategy                                        | Supports `default`, [cache](file://reactor\core\publisher\Mono.java#L109-L109)         |
| `store.type`             | Configuration persistence method                         | `memory` or [file](file://D:\IdeaProjects\model-router\Makefile)                  |
| `store.path`             | File storage path (effective only when `type=file`)      | `config/`                           |

> 📌 See [application.yml example](./src/main/resources/application.yml) for details.

---

### ✅ Method 2: Dynamic Configuration API

> API prefix: `/api/config/instance`

| Operation                 | Method | Path                                           | Description                                                                 |
|---------------------------|--------|------------------------------------------------|-----------------------------------------------------------------------------|
| Get Instance List         | `GET`  | `/api/config/instance/type/{serviceType}`      | Get all instances under a specific service                                 |
| Get Instance Details      | `GET`  | `/api/config/instance/info/{serviceType}`      | Requires [modelName](file://D:\IdeaProjects\model-router\src\main\java\org\unreal\modelrouter\ratelimit\RateLimitContext.java#L7-L7) and [baseUrl](file://D:\IdeaProjects\model-router\src\main\java\org\unreal\modelrouter\model\ModelRouterProperties.java#L143-L143) parameters                              |
| Add Instance              | `POST` | `/api/config/instance/add/{serviceType}`       | Add a model instance                                                       |
| Update Instance           | `PUT`  | `/api/config/instance/update/{serviceType}`    | Requires [UpdateInstanceDTO](file://D:\IdeaProjects\model-router\src\main\java\org\unreal\modelrouter\dto\UpdateInstanceDTO.java#L15-L85) including [instanceId](file://D:\IdeaProjects\model-router\src\main\java\org\unreal\modelrouter\dto\UpdateInstanceDTO.java#L17-L17)                        |
| Delete Instance           | `DELETE` | `/api/config/instance/del/{serviceType}`     | Requires [modelName](file://D:\IdeaProjects\model-router\src\main\java\org\unreal\modelrouter\ratelimit\RateLimitContext.java#L7-L7) and [baseUrl](file://D:\IdeaProjects\model-router\src\main\java\org\unreal\modelrouter\dto\UpdateInstanceDTO.java#L22-L22) parameters                              |

---

#### ✅ Example API Calls

##### 1. Get Instance List
```http
GET /api/config/instance/type/chat
```

##### 2. Get Instance Details
```http
GET /api/config/instance/info/chat?modelName=qwen3:1.7B&baseUrl=http://172.16.30.6:9090
```

##### 3. Add Instance
```http
POST /api/config/instance/add/chat
Content-Type: application/json

{
  "name": "qwen3:7B",
  "baseUrl": "http://172.16.30.7:9090",
  "path": "/v1/chat/completions",
  "weight": 2
}
```

##### 4. Update Instance
```http
PUT /api/config/instance/update/chat
Content-Type: application/json

{
  "instanceId": "qwen3:7B@http://172.16.30.7:9090",
  "instance": {
    "name": "qwen3:7B",
    "baseUrl": "http://172.16.30.8:9090",
    "path": "/v1/chat/completions",
    "weight": 3
  }
}
```

##### 5. Delete Instance
```http
DELETE /api/config/instance/del/chat?modelName=qwen3:7B&baseUrl=http://172.16.30.8:9090
```

---

### ✅ Configuration Priority

| Priority | Source                     | Hot Reload Supported |
|----------|----------------------------|----------------------|
| High     | Dynamic API configuration  | ✅                   |
| Low      | [application.yml](file://D:\IdeaProjects\model-router\target\classes\application.yml)          | ❌ (requires restart) |

> 🔁 When dynamic and static configurations conflict, **dynamic configuration takes precedence** and will be persisted to local files (if `store.type=file` is configured).

---

## 🔄 Automatic Configuration File Merging

JAiRouter provides a powerful automatic configuration file merging feature to handle multiple version configuration files under the config directory:

### 📋 Merging Feature Overview

| Feature              | Description                                                  | API Endpoint                        |
|----------------------|--------------------------------------------------------------|-------------------------------------|
| **Version Scanning** | Automatically scans all version configuration files in the config directory | `GET /api/config/merge/scan`        |
| **Merge Preview**    | Preview merged results without actual operation              | `GET /api/config/merge/preview`     |
| **Automatic Merge**  | Merge multiple version files and reset version numbers from 1  | `POST /api/config/merge/execute`    |
| **Backup Config**    | Backup existing configuration files to a timestamped directory | `POST /api/config/merge/backup`     |
| **File Cleanup**     | Clean up original configuration files (optional)             | `DELETE /api/config/merge/cleanup`  |
| **Batch Operation**  | Execute backup, merge, and cleanup in sequence               | `POST /api/config/merge/batch`      |
| **Config Validation**| Validate configuration file format and content               | `GET /api/config/merge/validate`    |
| **Statistics**       | Get detailed statistics of configuration files               | `GET /api/config/merge/statistics`  |
| **Service Status**   | Get current status information of the merge service          | `GET /api/config/merge/status`      |

### 🔧 Merge Strategy

- **Deep Merge**: Intelligently merge services configurations to avoid overwriting existing services
- **Instance Deduplication**: Automatically deduplicate instance configurations based on `name@baseUrl`
- **Version Reset**: Reset version numbers to 1 after merging for easier management
- **Error Handling**: Detailed error information and partial success handling mechanism

### 📝 Usage Examples

```bash
# 1. Scan version files
curl -X GET http://localhost:8080/api/config/merge/scan

# 2. Preview merge result
curl -X GET http://localhost:8080/api/config/merge/preview

# 3. Backup existing files
curl -X POST http://localhost:8080/api/config/merge/backup

# 4. Execute automatic merge
curl -X POST http://localhost:8080/api/config/merge/execute

# 5. Batch operation (backup + merge + cleanup)
curl -X POST "http://localhost:8080/api/config/merge/batch?deleteOriginals=true"

# 6. Validate configuration files
curl -X GET http://localhost:8080/api/config/merge/validate

# 7. Get statistics
curl -X GET http://localhost:8080/api/config/merge/statistics

# 8. Clean up original files (optional)
curl -X DELETE "http://localhost:8080/api/config/merge/cleanup?deleteOriginals=true"
```

---

## ⏰ Scheduled Tasks

JAiRouter includes multiple scheduled tasks to maintain system health and performance:

| Task Name              | Frequency   | Description                                                  | Implementation Class              |
|------------------------|-------------|--------------------------------------------------------------|-----------------------------------|
| **Service Health Check** | Every 30 seconds | Check connection status of all service instances, automatically remove unavailable instances | [ServerChecker](file://D:\IdeaProjects\model-router\src\main\java\org\unreal\modelrouter\checker\ServerChecker.java#L18-L165)                   |
| **Rate Limiter Cleanup** | Every 5 minutes | Clean up client IP rate limiters inactive for 30 minutes to prevent memory leaks | [RateLimiterCleanupChecker](file://D:\IdeaProjects\model-router\src\main\java\org\unreal\modelrouter\checker\RateLimiterCleanupChecker.java#L12-L38)       |

> 📌 All scheduled tasks are implemented using Spring's `@Scheduled` annotation and managed by the Spring container.

---

## 📘 API Documentation (SpringDoc OpenAPI)

JAiRouter uses [SpringDoc OpenAPI](https://springdoc.org/) to automatically generate RESTful API documentation.

After starting the project, visit the following addresses to view detailed interface descriptions, request parameters, response structures, and examples online:

| Document Type    | Access URL                                                       |
|------------------|------------------------------------------------------------------|
| **Swagger UI**   | [http://127.0.0.1:8080/swagger-ui/index.html](http://127.0.0.1:8080/swagger-ui/index.html) |
| **OpenAPI JSON** | [http://127.0.0.1:8080/v3/api-docs](http://127.0.0.1:8080/v3/api-docs) |

> 📌 The default port is `8080`. If `server.port` is modified, replace the port in the URL with the actual port.

---

## 🛡️ Code Quality Assurance

This project integrates multiple code quality tools to ensure code standards and quality:

| Tool         | Purpose                                  | Configuration File                                           |
|--------------|------------------------------------------|--------------------------------------------------------------|
| **Checkstyle** | Checks code style and formatting standards | [checkstyle.xml](checkstyle.xml)                             |
| **SpotBugs**   | Static analysis tool to find potential bugs | [spotbugs-security-include.xml](spotbugs-security-include.xml) [spotbugs-security-exclude.xml](spotbugs-security-exclude.xml) |
| **JaCoCo**     | Code coverage analysis tool              | Built into [pom.xml](pom.xml)                                |

These tools automatically run during Maven builds to help maintain high-quality code standards.

### 🔧 Code Quality Profiles

| Profile  | Purpose       | Checks Included                   | Use Case             |
|----------|---------------|-----------------------------------|----------------------|
| **Default** | Full check     | Checkstyle + SpotBugs + JaCoCo    | Development environment |
| **Fast**    | Fast build     | Skip all checks                   | Docker builds, CI/CD |
| **Prod**    | Production build | Skip tests                        | Production deployment |

### 📋 Common Build Commands

```bash
# Full build (including all checks)
mvn clean package

# Fast build (skip checks, for Docker)
mvn clean package -Pfast

# Production build (skip tests)
mvn clean package -Pprod

# Run code quality checks only
mvn checkstyle:check spotbugs:check
```

---

## 📝 Logging Management

JAiRouter uses the SLF4J + Logback logging framework, supporting multi-environment configuration and performance optimization:

### 🔧 Logging Configuration

| Configuration File       | Purpose                            | Environment          |
|--------------------------|------------------------------------|----------------------|
| [logback-spring.xml](file://D:\IdeaProjects\model-router\target\classes\logback-spring.xml)     | Main configuration file, supports multi-environment | Recommended use      |
| [logback.xml](file://D:\IdeaProjects\model-router\target\classes\logback.xml)            | Fallback configuration file        | Compatibility retention |
| [application-dev.yml](file://D:\IdeaProjects\model-router\target\classes\application-dev.yml)    | Development environment logging config | Development environment |
| [application-test.yml](file://D:\IdeaProjects\model-router\target\classes\application-test.yml)   | Test environment logging config    | Test environment     |
| [application-prod.yml](file://D:\IdeaProjects\model-router\target\classes\application-prod.yml)   | Production environment logging config | Production environment |

### 📊 Environment Log Levels

| Environment | Root Level | Application Components | Framework Components | Output Method        |
|-------------|------------|------------------------|----------------------|----------------------|
| **Development** | INFO       | DEBUG                  | INFO                 | Console + File       |
| **Test**        | INFO       | INFO/WARN              | WARN                 | Console + File       |
| **Production**  | WARN       | INFO/WARN              | ERROR                | File only            |

### 🎯 Logging Optimization Features

- **Multi-environment Support**: Automatically switch logging configurations based on Spring Profile
- **Asynchronous Output**: Improve performance using asynchronous Appender
- **File Rotation**: Automatically rotate log files by size and time
- **Traceability**: Support traceId for distributed tracing
- **Performance Optimization**: Reduce log output by 80% in production

### 📚 Related Documents

- [Logging Optimization Plan](docs/logging-optimization.md)
- [Logging Usage Standards](docs/logging-standards.md)
- [Logging Audit Report](docs/logging-audit-report.md)

---

## 🐳 Docker Deployment

JAiRouter provides a complete Dockerized deployment solution, supporting multi-environment configuration and container orchestration:

### 🎯 Docker Features

- **Multi-stage Build**: Optimized image size, production image ~200MB
- **Multi-environment Support**: Independent configurations for dev, test, and production
- **China Acceleration**: Alibaba Cloud Maven mirror optimized build
- **Security Best Practices**: Non-root user, minimal privilege execution
- **Health Check**: Built-in application health monitoring
- **Monitoring Integration**: Supports Prometheus + Grafana monitoring stack

### 🛠️ Build Methods

| Method         | Command                              | Features                                              |
|----------------|--------------------------------------|-------------------------------------------------------|
| **Standard Build** | `./scripts/docker-build.sh`         | International users, using Maven Central              |
| **China Acceleration** | `./scripts/docker-build-china.sh` | Chinese users, using Alibaba Cloud Maven mirror       |
| **Maven Plugin** | `mvn dockerfile:build -Pdocker`     | Integrated build process                              |
| **Jib Plugin**   | `mvn jib:dockerBuild -Pjib`         | No Docker required, faster build                      |

### 🇨🇳 China-Specific Build

Optimized for Chinese users' network environment using Alibaba Cloud Maven mirror:

```bash
# Use China-optimized version build (recommended for Chinese users)
./scripts/docker-build-china.sh

# Or use Maven china profile
mvn clean package -Pchina
docker build -f Dockerfile.china -t jairouter/model-router:latest .
```

**China Version Features:**
- ✅ Uses Alibaba Cloud Maven mirror (https://maven.aliyun.com/repository/public)
- ✅ Significantly improves dependency download speed
- ✅ Includes complete repository mirrors for Spring, Central, Plugin, etc.
- ✅ Automatically configures settings.xml

### 📋 Deployment Configuration

```yaml
# docker-compose.yml example
version: '3.8'
services:
  jairouter:
    image: jairouter/model-router:latest
    ports:
      - "8080:8080"
    environment:
      - SPRING_PROFILES_ACTIVE=prod
    volumes:
      - ./config:/app/config:ro
      - ./logs:/app/logs
    restart: unless-stopped
```

### 📚 Related Documents

- [Docker Deployment Guide](docs/docker-deployment.md) - Complete deployment documentation

---

## 📦 Dependency Versions

- **JDK**: 17+
- **Spring Boot**: 3.5.x
- **Spring WebFlux**: Reactive web framework
- **Reactor Core**: Reactive programming support

---

## 🚀 Startup and Deployment

### 🛠️ Build Methods

JAiRouter provides multiple build methods optimized for different user groups:

| Build Method     | Target Users | Command                              | Features                                              |
|------------------|--------------|--------------------------------------|-------------------------------------------------------|
| **China Acceleration** | Chinese users    | `./scripts/docker-build-china.sh` | Uses Alibaba Cloud Maven mirror, 5-10x speed improvement |
| **Standard Build**     | International users | `./scripts/docker-build.sh`      | Uses Maven Central, stable and reliable               |
| **Maven Build**        | Developers       | `mvn clean package -Pchina`       | Local development, supports china profile             |

> 📚 For detailed build instructions, refer to: [Build Guide](docs/build-guide.md)

### Traditional Deployment

```bash
# Chinese users (recommended)
mvn clean package -Pchina
java -jar target/model-router-*.jar

# International users
./mvnw clean package
java -jar target/model-router-*.jar

# Specify configuration file path
java -jar target/model-router-*.jar --spring.config.location=classpath:/application.yml
```

### Docker Deployment (Recommended)

#### 🐳 Quick Start

```bash
# 1. Build Docker image
make docker-build

# 2. Start application
make docker-run

# 3. Verify deployment
make health-check
```

#### 🛠️ Detailed Deployment Steps

##### Method 1: Using Makefile (Recommended)
```bash
# Development environment
make dev                    # Build and start development environment

# Production environment  
make prod                   # Build and start production environment

# Using Docker Compose
make compose-up             # Start application
make compose-up-monitoring  # Start application and monitoring
```

##### Method 2: Using Scripts
```bash
# Windows PowerShell
.\scripts\docker-build.ps1 prod
.\scripts\docker-run.ps1 prod

# Linux/macOS Bash
./scripts/docker-build.sh prod
./scripts/docker-run.sh prod
```

##### Method 3: Using Maven Plugin
```bash
# Using Dockerfile plugin
mvn clean package dockerfile:build -Pdocker

# Using Jib plugin (no Docker required)
mvn clean package jib:dockerBuild -Pjib
```

#### 🔧 Docker Configuration

| Environment | Port | Memory Config | Features                          |
|-------------|------|---------------|-----------------------------------|
| **Production** | 8080 | 512MB-1GB     | Optimized config, health check    |
| **Development** | 8080, 5005 | 256MB-512MB | Debug support, hot reload         |

#### 📊 Monitoring Deployment
```bash
# Start application and full monitoring stack
docker-compose --profile monitoring up -d

# Access monitoring interfaces
# Prometheus: http://localhost:9090
# Grafana: http://localhost:3000 (admin/admin)
```

#### 🔍 Common Commands
```bash
# View container status
docker ps --filter "name=jairouter"

# View application logs
make docker-logs

# Stop service
make docker-stop

# Clean up resources
make docker-clean
```

---

## 📌 Development Plan (Update Status)

| Phase    | Status | Content                                                                 |
|----------|--------|-------------------------------------------------------------------------|
| 0.1.0    | ✅     | Basic gateway, adapters, load balancing, health checks                  |
| 0.2.0    | ✅     | Rate limiting, circuit breaking, fallback, config persistence, dynamic update API |
| 0.2.1    | ✅     | Scheduled cleanup tasks, memory optimization, enhanced client IP rate limiting, auto config merging |
| 0.3.0    | ✅     | Docker containerization, multi-environment deployment, monitoring integration |
| 0.3.1    | ✅     | China users use Alibaba mvn source to accelerate image build            |
| 0.4.0    | ✅     | Monitoring metrics, Prometheus integration, alert notifications         |
| 0.5.0    | 🚧     | Manage all project-related documents using GitHub Pages                 |
| 0.6.0    | 📋     | authentication and authorization, log tracing     |

---

For further expansion, please refer to the [DeepWiki documentation](https://deepwiki.com/Lincoln-cn/JAiRouter) or submit an Issue to participate in the development.
