package org.unreal.modelrouter.router.adapter;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.unreal.modelrouter.router.adapter.config.AdapterDefinitionProperties;
import org.unreal.modelrouter.router.adapter.persistence.AdapterDefinitionPersistenceService;
import org.unreal.modelrouter.router.adapter.support.AdapterContext;
import org.unreal.modelrouter.router.adapter.support.RequestProcessingSupport;
import org.unreal.modelrouter.router.adapter.support.ResilienceSupport;
import org.unreal.modelrouter.router.adapter.transformer.OpenAiRequestTransformer;
import org.unreal.modelrouter.router.adapter.transformer.OpenAiResponseTransformer;
import org.unreal.modelrouter.router.model.ModelRouterProperties;
import org.unreal.modelrouter.router.model.ModelServiceRegistry;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * #778: 适配器加载失败必须留下可查证据，而不是只写一行日志。
 */
class AdapterRegistryLoadFailureTest {

    private AdapterRegistry newRegistry(final Map<String, AdapterDefinitionProperties.AdapterDefinition> definitions) {
        final AdapterDefinitionProperties properties = new AdapterDefinitionProperties();
        properties.setAdapterDefinitions(definitions);
        final AdapterDefinitionPersistenceService persistenceService =
                Mockito.mock(AdapterDefinitionPersistenceService.class);
        Mockito.when(persistenceService.loadAllDefinitions()).thenReturn(new HashMap<>());
        return new AdapterRegistry(
                new ModelRouterProperties(),
                Mockito.mock(ModelServiceRegistry.class),
                Mockito.mock(AdapterContext.class),
                Mockito.mock(RequestProcessingSupport.class),
                Mockito.mock(ResilienceSupport.class),
                Mockito.mock(OpenAiRequestTransformer.class),
                Mockito.mock(OpenAiResponseTransformer.class),
                properties,
                persistenceService);
    }

    private static AdapterDefinitionProperties.AdapterDefinition definition(final String type, final String parent) {
        final AdapterDefinitionProperties.AdapterDefinition definition =
                new AdapterDefinitionProperties.AdapterDefinition();
        definition.setType(type);
        definition.setParent(parent);
        return definition;
    }

    @Test
    @DisplayName("构造失败的适配器进入失败清单，加载成功的适配器不在清单里")
    void failedDefinitionIsRecorded() {
        final Map<String, AdapterDefinitionProperties.AdapterDefinition> definitions = new HashMap<>();
        definitions.put("healthy", definition("openai-compatible", null));
        definitions.put("broken", definition("openai-compatible", null));
        // capabilities 为 null ⇒ createConfigurableAdapter 解引用即 NPE（真实故障形态之一）
        definitions.get("broken").setCapabilities(null);
        final AdapterRegistry registry = newRegistry(definitions);

        assertThat(registry.getAdapterByName("healthy")).isNotNull();
        assertThat(registry.getAdapterByName("broken")).isNull();

        final Map<String, String> failures = registry.getLoadFailures();
        assertThat(failures).containsKey("broken");
        assertThat(failures).doesNotContainKey("healthy");
        assertThat(failures.get("broken")).contains("YAML").contains("NullPointerException");
    }

    @Test
    @DisplayName("extend 的 parent 不存在时，子适配器进入失败清单并写明原因")
    void extendedAdapterWithMissingParentIsRecorded() {
        final Map<String, AdapterDefinitionProperties.AdapterDefinition> definitions = new HashMap<>();
        definitions.put("child", definition("extend", "missing-parent"));
        final AdapterRegistry registry = newRegistry(definitions);

        assertThat(registry.getLoadFailures().get("child"))
                .contains("YAML")
                .contains("Parent adapter 'missing-parent' not found");
    }

    @Test
    @DisplayName("同一个适配器后来写入成功，历史失败记录被清掉")
    void registerAdapterClearsHistoricalFailure() {
        final Map<String, AdapterDefinitionProperties.AdapterDefinition> definitions = new HashMap<>();
        definitions.put("broken", definition("openai-compatible", null));
        definitions.get("broken").setCapabilities(null);
        final AdapterRegistry registry = newRegistry(definitions);
        assertThat(registry.getLoadFailures()).containsKey("broken");

        registry.registerAdapter("broken", Mockito.mock(ServiceCapability.class));

        assertThat(registry.getLoadFailures()).doesNotContainKey("broken");
        assertThat(registry.getAdapterByName("broken")).isNotNull();
    }

    @Test
    @DisplayName("全部加载成功时清单为空")
    void noFailureWhenEverythingLoads() {
        final Map<String, AdapterDefinitionProperties.AdapterDefinition> definitions = new HashMap<>();
        definitions.put("healthy", definition("openai-compatible", null));
        final AdapterRegistry registry = newRegistry(definitions);

        assertThat(registry.getLoadFailures()).isEmpty();
    }
}
