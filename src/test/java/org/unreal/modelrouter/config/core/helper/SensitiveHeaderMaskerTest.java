package org.unreal.modelrouter.config.core.helper;

import org.junit.jupiter.api.Test;
import org.unreal.modelrouter.config.version.diff.ConfigDiff;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 敏感请求头脱敏测试（issue #1053）
 */
class SensitiveHeaderMaskerTest {

    private static final String REAL_TOKEN = "Bearer sk-live-0123456789abcdef";

    @Test
    void maskReplacesCredentialValuesButKeepsTail() {
        Map<String, String> headers = new HashMap<>();
        headers.put("Authorization", REAL_TOKEN);
        headers.put("x-api-key", "sk-another-key-9876543210zyxw");
        headers.put("Content-Type", "application/json");

        Map<String, String> masked = SensitiveHeaderMasker.mask(headers);

        assertEquals(SensitiveHeaderMasker.MASK_PREFIX + "cdef", masked.get("Authorization"));
        assertEquals(SensitiveHeaderMasker.MASK_PREFIX + "zyxw", masked.get("x-api-key"));
        assertEquals("application/json", masked.get("Content-Type"), "非敏感头必须原样返回，否则管理台看不到自己的配置");
        assertFalse(masked.get("Authorization").contains("sk-live-0123456789"), "掩码里不能留下前段明文");
    }

    @Test
    void maskHidesShortValuesEntirely() {
        Map<String, String> headers = Map.of("token", "abc123");

        assertEquals(SensitiveHeaderMasker.MASK_PREFIX, SensitiveHeaderMasker.mask(headers).get("token"),
                "短值保留 4 位几乎等于泄漏全长，应整段遮住");
    }

    @Test
    void maskReturnsEmptyMapForNullOrEmptyInput() {
        assertTrue(SensitiveHeaderMasker.mask(null).isEmpty());
        assertTrue(SensitiveHeaderMasker.mask(Map.of()).isEmpty());
    }

    @Test
    void sensitiveNamesAreMatchedCaseInsensitivelyAndIgnoringSeparators() {
        assertTrue(SensitiveHeaderMasker.isSensitiveName("Authorization"));
        assertTrue(SensitiveHeaderMasker.isSensitiveName("x-api-key"));
        assertTrue(SensitiveHeaderMasker.isSensitiveName("X_API_KEY"));
        assertTrue(SensitiveHeaderMasker.isSensitiveName("X-Auth-Token"));
        assertTrue(SensitiveHeaderMasker.isSensitiveName("client-secret"));
        assertFalse(SensitiveHeaderMasker.isSensitiveName("Content-Type"));
        assertFalse(SensitiveHeaderMasker.isSensitiveName(null));
        assertFalse(SensitiveHeaderMasker.isSensitiveName("  "));
    }

    @Test
    void preserveMaskedRestoresOriginalWhenClientEchoesMask() {
        Map<String, String> existing = Map.of("Authorization", REAL_TOKEN);
        Map<String, String> incoming = Map.of("Authorization", SensitiveHeaderMasker.maskValue(REAL_TOKEN));

        Map<String, String> merged = SensitiveHeaderMasker.preserveMasked(incoming, existing);

        assertEquals(REAL_TOKEN, merged.get("Authorization"),
                "管理台把掩码整份提交回来时，必须还原库里的真值，否则上游立刻 401");
    }

    @Test
    void preserveMaskedKeepsUserSuppliedValueWhenItIsNotTheMaskOfExisting() {
        Map<String, String> existing = Map.of("Authorization", REAL_TOKEN);
        Map<String, String> incoming = Map.of("Authorization", "Bearer sk-rotated-new-credential");

        Map<String, String> merged = SensitiveHeaderMasker.preserveMasked(incoming, existing);

        assertEquals("Bearer sk-rotated-new-credential", merged.get("Authorization"), "换新凭证时不能被旧值覆盖");
    }

    @Test
    void preserveMaskedLeavesHandWrittenMaskAlone() {
        Map<String, String> existing = Map.of("Authorization", REAL_TOKEN);
        Map<String, String> incoming = Map.of("Authorization", "***");

        Map<String, String> merged = SensitiveHeaderMasker.preserveMasked(incoming, existing);

        assertEquals("***", merged.get("Authorization"),
                "手写 *** 与库里原值的掩码形态不同，视为用户意图覆盖，不做还原");
    }

