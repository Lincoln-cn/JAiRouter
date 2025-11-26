# Changelog

<!-- 版本信息 -->
> **文档版本**: 1.0.0  
> **最后更新**: 2025-08-19  
> **Git 提交**: c1aa5b0f  
> **作者**: Lincoln
<!-- /版本信息 -->



This document records the version update history and important changes of the JAiRouter project.

## Versioning Scheme

JAiRouter follows the [Semantic Versioning](https://semver.org/) specification:

- **Major Version**: Incompatible API changes
- **Minor Version**: Backward-compatible new features
- **Patch Version**: Backward-compatible bug fixes

## Version History

### [Unreleased] - In Development

#### New Features
- **Security Module**: Complete enterprise-grade security features including API Key authentication, JWT token support, and bidirectional data sanitization
- **Multi-tenancy Support**: Tenant isolation, resource quotas, and tenant-based configuration management
- **Authentication and Authorization**: API Key authentication mechanism, JWT Token support, OAuth 2.0 integration, and Role-Based Access Control (RBAC)
- **Data Protection**: Request/response data obfuscation, encrypted storage of sensitive information, and security audit logs
- **H2 Database Support**: H2 embedded database as default storage with automatic data migration for configuration, security audit, API keys, and JWT accounts
- **Monitoring Integration**: Complete Prometheus + Grafana monitoring stack with business and infrastructure metrics collection
- **Distributed Tracing**: End-to-end distributed tracing with Jaeger/Zipkin integration for full request lifecycle tracking
- Complete documentation system and user guides
- Multi-language documentation support (Chinese/English)
- Enhanced monitoring and alerting capabilities
- More adapter support

#### Improvements & Optimizations
- **Spring Security Integration**: Full integration with Spring Security framework for robust authentication and authorization
- **Cache Layer Optimization**: Redis cache optimization for improved performance
- **Database Support**: Added support for PostgreSQL and MySQL databases
- **H2 Storage Performance**: 5-20x performance improvement over file storage for configuration and security data operations
- **Docker Build Optimization**: China-optimized Docker builds with Alibaba Cloud Maven mirror, 5-10x faster dependency downloads
- Performance optimization and memory management improvements
- Better error handling and logging
- Configuration validation and user experience enhancements

#### Bug Fixes
- Fixed known memory leak issues
- Resolved race conditions in concurrent scenarios
- Improved stability of configuration hot updates
- Security scanning and vulnerability fixes
- Fixed configuration file path issues in container environments
- Resolved DNS resolution issues in Docker containers

---

### [0.3.2] - 2025-08-20

#### New Features
- **Monitoring Integration**: Complete Prometheus + Grafana monitoring stack
- **Performance Metrics**: Added collection of business and infrastructure metrics
- **Alert Rules**: Pre-configured alert rules and notification mechanisms
- **Monitoring Dashboard**: Professional Grafana dashboard templates

#### Improvements & Optimizations
- **Metrics Optimization**: Optimized metrics collection performance to reduce impact on main business
- **Memory Management**: Improved memory usage and cleanup mechanisms for metric data
- **Configuration Simplification**: Simplified monitoring-related configuration parameters

#### Bug Fixes
- Fixed inaccurate monitoring metrics under high concurrency
- Resolved memory leak issues after long-term operation
- Fixed Prometheus metrics format compatibility issues

#### Technical Debt
- Refactored monitoring module code structure
- Improved unit test coverage
- Optimized build and deployment processes

---

### [0.3.1] - 2025-08-15

#### New Features
- **China Accelerated Build**: Docker builds optimized specifically for Chinese users
- **Alibaba Cloud Mirror**: Using Alibaba Cloud Maven mirror to accelerate dependency downloads
- **Build Script**: Added [docker-build-china.sh](file://D:\IdeaProjects\model-router\scripts\docker-build-china.sh) build script
- **Maven Profile**: Added [china](file://D:\IdeaProjects\model-router\Dockerfile.china) profile support

#### Improvements & Optimizations
- **Build Speed**: Build speed for Chinese users improved by 5-10 times
- **Network Optimization**: Optimized network connections and timeout configurations
- **Documentation Enhancement**: Added China-specific build guides

#### Bug Fixes
- Fixed dependency download timeout issues in Chinese network environments
- Resolved unstable Maven repository connection issues
- Fixed network errors during Docker builds

---

### [0.3.0] - 2025-08-14

#### New Features
- **Docker Containerization**: Complete Docker deployment support
- **Multi-environment Deployment**: Support for development, testing, and production environment configurations
- **Docker Compose**: Provided complete container orchestration configuration
- **Health Check**: Container-level health check mechanism
- **Monitoring Integration**: Basic monitoring metrics exposure

#### Improvements & Optimizations
- **Image Optimization**: Multi-stage builds, production image approximately 200MB
- **Security Hardening**: Running as non-root user, principle of least privilege
- **Performance Tuning**: JVM parameter optimization in container environments
- **Log Management**: Log collection and rotation in containerized environments

#### Bug Fixes
- Fixed configuration file path issues in container environments
- Resolved configuration loss after container restarts
- Fixed network communication issues between containers

#### Breaking Changes
- Default configuration file path changed from `./config` to `/app/config`
- Environment variable naming convention adjusted

---

### [0.2.1] - 2025-08-12

#### New Features
- **Scheduled Cleanup Task**: Automatic cleanup of inactive rate limiters to prevent memory leaks
- **Memory Optimization**: Improved memory usage patterns to reduce GC pressure
- **Enhanced Client IP Rate Limiting**: More precise client IP identification and rate limiting
- **Automatic Configuration File Merging**: Support for intelligent merging of multi-version configuration files

#### Improvements & Optimizations
- **Performance Improvement**: Rate limiter performance optimization to reduce lock contention
- **Enhanced Monitoring**: Added monitoring metrics for memory usage and cleanup tasks
- **Log Optimization**: Improved log format and performance
- **Error Handling**: Better exception handling and error recovery mechanisms

#### Bug Fixes
- Fixed memory leak issues after long-term operation
- Resolved rate limiter race conditions in high-concurrency scenarios
- Fixed thread safety issues during configuration hot updates
- Resolved client IP acquisition issues in proxy environments

#### Technical Improvements
- Refactored rate limiter cleanup mechanism
- Improved unit test coverage to 85%
- Optimized code quality check rules

---

### [0.2.0] - 2024-08-11

#### New Features
- **Rate Limiting Mechanism**: Support for Token Bucket, Leaky Bucket, Sliding Window, and Warm Up rate limiting algorithms
- **Circuit Breaker**: Implemented circuit breaker pattern with support for failure thresholds, recovery detection, and fallback strategies
- **Fallback Strategies**: Support for default responses and cache fallback
- **Configuration Persistence**: Support for both in-memory and file storage backends
- **Dynamic Configuration Updates**: Runtime updates for service instances, weights, rate limiting, and circuit breaking configurations

#### Improvements & Optimizations
- **Performance Optimization**: Reactive programming model supporting high-concurrency processing
- **Configuration Management**: Automatic configuration file merging and version management
- **Error Handling**: Comprehensive exception handling and error recovery mechanisms
- **Monitoring Metrics**: Added rate limiting and circuit breaking related monitoring metrics

#### Bug Fixes
- Fixed thread safety issues in load balancer during instance changes
- Resolved data consistency issues during configuration updates
- Fixed memory leak issues in high-concurrency scenarios

#### API Changes
- Added dynamic configuration management API (`/api/config/instance/*`)
- Added configuration file merging API (`/api/config/merge/*`)
- Extended health check API to include more status information

---

### [0.1.0] - 2025-08-04

#### New Features
- **Basic Gateway**: Unified `/v1/*` API gateway supporting OpenAI-compatible format
- **Service Type Support**: Chat, Embedding, Rerank, TTS, STT, Image Generation, Image Editing
- **Adapter Pattern**: Support for GPUStack, Ollama, VLLM, Xinference, LocalAI, and OpenAI adapters
- **Load Balancing**: Implemented Random, Round Robin, Least Connections, and IP Hash strategies
- **Health Check**: Independent status interface per service, automatic removal of unavailable instances
- **Configuration Management**: Static configuration support based on YAML

#### Technical Features
- **Spring Boot 3.5.x**: Based on the latest Spring Boot framework
- **Reactive Programming**: Using Spring WebFlux and Reactor Core
- **Code Quality**: Integrated Checkstyle, SpotBugs, JaCoCo code quality tools
- **API Documentation**: Automatically generated API documentation using SpringDoc OpenAPI
- **Unit Testing**: Unit tests for core functions including load balancing and health checks

#### Project Structure
- Established clear modular architecture
- Defined unified coding standards and best practices
- Established complete build and testing processes

---

## Upgrade Guide

### Upgrading from 0.3.1 to 0.3.2

#### Configuration Changes
```yaml
# New monitoring configuration
monitoring:
  metrics:
    enabled: true
    categories:
      - system
      - business
      - infrastructure
```

#### Deployment Changes
- Added Prometheus and Grafana containers
- Updated [docker-compose.yml](file://D:\IdeaProjects\model-router\docker-compose.yml) configuration
- Imported new Grafana dashboards

#### Notes
- Monitoring functionality is enabled by default, which may add slight performance overhead
- New monitoring endpoints require corresponding network configuration

### Upgrading from 0.2.1 to 0.3.0

#### Breaking Changes
- Configuration file path change: `./config` → `/app/config`
- Environment variable naming adjustment

#### Migration Steps
1. Update configuration file paths
2. Adjust environment variable names
3. Update deployment scripts and container configurations

### Upgrading from 0.1.0 to 0.2.0

#### New Dependencies
- No additional dependencies required, all features are built-in

#### Configuration Extensions
```yaml
# New rate limiting configuration
model:
  services:
    chat:
      rate-limit:
        type: token-bucket
        capacity: 100
        refill-rate: 10

# New circuit breaker configuration
      circuit-breaker:
        failure-threshold: 5
        recovery-timeout: 30s
        success-threshold: 3
```

## Known Issues

### Current Version (0.3.2)
- In extremely high concurrency scenarios (>10k RPS), monitoring metrics may experience slight delays
- Docker containers may experience slow DNS resolution in certain network environments

### Historical Issues
- ~~0.3.1: Build timeouts in Chinese network environments~~ (Fixed)
- ~~0.2.1: Memory leaks after long-term operation~~ (Fixed)
- ~~0.2.0: Race conditions during configuration updates in high-concurrency scenarios~~ (Fixed)

## Contributors

Thank you to all developers who have contributed to the JAiRouter project:

- **Core Team**: Responsible for architecture design and core feature development
- **Community Contributors**: Provided feature suggestions, bug reports, and code contributions
- **Documentation Team**: Improved project documentation and user guides
- **Testing Team**: Conducted functional testing and performance verification

## Feedback and Suggestions

If you encounter issues or have improvement suggestions during usage, please feel free to provide feedback through the following channels:

- **GitHub Issues**: [Submit issue report](https://github.com/Lincoln-cn/JAiRouter/issues)
- **GitHub Discussions**: [Participate in discussions](https://github.com/Lincoln-cn/JAiRouter/discussions)
- **Email Contact**: jairouter@example.com

We value every piece of feedback and will respond and address them promptly.
