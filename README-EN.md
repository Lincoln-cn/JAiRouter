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
| **Health Checks** | Independent status interface per service, supports automatic removal of unavailable instances |
| **Adapter Support** | GPUStack, Ollama, VLLM, Xinference, LocalAI, OpenAI |
| **Dynamic Configuration Updates** | Supports runtime updates of service instances, weights, rate limits, circuit breakers, etc. |
| **Configuration Persistence** | Supports both in-memory and file-based backends |
| **Test Coverage** | Includes unit tests for load balancing, rate limiting, circuit breaking, and controllers |

---

## 🧱 Project Structure

```
src/main/java/org/unreal/modelrouter
├── adapter              # Adapter module: unifies calling methods for different backend services
│   ├── impl             # Adapter implementations: GpuStackAdapter, OllamaAdapter, etc.
├── checker              # Health check module: service status monitoring and removal
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
├── store                # Configuration storage module: supports in-memory and file persistence
├── util                 # Utility classes: IP retrieval, network tools, etc.
└── ModelRouterApplication.java  # Application startup class

src/main/resources
├── application.yml      # Main configuration file
└── logback.xml          # Logging configuration

src/test/java/org/unreal/moduler
├── CircuitBreakerTest.java
├── LoadBalancerTest.java
├── ModelManagerControllerTest.java
├── ModelServiceRegistryTest.java
├── RateLimiterTest.java
├── UniversalControllerTest.java
```

---

## 🧪 Test Module Description

| Test Class | Function Coverage |
|------------|-------------------|
| [CircuitBreakerTest](file://D:\IdeaProjects\model-router\src\test\java\org\unreal\moduler\CircuitBreakerTest.java#L9-L196) | Tests circuit breaker state switching, failure recovery, and fallback strategies |
| [LoadBalancerTest](file://D:\IdeaProjects\model-router\src\test\java\org\unreal\moduler\LoadBalancerTest.java#L13-L175) | Validates behaviors of load balancing strategies (Random, Round Robin, Least Connections, IP Hash) |
| [ModelManagerControllerTest](file://D:\IdeaProjects\model-router\src\test\java\org\unreal\moduler\ModelManagerControllerTest.java#L21-L105) | Tests dynamic configuration update interfaces |
| [ModelServiceRegistryTest](file://D:\IdeaProjects\model-router\src\test\java\org\unreal\moduler\ModelServiceRegistryTest.java#L24-L456) | Tests service registration, instance selection, and weight effectiveness |
| [RateLimiterTest](file://D:\IdeaProjects\model-router\src\test\java\org\unreal\moduler\RateLimiterTest.java#L22-L180) | Validates correctness of rate limiting algorithms and concurrent rate limiting behavior |
| [UniversalControllerTest](file://D:\IdeaProjects\model-router\src\test\java\org\unreal\moduler\UniversalControllerTest.java#L22-L220) | Validates service interface forwarding and response format |

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

## 🧩 Additional Module Responsibilities

| Module | Responsibility Description |
|--------|----------------------------|
| [adapter](file://D:\IdeaProjects\model-router\src\main\java\org\unreal\modelrouter\model\ModelServiceRegistry.java#L650-L650) | Wraps different backends (e.g., Ollama, VLLM) into OpenAI-compatible calls |
| `checker` | Periodically checks service health and automatically removes unavailable instances |
| `circuitbreaker` | Prevents service cascading failures, supports failure thresholds, recovery detection, and fallback strategies |
| [config](file://D:\IdeaProjects\model-router\src\main\java\org\unreal\modelrouter\ratelimit\BaseRateLimiter.java#L9-L9) | Loads YAML configurations and supports runtime hot updates |
| [fallback](file://D:\IdeaProjects\model-router\src\main\java\org\unreal\modelrouter\model\ModelRouterProperties.java#L14-L14) | Provides default or cached responses when services are rate-limited or circuit-breakered |
| `store` | Abstracts configuration persistence, supports both in-memory and file implementations |
| `util` | Provides general-purpose tools such as IP retrieval, URL construction, and request forwarding |

---

## 📦 Dependency Versions

- **JDK**: 17+
- **Spring Boot**: 3.5.x
- **Spring WebFlux**: Reactive web framework
- **Reactor Core**: Reactive programming support

---

## 🚀 Startup and Deployment

```bash
# Compile
./mvnw clean package

# Run
java -jar target/model-router-*.jar

# Specify configuration file path
java -jar target/model-router-*.jar --spring.config.location=classpath:/application.yml
```

---

## 📌 Development Roadmap (Status Update)

| Phase | Status | Content |
|-------|--------|---------|
| 0.1.0 | ✅ | Basic gateway, adapters, load balancing, health checks |
| 0.2.0 | ✅ | Rate limiting, circuit breaking, fallback, configuration persistence, dynamic update interfaces |
| 0.3.0 | 🚧 | Monitoring metrics, Prometheus integration, alert notifications |
| 0.4.0 | 📋 | Multi-tenancy support, authentication and authorization, log tracing |

---

For further extensions, please refer to the [DeepWiki documentation](https://deepwiki.com/Lincoln-cn/JAiRouter) or submit an Issue to contribute.
