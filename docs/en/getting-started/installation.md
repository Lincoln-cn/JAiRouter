# Installation Guide

<!-- 版本信息 -->
> **文档版本**: 1.0.0  
> **最后更新**: 2025-08-19  
> **Git 提交**: c1aa5b0f  
> **作者**: Lincoln
<!-- /版本信息 -->



This guide will walk you through installing and setting up JAiRouter in your environment.

## System Requirements

- **Java**: 17 or higher
- **Memory**: Minimum 512MB RAM (1GB recommended for production)
- **Storage**: At least 100MB free disk space
- **Network**: Access to your AI model services

## Installation Methods

### Method 1: Download Pre-built JAR (Recommended)

1. Download the latest release from [GitHub Releases](https://github.com/Lincoln-cn/JAiRouter/releases)
2. Extract the archive to your desired directory
3. Run the application:

```bash
java -jar jairouter-*.jar
```

### Method 2: Build from Source

1. Clone the repository:
```bash
git clone https://github.com/Lincoln-cn/JAiRouter.git
cd JAiRouter
```

2. Build the project:
```bash
# Windows
./mvnw.cmd clean package

# Linux/macOS
./mvnw clean package
```

3. Run the application:
```bash
java -jar target/model-router-*.jar
```

### Method 3: Docker Deployment

1. Pull the Docker image:
```bash
docker pull jairouter/model-router:latest
```

2. Run the container:
```bash
docker run -d \
  --name jairouter \
  -p 8080:8080 \
  -v ./config:/app/config \
  jairouter/model-router:latest
```

## Configuration

### Basic Configuration

Create a configuration file `application.yml`:

```yaml
server:
  port: 8080

model:
  services:
    chat:
      load-balance:
        type: round-robin
      instances:
        - name: "qwen2.5:7b"
          baseUrl: "http://localhost:11434"
          path: "/v1/chat/completions"
          weight: 1
```

### Environment Variables

You can override configuration using environment variables:

```bash
export SERVER_PORT=8080
export MODEL_SERVICES_CHAT_LOAD_BALANCE_TYPE=round-robin
```

### Configuration File Location

JAiRouter looks for configuration files in the following order:

1. `./config/application.yml` (current directory)
2. `./application.yml` (current directory)
3. `classpath:application.yml` (embedded in JAR)

## Verification

After starting JAiRouter, verify the installation:

1. **Health Check**:
```bash
curl http://localhost:8080/actuator/health
```

2. **API Documentation**:
Visit `http://localhost:8080/swagger-ui/index.html` in your browser

3. **Service Status**:
```bash
curl http://localhost:8080/api/config/instance/type/chat
```

## Troubleshooting

### Common Issues

**Port Already in Use**:
```bash
# Change the port in application.yml
server:
  port: 8081
```

**Java Version Issues**:
```bash
# Check Java version
java -version

# Should show Java 17 or higher
```

**Memory Issues**:
```bash
# Increase heap size
java -Xmx1g -jar jairouter-*.jar
```

### Log Files

Check the application logs for detailed error information:

```bash
# View logs in real-time
tail -f logs/jairouter.log

# Search for errors
grep ERROR logs/jairouter.log
```

## Next Steps

Now that JAiRouter is installed and running, proceed to the [Quick Start Guide](quick-start.md) to make your first API call.