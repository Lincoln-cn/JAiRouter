# Project Structure

## Root Directory Layout
```
├── src/main/java/org/unreal/modelrouter/    # Main application code
├── src/test/java/org/unreal/moduler/        # Unit tests
├── src/main/resources/                      # Configuration files
├── config/                                  # Runtime configuration files
├── docs/                                    # Documentation
├── target/                                  # Build output (generated)
├── pom.xml                                  # Maven build configuration
└── checkstyle.xml                          # Code style rules
```

## Main Package Structure (`src/main/java/org/unreal/modelrouter/`)

### Core Modules
- **`adapter/`**: Backend service adapters (GPUStack, Ollama, VLLM, etc.)
  - `impl/`: Concrete adapter implementations
- **`controller/`**: REST API endpoints and request handling
- **`model/`**: Configuration models and service registry
- **`dto/`**: Data transfer objects for API requests/responses

### Infrastructure Modules
- **`loadbalancer/`**: Load balancing strategies (Random, Round Robin, Least Connections, IP Hash)
- **`ratelimit/`**: Rate limiting algorithms (Token Bucket, Leaky Bucket, Sliding Window)
- **`circuitbreaker/`**: Circuit breaker implementation for failure protection
- **`checker/`**: Health check monitoring for service instances
- **`fallback/`**: Fallback strategies for degraded service responses

### Support Modules
- **`config/`**: Configuration loading, merging, and dynamic updates
- **`store/`**: Configuration persistence (memory and file backends)
- **`factory/`**: Component factories for creating load balancers, rate limiters
- **`exception/`**: Global exception handling
- **`util/`**: Utility classes (IP handling, network tools)

## Test Structure (`src/test/java/org/unreal/moduler/`)
- **Unit Tests**: Each major component has corresponding test class
- **Test Naming**: `{ComponentName}Test.java` pattern
- **Coverage**: Focus on core business logic, load balancing, rate limiting, circuit breaking

## Configuration Structure
- **`src/main/resources/application.yml`**: Main application configuration
- **`config/`**: Runtime configuration files for dynamic updates
- **`.kiro/steering/`**: AI assistant guidance documents

## Key Files
- **`ModelRouterApplication.java`**: Spring Boot application entry point
- **`pom.xml`**: Maven dependencies and build plugins
- **`checkstyle.xml`**: Code style enforcement rules
- **`spotbugs-security-*.xml`**: Security analysis configuration

## Naming Conventions
- **Packages**: lowercase, descriptive of functionality
- **Classes**: PascalCase, descriptive names (e.g., `RandomLoadBalancer`)
- **Methods**: camelCase, verb-based names
- **Constants**: UPPER_SNAKE_CASE
- **Configuration**: kebab-case in YAML files

## Module Dependencies
- Controllers depend on services and registries
- Load balancers, rate limiters, and circuit breakers are pluggable via factories
- Adapters abstract backend-specific implementations
- Store modules provide persistence abstraction