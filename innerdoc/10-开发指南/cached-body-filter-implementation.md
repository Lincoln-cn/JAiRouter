# 缓存请求体过滤器实现方案

## 概述

实现了方案 A：让 body 在重试时可重读，通过在 Gateway 层（或最外层 WebFilter）缓存 body 到内存。

## 实现细节

### 1. CachedBodyWebFilter

**文件位置**: `src/main/java/org/unreal/modelrouter/filter/CachedBodyWebFilter.java`

**核心功能**:
- 在所有过滤器之前执行（优先级：`Ordered.HIGHEST_PRECEDENCE - 1`）
- 检测请求是否有 body（基于 Content-Length、Transfer-Encoding 或 HTTP 方法）
- 将请求体缓存到内存中
- 创建可重复读取的 `ServerHttpRequestDecorator`
- 提供错误降级处理

**关键代码逻辑**:
```java
return DataBufferUtils.join(request.getBody())
    .flatMap(dataBuffer -> {
        try {
            // 读取字节数据
            byte[] bytes = new byte[dataBuffer.readableByteCount()];
            dataBuffer.read(bytes);
            
            // 创建可重复读取的请求装饰器
            ServerHttpRequest cachedRequest = new ServerHttpRequestDecorator(request) {
                @Override
                public Flux<DataBuffer> getBody() {
                    return Flux.just(exchange.getResponse()
                            .bufferFactory()
                            .wrap(bytes));
                }
            };
            
            return chain.filter(exchange.mutate().request(cachedRequest).build());
        } finally {
            DataBufferUtils.release(dataBuffer);
        }
    })
```

### 2. WebFilterConfiguration

**文件位置**: `src/main/java/org/unreal/modelrouter/config/WebFilterConfiguration.java`

**功能**:
- 注册 `CachedBodyWebFilter` 为 Spring Bean
- 确保过滤器被正确配置和管理

### 3. 过滤器优先级调整

**TracingWebFilter 优先级调整**:
- 保持 `Ordered.HIGHEST_PRECEDENCE` 优先级
- 确保在缓存 body 过滤器之后执行
- 更新注释说明执行顺序

## 过滤器执行顺序

```
1. CachedBodyWebFilter (HIGHEST_PRECEDENCE - 1) - 缓存请求体
2. TracingWebFilter (HIGHEST_PRECEDENCE) - 追踪和监控
3. 其他安全过滤器 - API Key 验证等
4. 重试机制 - 现在可以重复读取缓存的请求体
5. 业务逻辑处理
```

## 支持的场景

### 1. 有 body 的请求
- POST、PUT、PATCH 请求
- 有 `Content-Length > 0` 的请求
- 有 `Transfer-Encoding: chunked` 的请求

### 2. 无 body 的请求
- GET、DELETE 等请求
- 直接通过，不进行缓存处理

### 3. 错误处理
- 缓存失败时降级到原始请求处理
- 不影响主业务流程

## 内存管理

### 优势
- 解决了 WebFlux 中请求体只能读取一次的问题
- 支持重试机制和多个过滤器读取同一请求体
- 实现简单，性能开销较小

### 注意事项
- 大请求体会占用内存
- 建议配合请求大小限制使用
- 适合中小型请求体的场景

## 测试覆盖

**测试文件**: `src/test/java/org/unreal/modelrouter/filter/CachedBodyWebFilterTest.java`

**测试场景**:
- 过滤器优先级验证
- 有/无 body 请求的处理
- 不同 HTTP 方法的支持
- Chunked 编码支持
- 错误处理和降级
- 多次读取验证

## 使用示例

### 重试场景
```java
// 第一次请求失败后，重试时可以重新读取相同的请求体
return processRequestWithRetry(request, authorization, client, path, 
    selectedInstance, serviceType, processor, tracingContext, startTime, 0);
```

### API Key 验证
```java
// 多个过滤器可以独立读取请求体进行验证
// 不会因为前面的过滤器读取了 body 而导致后续读取失败
```

## 配置建议

### 1. 请求大小限制
```yaml
spring:
  webflux:
    multipart:
      max-in-memory-size: 10MB
server:
  max-http-header-size: 8KB
```

### 2. 监控指标
- 监控缓存成功/失败率
- 监控内存使用情况
- 监控请求体大小分布

## 后续优化方向

1. **大文件支持**: 对于大请求体，可以考虑使用临时文件缓存
2. **内存优化**: 实现请求体大小阈值，超过阈值使用文件缓存
3. **压缩支持**: 对缓存的请求体进行压缩以节省内存
4. **异步处理**: 优化缓存过程的性能

## 总结

该实现成功解决了 WebFlux 中请求体重试时不可重读的问题，通过在最外层缓存请求体，使得后续的重试机制和其他过滤器可以正常工作。实现简单、可靠，适合大多数业务场景。