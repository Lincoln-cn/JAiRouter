package org.unreal.modelrouter.config.core;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;
import org.unreal.modelrouter.router.model.ModelRouterProperties;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 实例配置完整性校验（#963）。
 *
 * <p>最关键的一例是 {@link #environmentOverrideReplacesWholeInstanceList()}：
 * 它用真实的 {@link Binder} 复现「环境变量覆盖实例级属性」时的绑定结果，证明
 * 校验器拦的正是这个机制，而不是构造出来的假数据。</p>
 *
 * @since 3.6.1
 */
@DisplayName("实例配置完整性校验")
class ModelInstanceConfigValidatorTest {

    private static ModelRouterProperties bind(final Map<String, Object> properties) {
        final MapConfigurationPropertySource source = new MapConfigurationPropertySource(properties);
        return new Binder(source)
                .bind("model", Bindable.of(ModelRouterProperties.class))
                .orElseGet(ModelRouterProperties::new);
    }

    private static ModelRouterProperties propertiesWith(final ModelRouterProperties.ModelInstance... instances) {
        final ModelRouterProperties properties = new ModelRouterProperties();
        final ModelRouterProperties.ServiceConfig service = new ModelRouterProperties.ServiceConfig();
        service.setInstances(List.of(instances));
        properties.setServices(Map.of("chat", service));
        return properties;
    }

    private static ModelRouterProperties.ModelInstance instance(final String name, final String baseUrl) {
        final ModelRouterProperties.ModelInstance instance = new ModelRouterProperties.ModelInstance();
        instance.setName(name);
        instance.setBaseUrl(baseUrl);
        return instance;
    }

    @Test
    @DisplayName("完整配置通过")
    void completeConfigPasses() {
        final ModelRouterProperties properties = propertiesWith(instance("qwen3:4b", "http://127.0.0.1:18080"));

        assertThat(ModelInstanceConfigValidator.collectProblems(properties)).isEmpty();
        assertThatCode(() -> ModelInstanceConfigValidator.assertValid(properties)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("缺 baseUrl 时指出是哪个服务的第几个实例")
    void reportsMissingBaseUrl() {
        final ModelRouterProperties properties = propertiesWith(instance("qwen3:4b", null));

        assertThat(ModelInstanceConfigValidator.collectProblems(properties))
                .singleElement()
                .asString()
                .contains("服务 chat 的第 1 个实例")
                .contains("baseUrl 为空");
    }

    @Test
    @DisplayName("缺 name 时同样指出位置")
    void reportsMissingName() {
        final ModelRouterProperties properties = propertiesWith(instance(" ", "http://127.0.0.1:18080"));

        assertThat(ModelInstanceConfigValidator.collectProblems(properties))
                .singleElement()
                .asString()
                .contains("name 为空");
    }

    @Test
    @DisplayName("同一服务内的同名实例不报错")
    void duplicateNamesAreAllowed() {
        // 仓库自带的 config/router/services.yml 里 embedding 就有两个同名实例
        // （bge-large-zh-v1.5，base-url 差一个尾斜杠）。同名不是本校验要拦的问题 ——
        // 拦它会让既有配置起不来，那是另一件需要单独定夺的事。
        final ModelRouterProperties properties = propertiesWith(
                instance("bge-large-zh-v1.5", "http://127.0.0.1:18080"),
                instance("bge-large-zh-v1.5", "http://127.0.0.1:18081"));

        assertThat(ModelInstanceConfigValidator.collectProblems(properties)).isEmpty();
    }

    @Test
    @DisplayName("抛出的消息里写明环境变量覆盖这个常见原因")
    void messageExplainsTheCommonCause() {
        final ModelRouterProperties properties = propertiesWith(instance(null, null));

        assertThatThrownBy(() -> ModelInstanceConfigValidator.assertValid(properties))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("实例配置不完整，启动中止")
                .hasMessageContaining("MODEL_SERVICES_<TYPE>_INSTANCES_0_")
                .hasMessageContaining("替换成只含被覆盖字段的一个元素");
    }

    @Test
    @DisplayName("环境变量覆盖实例级属性时，绑定结果是整个列表被替换（#963 的根因）")
    void environmentOverrideReplacesWholeInstanceList() {
        // 只提供实例级的一个字段，模拟 MODEL_SERVICES_CHAT_INSTANCES_0_RATELIMIT_ENABLED=false
        final Map<String, Object> envLike = new LinkedHashMap<>();
        envLike.put("model.services.chat.instances[0].rate-limit.enabled", "false");

        final ModelRouterProperties properties = bind(envLike);
        final List<ModelRouterProperties.ModelInstance> instances =
                properties.getServices().get("chat").getInstances();

        assertThat(instances)
                .as("Spring Boot 对 instances[0].xxx 的绑定结果是新建只含该元素的列表")
                .hasSize(1);
        assertThat(instances.get(0).getName()).isNull();
        assertThat(instances.get(0).getBaseUrl()).isNull();
        assertThat(instances.get(0).getRateLimit()).isNotNull();
        assertThat(instances.get(0).getRateLimit().getEnabled()).isFalse();

        // 正是这种绑定结果会在落库时撞 NULL not allowed for column "base_url"
        assertThatThrownBy(() -> ModelInstanceConfigValidator.assertValid(properties))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("baseUrl 为空");
    }

    @Test
    @DisplayName("没有任何服务配置时不报错（由其它校验负责）")
    void emptyServicesPass() {
        assertThat(ModelInstanceConfigValidator.collectProblems(new ModelRouterProperties())).isEmpty();
    }
}
