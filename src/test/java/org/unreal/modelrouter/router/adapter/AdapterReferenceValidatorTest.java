package org.unreal.modelrouter.router.adapter;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.unreal.modelrouter.router.model.ModelRouterProperties;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * {@link AdapterReferenceValidator} 的单元测试（#638）.
 *
 * <p>旧实现没有任何启动期校验：adapter 名写错要等首个命中该服务/实例的请求才抛
 * {@code IllegalArgumentException}（500）。这里覆盖三层引用（global / service / instance）、
 * strict-mode 的失败语义与禁用开关。
 */
@DisplayName("AdapterReferenceValidator adapter 引用校验")
class AdapterReferenceValidatorTest {

    private static AdapterRegistry registryKnowing(final String... names) {
        final AdapterRegistry registry = Mockito.mock(AdapterRegistry.class);
        final java.util.Set<String> registered = java.util.Set.of(names);
        when(registry.isAdapterSupported(anyString()))
                .thenAnswer(invocation -> registered.contains(invocation.getArgument(0, String.class)));
        return registry;
    }

    private static ModelRouterProperties propertiesWith(
            final String globalAdapter,
            final String chatAdapter,
            final String instanceAdapter,
            final String embeddingAdapter) {
        final ModelRouterProperties properties = new ModelRouterProperties();
        properties.setAdapter(globalAdapter);

        final ModelRouterProperties.ServiceConfig chat = new ModelRouterProperties.ServiceConfig();
        chat.setAdapter(chatAdapter);
        final ModelRouterProperties.ModelInstance instance = new ModelRouterProperties.ModelInstance();
        instance.setName("qwen3:4b");
        instance.setBaseUrl("http://172.16.30.6:9090");
        instance.setAdapter(instanceAdapter);
        chat.setInstances(List.of(instance));

        final ModelRouterProperties.ServiceConfig embedding = new ModelRouterProperties.ServiceConfig();
        embedding.setAdapter(embeddingAdapter);

        final Map<String, ModelRouterProperties.ServiceConfig> services = new HashMap<>();
        services.put("chat", chat);
        services.put("embedding", embedding);
        properties.setServices(services);
        return properties;
    }

    private static AdapterReferenceValidator validator(
            final ModelRouterProperties properties, final AdapterRegistry registry,
            final boolean enabled, final boolean strictMode) {
        return new AdapterReferenceValidator(properties, registry, enabled, strictMode);
    }

    @Test
    @DisplayName("全部 adapter 已注册时无任何问题")
    void allKnownAdaptersProduceNoIssues() {
        final AdapterRegistry registry = registryKnowing("gpustack", "ollama", "normal");
        final AdapterReferenceValidator validator = validator(
                propertiesWith("gpustack", "ollama", "gpustack", "normal"), registry, true, true);

        assertTrue(validator.findUnknownReferences().isEmpty());
        assertDoesNotThrow(validator::validate);
    }

    @Test
    @DisplayName("服务级写错：清单里带服务名与 adapter 名")
    void detectsUnknownServiceAdapter() {
        final AdapterRegistry registry = registryKnowing("gpustack", "normal");
        final AdapterReferenceValidator validator = validator(
                propertiesWith("gpustack", "openai-compat", null, "normal"), registry, true, false);

        final List<AdapterReferenceValidator.UnknownAdapterReference> unknown =
                validator.findUnknownReferences();

        assertEquals(1, unknown.size());
        assertTrue(unknown.get(0).describe().contains("scope=service"));
        assertTrue(unknown.get(0).describe().contains("service=chat"));
        assertTrue(unknown.get(0).describe().contains("adapter=openai-compat"));
    }

