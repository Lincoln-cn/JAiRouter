# 常见问题解答 (FAQ)

<!-- 版本信息 -->
> **文档版本**: 1.0.0  
> **最后更新**: 2025-08-19  
> **Git 提交**: c1aa5b0f  
> **作者**: Lincoln
<!-- /版本信息 -->



本文档收集了用户在使用 JAiRouter 过程中最常遇到的问题和解答。

## 基础问题

### Q1: JAiRouter 是什么？

**A**: JAiRouter 是一个基于 Spring Boot 的 AI 模型服务路由和负载均衡网关。它提供统一的 OpenAI 兼容 API 接口，支持多种后端 AI 服务，包括 GPUStack、Ollama、VLLM、OpenAI 等。主要功能包括负载均衡、限流、熔断、健康检查和动态配置管理。

### Q2: JAiRouter 支持哪些 AI 服务？

**A**: 目前支持以下 AI 服务：
- **GPUStack**: GPU 集群管理平台
- **Ollama**: 本地大语言模型运行平台
- **VLLM**: 高性能 LLM 推理引擎
- **Xinference**: 模型推理服务框架
- **LocalAI**: 本地 AI 模型服务
- **OpenAI**: OpenAI 官方 API

未来还将支持 Anthropic Claude、Google Gemini、Cohere 等更多服务。

### Q3: JAiRouter 与其他 API 网关有什么区别？

**A**: JAiRouter 专门为 AI 模型服务设计，具有以下特色：
- **AI 专用**: 针对 AI 模型服务的特殊需求优化
- **OpenAI 兼容**: 提供标准的 OpenAI API 格式
- **智能路由**: 支持多种负载均衡策略
- **模型感知**: 理解不同 AI 模型的特性和能力
- **成本优化**: 支持基于成本的路由策略
- **易于使用**: 专门为 AI 开发者设计的简单配置

### Q4: JAiRouter 是否免费？

**A**: 是的，JAiRouter 是完全开源免费的项目，采用 MIT 许可证。您可以自由使用、修改和分发。我们也在考虑提供企业级的商业支持服务。

## 安装和部署

### Q5: JAiRouter 的系统要求是什么？

**A**: 最低系统要求：
- **Java**: 17 或更高版本
- **内存**: 最少 512MB，推荐 2GB+
- **CPU**: 最少 1 核，推荐 2 核+
- **存储**: 最少 1GB 可用空间
- **操作系统**: 支持 Linux、Windows、macOS

### Q6: 如何快速开始使用 JAiRouter？

**A**: 最简单的方式是使用 Docker：

```bash
# 拉取镜像
docker pull sodlinken/jairouter:latest

# 启动服务
docker run -d -p 8080:8080 \
  -v ./config:/app/config \
  sodlinken/jairouter:latest
```

或者从源码构建：

```bash
# 克隆项目
git clone https://github.com/Lincoln-cn/JAiRouter.git
cd jairouter

# 构建运行
./mvnw clean package
java -jar target/model-router-*.jar
```

### Q7: 如何配置后端 AI 服务？

**A**: 在 `application.yml` 中配置：

```yaml
model:
  services:
    chat:
      instances:
        - name: "ollama-llama2"
          baseUrl: "http://localhost:11434"
          path: "/v1/chat/completions"
          weight: 1
        - name: "openai-gpt4"
          baseUrl: "https://api.openai.com"
          path: "/v1/chat/completions"
          weight: 2
```

### Q8: 支持哪些部署方式？

**A**: JAiRouter 支持多种部署方式：
- **直接运行**: Java JAR 包直接运行
- **Docker 容器**: 单容器部署
- **Docker Compose**: 多容器编排部署
- **Kubernetes**: 云原生部署（计划中）
- **云平台**: AWS、Azure、GCP 等云平台部署

## 功能使用

### Q9: 如何配置负载均衡策略？

**A**: 在配置文件中指定负载均衡类型：

```yaml
model:
  services:
    chat:
      load-balance:
        type: round-robin  # 可选: random, round-robin, least-connections, ip-hash
```

各策略特点：
- **random**: 随机选择，简单高效
- **round-robin**: 轮询调度，分布均匀
- **least-connections**: 最少连接，适合长连接
- **ip-hash**: IP 哈希，会话保持

### Q10: 如何设置限流？

**A**: 配置限流参数：

```yaml
model:
  services:
    chat:
      rate-limit:
        type: token-bucket
        capacity: 100        # 桶容量
        refill-rate: 10      # 补充速率（每秒）
        client-ip-enable: true  # 启用客户端 IP 独立限流
```

支持的限流算法：
- **token-bucket**: 令牌桶，支持突发流量
- **leaky-bucket**: 漏桶，平滑限流
- **sliding-window**: 滑动窗口，精确控制
- **warm-up**: 预热限流，逐步增加

### Q11: 熔断器如何工作？

**A**: 熔断器配置示例：

