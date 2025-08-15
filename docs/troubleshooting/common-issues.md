# 常见问题

本文档收集了 JAiRouter 使用过程中最常见的问题及其解决方案。

## 启动问题

### 问题 1: 应用启动失败

#### 症状
```
Error starting ApplicationContext. To display the conditions report re-run your application with 'debug' enabled.
```

#### 可能原因
1. 端口被占用
2. 配置文件格式错误
3. 依赖冲突
4. Java 版本不兼容

#### 解决方案

**检查端口占用**
```bash
# Windows
netstat -ano | findstr :8080

# Linux/macOS
lsof -i :8080
```

**检查配置文件**
```bash
# 验证 YAML 格式
./mvnw spring-boot:run --debug
```

**检查 Java 版本**
```bash
java -version
# 确保使用 Java 17 或更高版本
```

### 问题 2: 配置文件加载失败

#### 症状
```
Could not resolve placeholder 'model.services.chat.instances[0].name' in value "${model.services.chat.instances[0].name}"
```

#### 解决方案
```yaml
# 确保 application.yml 格式正确
model:
  services:
    chat:
      instances:
        - name: "test-model"
          baseUrl: "http://localhost:9090"
          path: "/v1/chat/completions"
          weight: 1
```

### 问题 3: 内存不足

#### 症状
```
java.lang.OutOfMemoryError: Java heap space
```

#### 解决方案
```bash
# 增加堆内存大小
java -Xms1g -Xmx2g -jar target/model-router-*.jar

# 或设置环境变量
export JAVA_OPTS="-Xms1g -Xmx2g"
```

## 连接问题

### 问题 4: 后端服务连接失败

#### 症状
```
Connection refused: localhost/127.0.0.1:9090
```

#### 诊断步骤
```bash
# 1. 检查后端服务是否运行
curl -v http://localhost:9090/health

# 2. 检查网络连通性
telnet localhost 9090

# 3. 检查防火墙设置
# Windows
netsh advfirewall show allprofiles

# Linux
sudo iptables -L
```

#### 解决方案
1. 确保后端服务正常运行
2. 检查服务配置中的 baseUrl 是否正确
3. 验证网络连通性和防火墙设置

### 问题 5: 请求超时

#### 症状
```
Read timeout on GET http://localhost:9090/v1/chat/completions
```

#### 解决方案
```yaml
# 调整超时配置
spring:
  webflux:
    timeout: 60s

# 或在配置中设置
model:
  services:
    chat:
      timeout: 30s
```

### 问题 6: SSL/TLS 连接问题

#### 症状
```
PKIX path building failed: sun.security.provider.certpath.SunCertPathBuilderException
```

#### 解决方案
```bash
# 1. 信任所有证书（仅开发环境）
java -Dcom.sun.net.ssl.checkRevocation=false \
     -Dtrust_all_cert=true \
     -jar target/model-router-*.jar

# 2. 导入证书到信任库
keytool -import -alias backend-cert -file backend.crt -keystore $JAVA_HOME/lib/security/cacerts
```

## 性能问题

### 问题 7: 响应时间过长

#### 症状
- API 响应时间超过预期
- 用户体验下降

#### 诊断步骤
```bash
# 1. 检查应用性能指标
curl http://localhost:8080/actuator/metrics/http.server.requests

# 2. 检查 JVM 性能
curl http://localhost:8080/actuator/metrics/jvm.gc.pause

# 3. 检查后端服务响应时间
time curl -X POST http://localhost:9090/v1/chat/completions \
  -H "Content-Type: application/json" \
  -d '{"model":"test","messages":[{"role":"user","content":"hello"}]}'
```

#### 解决方案

**优化连接池配置**
```yaml
spring:
  webflux:
    httpclient:
      pool:
        max-connections: 100
        max-idle-time: 30s
```

**调整负载均衡策略**
```yaml
model:
  services:
    chat:
      load-balance:
        type: least-connections  # 使用最少连接策略
```

### 问题 8: 内存使用过高

#### 症状
- JVM 堆内存持续增长
- 频繁的垃圾回收

