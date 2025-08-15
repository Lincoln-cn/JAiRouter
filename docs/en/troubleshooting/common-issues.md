# Common Issues

This document collects the most common issues encountered when using JAiRouter and their solutions.

## Startup Issues

### Issue 1: Application Startup Failure

#### Symptoms
```
Error starting ApplicationContext. To display the conditions report re-run your application with 'debug' enabled.
```

#### Possible Causes
1. Port is occupied
2. Configuration file format error
3. Dependency conflicts
4. Java version incompatibility

#### Solutions

**Check Port Usage**
```bash
# Windows
netstat -ano | findstr :8080

# Linux/macOS
lsof -i :8080
```

**Check Configuration Files**
```bash
# Validate YAML format
./mvnw spring-boot:run --debug
```

**Check Java Version**
```bash
java -version
# Ensure Java 17 or higher is used
```

### Issue 2: Configuration File Loading Failure

#### Symptoms
```
Could not resolve placeholder 'model.services.chat.instances[0].name' in value "${model.services.chat.instances[0].name}"
```

#### Solutions
```yaml
# Ensure application.yml format is correct
model:
  services:
    chat:
      instances:
        - name: "test-model"
          baseUrl: "http://localhost:9090"
          path: "/v1/chat/completions"
          weight: 1
```

### Issue 3: Insufficient Memory

#### Symptoms
```
java.lang.OutOfMemoryError: Java heap space
```

#### Solutions
```bash
# Increase heap memory size
java -Xms1g -Xmx2g -jar target/model-router-*.jar

# Or set environment variables
export JAVA_OPTS="-Xms1g -Xmx2g"
```

## Connection Issues

### Issue 4: Backend Service Connection Failure

#### Symptoms
```
Connection refused: localhost/127.0.0.1:9090
```

#### Diagnostic Steps
```bash
# 1. Check if backend service is running
curl -v http://localhost:9090/health

# 2. Check network connectivity
telnet localhost 9090

# 3. Check firewall settings
# Windows
netsh advfirewall show allprofiles

# Linux
sudo iptables -L
```

#### Solutions
1. Ensure backend service is running normally
2. Check if baseUrl in service configuration is correct
3. Verify network connectivity and firewall settings

### Issue 5: Request Timeout

#### Symptoms
```
Read timeout on GET http://localhost:9090/v1/chat/completions
```

#### Solutions
```yaml
# Adjust timeout configuration
spring:
  webflux:
    timeout: 60s

# Or set in configuration
model:
  services:
    chat:
      timeout: 30s
```

### Issue 6: SSL/TLS Connection Issues

#### Symptoms
```
PKIX path building failed: sun.security.provider.certpath.SunCertPathBuilderException
```

#### Solutions
```bash
# 1. Trust all certificates (development environment only)
java -Dcom.sun.net.ssl.checkRevocation=false \
     -Dtrust_all_cert=true \
     -jar target/model-router-*.jar

# 2. Import certificate to trust store
keytool -import -alias backend-cert -file backend.crt -keystore $JAVA_HOME/lib/security/cacerts
```

## Performance Issues

### Issue 7: Long Response Time

#### Symptoms
- API response time exceeds expectations
- Degraded user experience

#### Diagnostic Steps
```bash
# 1. Check application performance metrics
curl http://localhost:8080/actuator/metrics/http.server.requests

# 2. Check JVM performance
curl http://localhost:8080/actuator/metrics/jvm.gc.pause

# 3. Check backend service response time
time curl -X POST http://localhost:9090/v1/chat/completions \
  -H "Content-Type: application/json" \
  -d '{"model":"test","messages":[{"role":"user","content":"hello"}]}'
```

#### Solutions

**Optimize Connection Pool Configuration**
```yaml
spring:
  webflux:
    httpclient:
      pool:
        max-connections: 100
        max-idle-time: 30s
```

**Adjust Load Balancing Strategy**
```yaml
model:
  services:
    chat:
      load-balance:
        type: least-connections  # Use least connections strategy
```

### Issue 8: High Memory Usage

#### Symptoms
- JVM heap memory continuously growing
- Frequent garbage collection

#### Diagnostic Steps
```bash
# 1. Check memory usage
curl http://localhost:8080/actuator/metrics/jvm.memory.used

# 2. Generate heap dump for analysis
jcmd <pid> GC.run_finalization
jmap -dump:format=b,file=heap.hprof <pid>

# 3. Analyze GC logs
java -XX:+PrintGC -XX:+PrintGCDetails -Xloggc:gc.log -jar target/model-router-*.jar
```