    @Test
    void preserveMaskedToleratesHeaderNameCaseDifference() {
        Map<String, String> existing = Map.of("authorization", REAL_TOKEN);
        Map<String, String> incoming = Map.of("Authorization", SensitiveHeaderMasker.maskValue(REAL_TOKEN));

        assertEquals(REAL_TOKEN, SensitiveHeaderMasker.preserveMasked(incoming, existing).get("Authorization"));
    }

    @Test
    void preserveMaskedReturnsIncomingWhenNothingToRestore() {
        Map<String, String> incoming = Map.of("X-Tenant", "acme");

        assertEquals(incoming, SensitiveHeaderMasker.preserveMasked(incoming, null));
        assertEquals(incoming, SensitiveHeaderMasker.preserveMasked(incoming, Map.of()));
        assertTrue(SensitiveHeaderMasker.preserveMasked(null, Map.of("a", "b")) == null);
    }

    @Test
    void maskConfigTreeMasksHeadersAtAnyDepth() {
        Map<String, Object> instance = new LinkedHashMap<>();
        instance.put("name", "gpu-1");
        instance.put("headers", Map.of("Authorization", REAL_TOKEN, "Content-Type", "application/json"));
        Map<String, Object> service = new LinkedHashMap<>();
        service.put("instances", List.of(instance));
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("version", 2);
        root.put("services", Map.of("chat", service));

        Map<String, Object> masked = SensitiveHeaderMasker.maskConfigTree(root);

        assertEquals(2, masked.get("version"), "非 headers 部分必须原样保留");
        Map<?, ?> maskedService = (Map<?, ?>) ((Map<?, ?>) masked.get("services")).get("chat");
        Map<?, ?> maskedInstance = (Map<?, ?>) ((List<?>) maskedService.get("instances")).get(0);
        Map<?, ?> maskedHeaders = (Map<?, ?>) maskedInstance.get("headers");
        assertEquals(SensitiveHeaderMasker.MASK_PREFIX + "cdef", maskedHeaders.get("Authorization"));
        assertEquals("application/json", maskedHeaders.get("Content-Type"));

        Map<?, ?> originalInstance = (Map<?, ?>) ((List<?>) service.get("instances")).get(0);
        assertEquals(REAL_TOKEN, ((Map<?, ?>) originalInstance.get("headers")).get("Authorization"),
                "脱敏只能作用于返回副本，不能改调用方传进来的对象");
    }

    @Test
    void maskDiffMasksValuesOnHeaderPaths() {
        ConfigDiff diff = ConfigDiff.builder()
                .sourceVersion(1)
                .targetVersion(2)
                .modified(List.of(
                        ConfigDiff.DiffItem.builder()
                                .path("services.chat.instances[0].headers.Authorization")
                                .oldValue(REAL_TOKEN)
                                .newValue("Bearer sk-rotated-0123456789zzzz")
                                .changeType(ConfigDiff.DiffItem.ChangeType.MODIFIED)
                                .valueType("String")
                                .build(),
                        ConfigDiff.DiffItem.builder()
                                .path("services.chat.instances[0].weight")
                                .oldValue(1)
                                .newValue(2)
                                .changeType(ConfigDiff.DiffItem.ChangeType.MODIFIED)
                                .valueType("Integer")
                                .build()))
                .build();

        ConfigDiff masked = SensitiveHeaderMasker.maskDiff(diff);

        ConfigDiff.DiffItem headersItem = masked.getModified().get(0);
        assertEquals(SensitiveHeaderMasker.MASK_PREFIX + "cdef", headersItem.getOldValue());
        assertEquals(SensitiveHeaderMasker.MASK_PREFIX + "zzzz", headersItem.getNewValue());
        assertEquals(2, masked.getModified().size(), "差异项数量不应因脱敏而变化");

        ConfigDiff.DiffItem weightItem = masked.getModified().get(1);
        assertEquals(1, weightItem.getOldValue(), "非 headers 路径的值不受影响");
        assertEquals(2, weightItem.getNewValue());
    }
}