#### 诊断步骤
```bash
# 1. 检查内存使用情况
curl http://localhost:8080/actuator/metrics/jvm.memory.used

# 2. 生成堆转储分析
jcmd <pid> GC.run_finalization
jmap -dump:format=b,file=heap.hprof <pid>

# 3. 分析 GC 日志
java -XX:+PrintGC -XX:+PrintGCDetails -Xloggc:gc.log -jar target/model-router-*.jar
```

#### 解决方案

**优化 JVM 参数**
```bash
java -Xms2g -Xmx4g \
     -XX:+UseG1GC \
     -XX:MaxGCPauseMillis=200 \
     -jar target/model-router-*.jar
```

**启用限流器清理**
```yaml
model:
  rate-limit:
    cleanup:
      enabled: true
      interval: 5m
      inactive-threshold: 30m
```

### 问题 9: CPU 使用率过高

#### 症状
- CPU 使用率持续超过 80%
- 系统响应缓慢

#### 诊断步骤
```bash
# 1. 检查线程状态
jstack <pid> > threads.dump

# 2. 分析 CPU 热点
java -XX:+PrintCompilation -jar target/model-router-*.jar

# 3. 使用性能分析工具
java -XX:+FlightRecorder \
     -XX:StartFlightRecording=duration=60s,filename=profile.jfr \
     -jar target/model-router-*.jar
```

#### 解决方案

**优化线程池配置**
```yaml
spring:
  task:
    execution:
      pool:
        core-size: 4
        max-size: 16
        queue-capacity: 100
```

**启用异步处理**
```yaml
model:
  async:
    enabled: true
    thread-pool-size: 8
```

## 配置问题

### 问题 10: 动态配置不生效

#### 症状
- 通过 API 更新配置后不生效
- 配置持久化失败

#### 诊断步骤
```bash
# 1. 检查配置存储状态
curl http://localhost:8080/api/config/store/status

# 2. 检查配置文件权限
ls -la config/

# 3. 查看应用日志
tail -f logs/jairouter-debug.log | grep -i config
```

#### 解决方案

**检查存储配置**
```yaml
model:
  store:
    type: file
    path: config/
    auto-save: true
```

**确保文件权限**
```bash
# 确保应用有写权限
chmod 755 config/
chmod 644 config/*.json
```

### 问题 11: 负载均衡不均匀

#### 症状
- 某些后端实例负载过高
- 其他实例空闲

#### 诊断步骤
```bash
# 1. 检查实例权重配置
curl http://localhost:8080/api/config/instance/type/chat

# 2. 检查实例健康状态
curl http://localhost:8080/actuator/health

# 3. 分析请求分布
curl http://localhost:8080/actuator/metrics/jairouter.requests.total
```

#### 解决方案

**调整权重配置**
```yaml
model:
  services:
    chat:
      instances:
        - name: "model-1"
          baseUrl: "http://server1:9090"
          weight: 2  # 增加权重
        - name: "model-2"
          baseUrl: "http://server2:9090"
          weight: 1
```

**更换负载均衡策略**
```yaml
model:
  services:
    chat:
      load-balance:
        type: round-robin  # 或 least-connections
```

### 问题 12: 限流配置不当

#### 症状
- 正常请求被限流
- 或者限流不起作用

#### 诊断步骤
```bash
# 1. 检查限流配置
curl http://localhost:8080/api/config/rate-limit/status

# 2. 检查限流指标
curl http://localhost:8080/actuator/metrics/jairouter.rate.limit.rejected

# 3. 测试限流行为
for i in {1..20}; do
  curl -w "%{http_code}\n" http://localhost:8080/v1/chat/completions
done
```

#### 解决方案

**调整限流参数**
```yaml
model:
  services:
    chat:
      rate-limit:
        type: token-bucket
        capacity: 100      # 增加容量
        refill-rate: 10    # 增加补充速率
        client-ip-enable: true
```

**检查客户端 IP 获取**
```yaml
server:
  forward-headers-strategy: framework  # 支持代理头
```

## 监控问题

### 问题 13: 监控指标缺失

#### 症状
- Prometheus 无法抓取指标
- Grafana 显示 "No data"

#### 诊断步骤
```bash
# 1. 检查监控端点
curl http://localhost:8080/actuator/prometheus

# 2. 检查 Prometheus 配置
curl http://prometheus:9090/api/v1/targets

# 3. 检查网络连通性
telnet localhost 8080
```

#### 解决方案

