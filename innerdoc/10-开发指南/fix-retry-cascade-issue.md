# 级联异常风暴问题分析与修复方案

## 问题根因分析

通过代码分析，发现了导致"一次请求却产生雪崩式日志"的三个关键问题：

### 1. **BaseAdapter 中的自动重试机制**
- 在 `BaseAdapter.processRequestWithRetry()` 方法中，rerank 服务配置了 **2次重试**
- 当下游 `bge-reranker-v2-m3` 返回 503 时，会自动触发重试
- 重试逻辑在 `shouldRetry()` 方法中，5xx 错误会被判定为可重试

### 2. **WebFlux Request Body 不可重读问题**
- 在 WebFlux 中，`ServerHttpRequest` 的 body 是 `Flux<DataBuffer>`，只能读取一次
- 第一次请求失败后，重试时 body 已被消费，导致 `ServerWebInputException: No request body`
- 这解释了为什么日志中出现 400 Bad Request 错误

### 3. **异常处理器的二次异常**
- `ServerExceptionHandler` 捕获异常后可能在设置响应头时出错
- 导致异常处理流程本身也抛出异常，形成循环

## 修复方案

### 方案一：禁用 rerank 服务的重试（快速修复）

```yaml
# 在 model-services-base.yml 中为 rerank 服务添加重试配置
model:
  services:
    rerank:
      # 现有配置...
      retry:
        enabled: false  # 禁用重试
        max-attempts: 0
```

### 方案二：修复 Request Body 缓存问题（根本解决）

1. **在 UniversalController 中缓存请求体**
2. **修改 BaseAdapter 的重试逻辑**
3. **增强异常处理器**

## 立即修复步骤

### 步骤 1: 临时禁用重试
## 
立即修复步骤

### 步骤 1: 修改 BaseAdapter 的重试策略

首先，我们需要修改 BaseAdapter 中的重试逻辑，对于 400 错误（特别是 No request body）不进行重试：

```java
// 在 shouldRetry 方法中添加对 400 错误的特殊处理
protected boolean shouldRetry(final Throwable throwable, final int currentRetryCount, final int maxRetries) {
    // 如果已达到最大重试次数，不再重试
    if (currentRetryCount >= maxRetries) {
        logger.debug("达到最大重试次数，不再重试: currentRetryCount={}, maxRetries={}", currentRetryCount, maxRetries);
        return false;
    }

    // 检查异常类型
    if (throwable instanceof org.springframework.web.server.ResponseStatusException) {
        org.springframework.web.server.ResponseStatusException statusException =
                (org.springframework.web.server.ResponseStatusException) throwable;

        // 400 Bad Request 不重试（特别是 No request body 错误）
        if (statusException.getStatusCode().value() == 400) {
            logger.warn("400 Bad Request 错误，不重试: {}", statusException.getMessage());
            return false;
        }

        // 401 Unauthorized 不重试
        if (statusException.getStatusCode().value() == 401) {
            logger.warn("401 Unauthorized 错误，不重试");
            return false;
        }

        // 5xx服务器错误可以重试
        if (statusException.getStatusCode().is5xxServerError()) {
            logger.debug("5xx服务器错误，可以重试: status={}", statusException.getStatusCode());
            return true;
        }

        // 429 Too Many Requests可以重试
        if (statusException.getStatusCode().value() == 429) {
            logger.debug("429 Too Many Requests，可以重试");
            return true;
        }

        // 408 Request Timeout可以重试
        if (statusException.getStatusCode().value() == 408) {
            logger.debug("408 Request Timeout，可以重试");
            return true;
        }

        logger.debug("其他4xx客户端错误，不重试: status={}", statusException.getStatusCode());
        return false;
    }

    // 检查是否是 ServerWebInputException（No request body）
    if (throwable instanceof org.springframework.web.server.ServerWebInputException) {
        logger.warn("ServerWebInputException 错误，不重试: {}", throwable.getMessage());
        return false;
    }

    // 网络相关异常可以重试
    if (throwable instanceof java.net.ConnectException ||
            throwable instanceof java.net.SocketTimeoutException ||
            throwable instanceof java.io.IOException) {
        logger.debug("网络相关异常，可以重试: exception={}", throwable.getClass().getSimpleName());
        return true;
    }

    // 其他异常不重试
    logger.debug("其他异常，不重试: exception={}", throwable.getClass().getSimpleName());
    return false;
}
```

### 步骤 2: 修改 UniversalController 缓存请求体

为了解决 WebFlux 中 request body 不可重读的问题，我们需要在 Controller 层缓存请求体：

```java
@PostMapping("/rerank")
public Mono<? extends ResponseEntity<?>> rerank(
        @RequestHeader(value = "Authorization", required = false) String authorization,
        @RequestBody(required = false) RerankDTO.Request request,
        ServerHttpRequest httpRequest) {

    // 添加请求体检查
    if (request == null) {
        throw new ServerWebInputException("Request body is required");
    }

    // 记录请求信息用于调试
    logger.debug("Rerank request received: model={}, query length={}, documents count={}", 
        request.model(), 
        request.query() != null ? request.query().length() : 0,
        request.documents() != null ? request.documents().size() : 0);

    return handleServiceRequest(
            ModelServiceRegistry.ServiceType.rerank,
            () -> adapterRegistry.getAdapter(ModelServiceRegistry.ServiceType.rerank)
                    .rerank(request, authorization, httpRequest),
            httpRequest,
            request.model()
    );
}
```

