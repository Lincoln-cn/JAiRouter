package org.unreal.modelrouter.auth.security.quota;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * {@link QuotaDimension} 哨兵值与维度隔离测试（v3.1 PR-1）。
 *
 * @author JAiRouter Team
 * @since 3.1.0
 */
@DisplayName("QuotaDimension 维度哨兵值测试")
class QuotaDimensionTest {

    @Test
    @DisplayName("null 维度统一折叠为空串哨兵值（不使用 null）")
    void nullComponents_shouldBeNormalizedToSentinel() {
        final QuotaDimension dimension = new QuotaDimension(null, null, null, null, null);

        assertEquals(QuotaDimension.SENTINEL, dimension.tenantId());
        assertEquals(QuotaDimension.SENTINEL, dimension.apiKeyId());
        assertEquals(QuotaDimension.SENTINEL, dimension.userId());
        assertEquals(QuotaDimension.SENTINEL, dimension.serviceType());
        assertEquals(QuotaDimension.SENTINEL, dimension.model());
        assertEquals("", QuotaDimension.SENTINEL);
    }

    @Test
    @DisplayName("ofApiKey / empty 工厂方法返回哨兵值维度")
    void factories_shouldUseSentinel() {
        final QuotaDimension byKey = QuotaDimension.ofApiKey("key-1");

        assertEquals("key-1", byKey.apiKeyId());
        assertEquals("", byKey.tenantId());
        assertEquals("", byKey.model());
        assertEquals(QuotaDimension.empty(), new QuotaDimension(null, "", "", "", ""));
        assertEquals(QuotaDimension.empty(), QuotaDimension.ofApiKey(""));
    }

    @Test
    @DisplayName("null 与空串等价：归一化后 equals/hashCode 一致")
    void nullAndEmptyString_shouldBeEquivalent() {
        final QuotaDimension fromNulls = new QuotaDimension(null, "key-1", null, null, null);
        final QuotaDimension fromEmpty = new QuotaDimension("", "key-1", "", "", "");

        assertEquals(fromEmpty, fromNulls);
        assertEquals(fromEmpty.hashCode(), fromNulls.hashCode());
    }

    @Test
    @DisplayName("维度隔离：任一维度不同即为不同账本键")
    void differentComponents_shouldNotBeEqual() {
        final QuotaDimension base = new QuotaDimension("t1", "key-1", "u1", "chat", "gpt-a");

        assertNotEquals(base, new QuotaDimension("t2", "key-1", "u1", "chat", "gpt-a"));
        assertNotEquals(base, new QuotaDimension("t1", "key-2", "u1", "chat", "gpt-a"));
        assertNotEquals(base, new QuotaDimension("t1", "key-1", "u2", "chat", "gpt-a"));
        assertNotEquals(base, new QuotaDimension("t1", "key-1", "u1", "embedding", "gpt-a"));
        assertNotEquals(base, new QuotaDimension("t1", "key-1", "u1", "chat", "gpt-b"));
        assertEquals(base, new QuotaDimension("t1", "key-1", "u1", "chat", "gpt-a"));
    }

    @Test
    @DisplayName("record 组件访问器不返回 null")
    void accessors_shouldNeverReturnNull() {
        final QuotaDimension dimension = QuotaDimension.ofApiKey(null);

        assertNotNull(dimension.tenantId());
        assertNotNull(dimension.apiKeyId());
        assertNotNull(dimension.userId());
        assertNotNull(dimension.serviceType());
        assertNotNull(dimension.model());
        assertEquals("", dimension.apiKeyId());
    }
}
