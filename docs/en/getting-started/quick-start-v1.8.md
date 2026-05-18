# JAiRouter v1.8.1 Quick Start Guide

> **Document Version**: 1.0.1
> **Last Updated**: 2026-04-16
> **Applicable Version**: v1.8.0+

---

## 📋 Overview

This guide explains how to quickly deploy and start JAiRouter v1.8.0+, with a focus on using the key generation tool to create secure JWT keys and admin passwords.

### What's New in v1.8.0

- ✅ **Key Generation Tool** - Generate secure JWT keys and passwords from command line
- ✅ **Key Strength Check** - Automatic key strength verification at startup
- ✅ **Configuration Validation** - Critical configuration validation at startup
- ✅ **Enhanced Audit Logging** - Backup logging channel to ensure no data loss

---

## 🚀 Quick Start

### Step 1: Pull the Image

```bash
# Pull the latest image
docker pull sodlinken/jairouter:latest

# Or pull a specific version
docker pull sodlinken/jairouter:v1.8.1
```

### Step 2: Generate Secure Keys (Recommended for v1.8.0+)

**v1.8.0+ includes a new key generation tool** that supports automatic generation of secure JWT keys and admin passwords.

#### Option 1: Use Docker to Run Key Generation Tool (Recommended)

```bash
# Generate JWT key (Base64 encoded)
docker run --rm sodlinken/jairouter:latest java -jar /app/modelrouter.jar --generate-key

# Example output:
# ╔══════════════════════════════════════════════════════════════════════════════╗
# ║                         JAiRouter Key Generation Tool                        ║
# ╚══════════════════════════════════════════════════════════════════════════════╝
#
# 【JWT Key Generation】
# Base64 encoded (recommended for JWT HS256):
#   cGFzc3dvcmQtdGVzdC1rZXktZm9yLWphb3V0ZXItMjAyNg==
# Key strength: Very Strong
# Usage:
#   export JWT_SECRET="cGFzc3dvcmQtdGVzdC1rZXktZm9yLWphb3V0ZXItMjAyNg=="

# Generate admin password
docker run --rm sodlinken/jairouter:latest java -jar /app/modelrouter.jar --generate-password

# Example output:
# 【Random Password Generation】
# 16-character password: aB3dEfGhIjKlMnOp
#   Password strength: Strong
#
# 20-character password: xYz123AbC456DeF789Gh
#   Password strength: Very Strong
```

#### Option 2: Use Online Tools (No JAR Required)

If you don't have a local Java environment, you can use online tools or system commands:

```bash
# Generate Base64 encoded JWT key (at least 32 bytes)
# Using OpenSSL
openssl rand -base64 32

# Or using Python
python3 -c "import secrets; print(secrets.token_urlsafe(32))"

# Generate random password (16 characters, alphanumeric)
openssl rand -base64 24 | tr -dc 'A-Za-z0-9' | head -c 16

# Generate random password (with special characters)
openssl rand -base64 24 | tr -dc 'A-Za-z0-9!@#$%^&*' | head -c 16
```

#### Option 3: Use Local Java Environment

If you have a local Java environment, download the JAR and run:

```bash
# Download JAR from GitHub Release
wget https://github.com/Lincoln-cn/JAiRouter/releases/download/v1.8.1/model-router-1.8.1.jar

# Generate JWT key
java -jar model-router-1.8.1.jar --generate-key

# Generate admin password
java -jar model-router-1.8.1.jar --generate-password
```

### Step 3: Set Environment Variables

```bash
# Set JWT key (use the generated key)
export JWT_SECRET="cGFzc3dvcmQtdGVzdC1rZXktZm9yLWphb3V0ZXItMjAyNg=="

# Set admin password (use the generated password)
export INITIAL_ADMIN_PASSWORD="MyStr0ng!Pass#2026"
```

### Step 4: Run the Container

#### Production Environment (Recommended)

```bash
docker run -d \
  --name jairouter \
  -p 8080:8080 \
  -e SPRING_PROFILES_ACTIVE=prod \
  -e JWT_SECRET="$JWT_SECRET" \
  -e INITIAL_ADMIN_PASSWORD="$INITIAL_ADMIN_PASSWORD" \
  -v $(pwd)/config:/app/config:ro \
  -v $(pwd)/logs:/app/logs \
  -v $(pwd)/data:/app/data \
  --restart unless-stopped \
  sodlinken/jairouter:latest
```

#### Development Environment

```bash
docker run -d \
  --name jairouter-dev \
  -p 8080:8080 \
  -p 5005:5005 \
  -e SPRING_PROFILES_ACTIVE=dev \
  -e JWT_SECRET="your-very-strong-jwt-secret-key-at-least-32-characters-long" \
  -e JAVA_OPTS="-Xms256m -Xmx512m -agentlib:jdwp=transport=dt_socket,server=y,suspend=n" \
  sodlinken/jairouter:dev
```

#### Docker Compose (Recommended)

