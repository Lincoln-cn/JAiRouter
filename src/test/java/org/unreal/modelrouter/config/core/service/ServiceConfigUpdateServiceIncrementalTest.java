package org.unreal.modelrouter.config.core.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.unreal.modelrouter.common.dto.CircuitBreakerConfig;
import org.unreal.modelrouter.common.dto.LoadBalanceConfig;
import org.unreal.modelrouter.common.dto.RateLimitConfig;
import org.unreal.modelrouter.common.dto.StickyConfig;
import org.unreal.modelrouter.config.dto.UpdateServiceConfigRequest;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * #1088 回归：服务配置更新必须是增量合并，不能把未提交的字段清空。
 *
 * <p>修复前的实现从空 Map 开始、只保留 instances，于是
 * {@code PUT /api/config/type/services/chat} 只传 loadBalance 时会把 adapter 清成 null、
 * rateLimit 整块丢掉，而接口返回成功。这些用例逐个字段守住那条路径。
 */
class ServiceConfigUpdateServiceIncrementalTest {

    private final ServiceConfigUpdateService service = new ServiceConfigUpdateService();

    private Map<String, Object> existingChatConfig() {
        final Map<String, Object> rateLimit = new LinkedHashMap<>();
        rateLimit.put("burstSize", 10);
        rateLimit.put("requestsPerDay", 100000);
        rateLimit.put("requestsPerSecond", 100);

        final Map<String, Object> circuitBreaker = new LinkedHashMap<>();
        circuitBreaker.put("enabled", true);
        circuitBreaker.put("failureThreshold", 5);
        circuitBreaker.put("timeout", 60);

        final Map<String, Object> instance = new LinkedHashMap<>();
        instance.put("name", "qwen3:4b");
        instance.put("baseUrl", "http://localhost:11434");

        final Map<String, Object> config = new LinkedHashMap<>();
        config.put("adapter", "gpustack");
        config.put("loadBalance", new LinkedHashMap<>(Map.of("type", "least-connections")));
        config.put("rateLimit", rateLimit);
        config.put("circuitBreaker", circuitBreaker);
        config.put("fallback", Map.of("enabled", true));
        config.put("instances", List.of(instance));
        config.put("futureKey", "keep-me");
        return config;
    }

    @Test
    @DisplayName("只提交 loadBalance：adapter / rateLimit / circuitBreaker / instances 全部保留")
    void onlyLoadBalance_keepsEverythingElse() {
        final Map<String, Object> existing = existingChatConfig();
        final UpdateServiceConfigRequest request = UpdateServiceConfigRequest.builder()
                .loadBalance(LoadBalanceConfig.builder().type("round-robin").build())
                .build();

        final Map<String, Object> updated = service.buildServiceConfigUpdate(existing, request);

        assertEquals("gpustack", updated.get("adapter"), "adapter 不能被清空（线上就是这样丢的）");
        assertEquals(Map.of("type", "round-robin"), updated.get("loadBalance"));
        assertEquals(existing.get("rateLimit"), updated.get("rateLimit"), "rateLimit 不能被重置");
        assertEquals(existing.get("circuitBreaker"), updated.get("circuitBreaker"));
        assertEquals(existing.get("instances"), updated.get("instances"));
        assertEquals(Map.of("enabled", true), updated.get("fallback"));
    }

    @Test
    @DisplayName("只提交 adapter：负载均衡与限流原样保留")
    void onlyAdapter_keepsOthers() {
        final Map<String, Object> existing = existingChatConfig();
        final UpdateServiceConfigRequest request = UpdateServiceConfigRequest.builder()
                .adapter("openai")
                .build();

        final Map<String, Object> updated = service.buildServiceConfigUpdate(existing, request);

        assertEquals("openai", updated.get("adapter"));
        assertEquals(existing.get("loadBalance"), updated.get("loadBalance"));
        assertEquals(existing.get("rateLimit"), updated.get("rateLimit"));
    }

    @Test
    @DisplayName("rateLimit 只提交一个字段：块内其余字段按字段合并保留")
    void partialRateLimit_mergesWithinBlock() {
        final Map<String, Object> existing = existingChatConfig();
        final UpdateServiceConfigRequest request = UpdateServiceConfigRequest.builder()
                .rateLimit(RateLimitConfig.builder().rate(5).build())
                .build();

        final Map<String, Object> updated = service.buildServiceConfigUpdate(existing, request);

        @SuppressWarnings("unchecked")
        final Map<String, Object> rateLimit = (Map<String, Object>) updated.get("rateLimit");
        assertEquals(5, rateLimit.get("rate"));
        assertEquals(10, rateLimit.get("burstSize"), "GET 侧字段不能被 PUT 侧形状冲掉");
        assertEquals(100000, rateLimit.get("requestsPerDay"));
        assertEquals(100, rateLimit.get("requestsPerSecond"));
    }