#### Solutions

**Optimize JVM Parameters**
```bash
java -Xms2g -Xmx4g \
     -XX:+UseG1GC \
     -XX:MaxGCPauseMillis=200 \
     -jar target/model-router-*.jar
```

**Enable Rate Limiter Cleanup**
```yaml
model:
  rate-limit:
    cleanup:
      enabled: true
      interval: 5m
      inactive-threshold: 30m
```

### Issue 9: High CPU Usage

#### Symptoms
- CPU usage consistently above 80%
- Slow system response

#### Diagnostic Steps
```bash
# 1. Check thread status
jstack <pid> > threads.dump

# 2. Analyze CPU hotspots
java -XX:+PrintCompilation -jar target/model-router-*.jar

# 3. Use performance analysis tools
java -XX:+FlightRecorder \
     -XX:StartFlightRecording=duration=60s,filename=profile.jfr \
     -jar target/model-router-*.jar
```

#### Solutions

**Optimize Thread Pool Configuration**
```yaml
spring:
  task:
    execution:
      pool:
        core-size: 4
        max-size: 16
        queue-capacity: 100
```

**Enable Asynchronous Processing**
```yaml
model:
  async:
    enabled: true
    thread-pool-size: 8
```

## Configuration Issues

### Issue 10: Dynamic Configuration Not Taking Effect

#### Symptoms
- Configuration updates via API not taking effect
- Configuration persistence failure

#### Diagnostic Steps
```bash
# 1. Check configuration storage status
curl http://localhost:8080/api/config/store/status

# 2. Check configuration file permissions
ls -la config/

# 3. View application logs
tail -f logs/jairouter-debug.log | grep -i config
```

#### Solutions

**Check Storage Configuration**
```yaml
model:
  store:
    type: file
    path: config/
    auto-save: true
```

**Ensure File Permissions**
```bash
# Ensure application has write permissions
chmod 755 config/
chmod 644 config/*.json
```

### Issue 11: Uneven Load Balancing

#### Symptoms
- Some backend instances have high load
- Other instances are idle

#### Diagnostic Steps
```bash
# 1. Check instance weight configuration
curl http://localhost:8080/api/config/instance/type/chat

# 2. Check instance health status
curl http://localhost:8080/actuator/health

# 3. Analyze request distribution
curl http://localhost:8080/actuator/metrics/jairouter.requests.total
```

#### Solutions

**Adjust Weight Configuration**
```yaml
model:
  services:
    chat:
      instances:
        - name: "model-1"
          baseUrl: "http://server1:9090"
          weight: 2  # Increase weight
        - name: "model-2"
          baseUrl: "http://server2:9090"
          weight: 1
```

**Change Load Balancing Strategy**
```yaml
model:
  services:
    chat:
      load-balance:
        type: round-robin  # Or least-connections
```

### Issue 12: Improper Rate Limiting Configuration

#### Symptoms
- Normal requests being rate limited
- Or rate limiting not working

#### Diagnostic Steps
```bash
# 1. Check rate limiting configuration
curl http://localhost:8080/api/config/rate-limit/status

# 2. Check rate limiting metrics
curl http://localhost:8080/actuator/metrics/jairouter.rate.limit.rejected

# 3. Test rate limiting behavior
for i in {1..20}; do
  curl -w "%{http_code}\n" http://localhost:8080/v1/chat/completions
done
```

#### Solutions

**Adjust Rate Limiting Parameters**
```yaml
model:
  services:
    chat:
      rate-limit:
        type: token-bucket
        capacity: 100      # Increase capacity
        refill-rate: 10    # Increase refill rate
        client-ip-enable: true
```

**Check Client IP Retrieval**
```yaml
server:
  forward-headers-strategy: framework  # Support proxy headers
```

## Monitoring Issues

### Issue 13: Missing Monitoring Metrics

#### Symptoms
- Prometheus cannot scrape metrics
- Grafana shows "No data"

#### Diagnostic Steps
```bash
# 1. Check monitoring endpoint
curl http://localhost:8080/actuator/prometheus

# 2. Check Prometheus configuration
curl http://prometheus:9090/api/v1/targets

# 3. Check network connectivity
telnet localhost 8080
```

#### Solutions

**Enable Monitoring Endpoints**
```yaml
management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics,prometheus
  endpoint:
    prometheus:
      enabled: true
```

