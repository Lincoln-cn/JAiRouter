# JAiRouter

JAiRouter is a Spring Boot-based model service routing and load balancing gateway designed to centrally manage and route various AI model services (such as Chat, Embedding, Rerank, TTS, etc.), supporting multiple load balancing strategies, rate limiting, circuit breaking, health checks, and dynamic configuration updates.

[中文说明](README.md)

---

## ✨ Core Features

| Feature Category | Supported Content |
|------------------|-------------------|
| **Unified API Gateway** | Supports OpenAI-compatible format, unifying `/v1/*` interfaces |
| **Service Types** | Chat, Embedding, Rerank, TTS, STT, Image Generation, Image Editing |
| **Load Balancing Strategies** | Random, Round Robin, Least Connections, IP Hash |
| **Rate Limiting Algorithms** | Token Bucket, Leaky Bucket, Sliding Window, Warm Up |
| **Circuit Breaker Mechanism** | Supports failure thresholds, recovery detection, and fallback strategies |
| **Health Checks** | Independent status interface per service, supports automatic removal of unavailable instances, scheduled cleanup of inactive rate limiters |
| **Adapter Support** | GPUStack, Ollama, VLLM, Xinference, LocalAI, OpenAI |
| **Dynamic Configuration Updates** | Supports runtime updates of service instances, weights, rate limits, circuit breakers, etc. |
| **Configuration Persistence** | Supports both in-memory and file-based backends, automatic configuration file merging |
| **Test Coverage** | Includes unit tests for load balancing, rate limiting, circuit breaking, and controllers |

---

## 🧱 Project Structure