    @Test
    @DisplayName("circuitBreaker 只提交 enabled=false：阈值等配置保留")
    void partialCircuitBreaker_mergesWithinBlock() {
        final Map<String, Object> existing = existingChatConfig();
        final UpdateServiceConfigRequest request = UpdateServiceConfigRequest.builder()
                .circuitBreaker(CircuitBreakerConfig.builder().enabled(false).build())
                .build();

        final Map<String, Object> updated = service.buildServiceConfigUpdate(existing, request);

        @SuppressWarnings("unchecked")
        final Map<String, Object> cb = (Map<String, Object>) updated.get("circuitBreaker");
        assertEquals(false, cb.get("enabled"));
        assertEquals(5, cb.get("failureThreshold"));
        assertEquals(60, cb.get("timeout"));
    }

    @Test
    @DisplayName("未知键（未来版本新增的配置项）必须保留")
    void unknownKeys_arePreserved() {
        final UpdateServiceConfigRequest request = UpdateServiceConfigRequest.builder()
                .adapter("gpustack")
                .build();

        final Map<String, Object> updated =
                service.buildServiceConfigUpdate(existingChatConfig(), request);

        assertEquals("keep-me", updated.get("futureKey"));
    }

    @Test
    @DisplayName("没有现有配置时（新建服务）只包含请求里提交的字段")
    void nullExisting_onlyRequestFields() {
        final UpdateServiceConfigRequest request = UpdateServiceConfigRequest.builder()
                .adapter("openai")
                .loadBalance(LoadBalanceConfig.builder().type("random").build())
                .build();

        final Map<String, Object> updated = service.buildServiceConfigUpdate(null, request);

        assertEquals(2, updated.size());
        assertEquals("openai", updated.get("adapter"));
        assertEquals(Map.of("type", "random"), updated.get("loadBalance"));
    }

    @Test
    @DisplayName("空请求：返回现有配置的副本，不改内容也不返回同一对象")
    void emptyRequest_returnsCopy() {
        final Map<String, Object> existing = existingChatConfig();
        final Map<String, Object> updated =
                service.buildServiceConfigUpdate(existing, UpdateServiceConfigRequest.builder().build());

        assertEquals(existing, updated);
        assertNotSame(existing, updated, "必须返回副本，避免调用方改到缓存里的对象");
    }

    @Test
    @DisplayName("fallback 字段现在会被写回（原本有 DTO 字段但从未生效）")
    void fallback_isApplied() {
        final Map<String, Object> existing = existingChatConfig();
        final UpdateServiceConfigRequest request = UpdateServiceConfigRequest.builder()
                .fallback(Map.of("enabled", false, "strategy", "static"))
                .build();

        final Map<String, Object> updated = service.buildServiceConfigUpdate(existing, request);

        @SuppressWarnings("unchecked")
        final Map<String, Object> fallback = (Map<String, Object>) updated.get("fallback");
        assertEquals(false, fallback.get("enabled"));
        assertEquals("static", fallback.get("strategy"));
        assertFalse(updated.containsKey("adapter") && updated.get("adapter") == null);
        assertTrue(updated.containsKey("adapter"));
    }

    @Test
    @DisplayName("#1095：提交 sticky.enabled=false 会写进配置，且不影响其它字段")
    void sticky_isApplied() {
        final Map<String, Object> existing = existingChatConfig();
        final UpdateServiceConfigRequest request = UpdateServiceConfigRequest.builder()
                .sticky(StickyConfig.builder().enabled(false).build())
                .build();

        final Map<String, Object> updated = service.buildServiceConfigUpdate(existing, request);

        @SuppressWarnings("unchecked")
        final Map<String, Object> sticky = (Map<String, Object>) updated.get("sticky");
        assertEquals(Boolean.FALSE, sticky.get("enabled"));
        assertEquals("gpustack", updated.get("adapter"));
        assertTrue(updated.containsKey("rateLimit"));
        assertTrue(updated.containsKey("instances"));
    }

    @Test
    @DisplayName("#1095：sticky 块内部按字段合并，已有的 affinityKeyScope 不会丢")
    void sticky_blockIsMergedByField() {
        final Map<String, Object> existing = existingChatConfig();
        existing.put("sticky", new LinkedHashMap<>(Map.of("affinityKeyScope", "tenant")));

        final UpdateServiceConfigRequest request = UpdateServiceConfigRequest.builder()
                .sticky(StickyConfig.builder().enabled(true).build())
                .build();

        final Map<String, Object> updated = service.buildServiceConfigUpdate(existing, request);

        @SuppressWarnings("unchecked")
        final Map<String, Object> sticky = (Map<String, Object>) updated.get("sticky");
        assertEquals(Boolean.TRUE, sticky.get("enabled"));
        assertEquals("tenant", sticky.get("affinityKeyScope"));
    }
}