```yaml
version: '3.8'
services:
  jairouter:
    image: sodlinken/jairouter:latest
    container_name: jairouter
    ports:
      - "8080:8080"
    environment:
      - SPRING_PROFILES_ACTIVE=prod
      - JWT_SECRET=${JWT_SECRET}
      - INITIAL_ADMIN_PASSWORD=${INITIAL_ADMIN_PASSWORD}
    volumes:
      - ./config:/app/config:ro
      - ./logs:/app/logs
      - ./data:/app/data
    restart: unless-stopped
    healthcheck:
      test: ["CMD", "curl", "-f", "http://localhost:8080/actuator/health"]
      interval: 30s
      timeout: 10s
      retries: 3
```

Start:
```bash
docker-compose up -d
```

### Step 5: Verify Deployment

```bash
# Check container status
docker ps | grep jairouter

# View startup logs
docker logs -f jairouter

# Verify health check
curl http://localhost:8080/actuator/health

# Expected output:
# {"status":"UP"}
```

### Step 6: Access Web Console

Open your browser and visit: http://localhost:8080/admin

**Login Credentials**:
- Username: `admin`
- Password: The value of `$INITIAL_ADMIN_PASSWORD` environment variable
  - **Development default**: `ChangeMeOnFirstStartup123456` (when environment variable not set)
  - **Production**: Must be set via environment variable, otherwise a security warning will be issued at startup
  - **Recommended**: Use the key generation tool to generate a strong password:
    ```bash
    docker run --rm sodlinken/jairouter:latest java -jar /app/modelrouter.jar --generate-password
    ```

---

## 🔒 Security Notes

### Startup Security Check

v1.8.0+ automatically checks key strength at startup:

```
╔══════════════════════════════════════════════════════════════════════════════╗
║                            JAiRouter Security Key Check                       ║
╚══════════════════════════════════════════════════════════════════════════════╝

Checking JWT key configuration...
✓ JWT key strength: Strong - Check passed

Checking admin password configuration...
✓ Admin password strength: Strong - Check passed

╔══════════════════════════════════════════════════════════════════════════════╗
║  ✓ All key and password checks passed                                         ║
╚══════════════════════════════════════════════════════════════════════════════╝
```

### Weak Key Warning

If a weak key or password is detected, a warning will be displayed:

```
╔══════════════════════════════════════════════════════════════════════════════╗
║  ⚠️  Security Warning: Weak key or password detected                          ║
║                                                                              ║
║  Before using in production, please:                                         ║
║  1. Set a strong key: export JWT_SECRET="<random 32+ byte key>"              ║
║  2. Set a strong password: export INITIAL_ADMIN_PASSWORD="<complex password>"║
║  3. Use key generation tool: docker run --rm sodlinken/jairouter:latest java -jar /app/modelrouter.jar --generate-key  ║
╚══════════════════════════════════════════════════════════════════════════════╝
```

---

## 📊 Configuration Validation

v1.8.0+ automatically validates critical configuration at startup:

```
╔══════════════════════════════════════════════════════════════════════════════╗
║                            JAiRouter Configuration Validation                 ║
╚══════════════════════════════════════════════════════════════════════════════╝

✓ [rate-limit-capacity] Rate limit capacity - Passed
✓ [rate-limit-rate] Rate limit rate - Passed
✓ [circuit-breaker-threshold] Circuit breaker failure threshold - Passed
✓ [circuit-breaker-timeout] Circuit breaker timeout - Passed
✓ [jwt-expiration] JWT expiration time - Passed
✓ [server-port] Server port - Passed
✓ [thread-pool-size] Thread pool size - Passed

╔══════════════════════════════════════════════════════════════════════════════╗
║  Configuration Validation Summary                                             ║
║                                                                              ║
║  Total rules: 7                                                              ║
║  Passed: 7                                                                   ║
║  Warnings: 0                                                                 ║
║  Errors: 0                                                                   ║
╚══════════════════════════════════════════════════════════════════════════════╝
```

---

## 🔧 Troubleshooting

### Issue 1: Container Fails to Start

**Check logs**:
```bash
docker logs jairouter
```

**Common causes**:
- Port already in use
- Incorrect configuration file path
- Environment variables not set

### Issue 2: Cannot Access Web Console

**Check firewall**:
```bash
# Check if port is listening
netstat -tlnp | grep 8080

# Check firewall rules
sudo ufw status
```

### Issue 3: Key Strength Check Failed

**Regenerate keys**:
```bash
# Using Docker
docker run --rm sodlinken/jairouter:latest java -jar /app/modelrouter.jar --generate-key

# Or using OpenSSL
openssl rand -base64 32
```

**Set environment variable**:
```bash
export JWT_SECRET="generated-key"
```

---

## 📚 Related Documentation

- [Docker Deployment Complete Guide](../deployment/docker.md)
- [Configuration Validation Rules](../configuration/validation-rules.md)

---

**Document Version**: 1.0.1
**Last Updated**: 2026-04-16
