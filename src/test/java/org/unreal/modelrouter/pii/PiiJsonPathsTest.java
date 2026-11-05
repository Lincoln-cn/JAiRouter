package org.unreal.modelrouter.pii;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.json.JsonMapper;
import org.unreal.modelrouter.monitor.callhistory.crypto.RecordContentCipher;
import org.unreal.modelrouter.persistence.jpa.entity.PiiTokenVaultEntity;
import org.unreal.modelrouter.persistence.jpa.repository.PiiTokenVaultRepository;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 字段级脱敏策略（#988）。
 *
 * <p>配置了 {@code json-paths} 时只有命中路径的字符串值参与匹配；路径全未命中或
 * 文本不是 JSON 时回退到全文本匹配 —— 宁可多脱敏，不可漏脱敏。</p>
 */
class PiiJsonPathsTest {

    private static final String ID_CARD = "110101199003071234";
    private static final String MOBILE = "13812345678";

    private PiiTokenVaultRepository repository;
    private RecordContentCipher cipher;
    private PiiProperties properties;
    private ReversiblePiiService service;

    @BeforeEach
    void setUp() {
        repository = mock(PiiTokenVaultRepository.class);
        cipher = mock(RecordContentCipher.class);
        when(cipher.encrypt(any())).thenAnswer(inv -> "enc:" + inv.getArgument(0));
        when(cipher.decrypt(any())).thenAnswer(inv -> {
            final String arg = inv.getArgument(0);
            return arg.startsWith("enc:") ? arg.substring(4) : arg;
        });

        properties = new PiiProperties();
        properties.setEnabled(true);
        properties.setBuiltinRulesEnabled(true);

        service = new ReversiblePiiService(repository, cipher, properties);
        ReflectionTestUtils.setField(service, "objectMapper", JsonMapper.builder().build());
    }

    @Test
    void emptyJsonPathsKeepsWholeTextBehaviour() {
        final String text = "{\"a\":\"" + ID_CARD + "\",\"b\":\"" + MOBILE + "\"}";
        final String out = service.tokenizeNow("s1", text);
        assertFalse(out.contains(ID_CARD), "全文本模式应把两处都替换");
        assertFalse(out.contains(MOBILE));
    }

    @Test
    void onlyConfiguredPathIsReplaced() {
        properties.setJsonPaths(List.of("$.messages[*].content"));
        final String text = "{\"messages\":[{\"role\":\"user\",\"content\":\"id " + ID_CARD + "\"}],"
                + "\"metadata\":\"" + MOBILE + "\"}";
        final String out = service.tokenizeNow("s2", text);

        assertFalse(out.contains(ID_CARD), "content 里的身份证应被替换");
        assertTrue(out.contains(MOBILE), "metadata 不在路径里，应保持原样");
    }

    @Test
    void unmatchedPathFallsBackToWholeText() {
        properties.setJsonPaths(List.of("$.notThere.value"));
        final String text = "{\"a\":\"" + ID_CARD + "\"}";
        final String out = service.tokenizeNow("s3", text);

        // 路径未命中时回退全文本 —— 安全优先，不允许因为配错路径而漏脱敏
        assertTrue(out.contains("[PII_"), "未命中路径必须回退到全文本匹配");
        assertFalse(out.contains(ID_CARD));
    }

    @Test
    void nonJsonTextFallsBackToWholeText() {
        properties.setJsonPaths(List.of("$.messages[*].content"));
        final String out = service.tokenizeNow("s4", "纯文本里的 " + ID_CARD);

        assertTrue(out.contains("[PII_"));
        assertFalse(out.contains(ID_CARD));
    }

    @Test
    void numberingContinuesAcrossPaths() {
        properties.setJsonPaths(List.of("$.a", "$.b"));
        final String text = "{\"a\":\"" + ID_CARD + "\",\"b\":\"" + MOBILE + "\"}";
        final String out = service.tokenizeNow("s5", text);

        assertTrue(out.contains("[PII_1]"), "第一处应是 PII_1: " + out);
        assertTrue(out.contains("[PII_2]"), "第二处应是 PII_2（编号跨路径连续）: " + out);
    }

    @Test
    void detokenizeRestoresValuesInPathMode() {
        properties.setJsonPaths(List.of("$.content"));
        final List<PiiTokenVaultEntity> saved = new ArrayList<>();
        when(repository.saveAll(any())).thenAnswer(inv -> {
            saved.addAll(inv.getArgument(0));
            return inv.getArgument(0);
        });
        when(repository.findByScopeIdAndExpiresAtAfter(any(), any())).thenReturn(saved);

        final String out = service.tokenizeNow("s6", "{\"content\":\"" + ID_CARD + "\"}");
        final String restored = service.detokenizeNow("s6", out);

        assertTrue(restored.contains(ID_CARD), "还原后应看到原值: " + restored);
    }

    @Test
    void onePlaceholderPerMatchedValue() {
        properties.setJsonPaths(List.of("$.content"));
        final String out = service.tokenizeNow("s7", "{\"content\":\"" + ID_CARD + "\"}");
        assertEquals(1, service.countPlaceholders(out));
    }
}