```yaml
model:
  services:
    chat:
      circuit-breaker:
        failure-threshold: 5      # 失败阈值
        recovery-timeout: 30s     # 恢复超时
        success-threshold: 3      # 成功阈值
```

熔断器有三种状态：
- **CLOSED**: 正常状态，请求正常通过
- **OPEN**: 熔断状态，直接返回错误
- **HALF_OPEN**: 半开状态，尝试恢复

### Q12: 如何动态更新配置？

**A**: 使用 REST API 动态更新：

```bash
# 添加实例
curl -X POST http://localhost:8080/api/config/instance/add/chat \
  -H "Content-Type: application/json" \
  -d '{
    "name": "new-model",
    "baseUrl": "http://new-server:9090",
    "path": "/v1/chat/completions",
    "weight": 1
  }'

# 更新实例
curl -X PUT http://localhost:8080/api/config/instance/update/chat \
  -H "Content-Type: application/json" \
  -d '{
    "instanceId": "new-model@http://new-server:9090",
    "instance": {
      "name": "new-model",
      "baseUrl": "http://new-server:9090",
      "path": "/v1/chat/completions",
      "weight": 2
    }
  }'
```

## 监控和运维

### Q13: 如何监控 JAiRouter 的运行状态？

**A**: JAiRouter 提供多种监控方式：

**健康检查**:
```bash
curl http://localhost:8080/actuator/health
```

**Prometheus 指标**:
```bash
curl http://localhost:8080/actuator/prometheus
```

**关键指标**:
- 请求总数和成功率
- 响应时间分布
- 限流和熔断统计
- JVM 内存和 GC 指标
- 系统 CPU 和内存使用

### Q14: 如何查看日志？

**A**: 日志文件位置：
- **容器部署**: `/app/logs/jairouter-debug.log`
- **直接运行**: `./logs/jairouter-debug.log`

查看实时日志：
```bash
# Docker 容器
docker logs -f jairouter

# 直接运行
tail -f logs/jairouter-debug.log
```

调整日志级别：
```bash
curl -X POST http://localhost:8080/actuator/loggers/org.unreal.modelrouter \
  -H "Content-Type: application/json" \
  -d '{"configuredLevel": "DEBUG"}'
```

### Q15: 如何备份和恢复配置？

**A**: 配置文件备份：

```bash
# 备份配置目录
cp -r config/ config-backup-$(date +%Y%m%d)

# 使用 API 备份
curl -X POST http://localhost:8080/api/config/merge/backup
```

恢复配置：
```bash
# 恢复配置文件
cp -r config-backup-20250115/ config/

# 重启服务使配置生效
docker restart jairouter
```

## 性能和优化

### Q16: JAiRouter 的性能如何？

**A**: 性能指标（基于标准测试环境）：
- **吞吐量**: 1000+ RPS
- **延迟**: P95 < 100ms
- **并发**: 支持 1000+ 并发连接
- **内存**: 基础运行约 200MB

实际性能取决于：
- 硬件配置
- 后端服务性能
- 网络延迟
- 配置参数

### Q17: 如何优化性能？

**A**: 性能优化建议：

**JVM 调优**:
```bash
java -Xms2g -Xmx4g \
     -XX:+UseG1GC \
     -XX:MaxGCPauseMillis=200 \
     -jar target/model-router-*.jar
```

**连接池优化**:
```yaml
spring:
  webflux:
    httpclient:
      pool:
        max-connections: 200
        max-idle-time: 30s
```

**缓存配置**:
```yaml
model:
  cache:
    enabled: true
    ttl: 300s
    max-size: 1000
```

### Q18: 如何处理高并发场景？

**A**: 高并发优化策略：

1. **水平扩展**: 部署多个 JAiRouter 实例
2. **负载均衡**: 使用 Nginx 或云负载均衡器
3. **连接池**: 增加连接池大小
4. **缓存**: 启用响应缓存
5. **限流**: 合理设置限流参数
6. **监控**: 实时监控性能指标

## 故障排查

### Q19: 服务启动失败怎么办？

**A**: 常见启动问题和解决方案：

**端口被占用**:
```bash
# 检查端口占用
netstat -tlnp | grep :8080
# 修改端口或停止占用进程
```

**配置文件错误**:
```bash
# 验证 YAML 格式
./mvnw spring-boot:run --debug
```

**Java 版本不兼容**:
```bash
# 检查 Java 版本
java -version
# 确保使用 Java 17+
```

### Q20: 后端服务连接失败怎么办？

**A**: 连接问题排查步骤：

1. **检查服务状态**:
```bash
curl -v http://backend-server:9090/health
```

2. **检查网络连通性**:
```bash
telnet backend-server 9090
```

3. **检查配置**:
```bash
curl http://localhost:8080/api/config/instance/type/chat
```

4. **查看日志**:
```bash
grep -i "connection\|timeout" logs/jairouter-debug.log
```

### Q21: 性能问题如何排查？

**A**: 性能问题诊断：

