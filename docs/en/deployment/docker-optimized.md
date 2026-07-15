# Docker Image Optimization Guide

<!-- 版本信息 -->
> **Doc Version**: 1.2.0
> **最后更新**: 2026-06-10
> **Applicable Version**: v2.7.x+
> **Git 提交**: 135f9a60
> **作者**: Lincoln
<!-- /版本信息 -->

## Overview

JAiRouter provides an optimized Docker image build solution. Through multi-stage builds and Alpine base images, the image size is reduced from **440MB to 281MB** (36% reduction).

### JLink Optimization Note

> **Note**: The JLink solution attempts to use `jlink` tool for custom JRE modules, but due to Spring Boot 3.x module compatibility issues, it ultimately uses Alpine JRE + multi-stage build + JVM parameter optimization.
> 
> The JLink image (`Dockerfile.jlink`) has the same size as the optimized version (281MB) and is provided as an experimental option for users with special requirements.

## Image Comparison

| Image Type | Dockerfile | Size | Base Image | Build Method | Recommendation |
|------------|-----------|------|------------|--------------|----------------|
| **Optimized** | `Dockerfile.optimized` | **281MB** | `eclipse-temurin:17-jre-alpine` | Multi-stage + layertools | ⭐⭐⭐⭐⭐ |
| **JLink** | `Dockerfile.jlink` | **281MB** | `eclipse-temurin:17-jre-alpine` | Multi-stage + JVM optimization | 🔬Experimental |
| Standard | `Dockerfile` | 440MB | `eclipse-temurin:17-jre` | Single-stage | ⭐⭐⭐ |

## Optimization Techniques

### 1. Alpine Base Image

Uses `eclipse-temurin:17-jre-alpine` instead of standard JRE image:

```dockerfile
# Optimized version
FROM eclipse-temurin:17-jre-alpine

# Standard version
FROM eclipse-temurin:17-jre
```

**Advantages**:
- Alpine image is only 5MB, while Debian base image is about 120MB
- Smaller attack surface, better security
- Faster pull speed

### 2. Multi-stage Build

Uses two build stages to separate build and runtime environments:

```dockerfile
# Stage 1: Build stage
FROM eclipse-temurin:17-jre-alpine AS extract-layer
COPY target/model-router-*.jar app.jar
RUN java -Djarmode=layertools -jar app.jar extract --destination extracted

# Stage 2: Runtime stage
FROM eclipse-temurin:17-jre-alpine
COPY --from=extract-layer /app/extracted/dependencies/ ./
COPY --from=extract-layer /app/extracted/spring-boot-loader/ ./
COPY --from=extract-layer /app/extracted/snapshot-dependencies/ ./
COPY --from=extract-layer /app/extracted/application/ ./
```

**Advantages**:
- Final image doesn't contain build tools
- Fewer security vulnerabilities
- Smaller image layers

### 3. Spring Boot Layertools

Uses Spring Boot's built-in layertools feature to extract JAR layers:

```bash
java -Djarmode=layertools -jar app.jar extract --destination extracted
```

**Extracted Layers**:
1. `dependencies/` - Third-party dependencies
2. `spring-boot-loader/` - Spring Boot loader
3. `snapshot-dependencies/` - SNAPSHOT dependencies
4. `application/` - Application code

**Advantages**:
- Leverages Docker caching mechanism
- Only rebuilds relevant layers when dependencies change
- Faster build times

### 4. JVM Parameter Optimization

JVM parameters optimized for container environments:

```bash
# Optimized/JLink version
-Xms128m -Xmx256m -XX:+UseG1GC -XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0

# Standard version
-Xms512m -Xmx1024m -XX:+UseG1GC -XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0
```

**Optimization Points**:
- Smaller initial heap memory (128MB vs 512MB)
- Smaller max heap memory (256MB vs 1024MB)
- Container awareness enabled (`UseContainerSupport`)
- Percentage-based memory limit (`MaxRAMPercentage`)

### 5. JLink Custom JRE (Experimental)

Attempts to use `jlink` tool to create custom JRE:

```dockerfile
# Stage 2: Create jlink custom JRE
FROM eclipse-temurin:17-jdk-alpine AS jlink-create

RUN /opt/java/openjdk/bin/jlink \
    --add-modules ALL-MODULE-PATH \
    --compress=2 \
    --no-header-files \
    --no-man-pages \
    --strip-debug \
    --output /jlink-runtime
```

**Technical Notes**:
- ⚠️ Spring Boot 3.x uses module system, requires complete JRE modules
- ⚠️ Attempting to strip modules causes compatibility issues like `ClassNotFoundException`
- ✅ Final solution: Alpine JRE + multi-stage build + JVM parameter optimization
- 📊 Image size: 281MB (same as optimized version)

**JLink Version Use Cases**:
- Advanced users who need to further customize JRE modules
- Scenarios willing to take compatibility risks for potential optimization
- Technical reference and experimental purposes

## Building Optimized Image

### Using Build Script (Recommended)