**启用监控端点**
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

**检查 Prometheus 配置**
```yaml
# prometheus.yml
scrape_configs:
  - job_name: 'jairouter'
    static_configs:
      - targets: ['jairouter:8080']
    metrics_path: '/actuator/prometheus'
    scrape_interval: 30s
```

### 问题 14: 日志级别过高

#### 症状
- 日志文件过大
- 重要信息被淹没

#### 解决方案
```yaml
# application.yml
logging:
  level:
    org.unreal.modelrouter: INFO
    org.springframework: WARN
    reactor.netty: WARN
  
  # 日志轮转配置
  logback:
    rollingpolicy:
      max-file-size: 100MB
      max-history: 30
```

## 部署问题

### 问题 15: Docker 容器启动失败

#### 症状
```
docker: Error response from daemon: driver failed programming external connectivity
```

#### 解决方案
```bash
# 1. 检查端口冲突
docker ps -a

# 2. 清理无用容器
docker container prune

# 3. 重启 Docker 服务
# Windows/macOS: 重启 Docker Desktop
# Linux:
sudo systemctl restart docker
```

### 问题 16: 容器内存限制

#### 症状
- 容器被 OOM Killer 终止
- 应用异常退出

#### 解决方案
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

## 故障排查工具

### 健康检查脚本
```bash
#!/bin/bash
# health-check.sh

echo "=== JAiRouter 健康检查 ==="

# 检查服务状态
echo "1. 服务状态检查..."
if curl -f http://localhost:8080/actuator/health > /dev/null 2>&1; then
    echo "✅ 服务正常运行"
else
    echo "❌ 服务不可用"
    exit 1
fi

# 检查后端连接
echo "2. 后端连接检查..."
BACKEND_COUNT=$(curl -s http://localhost:8080/api/config/instance/type/chat | jq length)
echo "后端实例数量: $BACKEND_COUNT"

# 检查内存使用
echo "3. 内存使用检查..."
MEMORY_USED=$(curl -s http://localhost:8080/actuator/metrics/jvm.memory.used | jq -r '.measurements[0].value')
MEMORY_MAX=$(curl -s http://localhost:8080/actuator/metrics/jvm.memory.max | jq -r '.measurements[0].value')
MEMORY_USAGE=$(echo "scale=2; $MEMORY_USED / $MEMORY_MAX * 100" | bc)
echo "内存使用率: ${MEMORY_USAGE}%"

if (( $(echo "$MEMORY_USAGE > 85" | bc -l) )); then
    echo "⚠️  内存使用率过高"
fi

echo "=== 健康检查完成 ==="
```

### 日志分析脚本
```bash
#!/bin/bash
# log-analysis.sh

LOG_FILE="logs/jairouter-debug.log"

echo "=== 日志分析 ==="

# 错误统计
echo "1. 错误统计..."
ERROR_COUNT=$(grep -c "ERROR" $LOG_FILE)
echo "错误数量: $ERROR_COUNT"

# 最近的错误
echo "2. 最近的错误..."
grep "ERROR" $LOG_FILE | tail -5

# 性能警告
echo "3. 性能警告..."
grep -i "timeout\|slow\|performance" $LOG_FILE | tail -5

# 连接问题
echo "4. 连接问题..."
grep -i "connection\|refused\|timeout" $LOG_FILE | tail -5

echo "=== 日志分析完成 ==="
```

## 获得帮助

如果以上解决方案无法解决您的问题，请：

1. **查看详细日志**: 启用 DEBUG 级别日志获取更多信息
2. **搜索已知问题**: 在 [GitHub Issues](https://github.com/your-org/jairouter/issues) 中搜索类似问题
3. **提交新问题**: 创建新的 Issue，包含详细的错误信息和环境描述
4. **参与社区讨论**: 在项目讨论区寻求帮助

### 问题报告模板
```markdown
## 问题描述
简要描述遇到的问题

## 环境信息
- Java 版本: 
- 操作系统: 
- JAiRouter 版本: 
- 部署方式: (Docker/直接运行)

## 复现步骤
1. 
2. 
3. 

## 期望行为
描述期望的正常行为

## 实际行为
描述实际发生的异常行为

## 错误日志
```
粘贴相关的错误日志
```

## 其他信息
任何其他可能有用的信息
```