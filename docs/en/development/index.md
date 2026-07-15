# Development Guide

<!-- 版本信息 -->
> **Document Version**: 2.0.0
> **最后更新**: 2026-06-15
> **Git 提交**: 0f56b957
> **作者**: JAiRouter Team
<!-- /版本信息 -->

Welcome to the JAiRouter Development Guide! This guide provides developers with comprehensive information on setting up the development environment, coding standards, testing strategies, and contribution processes.

## Quick Start

If you are new to JAiRouter development, it is recommended to read the following documents in order:

1. **[Architecture Overview](architecture.md)** - Understand the overall system architecture and design principles
2. **[Contribution Guide](contributing.md)** - Learn how to participate in project development
3. **[Testing Guide](testing.md)** - Master testing strategies and best practices
4. **[Code Quality Standards](code-quality.md)** - Follow project coding standards

---

## Development Environment Requirements

| Tool | Version | Description |
|------|---------|-------------|
| **Java** | 17+ | Required, LTS version |
| **Maven** | 3.8+ | Maven Wrapper included in project |
| **Git** | 2.20+ | Version control |
| **IDE** | - | IntelliJ IDEA (recommended) or Eclipse |

---

## Core Development Workflow

### 1. Environment Setup

```bash
# Clone the project
git clone https://github.com/Lincoln-cn/JAiRouter.git
cd jairouter

# Build the project (recommended for China users)
./mvnw clean package -Pchina

# Run tests
./mvnw compiler:compile compiler:testCompile surefire:test
```

### 2. Development Standards

- Follow coding standards in [Code Quality Standards](code-quality.md)
- Use Checkstyle and SpotBugs for code quality checks
- Ensure test coverage is at least 80%

### 3. Submission Process

```bash
# Create feature branch
git checkout -b feature/your-feature-name

# Write code and tests
# ...

# Run tests and quality checks
./mvnw clean verify

# Commit code
git add .
git commit -m "feat: your feature description"

# Push branch
git push origin feature/your-feature-name

# Create Pull Request
```

---

## Project Architecture Overview

JAiRouter adopts a modular design with the following core modules:

| Module | Package Path | Responsibility |
|--------|--------------|----------------|
| **auth** | `org.unreal.modelrouter.auth` | Authentication, JWT, API Key |
| **config** | `org.unreal.modelrouter.config` | Configuration management, version control |
| **router** | `org.unreal.modelrouter.router` | Routing, load balancing, rate limiting, circuit breaker |
| **monitor** | `org.unreal.modelrouter.monitor` | Monitoring, tracing, metrics |
| **persistence** | `org.unreal.modelrouter.persistence` | Data persistence, repositories |
| **common** | `org.unreal.modelrouter.common` | Common utilities, exceptions, constants |

For detailed information, please refer to [Architecture Overview](architecture.md).

---

## Common Commands

### Build Commands

```bash
# Full build (including all checks)
./mvnw clean package

# Fast build (skip checks)
./mvnw clean package -Pfast

# China mirror acceleration
./mvnw clean package -Pchina
```

### Test Commands

```bash
# Run all tests
./mvnw test

# Run specific tests
./mvnw test -Dtest=LoadBalancerTest

# Generate coverage report
./mvnw clean test jacoco:report
```

### Quality Checks

```bash
# Code style check
./mvnw checkstyle:check

# Static analysis
./mvnw spotbugs:check

# Full quality check
./mvnw verify
```

---

## Development Tool Integration

### Code Quality Tools

| Tool | Purpose | Configuration File |
|------|---------|-------------------|
| **Checkstyle** | Code style checking | `checkstyle.xml` |
| **SpotBugs** | Static code analysis | `spotbugs-security-*.xml` |
| **JaCoCo** | Code coverage analysis | `pom.xml` |

### Testing Frameworks

| Framework | Purpose |
|-----------|---------|
| **JUnit 5** | Main testing framework |
| **Mockito** | Mock framework |
| **Spring Boot Test** | Integration testing support |
| **Reactor Test** | Reactive stream testing |

---

## Extension Development

### Adding New Adapters

1. Create adapter class extending `BaseAdapter`
2. Implement request transformation and response mapping logic
3. Register in `AdapterRegistry`
4. Write unit tests

For detailed instructions, please refer to [Contribution Guide](contributing.md).

### Adding New Load Balancing Strategies

1. Implement `LoadBalancer` interface
2. Register in `LoadBalancerFactory`
3. Add configuration support
4. Write test cases

### Adding New Rate Limiting Algorithms

1. Implement `RateLimiter` interface
2. Register in `RateLimiterFactory`
3. Add YAML configuration support
4. Write concurrency tests

---

## Getting Help

If you encounter problems during development, you can get help in the following ways:

- Check project documentation and FAQ
- Search existing GitHub Issues
- Create a new Issue to describe the problem
- Participate in community discussions

---

## Contribution Methods

We welcome all forms of contributions:

| Type | Description |
|------|-------------|
| 🐛 **Bug Reports** | Report issues promptly when discovered |
| ✨ **Feature Development** | Implement new features or improve existing ones |
| 📚 **Documentation Improvements** | Enhance documentation content and examples |
| 🧪 **Test Enhancements** | Add test cases to improve coverage |
| 💡 **Suggestion Discussions** | Propose improvement suggestions and ideas |

For detailed contribution processes, please refer to [Contribution Guide](contributing.md).

---

Thank you for your contributions to the JAiRouter project!