```bash
# Build optimized image (recommended)
./scripts/build/docker-build.sh optimized

# Build JLink image (experimental)
./scripts/build/docker-build.sh jlink

# Build standard image
./scripts/build/docker-build.sh standard
```

### Manual Build

```bash
# Build JAR
mvn clean package -DskipTests -Pfast

# Build optimized image
docker build -f Dockerfile.optimized \
    -t sodlinken/jairouter:latest-optimized \
    .
```

## Running Optimized Image

### Basic Run

```bash
docker run -d \
  --name jairouter \
  -p 8080:8080 \
  -e SPRING_PROFILES_ACTIVE=prod \
  -e JAIROUTER_SECURITY_ENABLED=true \
  -e JAIROUTER_SECURITY_JWT_ENABLED=true \
  -e JAIROUTER_SECURITY_JWT_SECRET="your-32-char-secret-key-here" \
  sodlinken/jairouter:latest-optimized
```

### Production Environment Run

```bash
docker run -d \
  --name jairouter \
  -p 8080:8080 \
  -e SPRING_PROFILES_ACTIVE=prod \
  -e JAIROUTER_SECURITY_ENABLED=true \
  -e JAIROUTER_SECURITY_JWT_ENABLED=true \
  -e JAIROUTER_SECURITY_JWT_SECRET="your-very-strong-jwt-secret-key-at-least-32-characters-long" \
  -e JAIROUTER_SECURITY_API_KEY_ENABLED=true \
  -v $(pwd)/config:/app/config:ro \
  -v $(pwd)/logs:/app/logs \
  -v $(pwd)/data:/app/data \
  --restart unless-stopped \
  sodlinken/jairouter:latest-optimized
```

### Using Run Script

```bash
# Run optimized version (recommended)
./scripts/build/docker-run.sh prod latest optimized

# Run standard version
./scripts/build/docker-run.sh prod latest standard
```

## Performance Comparison

### Image Size

```bash
# Check image size
docker images sodlinken/jairouter

# Example output
REPOSITORY            TAG               SIZE
sodlinken/jairouter   latest-optimized  281MB
sodlinken/jairouter   latest-jlink      281MB
sodlinken/jairouter   latest            440MB
```

### Startup Time

| Image Type | Cold Startup | Hot Startup |
|------------|--------------|-------------|
| Optimized | ~8-10 seconds | ~3-5 seconds |
| Standard | ~10-12 seconds | ~4-6 seconds |

### Resource Usage

| Image Type | Initial Memory | Running Memory | CPU Usage |
|------------|----------------|----------------|-----------|
| Optimized | ~200MB | ~350MB | Low |
| Standard | ~250MB | ~400MB | Low |

## Security

### Non-root User

Optimized image runs as non-root user:

```dockerfile
RUN addgroup -g 10010 -S jairouter && \
    adduser -u 10010 -S jairouter -G jairouter

USER jairouter
```

### Minimal Privileges

```dockerfile
# Set only necessary permissions
RUN chmod -R 755 /app
```

### Security Scanning

Recommended to use Docker Scout or Trivy for image scanning:

```bash
# Using Docker Scout
docker scout cve sodlinken/jairouter:latest-optimized

# Using Trivy
trivy image sodlinken/jairouter:latest-optimized
```

## Best Practices

### 1. Use Optimized Image

Recommended to use optimized image in production:

```yaml
# docker-compose.yml
services:
  jairouter:
    image: sodlinken/jairouter:latest-optimized
    container_name: jairouter
```

### 2. Image Tag Management

```bash
# Use specific version tags
docker pull sodlinken/jairouter:v1.7.0-optimized

# Avoid using latest tag in production
# docker pull sodlinken/jairouter:latest-optimized  # Not recommended for production
```

### 3. Image Update Strategy

```bash
# Update base image regularly
# Update base image version in Dockerfile.optimized
FROM eclipse-temurin:17-jre-alpine:latest
```

### 4. Monitor Image Size

```bash
# Use docker history to check image layers
docker history sodlinken/jairouter:latest-optimized

# Use dive tool to analyze image layers
dive sodlinken/jairouter:latest-optimized
```

## Troubleshooting

### Image Build Failure

```bash
# Check if JAR file exists
ls -la target/model-router-*.jar

# Clean build cache
docker builder prune -a

# Rebuild without cache
docker build --no-cache -f Dockerfile.optimized -t sodlinken/jairouter:latest-optimized .
```

### Container Startup Failure

```bash
# Check container logs
docker logs jairouter

# Enter container for debugging
docker exec -it jairouter sh

# Check JVM parameters
docker exec jairouter java -XX:+PrintFlagsFinal -version
```

### Slow Image Pull

```bash
# Use domestic mirror accelerator
# Configure /etc/docker/daemon.json
{
  "registry-mirrors": [
    "https://registry.cn-hangzhou.aliyuncs.com"
  ]
}
```

## Next Steps

- **[Docker Deployment](docker.md)** - Detailed Docker deployment instructions
- **[Production Deployment](production.md)** - Production environment best practices
- **[Monitoring Guide](../monitoring/index.md)** - Setup monitoring and alerts