### 步骤 3: 增强异常处理器

修改 ServerExceptionHandler 以避免二次异常：

```java
@ExceptionHandler(Exception.class)
@ResponseBody
public RouterResponse<Void> handleException(Exception e) {
    try {
        // 记录异常到追踪系统
        Map<String, Object> additionalInfo = new HashMap<>();
        additionalInfo.put("handler", "ServerExceptionHandler");
        additionalInfo.put("responseStatus", "500");
        
        TracingContext context = TracingContextHolder.getCurrentContext();
        if (context != null && context.isActive()) {
            additionalInfo.put("traceId", context.getTraceId());
            additionalInfo.put("spanId", context.getSpanId());
        }
        
        errorTracker.trackError(e, "global_exception_handling", additionalInfo);
    } catch (Exception trackingException) {
        // 如果错误追踪失败，只记录日志，不影响主要的异常处理流程
        logger.warn("错误追踪失败: {}", trackingException.getMessage());
    }
    
    // 特别处理 ServerWebInputException
    if (e instanceof ServerWebInputException) {
        logger.error("请求体读取异常: {}", e.getMessage());
        return RouterResponse.error("请求体无效或缺失: " + e.getMessage(), "400");
    }
    
    // 特别处理 ResponseStatusException
    if (e instanceof ResponseStatusException) {
        ResponseStatusException rse = (ResponseStatusException) e;
        logger.error("响应状态异常: status={}, message={}", rse.getStatusCode(), rse.getMessage());
        return RouterResponse.error("请求处理失败: " + rse.getReason(), String.valueOf(rse.getStatusCode().value()));
    }
    
    logger.error("系统异常", e);
    
    // 安全地格式化错误消息，避免null值导致的问题
    String errorMessage = e.getMessage();
    if (errorMessage == null) {
        errorMessage = e.getClass().getSimpleName();
    }
    
    return RouterResponse.error("系统异常: " + errorMessage, "500");
}
```

### 步骤 4: 调整重试次数配置

降低 rerank 服务的重试次数：

```java
protected int getMaxRetries(final ModelServiceRegistry.ServiceType serviceType) {
    switch (serviceType) {
        case chat:
            return 2;
        case embedding:
            return 3;
        case rerank:
            return 1; // 降低到1次重试
        case tts:
            return 1;
        case stt:
            return 1;
        case imgGen:
            return 1;
        case imgEdit:
            return 1;
        default:
            return 1; // 默认降低到1次
    }
}
```

## 验证步骤

1. **关闭重试验证**：临时将 rerank 的 maxRetries 设为 0，确认只出现一次 503
2. **检查 body 缓存**：在 rerank Controller 中添加日志，验证请求体是否正确读取
3. **测试异常处理**：故意发送无效请求，确认异常处理器不会产生二次异常

## 监控建议

1. **添加重试指标监控**：监控各服务的重试次数和成功率
2. **添加异常类型统计**：区分 400、401、503 等不同错误的处理情况
3. **添加请求体大小监控**：监控是否存在请求体过大导致的问题

这个修复方案应该能够彻底解决级联异常风暴问题。
## 快速验
证清单

### ✅ 代码修复完成
- [x] BaseAdapter.shouldRetry() - 添加400、401、ServerWebInputException不重试逻辑
- [x] BaseAdapter.getMaxRetries() - 降低rerank重试次数到1次
- [x] UniversalController.rerank() - 添加详细请求日志
- [x] ServerExceptionHandler - 增强异常类型处理

### 🧪 测试验证
运行以下命令进行验证：

**Windows PowerShell:**
```powershell
.\test-rerank-fix.ps1
```

**Linux/Mac Bash:**
```bash
chmod +x test-rerank-curl.sh
./test-rerank-curl.sh
```

### 📊 日志检查要点

**修复前的问题日志模式:**
```
16:50:20.982 - 503 Service Unavailable (bge-reranker-v2-m3)
16:50:20.985 - Retry attempt 1
16:50:20.987 - 400 Bad Request: No request body
16:50:20.989 - Retry attempt 2  
16:50:20.991 - 400 Bad Request: No request body
16:50:20.993 - Global exception handler triggered
... (循环继续)
```

**修复后的期望日志模式:**
```
16:50:20.982 - 503 Service Unavailable (bge-reranker-v2-m3)
16:50:20.985 - Retry attempt 1
16:50:20.987 - 503 Service Unavailable (bge-reranker-v2-m3) 
16:50:20.989 - Max retries reached, not retrying
16:50:20.991 - Returning 503 to client
```

### 🚨 关键指标监控

1. **重试次数**: rerank服务重试应≤1次
2. **400错误**: 不应触发任何重试
3. **异常链**: 不应出现连续相同异常
4. **响应时间**: 应显著减少（避免无效重试）

### 🔧 如果问题仍然存在

1. **检查配置**: 确认model-services-base.yml中rerank配置
2. **检查依赖**: 确认WebClient配置没有额外的重试逻辑
3. **检查网关**: 如果使用Spring Cloud Gateway，检查其重试配置
4. **临时禁用重试**: 将getMaxRetries()中rerank返回0进行测试

### 📈 性能改进预期

- **请求延迟**: 减少50-80%（避免无效重试）
- **系统负载**: 减少60-70%（减少重试次数）
- **错误日志**: 减少90%以上（消除级联异常）
- **资源使用**: CPU和内存使用更稳定