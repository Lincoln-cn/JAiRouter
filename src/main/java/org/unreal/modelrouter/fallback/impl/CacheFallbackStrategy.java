package org.unreal.modelrouter.fallback.impl;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.ResponseEntity;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.unreal.modelrouter.fallback.FallbackStrategy;
import org.unreal.modelrouter.model.ModelServiceRegistry;
import org.unreal.modelrouter.util.IpUtils;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.security.MessageDigest;
import java.util.Base64;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * 基于缓存的降级策略实现
 * 当服务不可用时，返回最近的缓存结果
 */
public class CacheFallbackStrategy implements FallbackStrategy<ResponseEntity<?>> {
    private static final Logger logger = LoggerFactory.getLogger(CacheFallbackStrategy.class);

    private final String serviceType;
    private final ConcurrentMap<String, ResponseEntity<?>> cache = new ConcurrentHashMap<>();
    private final int maxCacheSize;

    public CacheFallbackStrategy(String serviceType, int maxCacheSize) {
        this.serviceType = serviceType;
        this.maxCacheSize = maxCacheSize;
    }

    @Override
    public ResponseEntity<?> fallback(Exception cause) {
        logger.warn("Service {} fallback triggered due to: {}", serviceType, cause.getMessage());

        // 返回缓存中的数据（如果有）
        // 这里只是一个示例，实际应用中需要根据具体请求参数查找缓存
        if (!cache.isEmpty()) {
            ResponseEntity<?> cachedResponse = cache.values().iterator().next();
            logger.info("Returning cached response for service {}", serviceType);
            return cachedResponse;
        }

        return ResponseEntity.status(503)
                .header("Content-Type", "application/json")
                .body("Service is currently degraded and no cached data available");
    }

    /**
     * 缓存响应结果
     *
     * @param serviceType
     * @param modelName
     * @param httpRequest
     * @param response    响应结果
     */
    public void cacheResponse(ModelServiceRegistry.ServiceType serviceType, String modelName, ServerHttpRequest httpRequest, ResponseEntity<?> response) {
        if (cache.size() >= maxCacheSize) {
            // 简单的LRU实现：移除第一个元素
            cache.remove(cache.keySet().iterator().next());
        }
        // 由于generateCacheKey现在是异步的，我们需要使用不同的方式处理
        String cacheKey = generateCacheKeySync(serviceType, modelName, httpRequest);
        cache.put(cacheKey, response);
    }

    /**
     * 同步生成缓存键（用于缓存响应）
     */
    protected String generateCacheKeySync(
            ModelServiceRegistry.ServiceType serviceType,
            String modelName,
            ServerHttpRequest httpRequest) {
        String clientIp = IpUtils.getClientIp(httpRequest);
        String requestHash = calculateRequestHashSync(httpRequest);
        return serviceType.name() + ":" + clientIp + ":" + modelName + ":" + requestHash;
    }

    /**
     * 异步生成缓存键
     */
    protected Mono<String> generateCacheKey(
            ModelServiceRegistry.ServiceType serviceType,
            String modelName,
            ServerHttpRequest httpRequest) {
        return calculateRequestHash(httpRequest)
                .map(hash -> {
                    String clientIp = IpUtils.getClientIp(httpRequest);
                    return serviceType.name() + ":" + clientIp + ":" + modelName + ":" + hash;
                });
    }

    /**
     * 异步计算请求体的SHA256哈希值
     */
    private Mono<String> calculateRequestHash(ServerHttpRequest httpRequest) {
        try {
            Flux<DataBuffer> body = httpRequest.getBody();
            return body
                    .map(dataBuffer -> {
                        try {
                            byte[] bytes = new byte[dataBuffer.readableByteCount()];
                            dataBuffer.read(bytes);
                            return bytes;
                        } catch (Exception e) {
                            logger.warn("读取请求体数据时出错", e);
                            return new byte[0];
                        }
                    })
                    .reduce(new byte[0], (bytes1, bytes2) -> {
                        byte[] result = new byte[bytes1.length + bytes2.length];
                        System.arraycopy(bytes1, 0, result, 0, bytes1.length);
                        System.arraycopy(bytes2, 0, result, bytes1.length, bytes2.length);
                        return result;
                    })
                    .map(bytes -> {
                        try {
                            MessageDigest digest = MessageDigest.getInstance("SHA-256");
                            byte[] hash = digest.digest(bytes);
                            return Base64.getEncoder().encodeToString(hash);
                        } catch (Exception e) {
                            logger.warn("计算SHA256哈希值时出错", e);
                            return "";
                        }
                    });
        } catch (Exception e) {
            logger.warn("处理请求体时出错", e);
            return Mono.just("");
        }
    }

    /**
     * 同步计算请求体的SHA256哈希值（用于缓存响应）
     * 注意：这种方法在响应式流中可能不适用，因为请求体可能已经被消费
     */
    private String calculateRequestHashSync(ServerHttpRequest httpRequest) {
        // 对于同步方法，我们返回一个简单的标识符，因为请求体可能已经被消费
        return "sync-request";
    }
}