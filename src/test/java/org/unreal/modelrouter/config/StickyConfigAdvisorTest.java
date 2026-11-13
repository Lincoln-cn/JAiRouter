package org.unreal.modelrouter.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.unreal.modelrouter.router.model.ModelRouterProperties;
import org.unreal.modelrouter.router.model.ModelRouterProperties.ModelInstance;
import org.unreal.modelrouter.router.model.ModelRouterProperties.ServiceConfig;
import org.unreal.modelrouter.router.model.ModelRouterProperties.StickyConfig;

/**
 * {@link StickyConfigAdvisor} 的判定与文案测试（#1126）。
 */
@DisplayName("会话粘性配置提示（#1126）")
class StickyConfigAdvisorTest {

    private static ModelInstance instance(final String baseUrl) {
        final ModelInstance instance = new ModelInstance();
        instance.setName("qwen3:4b");
        instance.setBaseUrl(baseUrl);
        instance.setPath("/v1/chat/completions");
        return instance;
    }

    private static ServiceConfig service(final int instanceCount, final Boolean stickyEnabled) {
        final ServiceConfig service = new ServiceConfig();
        final List<ModelInstance> instances = new ArrayList<>();
        for (int i = 0; i < instanceCount; i++) {
            instances.add(instance("http://127.0.0.1:910" + i));
        }
        service.setInstances(instances);
        if (stickyEnabled != null) {
            final StickyConfig sticky = new StickyConfig();
            sticky.setEnabled(stickyEnabled);
            service.setSticky(sticky);
        }
        return service;
    }

    private static ModelRouterProperties properties(final Map<String, ServiceConfig> services) {
        final ModelRouterProperties properties = new ModelRouterProperties();
        properties.setServices(services);
        return properties;
    }

    @Test
    @DisplayName("属性对象为 null 或没有服务时不提示")
    void absentConfigurationReportsNothing() {
        assertTrue(StickyConfigAdvisor.findIneffectiveStickiness(null).isEmpty());
        assertTrue(StickyConfigAdvisor.findIneffectiveStickiness(properties(null)).isEmpty());
        assertTrue(StickyConfigAdvisor.findIneffectiveStickiness(properties(Map.of())).isEmpty());
    }

    @Test
    @DisplayName("没有 sticky 段或 enabled 非 true 时不提示")
    void stickyDisabledOrAbsentIsIgnored() {
        final Map<String, ServiceConfig> services = new LinkedHashMap<>();
        services.put("chat", service(2, null));
        services.put("embedding", service(3, Boolean.FALSE));
        services.put("rerank", service(3, null));

        assertTrue(StickyConfigAdvisor.findIneffectiveStickiness(properties(services)).isEmpty());
    }

    @Test
    @DisplayName("单实例即使开了粘性也不提示（没有可分流的对象）")
    void singleInstanceIsIgnored() {
        final Map<String, ServiceConfig> services = new LinkedHashMap<>();
        services.put("chat", service(1, Boolean.TRUE));

        assertTrue(StickyConfigAdvisor.findIneffectiveStickiness(properties(services)).isEmpty());
    }

    @Test
    @DisplayName("开了粘性且实例数大于 1 时提示，并带上实例数")
    void enabledWithMultipleInstancesIsReported() {
        final Map<String, ServiceConfig> services = new LinkedHashMap<>();
        services.put("chat", service(2, Boolean.TRUE));

        final List<StickyConfigAdvisor.IneffectiveSticky> findings =
                StickyConfigAdvisor.findIneffectiveStickiness(properties(services));

        assertEquals(1, findings.size());
        assertEquals("chat", findings.get(0).serviceType());
        assertEquals(2, findings.get(0).instanceCount());
    }

    @Test
    @DisplayName("只报开了粘性的那个服务，其余服务保持静默")
    void onlyConstrainedServiceIsReported() {
        final Map<String, ServiceConfig> services = new LinkedHashMap<>();
        services.put("embedding", service(3, Boolean.FALSE));
        services.put("chat", service(2, Boolean.TRUE));
        services.put("tts", service(2, null));

        final List<StickyConfigAdvisor.IneffectiveSticky> findings =
                StickyConfigAdvisor.findIneffectiveStickiness(properties(services));

        assertEquals(List.of("chat"), findings.stream()
                .map(StickyConfigAdvisor.IneffectiveSticky::serviceType)
                .toList());
    }

    @Test
    @DisplayName("告警文案带固定检索标识、服务名与可执行的修复指引")
    void descriptionCarriesMarkerAndFixHint() {
        final String message = StickyConfigAdvisor.describe(
                List.of(new StickyConfigAdvisor.IneffectiveSticky("chat", 2)));

        assertTrue(message.startsWith(StickyConfigAdvisor.LOG_MARKER));
        assertTrue(message.contains("chat"));
        assertTrue(message.contains("2 个实例"));
        assertTrue(message.contains("/api/config/type/services/<serviceType>"));
        assertTrue(message.contains("\"sticky\":{\"enabled\":false}"));
        assertFalse(message.contains("null"));
    }
}