```
src/main/java/org/unreal/modelrouter
├── adapter              # Adapter module: unifies calling methods for different backend services
│   ├── impl             # Adapter implementations: GpuStackAdapter, OllamaAdapter, etc.
├── checker              # Health check module: service status monitoring, removal, scheduled cleanup tasks
├── circuitbreaker       # Circuit breaker module: failure protection mechanism
├── config               # Configuration module: loading, merging, and dynamically updating configurations
├── controller           # Web controllers: unified request entry and status interface
├── dto                  # Request/response data structure definitions
├── exception            # Global exception handling
├── factory              # Component factory: dynamically creates load balancers, rate limiters, etc.
├── fallback             # Fallback strategies: default responses, caching, etc.
├── loadbalancer         # Load balancing module: four strategy implementations
├── model                # Configuration models and registry center
├── ratelimit            # Rate limiting module: multiple algorithm implementations
├── store                # Configuration storage module: supports in-memory and file persistence, automatic configuration file merging
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

| Test Class | Function Coverage |
|------------|-------------------|
| `AutoMergeControllerTest` | Tests automatic configuration file merge controller interfaces |
| `AutoMergeServiceTest` | Tests automatic configuration file merging, backup, and cleanup functionality |
| `CircuitBreakerTest` | Tests circuit breaker state switching, failure recovery, and fallback strategies |
| `LoadBalancerTest` | Validates behaviors of load balancing strategies (Random, Round Robin, Least Connections, IP Hash) |
| `ModelManagerControllerTest` | Tests dynamic configuration update interfaces |
| `RateLimiterTest` | Validates correctness of rate limiting algorithms and concurrent rate limiting behavior |
| `RateLimiterCleanupCheckerTest` | Tests scheduled cleanup tasks for rate limiters |
| `UniversalControllerTest` | Validates service interface forwarding and response format |

---

## ⚙️ Configuration Instructions

JAiRouter supports two configuration methods:

- **Static Configuration**: Defines service, instance, rate limiting, and circuit breaker parameters via [application.yml](file://D:\IdeaProjects\model-router\target\classes\application.yml);
- **Dynamic Configuration**: Dynamically adds, deletes, or modifies service instances at runtime via REST API without restarting the service.

---

### ✅ Method 1: Configuration File [application.yml](file://D:\IdeaProjects\model-router\target\classes\application.yml)

| Configuration Item | Description | Example |
|--------------------|-------------|---------|
| `model.services.<type>` | Defines global behavior for a service type | [chat](file://D:\IdeaProjects\model-router\src\main\java\org\unreal\modelrouter\model\ModelServiceRegistry.java#L41-L41), [embedding](file://D:\IdeaProjects\model-router\src\main\java\org\unreal\modelrouter\model\ModelServiceRegistry.java#L41-L41), [tts](file://D:\IdeaProjects\model-router\src\main\java\org\unreal\modelrouter\model\ModelServiceRegistry.java#L41-L41), etc. |
| [instances](file://D:\IdeaProjects\model-router\src\main\java\org\unreal\modelrouter\model\ModelServiceRegistry.java#L649-L649) | List of model instances under each service | Supports weights, paths, rate limiting, etc. |
| `load-balance.type` | Load balancing strategy | [random](file://D:\IdeaProjects\model-router\src\main\java\org\unreal\modelrouter\loadbalancer\impl\RandomLoadBalancer.java#L21-L21), `round-robin`, `least-connections`, `ip-hash` |
| `rate-limit` | Rate limiting configuration | Supports `token-bucket`, `leaky-bucket`, `sliding-window` |
| `client-ip-enable` | Whether to enable client IP-based independent rate limiting | `true`/`false` |
| `circuit-breaker` | Circuit breaker configuration | Failure threshold, recovery time, success threshold |
| [fallback](file://D:\IdeaProjects\model-router\src\main\java\org\unreal\modelrouter\model\ModelRouterProperties.java#L14-L14) | Fallback strategy | Supports `default`, [cache](file://reactor\core\publisher\Mono.java#L109-L109) |
| `store.type` | Configuration persistence method | `memory` or `file` |
| `store.path` | File storage path (effective only when `type=file`) | `config/` |

> 📌 See [application.yml example](./src/main/resources/application.yml) for details

---

✅ Based on the latest [ServiceInstanceController.java](file://D:\IdeaProjects\model-router\src\main\java\org\unreal\modelrouter\controller\ServiceInstanceController.java) API paths, the **Dynamic Configuration Interface Documentation** has been updated as follows:

---

### ✅ Method 2: Dynamic Configuration Interface

> Unified API prefix: `/api/config/instance`

| Operation | Method | Path | Description |
|-----------|--------|------|-------------|
| Get instance list | `GET` | `/api/config/instance/type/{serviceType}` | Retrieves all instances under the specified service |
| Get instance details | `GET` | `/api/config/instance/info/{serviceType}` | Requires [modelName](file://D:\IdeaProjects\model-router\src\main\java\org\unreal\modelrouter\ratelimit\RateLimitContext.java#L7-L7) and [baseUrl](file://D:\IdeaProjects\model-router\src\main\java\org\unreal\modelrouter\model\ModelRouterProperties.java#L143-L143) parameters |
| Add instance | `POST` | `/api/config/instance/add/{serviceType}` | Adds a model instance |
| Update instance | `PUT` | `/api/config/instance/update/{serviceType}` | Requires [UpdateInstanceDTO](file://D:\IdeaProjects\model-router\src\main\java\org\unreal\modelrouter\dto\UpdateInstanceDTO.java#L15-L85) including [instanceId](file://D:\IdeaProjects\model-router\src\main\java\org\unreal\modelrouter\dto\UpdateInstanceDTO.java#L17-L17) |
| Delete instance | `DELETE` | `/api/config/instance/del/{serviceType}` | Requires [modelName](file://D:\IdeaProjects\model-router\src\main\java\org\unreal\modelrouter\ratelimit\RateLimitContext.java#L7-L7) and [baseUrl](file://D:\IdeaProjects\model-router\src\main\java\org\unreal\modelrouter\dto\UpdateInstanceDTO.java#L22-L22) parameters |

---

#### ✅ Example API Calls

##### 1. Get instance list
```http
GET /api/config/instance/type/chat
```

##### 2. Get instance details
```http
GET /api/config/instance/info/chat?modelName=qwen3:1.7B&baseUrl=http://172.16.30.6:9090
```

##### 3. Add instance
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

##### 4. Update instance
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

##### 5. Delete instance
```http
DELETE /api/config/instance/del/chat?modelName=qwen3:7B&baseUrl=http://172.16.30.8:9090
```

---

For integration with frontend consoles or automation scripts, you can directly use the above APIs for hot updates of service instances.

---

### ✅ Configuration Priority Explanation

| Priority | Source | Hot Update Supported |
|----------|--------|----------------------|
| High | Dynamic API configuration | ✅ |
| Low | [application.yml](file://D:\IdeaProjects\model-router\target\classes\application.yml) | ❌ (requires restart) |

> 🔁 When dynamic and static configurations conflict, **dynamic configuration takes precedence** and will be persisted to local files (if `store.type=file` is configured).

- **Configuration Persistence**: Supports both in-memory and file-based backends, configured via `store.type=memory|file`.

---

## 🔄 Automatic Configuration File Merging

JAiRouter provides powerful automatic configuration file merging functionality to handle multiple version configuration files in the config directory:

### 📋 Merge Features

| Feature | Description | API Interface |
|---------|-------------|---------------|
| **Version Scanning** | Automatically scan all version configuration files in the config directory | `GET /api/config/merge/scan` |
| **Merge Preview** | Preview the result of configuration file merging without executing actual operations | `GET /api/config/merge/preview` |
| **Auto Merge** | Merge multiple version configuration files and reset version starting from 1 | `POST /api/config/merge/execute` |
| **Configuration Backup** | Backup existing configuration files to timestamped directory | `POST /api/config/merge/backup` |
| **File Cleanup** | Clean up original configuration files (optional) | `DELETE /api/config/merge/cleanup` |
| **Batch Operations** | Execute backup, merge, and cleanup operations sequentially | `POST /api/config/merge/batch` |
| **Configuration Validation** | Validate configuration file format and content | `GET /api/config/merge/validate` |
| **Statistics** | Get detailed statistics of configuration files | `GET /api/config/merge/statistics` |
| **Service Status** | Get current status information of merge service | `GET /api/config/merge/status` |

### 🔧 Merge Strategy

- **Deep Merge**: Intelligently merge services configuration, avoiding overwriting existing services
- **Instance Deduplication**: Automatically deduplicate instance configurations based on `name@baseUrl`
- **Version Reset**: Reset version number starting from 1 after merging for easier management
- **Error Handling**: Detailed error information and partial success handling mechanism

### 📝 Usage Examples

```bash
# 1. Scan version files
curl -X GET http://localhost:8080/api/config/merge/scan

