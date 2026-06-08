# Development Guide

<!-- Version Info -->
> **Document Version**: 1.8.0
> **Last Updated**: 2026-06-08
> **Git Commit**: dd7b68ad
> **Author**: JAiRouter Team
<!-- /Version Info -->



Welcome to the JAiRouter Development Guide! This guide provides developers with comprehensive information on setting up the development environment, coding standards, testing strategies, and contribution processes.

## 📢 Latest Updates

- **V1.7.3** (2026-04-14) - Playground Chat streaming response fix, port configuration restoration
- **V1.7.2** (2026-04-14) - Playground component refactoring: 18 new components + 4 composables
- **V1.7.1** (2026-04-13) - Tracing fixes, TraceDetail component, Tracing Dashboard
- **V1.7.0** (2026-04-10) - Security blacklist management, audit log enhancement, JWT account status toggle
- **V1.6.2** (2026-04-08) - API Key batch import/export, key rotation, expiration cleanup
- **V1.6.1** (2026-04-06) - API Key hashed storage, IP whitelist, rate limiting
- **V1.6.0** (2026-04-04) - Removed configuration merge feature, simplified version management
- **V1.5.7** (2026-04-02) - JWT account initialization, account management API optimization
- **V1.5.6** (2026-03-30) - Instance-level rate limiter/circuit breaker independent storage

For complete changelog, please refer to [Changelog](../reference/changelog.md)

## Quick Start

If you are new to JAiRouter development, it is recommended to read the following documents in order:

1. **[Architecture Overview](architecture.md)** - Understand the overall system architecture and design principles
2. **[Contribution Guide](contributing.md)** - Learn how to participate in project development
3. **[Testing Guide](testing.md)** - Master testing strategies and best practices
4. **[Code Quality Standards](code-quality.md)** - Follow project coding standards

## Development Environment Requirements

- **Java**: Version 17 or higher
- **Maven**: 3.8+ (It is recommended to use the Maven Wrapper included in the project)
- **Git**: 2.20+
- **IDE**: IntelliJ IDEA (recommended) or Eclipse

## Core Development Workflow

### 1. Environment Setup
```bash
# Clone the project
git clone https://github.com/Lincoln-cn/JAiRouter.git
cd jairouter

# Build the project (recommended for users in China)
./mvnw clean package -Pchina

# Run tests
./mvnw compiler:compile compiler:testCompile surefire:test
```

### 2. Development Standards
- Follow the coding standards outlined in [Code Quality Standards](code-quality.md)
- Use Checkstyle and SpotBugs for code quality checks
- Ensure test coverage is no less than 80%

### 3. Submission Process
- Create a feature branch for development
- Write unit tests and integration tests
- Submit a Pull Request for code review
- Merge into the main branch

## Project Architecture Overview

JAiRouter adopts a modular design and mainly includes the following core modules:

- **Controller Layer**: Unified API entry point and configuration management
- **Adapter Layer**: Protocol adaptation for different backend services
- **Load Balancing Layer**: Implementation of various load balancing strategies
- **Rate Limiting Layer**: Support for multiple rate limiting algorithms
- **Circuit Breaker Layer**: Service fault tolerance and failure recovery
- **Storage Layer**: Configuration persistence and version management

For detailed information, please refer to [Architecture Overview](architecture.md).

## Development Tool Integration

### Code Quality Tools
- **Checkstyle**: Code style checking
- **SpotBugs**: Static code analysis
- **JaCoCo**: Code coverage analysis

### Testing Frameworks
- **JUnit 5**: Main testing framework
- **Mockito**: Mock framework
- **Spring Boot Test**: Integration testing support
- **Reactor Test**: Reactive stream testing

### Build and Deployment
- **Maven**: Project building and dependency management
- **Docker**: Containerized deployment
- **GitHub Actions**: Continuous integration and deployment

## Common Commands

```bash
# Full build (including all checks)
./mvnw clean package

# Fast build (skip checks)
./mvnw clean package -Pfast

# Run specific tests
./mvnw test -Dtest=LoadBalancerTest

# Generate coverage report
./mvnw clean test jacoco:report

# Code quality checks
./mvnw checkstyle:check spotbugs:check
```

## Getting Help

If you encounter problems during development, you can get help in the following ways:

- Check project documentation and FAQ
- Search existing GitHub Issues
- Create a new Issue to describe the problem
- Participate in community discussions

## Contribution Methods

We welcome all forms of contributions:

- 🐛 **Bug Reports**: Please report issues promptly when discovered
- ✨ **Feature Development**: Implement new features or improve existing ones
- 📚 **Documentation Improvements**: Enhance documentation content and examples
- 🧪 **Test Enhancements**: Add test cases to improve coverage
- 💡 **Suggestion Discussions**: Propose improvement suggestions and ideas

For detailed contribution processes, please refer to the [Contribution Guide](contributing.md).

Thank you for your contributions to the JAiRouter project!
