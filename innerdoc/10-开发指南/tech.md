# Technology Stack

## Core Framework
- **Java**: 17+ (required)
- **Spring Boot**: 3.5.x
- **Spring WebFlux**: Reactive web framework for non-blocking I/O
- **Reactor Core**: Reactive programming support

## Build System
- **Maven**: Primary build tool with `pom.xml` configuration
- **Maven Wrapper**: Use `./mvnw` for consistent builds across environments

## Key Dependencies
- **SpringDoc OpenAPI**: API documentation generation (`springdoc-openapi-starter-webflux-ui`)
- **Micrometer**: Metrics collection with Prometheus registry
- **Spring Boot Actuator**: Health checks and monitoring endpoints

## Code Quality Tools
- **Checkstyle**: Code style enforcement (config: `checkstyle.xml`)
  - Max line length: 120 characters
  - Max file length: 2000 lines
  - Enforces naming conventions and import organization
- **SpotBugs**: Static analysis for bug detection with security plugin
- **JaCoCo**: Code coverage analysis (minimum 60% complexity coverage)

## Common Commands

### Build & Test
```bash
# Clean build
./mvnw clean package

# Run tests with coverage
./mvnw test

# Run code quality checks
./mvnw checkstyle:check
./mvnw spotbugs:check

# Generate coverage report
./mvnw jacoco:report
```

### Running the Application
```bash
# Standard run
java -jar target/model-router-*.jar

# With custom config
java -jar target/model-router-*.jar --spring.config.location=classpath:/application.yml

# Development mode
./mvnw spring-boot:run
```

## Configuration
- **Main Config**: `src/main/resources/application.yml`
- **Logging**: `src/main/resources/logback.xml`
- **Default Port**: 8080
- **API Documentation**: Available at `/swagger-ui/index.html` when running

## Testing Framework
- **JUnit**: Primary testing framework
- **Spring Boot Test**: Integration testing support
- **Reactor Test**: Reactive stream testing utilities