# 2. Preview merge result
curl -X GET http://localhost:8080/api/config/merge/preview

# 3. Backup existing files
curl -X POST http://localhost:8080/api/config/merge/backup

# 4. Execute auto merge
curl -X POST http://localhost:8080/api/config/merge/execute

# 5. Batch operation (backup+merge+cleanup)
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

| Task Name | Frequency | Description | Implementation Class |
|-----------|-----------|-------------|---------------------|
| **Service Health Check** | Every 30 seconds | Check connection status of all service instances, automatically remove unavailable instances | `ServerChecker` |
| **Rate Limiter Cleanup** | Every 5 minutes | Clean up client IP rate limiters inactive for 30 minutes, prevent memory leaks | `RateLimiterCleanupChecker` |

> 📌 All scheduled tasks are implemented based on Spring's `@Scheduled` annotation and managed uniformly by the Spring container.

---

## 🧩 Additional Module Responsibilities

| Module | Responsibility Description |
|--------|----------------------------|
| `adapter` | Wraps different backends (e.g., Ollama, VLLM) into OpenAI-compatible calls |
| `checker` | Periodically checks service health, automatically removes unavailable instances, scheduled cleanup tasks |
| `circuitbreaker` | Prevents service cascading failures, supports failure thresholds, recovery detection, and fallback strategies |
| `config` | Loads YAML configurations and supports runtime hot updates |
| `fallback` | Provides default or cached responses when services are rate-limited or circuit-breakered |
| `store` | Abstracts configuration persistence, supports both in-memory and file implementations, automatic configuration file merging |
| `util` | Provides general-purpose tools such as IP retrieval, URL construction, and request forwarding |