    @Test
    @DisplayName("实例级写错：清单里带实例名，便于定位是哪台机器")
    void detectsUnknownInstanceAdapter() {
        final AdapterRegistry registry = registryKnowing("gpustack", "normal");
        final AdapterReferenceValidator validator = validator(
                propertiesWith("gpustack", "gpustack", "ollama-typo", "normal"), registry, true, false);

        final List<AdapterReferenceValidator.UnknownAdapterReference> unknown =
                validator.findUnknownReferences();

        assertEquals(1, unknown.size());
        assertTrue(unknown.get(0).describe().contains("scope=instance"));
        assertTrue(unknown.get(0).describe().contains("instance=qwen3:4b"));
        assertTrue(unknown.get(0).describe().contains("adapter=ollama-typo"));
    }

    @Test
    @DisplayName("全局 model.adapter 写错也会被抓到")
    void detectsUnknownGlobalAdapter() {
        final AdapterRegistry registry = registryKnowing("normal");
        final AdapterReferenceValidator validator = validator(
                propertiesWith("not-exists", null, null, null), registry, true, false);

        final List<AdapterReferenceValidator.UnknownAdapterReference> unknown =
                validator.findUnknownReferences();

        assertEquals(1, unknown.size());
        assertEquals("global", unknown.get(0).scope());
        assertEquals("not-exists", unknown.get(0).adapterName());
    }

    @Test
    @DisplayName("空白 adapter 视为未配置，不参与校验")
    void blankAdapterIsIgnored() {
        final AdapterRegistry registry = registryKnowing("normal");
        final AdapterReferenceValidator validator = validator(
                propertiesWith("  ", "", null, null), registry, true, true);

        assertTrue(validator.findUnknownReferences().isEmpty());
    }

    @Test
    @DisplayName("strict-mode 打开时未注册引用让启动失败（A/B 的 A）")
    void strictModeFailsFast() {
        final AdapterRegistry registry = registryKnowing("normal");
        final AdapterReferenceValidator validator = validator(
                propertiesWith("gpustack", null, null, null), registry, true, true);

        final IllegalStateException error = assertThrows(IllegalStateException.class, validator::validate);

        assertTrue(error.getMessage().contains("adapter=gpustack"));
        assertTrue(error.getMessage().contains("1 处未注册的 adapter"));
    }

    @Test
    @DisplayName("非 strict-mode 只记日志、继续启动（A/B 的 B：旧行为等价但不静默）")
    void nonStrictModeOnlyLogs() {
        final AdapterRegistry registry = registryKnowing("normal");
        final AdapterReferenceValidator validator = validator(
                propertiesWith("gpustack", null, null, null), registry, true, false);

        assertDoesNotThrow(validator::validate);
        assertEquals(1, validator.findUnknownReferences().size());
    }

    @Test
    @DisplayName("校验被禁用时即使 strict-mode 也不拦启动")
    void disabledValidationNeverFails() {
        final AdapterRegistry registry = registryKnowing("normal");
        final AdapterReferenceValidator validator = validator(
                propertiesWith("gpustack", null, null, null), registry, false, true);

        assertDoesNotThrow(validator::validate);
    }

    @Test
    @DisplayName("多个问题按描述排序返回，便于比对日志")
    void issuesAreSorted() {
        final AdapterRegistry registry = registryKnowing("normal");
        final AdapterReferenceValidator validator = validator(
                propertiesWith("bad-global", "bad-service", "bad-instance", "normal"), registry, true, false);

        final List<String> lines = validator.findUnknownReferences().stream()
                .map(AdapterReferenceValidator.UnknownAdapterReference::describe)
                .toList();

        assertEquals(3, lines.size());
        assertEquals(lines.stream().sorted().toList(), lines);
    }

    @Test
    @DisplayName("注册表未命中判断走 isAdapterSupported（大小写由注册表负责）")
    void usesRegistrySupportCheck() {
        final AdapterRegistry registry = Mockito.mock(AdapterRegistry.class);
        when(registry.isAdapterSupported(anyString())).thenReturn(false);
        final AdapterReferenceValidator validator = validator(
                propertiesWith("GPUStack", null, null, null), registry, true, false);

        assertEquals(1, validator.findUnknownReferences().size());
    }
}