**检查系统资源**:
```bash
# CPU 使用率
curl http://localhost:8080/actuator/metrics/system.cpu.usage

# 内存使用
curl http://localhost:8080/actuator/metrics/jvm.memory.used

# GC 情况
curl http://localhost:8080/actuator/metrics/jvm.gc.pause
```

**分析响应时间**:
```bash
# 响应时间分布
curl http://localhost:8080/actuator/metrics/jairouter.request.duration

# 请求统计
curl http://localhost:8080/actuator/metrics/jairouter.requests.total
```

## 开发和集成

### Q22: 如何集成到现有项目？

**A**: 集成方式：

**作为代理服务**:
```python
# Python 示例
import openai

# 将 OpenAI 客户端指向 JAiRouter
openai.api_base = "http://jairouter:8080/v1"
openai.api_key = "your-api-key"

response = openai.ChatCompletion.create(
    model="gpt-3.5-turbo",
    messages=[{"role": "user", "content": "Hello!"}]
)
```

**作为负载均衡器**:
```javascript
// Node.js 示例
const axios = require('axios');

const response = await axios.post('http://jairouter:8080/v1/chat/completions', {
  model: 'gpt-3.5-turbo',
  messages: [{ role: 'user', content: 'Hello!' }]
}, {
  headers: {
    'Content-Type': 'application/json',
    'Authorization': 'Bearer your-api-key'
  }
});
```

### Q23: 如何开发自定义适配器？

**A**: 自定义适配器开发：

```java
@Component
public class CustomAdapter extends BaseAdapter {
    
    @Override
    public Mono<String> processRequest(String serviceType, String requestBody, ServiceInstance instance) {
        return webClient.post()
            .uri(instance.getBaseUrl() + instance.getPath())
            .bodyValue(transformRequest(requestBody))
            .retrieve()
            .bodyToMono(String.class)
            .map(this::transformResponse);
    }
    
    private String transformRequest(String requestBody) {
        // 请求格式转换逻辑
        return requestBody;
    }
    
    private String transformResponse(String responseBody) {
        // 响应格式转换逻辑
        return responseBody;
    }
}
```

### Q24: 如何贡献代码？

**A**: 贡献流程：

1. **Fork 项目**: 在 GitHub 上 Fork 项目
2. **创建分支**: `git checkout -b feature/your-feature`
3. **开发功能**: 编写代码和测试
4. **提交代码**: `git commit -m "feat: add new feature"`
5. **推送分支**: `git push origin feature/your-feature`
6. **创建 PR**: 在 GitHub 上创建 Pull Request

详细信息请参考 [贡献指南](../development/contributing.md)。

## 商业使用

### Q25: 可以商业使用吗？

**A**: 是的，JAiRouter 采用 MIT 许可证，允许商业使用。您可以：
- 在商业项目中使用
- 修改和定制功能
- 重新分发（需保留许可证）
- 提供基于 JAiRouter 的商业服务

### Q26: 是否提供技术支持？

**A**: 目前提供的支持方式：
- **社区支持**: GitHub Issues 和 Discussions
- **文档支持**: 完整的用户文档和 API 文档
- **示例代码**: 丰富的使用示例

我们正在考虑提供企业级的商业技术支持服务。

### Q27: 如何获得企业级功能？

**A**: 企业级功能规划：
- **多租户支持**: 租户隔离和管理
- **高级安全**: 企业级认证和授权
- **专业监控**: 高级监控和分析功能
- **技术支持**: 专业的技术支持服务

如有企业级需求，请联系我们讨论定制开发。

## 其他问题

### Q28: JAiRouter 的未来规划是什么？

**A**: 请查看我们的 [发展路线图](roadmap.md)，主要包括：
- 安全和认证体系完善
- 智能化功能增强
- 云原生支持
- 生态系统建设

### Q29: 如何参与社区？

**A**: 参与方式：
- **GitHub**: 提交 Issue、PR 或参与讨论
- **文档**: 帮助完善文档和翻译
- **测试**: 参与功能测试和反馈
- **推广**: 分享使用经验和案例

### Q30: 遇到问题如何求助？

**A**: 求助渠道：
1. **查看文档**: 首先查看相关文档和 FAQ
2. **搜索 Issues**: 在 GitHub Issues 中搜索类似问题
3. **提交 Issue**: 创建新的 Issue 详细描述问题
4. **社区讨论**: 在 GitHub Discussions 中寻求帮助
5. **邮件联系**: jairouter@example.com

---

## 问题反馈

如果您的问题在此 FAQ 中没有找到答案，欢迎通过以下方式反馈：

- **GitHub Issues**: [提交问题](https://github.com/Lincoln-cn/JAiRouter/issues)
- **GitHub Discussions**: [参与讨论](https://github.com/Lincoln-cn/JAiRouter/discussions)
- **邮件联系**: jairouter@example.com

我们会持续更新 FAQ 内容，为用户提供更好的支持。

**最后更新**: 2025年1月15日