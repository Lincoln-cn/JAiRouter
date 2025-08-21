package org.unreal.modelrouter.security.cache;

import org.junit.jupiter.api.Test;
import org.unreal.modelrouter.security.cache.impl.InMemoryApiKeyCache;
import org.unreal.modelrouter.security.model.ApiKeyInfo;
import org.unreal.modelrouter.security.model.UsageStatistics;
import reactor.test.StepVerifier;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 缓存集成测试
 */
class CacheIntegrationTest {
    
    @Test
    void testInMemoryCacheBasicOperations() {
        InMemoryApiKeyCache cache = new InMemoryApiKeyCache(null);
        
        ApiKeyInfo testApiKey = ApiKeyInfo.builder()
                .keyId("test-key-1")
                .keyValue("test-value-1")
                .description("测试API Key")
                .createdAt(LocalDateTime.now())
                .expiresAt(LocalDateTime.now().plusDays(30))
                .enabled(true)
                .permissions(List.of("read", "write"))
                .usage(UsageStatistics.builder()
                        .totalRequests(0L)
                        .successfulRequests(0L)
                        .failedRequests(0L)
                        .lastUsedAt(LocalDateTime.now())
                        .build())
                .build();
        
        // 测试缓存写入
        StepVerifier.create(cache.put(testApiKey.getKeyValue(), testApiKey))
                .verifyComplete();
        
        // 测试缓存读取
        StepVerifier.create(cache.get(testApiKey.getKeyValue()))
                .expectNext(testApiKey)
                .verifyComplete();
        
        // 测试缓存存在检查
        StepVerifier.create(cache.exists(testApiKey.getKeyValue()))
                .expectNext(true)
                .verifyComplete();
        
        // 测试缓存移除
        StepVerifier.create(cache.evict(testApiKey.getKeyValue()))
                .verifyComplete();
        
        // 验证移除后不存在
        StepVerifier.create(cache.exists(testApiKey.getKeyValue()))
                .expectNext(false)
                .verifyComplete();
    }
    
    @Test
    void testCacheExpiration() {
        InMemoryApiKeyCache cache = new InMemoryApiKeyCache(null);
        
        ApiKeyInfo testApiKey = ApiKeyInfo.builder()
                .keyId("test-key-2")
                .keyValue("test-value-2")
                .description("测试过期API Key")
                .createdAt(LocalDateTime.now())
                .expiresAt(LocalDateTime.now().plusDays(30))
                .enabled(true)
                .permissions(List.of("read"))
                .build();
        
        // 写入带有短过期时间的缓存
        StepVerifier.create(cache.put(testApiKey.getKeyValue(), testApiKey, Duration.ofMillis(100)))
                .verifyComplete();
        
        // 立即读取应该成功
        StepVerifier.create(cache.get(testApiKey.getKeyValue()))
                .expectNext(testApiKey)
                .verifyComplete();
        
        // 等待过期后读取应该为空
        StepVerifier.create(cache.get(testApiKey.getKeyValue()).delayElement(Duration.ofMillis(200)))
                .verifyComplete();
    }
}