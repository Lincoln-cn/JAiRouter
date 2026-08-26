package org.unreal.modelrouter.router.loadbalancer;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * v2.9.0: AffinityKeyResolver 单元测试
 */
@DisplayName("AffinityKeyResolver 单元测试")
class AffinityKeyResolverTest {

    @Test
    @DisplayName("apiKeyId 存在时返回 apiKeyId|serviceType|modelName")
    void resolveWithApiKeyId() {
        String key = AffinityKeyResolver.resolve("key-123", "192.168.1.1", "chat", "gpt-4");
        assertEquals("key-123|chat|gpt-4", key);
    }

    @Test
    @DisplayName("apiKeyId 为空时回退到 clientIp")
    void resolveFallbackToClientIp() {
        String key = AffinityKeyResolver.resolve(null, "192.168.1.1", "chat", "gpt-4");
        assertEquals("192.168.1.1|chat|gpt-4", key);
    }

    @Test
    @DisplayName("apiKeyId 为空白字符串时回退到 clientIp")
    void resolveBlankApiKeyIdFallback() {
        String key = AffinityKeyResolver.resolve("  ", "192.168.1.1", "chat", "gpt-4");
        assertEquals("192.168.1.1|chat|gpt-4", key);
    }

    @Test
    @DisplayName("apiKeyId 和 clientIp 都为空时返回 null")
    void resolveBothNull() {
        String key = AffinityKeyResolver.resolve(null, null, "chat", "gpt-4");
        assertNull(key);
    }

    @Test
    @DisplayName("apiKeyId 和 clientIp 都为空白时返回 null")
    void resolveBothBlank() {
        String key = AffinityKeyResolver.resolve("  ", "  ", "chat", "gpt-4");
        assertNull(key);
    }

    @Test
    @DisplayName("serviceType 和 modelName 为 null 时不影响键生成")
    void resolveWithNullServiceAndModel() {
        String key = AffinityKeyResolver.resolve("key-123", "192.168.1.1", null, null);
        assertEquals("key-123||", key);
    }

    @Test
    @DisplayName("相同 apiKeyId + serviceType + modelName 产生相同键")
    void sameInputsProduceSameKey() {
        String key1 = AffinityKeyResolver.resolve("k1", "ip1", "chat", "model-a");
        String key2 = AffinityKeyResolver.resolve("k1", "ip2", "chat", "model-a");
        // apiKeyId 优先于 clientIp，所以不同 clientIp 不影响
        assertEquals(key1, key2);
    }

    @Test
    @DisplayName("不同 apiKeyId 产生不同键")
    void differentApiKeysProduceDifferentKeys() {
        String key1 = AffinityKeyResolver.resolve("k1", "ip", "chat", "model");
        String key2 = AffinityKeyResolver.resolve("k2", "ip", "chat", "model");
        assertNotEquals(key1, key2);
    }

    @Test
    @DisplayName("resolveTenantKey 返回纯租户维度键")
    void resolveTenantKeyWithApiKeyId() {
        String key = AffinityKeyResolver.resolveTenantKey("key-123", "192.168.1.1");
        assertEquals("key-123", key);
    }

    @Test
    @DisplayName("resolveTenantKey apiKeyId 为空时回退到 clientIp")
    void resolveTenantKeyFallbackToIp() {
        String key = AffinityKeyResolver.resolveTenantKey(null, "192.168.1.1");
        assertEquals("192.168.1.1", key);
    }

    @Test
    @DisplayName("scope=tenant_model(默认)包含 modelName")
    void resolveScopeTenantModel() {
        String key = AffinityKeyResolver.resolve("key-1", "10.0.0.1", "chat", "gpt-4",
                AffinityKeyResolver.SCOPE_TENANT_MODEL);
        assertEquals("key-1|chat|gpt-4", key);
    }

    @Test
    @DisplayName("scope=tenant 不包含 modelName")
    void resolveScopeTenant() {
        String key = AffinityKeyResolver.resolve("key-1", "10.0.0.1", "chat", "gpt-4",
                AffinityKeyResolver.SCOPE_TENANT);
        assertEquals("key-1|chat", key);
    }

    @Test
    @DisplayName("scope=tenant 时不同 modelName 产生相同键")
    void scopeTenantIgnoresModelName() {
        String key1 = AffinityKeyResolver.resolve("key-1", "10.0.0.1", "chat", "model-a",
                AffinityKeyResolver.SCOPE_TENANT);
        String key2 = AffinityKeyResolver.resolve("key-1", "10.0.0.1", "chat", "model-b",
                AffinityKeyResolver.SCOPE_TENANT);
        assertEquals(key1, key2, "tenant scope should ignore modelName");
    }

    @Test
    @DisplayName("scope=tenant_model 时不同 modelName 产生不同键")
    void scopeTenantModelDistinguishesModel() {
        String key1 = AffinityKeyResolver.resolve("key-1", "10.0.0.1", "chat", "model-a",
                AffinityKeyResolver.SCOPE_TENANT_MODEL);
        String key2 = AffinityKeyResolver.resolve("key-1", "10.0.0.1", "chat", "model-b",
                AffinityKeyResolver.SCOPE_TENANT_MODEL);
        assertNotEquals(key1, key2, "tenant_model scope should distinguish modelName");
    }

    @Test
    @DisplayName("4参数 resolve 默认使用 tenant_model 粒度")
    void resolveDefaultScopeIsTenantModel() {
        String key4 = AffinityKeyResolver.resolve("k1", "ip", "chat", "model-x");
        String key5 = AffinityKeyResolver.resolve("k1", "ip", "chat", "model-x",
                AffinityKeyResolver.SCOPE_TENANT_MODEL);
        assertEquals(key4, key5, "4-param resolve should default to tenant_model");
    }
}
