package org.unreal.modelrouter.config.core.helper;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.unreal.modelrouter.router.model.ModelRouterProperties;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * #1095 回归：配置里的 sticky 块必须被读进 {@code ServiceConfig}。
 *
 * <p>这条链路是「关掉会话粘性」能否生效的唯一通道：
 * DB/YAML 的 {@code services.<type>.sticky} → {@code convertMapToServiceConfig} →
 * {@code originalProperties} → {@code ModelServiceRegistry.getStickyScope}。
 * 中间任何一环丢掉 sticky，getStickyScope 就会回落到「默认 tenant_model」，
 * 表现为「配置写了 false、路由却照样粘住」。
 */
class ConfigConverterHelperStickyTest {

    private final ConfigConverterHelper helper = new ConfigConverterHelper();

    @Test
    @DisplayName("sticky.enabled=false 会被读进 ServiceConfig")
    void stickyDisabled_isMapped() {
        final Map<String, Object> sticky = new LinkedHashMap<>();
        sticky.put("enabled", false);

        final Map<String, Object> serviceMap = new LinkedHashMap<>();
        serviceMap.put("adapter", "gpustack");
        serviceMap.put("sticky", sticky);

        final ModelRouterProperties.ServiceConfig config = helper.convertMapToServiceConfig(serviceMap);

        assertNotNull(config.getSticky(), "sticky 块必须被映射，否则关闭粘性永远不生效");
        assertEquals(Boolean.FALSE, config.getSticky().getEnabled());
    }

    @Test
    @DisplayName("sticky 缺失时 getSticky() 为 null（保持调用方的默认语义）")
    void stickyAbsent_returnsNull() {
        final Map<String, Object> serviceMap = new LinkedHashMap<>();
        serviceMap.put("adapter", "gpustack");

        final ModelRouterProperties.ServiceConfig config = helper.convertMapToServiceConfig(serviceMap);

        assertNull(config.getSticky());
    }

    @Test
    @DisplayName("字符串形式的 \"false\" 也能解析（YAML 之外的来源可能给字符串）")
    void stickyDisabledAsString_isMapped() {
        final Map<String, Object> sticky = new LinkedHashMap<>();
        sticky.put("enabled", "false");
        sticky.put("affinityKeyScope", "tenant");

        final Map<String, Object> serviceMap = new LinkedHashMap<>();
        serviceMap.put("sticky", sticky);

        final ModelRouterProperties.ServiceConfig config = helper.convertMapToServiceConfig(serviceMap);

        assertNotNull(config.getSticky());
        assertEquals(Boolean.FALSE, config.getSticky().getEnabled());
        assertEquals("tenant", config.getSticky().getAffinityKeyScope());
    }

    @Test
    @DisplayName("无法识别的 enabled 不会把开关关掉（保持默认值，3.8.6 起为 false）")
    void stickyUnparsableEnabled_keepsDefault() {
        final Map<String, Object> sticky = new LinkedHashMap<>();
        sticky.put("enabled", "maybe");

        final Map<String, Object> serviceMap = new LinkedHashMap<>();
        serviceMap.put("sticky", sticky);

        final ModelRouterProperties.ServiceConfig config = helper.convertMapToServiceConfig(serviceMap);

        assertNotNull(config.getSticky());
        assertEquals(Boolean.FALSE, config.getSticky().getEnabled(),
                "解析失败必须保持默认值——写成 null 会被 getStickyScope 当成「已关闭」");
    }
}
