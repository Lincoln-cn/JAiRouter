The user wants me to create a comprehensive project summary in markdown format based on the conversation history. Let me analyze the key information from this session:

1. **Overall Goal**: The user wanted to test the Embedding model API in the API Playground, using Ollama running in Docker, and fix the circuit breaker state sharing issue.

2. **Key Knowledge**:
   - Project: JAiRouter - AI model service routing gateway (Spring Boot 3.5.5 WebFlux + Vue 3)
   - Ollama runs in Docker on port 12434
   - Circuit breaker was using instanceUrl as key, causing shared state for same URL instances
   - The fix changes to use instanceId as key
   - Build commands: mvn clean package -DskipTests -Pfast
   - Scripts location: scripts/build/build-and-deploy.sh

3. **Recent Actions**:
   - Tested Ollama embedding API directly (works)
   - Identified circuit breaker sharing issue
   - Fixed CircuitBreakerManager.java to use instanceId instead of instanceUrl
   - Fixed schema.sql to add adapter and headers fields
   - Fixed docker-compose.yml volume mount path
   - Committed code changes

4. **Current Plan**:
   - [DONE] Fix circuit breaker state sharing
   - [DONE] Fix schema.sql
   - [DONE] Fix docker-compose.yml
   - [DONE] Commit code
   - [TODO] Push to remote repository
   - [TODO] Verify the fix works with proper testing

Let me create a comprehensive summary.# JAiRouter 项目会话总结

## Overall Goal
修复熔断器状态共享问题（相同 URL 的不同实例共享同一个熔断器状态），并完成 Ollama 嵌入模型 API 试验场的测试验证。

## Key Knowledge

### 项目信息
- **项目名称**: JAiRouter - AI 模型服务路由网关
- **技术栈**: Spring Boot 3.5.5 (WebFlux) + Vue 3 + TypeScript + Element Plus
- **数据库**: H2 (嵌入式，R2DBC 访问)
- **构建工具**: Maven 3.x
- **项目路径**: `/home/ubuntu/jairouter/modelrouter`

### 关键架构决策
- **熔断器实现**: 使用 `CircuitBreakerManager` 管理所有实例的熔断器状态
- **熔断器 Key**: 原本使用 `instanceUrl`，修复后使用 `instanceId`
- **限流器实现**: 已正确使用 `instanceId` 作为 key（与熔断器不一致的问题已修复）
- **适配器模式**: 支持多种 AI 服务适配器（Ollama、GPUStack、vLLM 等）

### 重要配置
- **Ollama 服务**: Docker 容器，端口映射 `12434->11434`
- **嵌入模型**: `shaw/dmeta-embedding-zh:latest` (768 维向量)
- **健康检查频率**: 每 30 秒执行一次 (`@Scheduled(fixedRate = 30000)`)
- **熔断器超时**: 60 秒 (60000ms)

### 构建和启动命令
```bash
# 快速构建
mvn clean package -DskipTests -Pfast

# 使用脚本启动
bash scripts/build/build-and-deploy.sh -f  # 仅前端
bash scripts/build/build-and-deploy.sh     # 完整构建部署

# 直接运行 JAR
java -jar target/model-router-1.7.0.jar --spring.profiles.active=dev
```

## Recent Actions

### 完成的工作
1. **[DONE] 测试 Ollama 嵌入模型 API**
   - 直接调用 Ollama API 正常工作（返回 768 维向量）
   - Ollama 的 `/api/embeddings` 端点使用 `prompt` 字段（非 `input`）
   - 模型 5 分钟后自动卸载导致 404 错误

2. **[DONE] 识别熔断器共享问题**
   - 问题：`CircuitBreakerManager.getCircuitBreaker()` 使用 `instanceUrl` 作为 key
   - 影响：相同 URL 的不同实例共享同一个熔断器状态
   - 对比：限流器已正确使用 `instanceId` 作为 key

3. **[DONE] 修复 CircuitBreakerManager.java**
   ```java
   // 修复前
   String key = instanceUrl != null && !instanceUrl.trim().isEmpty() ? instanceUrl : instanceId;
   
   // 修复后
   String key = instanceId != null && !instanceId.trim().isEmpty() ? instanceId : instanceUrl;
   ```

4. **[DONE] 修复 schema.sql**
   - 添加 `adapter VARCHAR(255)` 字段到 `service_instance` 表
   - 将 `headers TEXT` 改为 `headers JSON`

5. **[DONE] 修复 docker-compose.yml**
   - 修复卷挂载路径：`./data:/app/r2dbc:h2:file/data` → `./data:/app/data`

6. **[DONE] 代码提交**
   - 提交 ID: `5c5ddc5`
   - 分支: `master`
   - 提交信息：fix(circuitbreaker): 修复熔断器状态共享问题

### 发现的问题
1. **Ollama 模型自动卸载**: 默认 5 分钟后卸载未使用的模型，导致间歇性 404 错误
2. **服务配置适配器不匹配**: embedding 服务配置使用 `gpustack` 适配器，但实例配置使用 `ollama`
3. **应用健康检查**: 不会导致应用自动关闭（之前观察到的关闭是外部因素）

## Current Plan

### 已完成
1. **[DONE]** 识别并分析熔断器状态共享问题
2. **[DONE]** 修复 CircuitBreakerManager 使用 instanceId 作为 key
3. **[DONE]** 修复 schema.sql 添加 adapter 和 headers 字段
4. **[DONE]** 修复 docker-compose.yml 卷挂载路径
5. **[DONE]** 提交代码到本地仓库

### 进行中
1. **[IN PROGRESS]** 推送到远程仓库（需要 Gitee 认证）
   ```bash
   git push origin master
   ```

### 待完成
1. **[TODO]** 验证熔断器修复效果
   - 添加两个相同 URL 的实例
   - 让一个实例失败，验证另一个实例不受影响
   
2. **[TODO]** 完成 Embedding API 试验场测试
   - 确保服务配置适配器与实例适配器一致
   - 保持模型加载状态（定期预热）
   
3. **[TODO]** 考虑优化建议
   - 添加模型保活配置（keep_alive 参数）
   - 考虑熔断器状态持久化（重启后重置问题）

## 注意事项

### 测试限制
- Ollama 模型 5 分钟自动卸载会导致测试中断
- 需要在测试前预热模型
- 熔断器超时时间为 60 秒，测试需要等待

### 重要文件
- **熔断器实现**: `src/main/java/org/unreal/modelrouter/circuitbreaker/CircuitBreakerManager.java`
- **数据库 Schema**: `src/main/resources/schema.sql`
- **Ollama 适配器**: `src/main/java/org/unreal/modelrouter/adapter/impl/OllamaAdapter.java`
- **部署配置**: `docker-compose.yml`

### 下次会话建议
1. 推送代码到远程仓库
2. 创建新的 Git 分支进行后续开发
3. 考虑添加熔断器状态管理 UI（重置/查看状态）

---

## Summary Metadata
**Update time**: 2026-04-15T10:03:06.271Z 
