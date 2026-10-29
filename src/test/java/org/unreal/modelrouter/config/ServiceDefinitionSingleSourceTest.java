package org.unreal.modelrouter.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.env.EnumerablePropertySource;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.unreal.modelrouter.router.model.ModelRouterProperties;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 守卫测试：{@code model.services} 只能有一处定义（#643）。
 *
 * <p>背景：{@code config/router/services.yml} 与 {@code config/base/model-services-base.yml}
 * 曾各定义一份 {@code model.services}，而 {@code spring.config.import} 是「后导入者优先」，
 * 于是 base 那份静默覆盖了 router 那份 —— 改 router 文件不生效、且没人知道。</p>
 *
 * <p>两条断言互补：第一条守「结构上只有一处定义」，第二条守「生效内容仍是合并前的那一套」，
 * 避免以后谁把定义搬到别的文件却顺手改了默认实例。</p>
 *
 * @author JAiRouter Team
 * @since v3.5.3
 */
@DisplayName("model.services 单一定义守卫（#643）")
class ServiceDefinitionSingleSourceTest {

    private static final String SERVICES_KEY = "model.services";
    private static final String EXPECTED_OWNER = "config/router/services.yml";

    @Test
    @DisplayName("classpath 配置里只有 config/router/services.yml 定义 model.services")
    void onlyRouterServicesFileDefinesServices() throws Exception {
        final PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
        final Resource[] resources = concat(
                resolver.getResources("classpath*:config/**/*.yml"),
                resolver.getResources("classpath*:application*.yml"));

        final List<String> owners = new ArrayList<>();
        int scanned = 0;
        for (final Resource resource : resources) {
            final String path = normalize(resource.getURL().toString());
            if (path.contains("/test/") || path.contains("target/test-classes")) {
                continue;
            }
            scanned++;
            final List<PropertySource<?>> sources =
                    new YamlPropertySourceLoader().load(path, resource);
            final boolean definesServices = sources.stream()
                    .filter(source -> source instanceof EnumerablePropertySource)
                    .map(source -> (EnumerablePropertySource<?>) source)
                    .flatMap(source -> java.util.Arrays.stream(source.getPropertyNames()))
                    .anyMatch(name -> name.startsWith(SERVICES_KEY + '.'));
            if (definesServices) {
                owners.add(path);
            }
        }

        // 反空转：扫描面必须真的覆盖到配置文件，否则「找不到定义」会让断言假通过。
        assertTrue(scanned > 20, "扫描到的 yml 数量异常（" + scanned + "），枚举逻辑可能失效");
        assertEquals(List.of(EXPECTED_OWNER), owners,
                "model.services 必须且只能由 " + EXPECTED_OWNER + " 定义，实际定义方 = " + owners);
    }

    @Test
    @DisplayName("合并后默认实例仍是 qwen3:4b / gpustack，行为不变")
    void defaultsAreUnchangedAfterSingleSourcing() {
        new ApplicationContextRunner()
                .withInitializer(new ConfigDataApplicationContextInitializer())
                .run(context -> {
                    final Binder binder = Binder.get(context.getEnvironment());
                    final ModelRouterProperties properties = binder
                            .bind("model", Bindable.of(ModelRouterProperties.class))
                            .orElseThrow(() -> new AssertionError("model.* 绑定失败"));

                    final ModelRouterProperties.ServiceConfig chat = properties.getServices().get("chat");
                    assertNotNull(chat, "chat 服务必须存在");
                    assertEquals(1, chat.getInstances().size(), "chat 默认实例数量应保持 1 个");
                    assertEquals("qwen3:4b", chat.getInstances().get(0).getName(),
                            "chat 默认实例名应与合并前（base 文件生效时）一致");
                    // #804：默认上游不再是内网示例地址 172.16.30.6:9090，
                    // 而是 MODEL_UPSTREAM_BASE_URL 的兜底值（本机 Ollama）。
                    assertEquals("http://localhost:11434", chat.getInstances().get(0).getBaseUrl(),
                            "chat 默认上游应回落到 MODEL_UPSTREAM_BASE_URL 的默认值");
                    assertEquals("gpustack", chat.getAdapter(),
                            "chat 服务级 adapter 应与合并前一致");

                    assertEquals(3, properties.getServices().get("embedding").getInstances().size(),
                            "embedding 默认实例数应与合并前一致");
                    assertEquals("gpustack", properties.getAdapter(), "全局 adapter 仍应来自 base 文件");
                });
    }

    private static String normalize(final String url) {
        final String trimmed = url.replace("file:", "");
        final int index = trimmed.indexOf("!/");
        final String withoutJar = index >= 0 ? trimmed.substring(0, index) : trimmed;
        final String normalized = withoutJar.replace('\\', '/');
        final int marker = normalized.indexOf("/config/");
        if (marker >= 0) {
            return normalized.substring(marker + 1);
        }
        final int markerNoSlash = normalized.indexOf("config/");
        return markerNoSlash >= 0 ? normalized.substring(markerNoSlash) : normalized;
    }

    private static Resource[] concat(final Resource[] first, final Resource[] second) {
        final Resource[] all = new Resource[first.length + second.length];
        System.arraycopy(first, 0, all, 0, first.length);
        System.arraycopy(second, 0, all, first.length, second.length);
        return all;
    }
}