**Check Prometheus Configuration**
```yaml
# prometheus.yml
scrape_configs:
  - job_name: 'jairouter'
    static_configs:
      - targets: ['jairouter:8080']
    metrics_path: '/actuator/prometheus'
    scrape_interval: 30s
```

### Issue 14: Excessive Log Level

#### Symptoms
- Log files too large
- Important information buried

#### Solutions
```yaml
# application.yml
logging:
  level:
    org.unreal.modelrouter: INFO
    org.springframework: WARN
    reactor.netty: WARN
  
  # Log rotation configuration
  logback:
    rollingpolicy:
      max-file-size: 100MB
      max-history: 30
```

## Deployment Issues

### Issue 15: Docker Container Startup Failure

#### Symptoms
```
docker: Error response from daemon: driver failed programming external connectivity
```

#### Solutions
```bash
# 1. Check port conflicts
docker ps -a

# 2. Clean up unused containers
docker container prune

# 3. Restart Docker service
# Windows/macOS: Restart Docker Desktop
# Linux:
sudo systemctl restart docker
```

### Issue 16: Container Memory Limitations

#### Symptoms
- Container terminated by OOM Killer
- Application exits abnormally

#### Solutions
```yaml
# docker-compose.yml
services:
  jairouter:
    image: jairouter/model-router:latest
    deploy:
      resources:
        limits:
          memory: 2G
        reservations:
          memory: 1G
    environment:
      - JAVA_OPTS=-Xms1g -Xmx1800m
```

## Troubleshooting Tools

### Health Check Script
```bash
#!/bin/bash
# health-check.sh

echo "=== JAiRouter Health Check ==="

# Check service status
echo "1. Service status check..."
if curl -f http://localhost:8080/actuator/health > /dev/null 2>&1; then
    echo "✅ Service running normally"
else
    echo "❌ Service unavailable"
    exit 1
fi

# Check backend connections
echo "2. Backend connection check..."
BACKEND_COUNT=$(curl -s http://localhost:8080/api/config/instance/type/chat | jq length)
echo "Backend instance count: $BACKEND_COUNT"

# Check memory usage
echo "3. Memory usage check..."
MEMORY_USED=$(curl -s http://localhost:8080/actuator/metrics/jvm.memory.used | jq -r '.measurements[0].value')
MEMORY_MAX=$(curl -s http://localhost:8080/actuator/metrics/jvm.memory.max | jq -r '.measurements[0].value')
MEMORY_USAGE=$(echo "scale=2; $MEMORY_USED / $MEMORY_MAX * 100" | bc)
echo "Memory usage: ${MEMORY_USAGE}%"

if (( $(echo "$MEMORY_USAGE > 85" | bc -l) )); then
    echo "⚠️  High memory usage"
fi

echo "=== Health Check Complete ==="
```

### Log Analysis Script
```bash
#!/bin/bash
# log-analysis.sh

LOG_FILE="logs/jairouter-debug.log"

echo "=== Log Analysis ==="

# Error statistics
echo "1. Error statistics..."
ERROR_COUNT=$(grep -c "ERROR" $LOG_FILE)
echo "Error count: $ERROR_COUNT"

# Recent errors
echo "2. Recent errors..."
grep "ERROR" $LOG_FILE | tail -5

# Performance warnings
echo "3. Performance warnings..."
grep -i "timeout\|slow\|performance" $LOG_FILE | tail -5

# Connection issues
echo "4. Connection issues..."
grep -i "connection\|refused\|timeout" $LOG_FILE | tail -5

echo "=== Log Analysis Complete ==="
```

## Getting Help

If the above solutions don't resolve your issue, please:

1. **Check detailed logs**: Enable DEBUG level logging for more information
2. **Search known issues**: Search for similar issues in [GitHub Issues](https://github.com/Lincoln-cn/JAiRouter/issues)
3. **Submit a new issue**: Create a new Issue with detailed error information and environment description
4. **Participate in community discussions**: Seek help in the project discussion area

### Issue Report Template
```markdown
## Issue Description
Briefly describe the problem encountered

## Environment Information
- Java Version: 
- Operating System: 
- JAiRouter Version: 
- Deployment Method: (Docker/Direct execution)

## Reproduction Steps
1. 
2. 
3. 

## Expected Behavior
Describe the expected normal behavior

## Actual Behavior
Describe the actual abnormal behavior

## Error Logs
```
Paste relevant error logs
```

## Additional Information
Any other potentially useful information
```