package org.unreal.modelrouter.auth.security.config.properties;

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
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@code jairouter.security.audit} 前缀的「键必须都能绑上」守护（issue #312）。
 *
 * <p>起因：该前缀下有约 100 行配置没有任何绑定字段（{@code jwt-operations} /
 * {@code api-key-operations} / {@code security-events} / {@code retention.*} /
 * {@code storage.*} / {@code performance.*} / {@code event-types}），而 Spring 默认
 * {@code ignoreUnknownFields=true} 不报错 ⇒ 运维照着 yml 调参会**静默不生效**。
 * 这类缺陷读代码看不出来，只能靠「把真实 yml 绑一遍」暴露 —— 手法照
 * {@code TracingConfigBindingGuardTest}（issue #215 / #220）。</p>
 *
 * <p><b>与 tracing 那份的关键差异</b>：{@code jairouter.security.audit} 的键**散在多个文件**里
 * （{@code config/auth/audit.yml}、{@code config/security/security-base.yml}、
 * {@code config/security/persistence-base.yml} 以及四个 {@code application*.yml}），
 * 所以「未绑定键」这条用例不逐个列举文件，而是把 classpath 上的 {@code application*.yml} 与
 * {@code config/**}{@code /*.yml} 全部加载 —— 这样以后往任何新配置文件里加这个前缀的键也会被覆盖。</p>
 *
 * <p>用法：{@link NoUnboundElementsBindHandler} 把「前缀下存在、类里没有对应字段」的键收集起来
 * 并让绑定失败。因此**新增任何未登记的绑定不上的键都会红**，而不是等运维发现自己调参没用。</p>
 *
 * <p><b>⚠️ 本地排查提示</b>：本用例读的是 <i>classpath</i> 上的 yml。Maven 的资源插件只做拷贝、
 * 不会删除「源文件已被删掉」的旧副本 ⇒ 若你刚删掉某个 yml，`target/classes` 下可能还留着它，
 * 此时本用例会报出已不存在的键。**先 `mvn clean`（或删掉那个残留文件）再看**；
 * CI 是干净构建，不存在这个问题。issue #312 本轮删除 {@code config/security/audit-base.yml}
 * 时实测踩到过这一点。</p>
 */
@DisplayName("jairouter.security.audit 的键必须都能绑上（issue #312）")
class AuditConfigBindingGuardTest {

    private static final String PREFIX = "jairouter.security.audit";

    @Test
    @DisplayName("真实 yml 的键确实落到字段上")
    void everyKeyBinds() {
        // 只加载显式列出的两个文件，并按固定顺序（后加载的优先），
        // 否则「值是多少」取决于 classpath 枚举顺序 —— 那是过度指定
        AuditConfig bound = bind(loadYml(
                "config/security/security-base.yml",
                "config/auth/audit.yml"), false);

        assertNotNull(bound, "应当能从真实 yml 绑定出 AuditConfig");
        assertTrue(bound.isEnabled(),
                "audit.yml 与 security-base.yml 都设了 enabled: true，绑定后应为 true");
        assertEquals("INFO", bound.getLogLevel(),
                "log-level 应当来自真实 yml");
        assertEquals(90, bound.getRetentionDays(),
                "retention-days 应当来自真实 yml");
    }

    @Test
    @DisplayName("未绑定的键恰好等于登记在案的那几个（新增任何别的绑定不上的键都会红）")
    void onlyKnownNonBindingKeysAreUnbound() {
        // Binder 会把 NoUnboundElementsBindHandler 抛出的异常包一层 BindException，
        // 未绑定键清单在其 cause 上
        BindException thrown = assertThrows(BindException.class,
                AuditConfigBindingGuardTest::bindEveryYmlStrictly,
                "本用例预期存在登记在案的非绑定键，因此严格绑定应当抛异常；"
                        + "若这里没抛，说明它们已被改成可绑定字段或被删除 —— 请把登记集合一并清空");

        UnboundConfigurationPropertiesException unbound = assertInstanceOf(
                UnboundConfigurationPropertiesException.class, thrown.getCause(),
                "严格绑定失败的原因应当是「存在未绑定键」，实际是：" + thrown);

        assertEquals(knownNonBindingKeys(), names(unbound),
                "yml 里出现了未登记的绑定不上的键：要么补上对应字段，要么在这里登记并写清它由谁消费");
    }

    /**
     * 允许存在的「绑定不上的键」——每一项都必须写清它由谁消费，新增前先确认。
     *
     * <p>这是 {@code NoUnboundElementsBindHandler} 的**显式登记表**，不是「忽略清单」：
     * 断言的是「未绑定集合**恰好等于**本集合」，所以任何未登记的键都会让测试变红。</p>
     */
    private static Set<String> knownNonBindingKeys() {
        // 只剩这一项：由 ExtendedSecurityAuditController 上的 @ConditionalOnProperty 直接读属性，
        // 不进 AuditConfig（同 tracing 的 performance.scheduler.*，见 TracingConfigBindingGuardTest）。
        //
        // 原 extended.persistence.* / extended.structured-logging.* / extended.memory-cache.*
        // （14 个键，无任何消费方）已随 issue #321 删除 —— 登记表随之收敛到「只剩有消费方的那一个」，
        // 于是本测试现在是 jairouter.security.audit 前缀的完整守卫（不再是「已清理 + 登记残留」的混合状态）。
        return Set.of(PREFIX + ".extended.enabled");
    }

    private static Set<String> names(final UnboundConfigurationPropertiesException thrown) {
        return thrown.getUnboundProperties().stream()
                .map(property -> property.getName().toString())
                .collect(Collectors.toCollection(TreeSet::new));
    }

    /** 严格绑定 classpath 上全部真实 yml：任何未绑定键都让绑定失败 */
    private static AuditConfig bindEveryYmlStrictly() {
        return bind(loadAllYml(), true);
    }

    private static AuditConfig bind(final StandardEnvironment environment, final boolean strict) {
        Binder binder = new Binder(ConfigurationPropertySources.get(environment));

        BindHandler handler = strict ? new NoUnboundElementsBindHandler(BindHandler.DEFAULT) : BindHandler.DEFAULT;
        return binder.bind(PREFIX, Bindable.of(AuditConfig.class), handler)
                .orElseThrow(() -> new AssertionError("未能把真实 yml 绑到 AuditConfig（前缀 " + PREFIX + "）"));
    }

    /** 按给定顺序读**真实** yml（先加载的优先级低） */
    private static StandardEnvironment loadYml(final String... locations) {
        StandardEnvironment environment = new StandardEnvironment();
        MutablePropertySources sources = environment.getPropertySources();
        YamlPropertySourceLoader loader = new YamlPropertySourceLoader();
        for (String location : locations) {
            load(loader, sources, new ClassPathResource(location));
        }
        return environment;
    }

    /**
     * 把 classpath 上的 {@code application*.yml} 与 {@code config/**}{@code /*.yml} 全部加载。
     *
     * <p>本用例只关心「键的集合」，不关心文件间覆盖优先级，因此加载顺序无所谓
     * （逐个 {@code addFirst} 与遍历顺序无关地并起全部键）。</p>
     */
    private static StandardEnvironment loadAllYml() {
        StandardEnvironment environment = new StandardEnvironment();
        MutablePropertySources sources = environment.getPropertySources();
        YamlPropertySourceLoader loader = new YamlPropertySourceLoader();

        List<Resource> resources = new ArrayList<>();
        try {
            PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
            resources.addAll(Arrays.asList(resolver.getResources("classpath*:application*.yml")));
            resources.addAll(Arrays.asList(resolver.getResources("classpath*:config/**/*.yml")));
        } catch (IOException e) {
            throw new IllegalStateException("枚举 classpath 上的 yml 失败", e);
        }

        assertTrue(resources.size() > 5,
                "应当枚举到多个 yml（含 config/ 下的模块配置），实际 " + resources.size() + " 个");

        for (Resource resource : resources) {
            load(loader, sources, resource);
        }
        return environment;
    }

    private static void load(final YamlPropertySourceLoader loader,
                             final MutablePropertySources sources,
                             final Resource resource) {
        try {
            loader.load(resource.getFilename(), resource).forEach(sources::addFirst);
        } catch (IOException e) {
            throw new IllegalStateException("读取 " + resource.getDescription() + " 失败", e);
        }
    }
}
