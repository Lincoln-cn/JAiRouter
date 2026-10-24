package org.unreal.modelrouter.router.ratelimit.enduser;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.BindException;
import org.springframework.boot.context.properties.bind.BindHandler;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.bind.UnboundConfigurationPropertiesException;
import org.springframework.boot.context.properties.bind.handler.NoUnboundElementsBindHandler;
import org.springframework.boot.context.properties.source.ConfigurationPropertySources;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.ClassPathResource;

import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@code config/router/ratelimit.yml} 的 {@code jairouter.ratelimit.end-user} 段必须全部能绑上。
 *
 * <p>Spring 默认 {@code ignoreUnknownFields=true}，所以「yml 里写了键、类里没有字段」不会报错，
 * 只会让运维照着 yml 调参而<b>静默不生效</b>（本仓已因此收过 #312 / #320 / #321 三批）。
 * 这里用 Spring 自己的 {@link NoUnboundElementsBindHandler} 把真实 yml 绑一遍：只要该前缀下
 * 出现任何绑不上的键（写错键名、加了键没加字段），本测试就红。</p>
 *
 * <p>反向自检：往环境里塞一个未登记的键，必须能被同一个手法捕获 —— 否则「绑定成功」
 * 可能只是枚举/前缀逻辑失效后的假绿。</p>
 */
@DisplayName("ratelimit.yml 的 end-user 段必须都能绑上（配置诚实性守卫）")
class EndUserRateLimitPropertiesBindingGuardTest {

    /** 真实 yml 在 classpath 上的位置（src/main/resources 下的同一份文件）。 */
    private static final String LOCATION = "config/router/ratelimit.yml";

    /** 本次新增的配置前缀。 */
    private static final String PREFIX = "jairouter.ratelimit.end-user";

    @Test
    @DisplayName("真实 yml 严格绑定成功，且取值与 yml 一致")
    void everyKeyBinds() {
        final EndUserRateLimitProperties properties = bind(loadYml());

        assertFalse(properties.isEnabled(), "yml 里 enabled: false 应生效");
        assertEquals(60, properties.getRequestsPerMinute(), "requests-per-minute: 60");
        assertTrue(properties.isHeaderEnabled(), "header-enabled: true");
        assertEquals(EndUserRateLimitProperties.MissingIdentity.SKIP,
                properties.getMissingIdentity(), "missing-identity: skip");
        assertEquals(50000, properties.getLocalCacheSize(), "local-cache-size: 50000");
        assertEquals(16, properties.getFingerprintLength(), "fingerprint-length: 16");
    }

    @Test
    @DisplayName("多一个未登记的键会被严格绑定捕获（证明该守卫不是空转）")
    void unregisteredKeyIsDetected() {
        final StandardEnvironment environment = loadYml();
        environment.getPropertySources().addFirst(new MapPropertySource("canary",
                Map.of(PREFIX + ".canary-unregistered-key", "true")));

        final BindException thrown = assertThrows(BindException.class,
                () -> bindStrictly(environment),
                "多了未登记键却没有失败，说明这个守卫抓不到「能设、没人读」的键");

        final UnboundConfigurationPropertiesException unbound = assertInstanceOf(
                UnboundConfigurationPropertiesException.class, thrown.getCause(),
                "失败原因应当是存在未绑定键，实际是：" + thrown);
        assertTrue(names(unbound).contains(PREFIX + ".canary-unregistered-key"),
                "未绑定键清单里应包含刚塞进去的那个键，实际是：" + names(unbound));
    }

    private static Set<String> names(final UnboundConfigurationPropertiesException thrown) {
        return thrown.getUnboundProperties().stream()
                .map(property -> property.getName().toString())
                .collect(Collectors.toCollection(TreeSet::new));
    }

    /** 真实 yml 用严格模式绑定：任何未绑定键都让本方法失败。 */
    private static EndUserRateLimitProperties bind(final StandardEnvironment environment) {
        return bindStrictly(environment);
    }

    private static EndUserRateLimitProperties bindStrictly(final StandardEnvironment environment) {
        final Binder binder = new Binder(ConfigurationPropertySources.get(environment));
        final BindHandler handler = new NoUnboundElementsBindHandler(BindHandler.DEFAULT);
        return binder.bind(PREFIX, Bindable.of(EndUserRateLimitProperties.class), handler)
                .orElseThrow(() -> new AssertionError("未能把 " + LOCATION + " 的 end-user 段绑到配置类"));
    }

    /** 读仓库里**真实**的 yml（不是测试专用夹具）。 */
    private static StandardEnvironment loadYml() {
        final StandardEnvironment environment = new StandardEnvironment();
        final MutablePropertySources sources = environment.getPropertySources();
        try {
            new YamlPropertySourceLoader()
                    .load("ratelimit", new ClassPathResource(LOCATION))
                    .forEach(sources::addFirst);
        } catch (Exception e) {
            throw new IllegalStateException("读取 " + LOCATION + " 失败", e);
        }
        return environment;
    }
}
