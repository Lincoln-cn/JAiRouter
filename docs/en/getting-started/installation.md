# Installation Guide

<!-- 版本信息 -->
> **Doc Version**: 1.0.2  
> **Last Updated**: 2026-05-21  
> **Git Commit**: 61384b4a  
> **Author**: Lincoln
<!-- /版本信息 -->



This guide provides a detailed introduction to installing and building JAiRouter in different environments.

## Environment Requirements

### System Requirements

| Component | Minimum Version | Recommended Version | Description |
|-----------|-----------------|---------------------|-------------|
| **JDK** | 17 | 21+ | Supports OpenJDK and Oracle JDK |
| **Maven** | 3.6.0 | 3.9+ | Optional, the project includes the Maven Wrapper |
| **Docker** | 20.10 | 24+ | Used for containerized deployment |
| **Memory** | 512MB | 1GB+ | Runtime memory requirements |
| **Disk Space** | 1GB | 2GB+ | Includes space for dependencies and logs |

### Supported Operating Systems

- **Windows**: Windows 10/11, Windows Server 2019+
- **Linux**: Ubuntu 18.04+, CentOS 7+, RHEL 7+, Debian 10+
- **macOS**: macOS 10.15+

## Choosing an Installation Method

JAiRouter offers multiple installation methods. Choose according to your needs:

| Installation Method | Suitable Scenarios | Advantages | Disadvantages |
|---------------------|--------------------|------------|---------------|
| **Docker Deployment** | Production environments, quick experience | Environment isolation, easy deployment | Requires a Docker environment |
| **Traditional Deployment** | Development environments, system integration | Runs directly, easy to debug | Requires configuring the Java environment |
| **Source Code Build** | Development contributions, customization | Full control, customizable | Requires a development environment |

## Docker Installation (Recommended)

Docker installation is the simplest and fastest method and is suitable for most users.

### 1. Install Docker

