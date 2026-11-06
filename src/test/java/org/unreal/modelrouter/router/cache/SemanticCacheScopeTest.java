package org.unreal.modelrouter.router.cache;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 语义缓存租户 scope 与 L3 准入测试.
 */
class SemanticCacheScopeTest {

    @Test
    @DisplayName("同一 API Key 同模型同 user 得到相同 scope")
    void sameTenantProducesSameScope() {
        assertThat(SemanticCacheScope.tenant("key-1", "alice", "qwen3:4b"))
                .isEqualTo(SemanticCacheScope.tenant("key-1", "alice", "qwen3:4b"));
    }

    @Test
    @DisplayName("不同 API Key 的 scope 必须不同")
    void differentApiKeysNeverShareScope() {
        assertThat(SemanticCacheScope.tenant("key-1", "alice", "qwen3:4b"))
                .isNotEqualTo(SemanticCacheScope.tenant("key-2", "alice", "qwen3:4b"));
    }

    @Test
    @DisplayName("同一 API Key 下不同 user 的 scope 不同")
    void differentUsersNeverShareScope() {
        assertThat(SemanticCacheScope.tenant("key-1", "alice", "qwen3:4b"))
                .isNotEqualTo(SemanticCacheScope.tenant("key-1", "bob", "qwen3:4b"));
    }

    @Test
    @DisplayName("缺 user 时退化到「无 user」而不是退化成空串或 IP")
    void missingUserDegradesExplicitly() {
        String withoutUser = SemanticCacheScope.tenant("key-1", null, "qwen3:4b");

        assertThat(withoutUser).contains("|-|");
        assertThat(withoutUser).isNotEqualTo(SemanticCacheScope.tenant("key-1", "alice", "qwen3:4b"));
    }

    @Test
    @DisplayName("user 里出现分隔符不会串桶")
    void separatorInUserDoesNotCollide() {
        // "a|b" 若直接拼接会与 tenant(a) + 模型 "b|..." 撞桶
        assertThat(SemanticCacheScope.tenant("key-1", "a|b", "m"))
                .isNotEqualTo(SemanticCacheScope.tenant("key-1", "a", "b|m"));
    }

    @Test
    @DisplayName("公共池 scope 与租户 scope 不同命名空间")
    void publicScopeIsDistinct() {
        assertThat(SemanticCacheScope.publicScope("qwen3:4b"))
                .isNotEqualTo(SemanticCacheScope.tenant("key-1", null, "qwen3:4b"));
        assertThat(SemanticCacheScope.isPublic(SemanticCacheScope.publicScope("qwen3:4b"))).isTrue();
        assertThat(SemanticCacheScope.isPublic(SemanticCacheScope.tenant("key-1", null, "qwen3:4b"))).isFalse();
    }

    @Test
    @DisplayName("belongsToTenant 只认同一个 API Key 的 scope")
    void belongsToTenantMatchesApiKeyOnly() {
        String scope = SemanticCacheScope.tenant("key-1", "alice", "qwen3:4b");

        assertThat(SemanticCacheScope.belongsToTenant(scope, "key-1")).isTrue();
        assertThat(SemanticCacheScope.belongsToTenant(scope, "key-2")).isFalse();
    }
}

/**
 * L3 准入判定测试.
 */
class SemanticAdmissionPolicyTest {

    private SemanticCacheProperties properties;

    private SemanticAdmissionPolicy policy;

    @BeforeEach
    void setUp() {
        properties = new SemanticCacheProperties();
        // 准入判定本身会先看「公共池有没有开」，测试只验准入规则，所以显式打开
        properties.setPublicScopeEnabled(true);
        policy = new SemanticAdmissionPolicy(properties);
    }

    private Map<String, Object> body(Object... keyValues) {
        Map<String, Object> map = new java.util.LinkedHashMap<>();
        for (int i = 0; i < keyValues.length; i += 2) {
            map.put(String.valueOf(keyValues[i]), keyValues[i + 1]);
        }
        return map;
    }

    @Test
    @DisplayName("确定性单轮请求可进公共池")
    void deterministicSingleTurnEntersPublic() {
        Map<String, Object> body = body("model", "qwen3:4b",
                "messages", java.util.List.of(Map.of("role", "user", "content", "法国的首都是哪里")));

        assertThat(policy.canEnterPublic(body, "法国的首都是哪里")).isTrue();
    }

    @Test
    @DisplayName("temperature 非 0 时不得进公共池")
    void nonDeterministicRejected() {
        Map<String, Object> body = body("model", "qwen3:4b", "temperature", 0.7,
                "messages", java.util.List.of(Map.of("role", "user", "content", "随便聊聊")));

        assertThat(policy.canEnterPublic(body, "随便聊聊")).isFalse();
    }

    @Test
    @DisplayName("带 system 角色不得进公共池")
    void systemRoleRejected() {
        Map<String, Object> body = body("model", "qwen3:4b",
                "messages", java.util.List.of(
                        Map.of("role", "system", "content", "你是某公司的客服"),
                        Map.of("role", "user", "content", "你好")));

        assertThat(policy.canEnterPublic(body, "你好")).isFalse();
    }

    @Test
    @DisplayName("超过长度上限不得进公共池")
    void tooLongRejected() {
        String longText = "x".repeat(properties.getMaxPublicChars() + 1);
        Map<String, Object> body = body("model", "qwen3:4b",
                "messages", java.util.List.of(Map.of("role", "user", "content", longText)));

        assertThat(policy.canEnterPublic(body, longText)).isFalse();
    }

    @Test
    @DisplayName("顶层出现白名单外字段不得进公共池")
    void unknownTopLevelFieldRejected() {
        Map<String, Object> body = body("model", "qwen3:4b", "metadata", Map.of("tenant", "x"),
                "messages", java.util.List.of(Map.of("role", "user", "content", "你好")));

        assertThat(policy.canEnterPublic(body, "你好")).isFalse();
    }
}
