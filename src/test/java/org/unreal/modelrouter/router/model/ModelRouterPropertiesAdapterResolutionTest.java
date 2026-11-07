package org.unreal.modelrouter.router.model;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * 实例适配器的回退顺序（#1004）。
 *
 * <p>回归的是「实例健康但调用 502」：实例行落库时 adapter 为空，网关注入适配器拿到
 * 空值，转发阶段直接失败，而健康检查只测端口仍报 HEALTHY。</p>
 */
class ModelRouterPropertiesAdapterResolutionTest {

    @Test
    void instanceLevelWins() {
        final ModelRouterProperties.ModelInstance instance = instance("anthropic");
        final ModelRouterProperties.ServiceConfig service = service("gpustack");

        assertThat(ModelRouterProperties.resolveAdapter(instance, service, "normal"))
                .isEqualTo("anthropic");
    }

    @Test
    void fallsBackToServiceLevelWhenInstanceIsBlank() {
        final ModelRouterProperties.ModelInstance instance = instance(null);
        final ModelRouterProperties.ServiceConfig service = service("gpustack");

        assertThat(ModelRouterProperties.resolveAdapter(instance, service, "normal"))
                .as("实例级留空是常态，必须回退到服务级，否则落库后 adapter 为空")
                .isEqualTo("gpustack");
    }

    @Test
    void fallsBackToGlobalWhenServiceIsBlank() {
        final ModelRouterProperties.ModelInstance instance = instance("   ");
        final ModelRouterProperties.ServiceConfig service = service(null);

        assertThat(ModelRouterProperties.resolveAdapter(instance, service, "normal"))
                .as("空白字符串等同于未配置")
                .isEqualTo("normal");
    }

    @Test
    void toleratesNullInstanceAndService() {
        assertThat(ModelRouterProperties.resolveAdapter(null, null, "normal")).isEqualTo("normal");
    }

    private ModelRouterProperties.ModelInstance instance(final String adapter) {
        final ModelRouterProperties.ModelInstance instance = new ModelRouterProperties.ModelInstance();
        instance.setName("qwen3:4b");
        instance.setBaseUrl("http://upstream:8080");
        instance.setAdapter(adapter);
        return instance;
    }

    private ModelRouterProperties.ServiceConfig service(final String adapter) {
        final ModelRouterProperties.ServiceConfig service = new ModelRouterProperties.ServiceConfig();
        service.setAdapter(adapter);
        return service;
    }
}
