package org.unreal.modelrouter.fallback;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.ResponseEntity;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.unreal.modelrouter.config.ModelServiceRegistry;
import org.unreal.modelrouter.response.ErrorResponse;
import org.unreal.modelrouter.util.IpUtils;
import reactor.core.publisher.Flux;

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

        // 如果没有缓存，返回默认降级响应
        ErrorResponse errorResponse = ErrorResponse.builder()
                .code("service_degraded")
                .type(serviceType)
                .message("Service is currently degraded and no cached data available")
                .build();

        return ResponseEntity.status(503)
                .header("Content-Type", "application/json")
                .body(errorResponse.toJson());
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
        cache.put(generateCacheKey(serviceType,modelName,httpRequest), response);
    }

    /**
     * 生成缓存键
     */
    protected String generateCacheKey(
            ModelServiceRegistry.ServiceType serviceType,
            String modelName,
            ServerHttpRequest httpRequest) {
        // 简单的缓存键生成策略，可以根据需要扩展
        String clientIp = IpUtils.getClientIp(httpRequest);
        return serviceType.name() + ":" + clientIp + ":" + modelName + ":" + requestSha(httpRequest);
    }

    private String requestSha(ServerHttpRequest httpRequest) {
        try {
            Flux<DataBuffer> body = httpRequest.getBody();
            return body
                    .map(dataBuffer -> {
                        try {
                            byte[] bytes = new byte[dataBuffer.readableByteCount()];
                            dataBuffer.read(bytes);
                            return bytes;
                        } catch (Exception e) {
                            e.printStackTrace();
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
                            e.printStackTrace();
                            return "";
                        }
                    })
                    .block();
        } catch (Exception e) {
            e.printStackTrace();
            return "";
        }
    }
}