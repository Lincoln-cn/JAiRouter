package org.unreal.modelrouter.config.core.dto;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * {@link ServiceConfiguration} 与 sticky 块的往返测试（issue #1095）。
 *
 * <p>这一层的意义：{@code GET /api/config/type/services/{serviceType}} 的响应体就是
 * {@code ServiceConfiguration#toMap()} 的结果。若这里丢掉 sticky，运维在控制台和接口上都看不到
 * 粘性当前是开是关，「关闭是否生效」也无从回读确认。
 */
class ServiceConfigurationStickyTest {

    @Test
    @DisplayName("sticky 块能原样往返：关闭状态不回退成默认开启")
    void stickyDisabled_roundTrips() {
        final Map<String, Object> input = Map.of(
                "adapter", "gpustack",
                "sticky", Map.of("enabled", false, "affinityKeyScope", "instance")
        );

        final Map<String, Object> out = ServiceConfiguration.fromMap(input).toMap();

        assertTrue(out.containsKey("sticky"), "读面必须回显 sticky，否则无法确认关闭是否生效");
        @SuppressWarnings("unchecked")
        final Map<String, Object> sticky = (Map<String, Object>) out.get("sticky");
        assertEquals(Boolean.FALSE, sticky.get("enabled"));
        assertEquals("instance", sticky.get("affinityKeyScope"));
    }

    @Test
    @DisplayName("字符串形式的 false 同样能识别（配置树可能来自 YAML 文本）")
    void stickyDisabledAsString_isParsed() {
        final Map<String, Object> out = ServiceConfiguration.fromMap(
                Map.of("sticky", Map.of("enabled", "false"))).toMap();

        @SuppressWarnings("unchecked")
        final Map<String, Object> sticky = (Map<String, Object>) out.get("sticky");
        assertEquals(Boolean.FALSE, sticky.get("enabled"));
    }

    @Test
    @DisplayName("无法识别的 enabled 不写成 null，避免被当成「已关闭」")
    void unparsableEnabled_isOmitted() {
        final Map<String, Object> out = ServiceConfiguration.fromMap(
                Map.of("sticky", Map.of("enabled", "maybe"))).toMap();

        @SuppressWarnings("unchecked")
        final Map<String, Object> sticky = (Map<String, Object>) out.get("sticky");
        assertNotNull(sticky, "块本身存在，只是 enabled 不输出");
        assertFalse(sticky.containsKey("enabled"), "解析不了就不要输出，让下游回落默认值");
    }

    @Test
    @DisplayName("没有 sticky 块时读面不凭空造一个，保持与改动前一致")
    void missingSticky_isAbsent() {
        final Map<String, Object> out = ServiceConfiguration.fromMap(Map.of("adapter", "normal")).toMap();
        assertFalse(out.containsKey("sticky"));
    }

    @Test
    @DisplayName("旧 5 参构造器仍可用，且不会输出 sticky（向后兼容）")
    void legacyConstructor_stillWorks() {
        final ServiceConfiguration config = new ServiceConfiguration(
                "normal", List.of(), null, null, null, null);

        assertNull(config.sticky());
        assertFalse(config.toMap().containsKey("sticky"));
    }

    @Test
    @DisplayName("默认配置里粘性是开启的，与服务端缺省一致")
    void defaultConfig_stickyEnabled() {
        final ServiceConfiguration config = ServiceConfiguration.defaultConfig();
        assertNotNull(config.sticky());
        assertEquals(Boolean.TRUE, config.sticky().enabled());
        assertEquals("tenant_model", config.sticky().affinityKeyScope());
    }
}