If you do not have Docker installed yet, refer to the [Docker official installation guide](https://docs.docker.com/get-docker/).

### 2. Pull the Image

```bash
# Pull the latest version
docker pull sodlinken/jairouter:latest

# Or pull a specific version
docker pull sodlinken/jairouter:v0.3.1
```

### 3. Run the Container

```bash
# Basic run
docker run -d \
  --name jairouter \
  -p 8080:8080 \
  sodlinken/jairouter:latest

# Run with a configuration file
docker run -d \
  --name jairouter \
  -p 8080:8080 \
  -v $(pwd)/config:/app/config:ro \
  -v $(pwd)/logs:/app/logs \
  sodlinken/jairouter:latest
```

### 4. Verify the Installation

```bash
# Check the container status
docker ps --filter "name=jairouter"

# View the logs
docker logs jairouter

# Test the API
curl http://localhost:8080/actuator/health
```

## Traditional Installation

The traditional installation method runs the JAR file directly on the system and is suitable for development and debugging.

### 1. Install Java

Make sure JDK 17 or higher is installed on your system:

```bash
# Check the Java version
java -version

# Should show output similar to:
# openjdk version "17.0.2" 2022-01-18
```

If Java is not installed, download it from one of the following channels:
- [OpenJDK](https://openjdk.org/install/)
- [Oracle JDK](https://www.oracle.com/java/technologies/downloads/)
- [Amazon Corretto](https://aws.amazon.com/corretto/)

### 2. Download the JAR File

Download the latest JAR file from [GitHub Releases](https://github.com/Lincoln-cn/JAiRouter/releases):

```bash
# Visit the Releases page to download
# https://github.com/Lincoln-cn/JAiRouter/releases

# Or use the command line (replace VERSION with the actual version number)
wget https://github.com/Lincoln-cn/JAiRouter/releases/download/vVERSION/model-router-VERSION.jar
```

### 3. Run the Application

```bash
# Basic run
java -jar model-router.jar

# Specify a configuration file
java -jar model-router.jar --spring.config.location=classpath:/application.yml

# Specify JVM parameters
java -Xmx1g -Xms512m -jar model-router.jar

# Run in the background
nohup java -jar model-router.jar > jairouter.log 2>&1 &
```

### 4. Verify the Installation

```bash
# Check the process
ps aux | grep model-router

# Test the API
curl http://localhost:8080/actuator/health
```

## Building from Source

Building from source is suitable for developers and users who need customization.

### 1. Clone the Code

```bash
git clone https://github.com/Lincoln-cn/JAiRouter.git
cd jairouter
```

### 2. Choosing a Build Method

JAiRouter offers multiple build methods, optimized for different user groups and network environments:

| Build Method | Target Users | Maven Repository | Build Speed | Recommendation |
|--------------|--------------|------------------|-------------|----------------|
| **China Accelerated** | Chinese users | Alibaba Cloud mirror | Fast | ⭐⭐⭐⭐⭐ |
| **Standard Build** | International users | Maven Central | Normal | ⭐⭐⭐ |
| **Fast Build** | Development and debugging | Skips tests | Fastest | ⭐⭐⭐⭐ |

### 3. China-Specific Build (Recommended)

#### Optimized Features
- **Alibaba Cloud Maven Mirror**: uses `https://maven.aliyun.com/repository/public`
- **Full Repository Support**: mirrored repositories for Central, Spring, Plugin, etc.
- **Automatic Configuration**: built-in settings.xml, no manual configuration required
- **Significant Speedup**: dependency download speed improved 5-10x

#### Build Commands

```bash
# Use the Maven Wrapper (recommended)
./mvnw clean package -Pchina

# Or use the system Maven
mvn clean package -Pchina

# Use the dedicated configuration file
mvn clean package -s settings-china.xml
```

#### Related Configuration Files
```
├── Dockerfile.china              # China-optimized Docker build file
├── settings-china.xml            # Alibaba Cloud Maven mirror configuration
├── scripts/docker-build-china.sh # China-optimized build script
└── pom.xml (china profile)       # Maven China acceleration configuration
```

### 4. Standard Build for International Users

Use the standard Maven Central repository:

```bash
# Use the Maven Wrapper (recommended)
./mvnw clean package

# Or use the system Maven
mvn clean package
```

### 5. Fast Build (Development and Debugging)

Suitable for development environments and quick testing:

```bash
# Skip all checks and tests
./mvnw clean package -Pfast

# Skip tests only
./mvnw clean package -DskipTests

# Skip code quality checks
./mvnw compiler:compile compiler:testCompile surefire:test
```

### 6. Build Performance Comparison

| Build Method | First Build Time | Incremental Build Time | Network Requirements | Suitable Scenarios |
|--------------|------------------|------------------------|----------------------|--------------------|
| **China Accelerated** | 1-2 minutes | 30-60 seconds | China network | Daily development for Chinese users |
| **Standard Build** | 5-10 minutes | 2-3 minutes | International network | Development for international users |
| **Fast Build** | 30-60 seconds | 10-20 seconds | Any | Development and debugging |

### 7. Run the Build Result

```bash
# Run the built JAR file
java -jar target/model-router-*.jar

# Run with a specified configuration file
java -jar target/model-router-*.jar --spring.config.location=classpath:/application.yml

# Specify JVM parameters
java -Xmx1g -Xms512m -jar target/model-router-*.jar
```

## Building Docker Images

If you need to build a custom Docker image:

### 1. Use the Build Scripts (Recommended)

JAiRouter provides build scripts optimized for different user groups:

```bash
# Chinese users (use the Alibaba Cloud mirror, fast build)
./scripts/docker-build-china.sh

# International users (use the standard image)
./scripts/docker-build.sh

# Windows users
.\scripts\docker-build.ps1
```

### 2. Manual Build

#### China-Optimized Build for Chinese Users

```bash
# Use the China-optimized Dockerfile
docker build -f Dockerfile.china -t sodlinken/jairouter:latest .
```

**Dockerfile.china features**:
- Automatically configures the Alibaba Cloud Maven mirror during the build stage
- Builds using the china profile
- Optimized multi-stage build process

#### Standard Build for International Users

```bash
# Use the standard Dockerfile
docker build -t sodlinken/jairouter:latest .
```

### 3. Use Maven Plugins

```bash
# Use the Dockerfile plugin
mvn clean package dockerfile:build -Pdocker

# Use the Jib plugin (no Docker required)
mvn clean package jib:dockerBuild -Pjib

# Chinese users use the Jib plugin
mvn clean package jib:dockerBuild -Pjib,china
```

### 4. Detailed Build Process

The Docker build includes the following stages:

1. **Preparation stage**: Copy the source code and configuration files
2. **Dependency download**: Download dependencies from the configured Maven repository
3. **Compile and build**: Compile the Java code and package it
4. **Image packaging**: Create the final runtime image

### 5. Build Optimization Suggestions

#### For Chinese Users
- Prefer using `./scripts/docker-build-china.sh`
- Configure the local Docker to use a domestic mirror for acceleration
- Use multi-stage builds to reduce image size

#### For International Users
- Use the standard build script
- Configure a Docker proxy (if needed)
- Leverage Docker layer caching to improve build speed

## Development Environment Installation

The development environment requires additional tools and configuration.

### 1. IDE Configuration

The following IDEs are recommended:
- **IntelliJ IDEA**: Recommended, built-in Spring Boot support
- **Eclipse**: Requires installing Spring Tools Suite
- **VS Code**: Requires installing the Java and Spring Boot extensions

### 2. Development Tools

```bash
# Install Maven (if not using the Wrapper)
# Ubuntu/Debian
sudo apt install maven

# CentOS/RHEL
sudo yum install maven

# macOS
brew install maven

# Windows
# Download and configure the environment variables
```

### 3. Code Quality Tools

The project integrates multiple code quality tools:

```bash
# Run code checks
./mvnw checkstyle:check

# Run static analysis
./mvnw spotbugs:check

# Generate a coverage report
./mvnw jacoco:report
```

### 4. Run in Development Mode

```bash
# Use the Spring Boot Maven plugin
./mvnw spring-boot:run

# Enable debug mode
./mvnw spring-boot:run -Dspring-boot.run.jvmArguments="-Xdebug -Xrunjdwp:transport=dt_socket,server=y,suspend=n,address=5005"

# Use the development profile
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
```

## Troubleshooting

### Common Issues

#### 1. Incompatible Java Version

```bash
# Error message: UnsupportedClassVersionError
# Solution: upgrade to JDK 17+

# Check the Java version
java -version
javac -version

# Set JAVA_HOME
export JAVA_HOME=/path/to/jdk17
```

#### 2. Port Already in Use

```bash
# Error message: Port 8080 was already in use
# Solution: change the port or stop the process occupying it

# Find the process occupying the port
netstat -tulpn | grep 8080
lsof -i :8080

# Change the port
java -jar model-router.jar --server.port=8081
```

#### 3. Insufficient Memory

```bash
# Error message: OutOfMemoryError
# Solution: increase JVM memory

# Set memory parameters
java -Xmx2g -Xms1g -jar model-router.jar
```

#### 4. Dependency Download Failure

```bash
# Chinese users use the Alibaba Cloud mirror
./mvnw clean package -Pchina

# Or configure a proxy
./mvnw clean package -Dhttp.proxyHost=proxy.example.com -Dhttp.proxyPort=8080
```

#### 5. Docker Build Failure

```bash
# Check the Docker version
docker --version

# Clean the Docker cache
docker system prune -a

# Rebuild
docker build --no-cache -t sodlinken/jairouter:latest .
```

### Getting Help

If you encounter other problems, please:

1. Check the [Troubleshooting documentation](../troubleshooting/index.md)
2. Search [GitHub Issues](https://github.com/Lincoln-cn/JAiRouter/issues)
3. Submit a new [Issue](https://github.com/Lincoln-cn/JAiRouter/issues/new)

## Troubleshooting {#troubleshooting}

### Common Issues

#### 1. Port Already in Use

**Problem**: `Port 8080 was already in use`

**Solution**:
```bash
# Find the process occupying the port
netstat -tulpn | grep 8080
lsof -i :8080

# Change the port
java -jar model-router.jar --server.port=8081
```

#### 2. Incompatible Java Version

**Problem**: `Unsupported class file major version`

**Solution**:
```bash
# Check the Java version
java -version

# Install JDK 17+
# Ubuntu/Debian
apt-get install openjdk-17-jdk

# CentOS/RHEL
yum install java-17-openjdk
```

#### 3. Insufficient Memory

**Problem**: `OutOfMemoryError`

**Solution**:
```bash
# Increase JVM memory
java -Xmx2g -Xms1g -jar model-router.jar
```

#### 4. Dependency Download Failure

**Problem**: Maven dependency download times out or fails

**Solution**:
```bash
# Chinese users use the Alibaba Cloud mirror
./mvnw clean package -Pchina

# Or configure a proxy
./mvnw clean package -Dhttp.proxyHost=proxy.example.com -Dhttp.proxyPort=8080
```

#### 5. Docker Build Failure

**Problem**: Docker image build fails

**Solution**:
```bash
# Check the Docker version
docker --version

# Clean the Docker cache
docker system prune -a

# Rebuild
docker build --no-cache -t sodlinken/jairouter:latest .
```

## Next Steps

After installation, you can:

1. **[Quick Start](quick-start.md)** - Experience JAiRouter in 5 minutes
2. **[First Steps](first-steps.md)** - Configure your first AI service
3. **[Configuration Guide](../configuration/index.md)** - Detailed configuration instructions