---



---

## 🛡️ Code Quality Assurance

This project integrates multiple code quality tools to ensure code standards and quality:

| Tool | Purpose | Configuration Files |
|------|---------|---------------------|
| **Checkstyle** | Checks code style and formatting standards | [checkstyle.xml](checkstyle.xml) |
| **SpotBugs** | Static analysis tool to find potential bugs | [spotbugs-security-include.xml](spotbugs-security-include.xml) [spotbugs-security-exclude.xml](spotbugs-security-exclude.xml) |
| **JaCoCo** | Code coverage analysis tool | Built into [pom.xml](pom.xml) |

These tools run automatically during the Maven build process, helping us maintain high-quality code standards.

## 📦 Dependency Versions

- **JDK**: 17+
- **Spring Boot**: 3.5.x
- **Spring WebFlux**: Reactive web framework
- **Reactor Core**: Reactive programming support

---

## 🚀 Startup and Deployment

### Traditional Deployment

```bash
# Compile
./mvnw clean package

# Run
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
make compose-up-monitoring  # Start application with monitoring
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

##### Method 3: Using Maven Plugins
```bash
# Using Dockerfile plugin
mvn clean package dockerfile:build -Pdocker

# Using Jib plugin (no Docker required)
mvn clean package jib:dockerBuild -Pjib
```

#### 🔧 Docker Configuration

| Environment | Ports | Memory Config | Features |
|-------------|-------|---------------|----------|
| **Production** | 8080 | 512MB-1GB | Optimized config, health checks |
| **Development** | 8080, 5005 | 256MB-512MB | Debug support, hot reload |

#### 📊 Monitoring Deployment
```bash
# Start application with full monitoring stack
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

# Stop services
make docker-stop

# Clean up resources
make docker-clean
```

---

## 📌 Development Roadmap (Status Update)

| Phase | Status | Content |
|-------|--------|---------|
| 0.1.0 | ✅ | Basic gateway, adapters, load balancing, health checks |
| 0.2.0 | ✅ | Rate limiting, circuit breaking, fallback, configuration persistence, dynamic update interfaces |
| 0.2.1 | ✅ | Scheduled cleanup tasks, memory optimization, client IP rate limiting enhancement, automatic configuration file merging |
| 0.2.2 | ✅ | Docker containerization, multi-environment deployment, monitoring integration |
| 0.3.0 | 🚧 | Monitoring metrics, Prometheus integration, alert notifications |
| 0.4.0 | 📋 | Multi-tenancy support, authentication and authorization, log tracing |

---

---

## 🐳 Docker Deployment

JAiRouter provides a complete Docker deployment solution with multi-environment configuration and container orchestration:

### 🎯 Docker Features

- **Multi-stage Build**: Optimized image size, production image ~200MB
- **Multi-environment Support**: Independent configuration for dev, test, and production
- **Security Best Practices**: Non-root user, minimal privilege execution
- **Health Checks**: Built-in application health monitoring
- **Monitoring Integration**: Supports Prometheus + Grafana monitoring stack

### 🛠️ Build Methods

| Method | Command | Features |
|--------|---------|----------|
| **Makefile** | `make docker-build` | Simple and easy, recommended |
| **Scripts** | `./scripts/docker-build.sh` | Cross-platform support |
| **Maven Plugin** | `mvn dockerfile:build -Pdocker` | Integrated build process |
| **Jib Plugin** | `mvn jib:dockerBuild -Pjib` | No Docker required, faster build |

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

### 📚 Related Documentation

- [Docker Deployment Guide](docs/docker-deployment.md) - Complete deployment documentation

---

For further extensions, please refer to the [DeepWiki documentation](https://deepwiki.com/Lincoln-cn/JAiRouter) or submit an Issue to contribute.
