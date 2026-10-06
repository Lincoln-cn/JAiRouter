package org.unreal.modelrouter.monitor.tracing.config;

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

import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 「绑定了但没人读」的配置键守护（issue #215 / #220）。
 *
 * <p>起因：{@code tracing-base.yml} 的 {@code performance.async.*} 等 8 个键**没有任何字段接它们**，
 * 而 Spring 默认 {@code ignoreUnknownFields=true} 不报错 ⇒ 运维照着 yml 调参**静默不生效**。
 * 这类缺陷靠读代码看不出来，只能靠「把真实 yml 绑一遍」来暴露。</p>
 *
 * <p>做法是用 Spring 自己的 {@link NoUnboundElementsBindHandler}：它会把「在
 * {@code jairouter.tracing} 前缀下存在、但类里没有对应字段」的键收集起来并让绑定失败。
 * 于是**以后往这个 yml 里加键却不加字段（或写错键名）会在这里红**，而不是等运维发现自己调参没用。</p>
 *
 * <p><b>issue #220 的补充</b>：有些键**故意**没有字段，由 {@code @Value} 或
 * {@code @ConditionalOnProperty} 直接读属性 —— 例如 {@code performance.scheduler.*}
 * 必须走构造器参数注入，不能有字段（字段注入发生在构造器之后，而调度器在构造器里建）。
 * 这类键由 {@link #knownNonBindingKeys()} 显式登记；该测试断言「未绑定键集合恰好等于登记集合」，
 * 因此新增**任何**未登记的键仍会红 —— 比单纯禁用这个 handler 严格得多。</p>
 *
 * <p>范围只覆盖 {@code config/tracing/tracing-base.yml}。同一手法可以推广到别的模块配置，
 * 但那要先清掉它们各自的死键，属另外的工作。</p>
 */
@DisplayName("tracing-base.yml 的键必须都能绑上（issue #215 / #220）")
class TracingConfigBindingGuardTest {

    private static final String LOCATION = "config/tracing/tracing-base.yml";

    @Test
    @DisplayName("真实 yml 能绑到 TracingConfiguration")
    void everyKeyBinds() {
        TracingConfiguration bound = bindRealYml();
        assertNotNull(bound.getPerformance(), "performance 段应当绑定成功");
    }

    @Test
    @DisplayName("归并后的键确实生效：thread-pool 得到原 async.* 声明的 8 / 8192")
    void mergedKeysTakeEffect() {
        TracingPerformanceConfig performance = bindRealYml().getPerformance();

        assertEquals(8, performance.getThreadPool().getCoreSize(),
                "原 async.worker-threads: 8 应归并到 thread-pool.core-size 并真正生效");
        assertEquals(8192, performance.getThreadPool().getQueueCapacity(),
                "原 async.queue-size: 8192 应归并到 thread-pool.queue-capacity 并真正生效");
        assertTrue(performance.isAsyncProcessing(), "原 async.enabled: true 应归并到 async-processing");
    }

    @Test
    @DisplayName("未绑定的键恰好等于显式登记的那几个（新增任何别的绑定不上的键都会红）")
    void onlyKnownNonBindingKeysAreUnbound() {
        // Binder 会把 NoUnboundElementsBindHandler 抛出的异常包一层 BindException，
        // 未绑定键清单在其 cause 上
        BindException thrown = assertThrows(BindException.class,
                TracingConfigBindingGuardTest::bindRealYmlStrictly,
                "本用例预期存在登记在案的非绑定键，因此严格绑定应当抛异常；"
                        + "若这里没抛，说明那几个键已被改成可绑定字段 —— 请把登记集合一并清空");

        UnboundConfigurationPropertiesException unbound = assertInstanceOf(
                UnboundConfigurationPropertiesException.class, thrown.getCause(),
                "严格绑定失败的原因应当是「存在未绑定键」，实际是：" + thrown);

        assertEquals(knownNonBindingKeys(), names(unbound),
                "yml 里出现了未登记的非绑定键：要么补上对应字段，要么在这里登记并写清它由谁消费");
    }

    /**
     * 允许存在的「非绑定键」——每一个都必须有字段之外的消费方，改动前先确认。
     *
     * <p>{@code performance.scheduler.*} 由 {@code TracingMemoryManager} / {@code TracingPerformanceMonitor}
     * 的构造器参数 {@code @Value} 读取（#182）。它们**不能**改成字段：字段注入晚于构造器，
     * 而这两个调度器正是在构造器里创建的，用字段会静默读到默认值。</p>
     */
    private static Set<String> knownNonBindingKeys() {
        return Set.of(
                "jairouter.tracing.performance.scheduler.thread-cap",
                "jairouter.tracing.performance.scheduler.queue-capacity");
    }

    private static Set<String> names(final UnboundConfigurationPropertiesException thrown) {
        return thrown.getUnboundProperties().stream()
                .map(property -> property.getName().toString())
                .collect(Collectors.toCollection(TreeSet::new));
    }

    /** 宽松绑定：只验证「能绑上」，不检查未绑定键 */
    private static TracingConfiguration bindRealYml() {
        return bind(false);
    }

    /** 严格绑定：任何未绑定键都抛 {@link UnboundConfigurationPropertiesException} */
    private static TracingConfiguration bindRealYmlStrictly() {
        return bind(true);
    }

    private static TracingConfiguration bind(final boolean strict) {
        StandardEnvironment environment = loadYml();
        Binder binder = new Binder(ConfigurationPropertySources.get(environment));

        BindHandler handler = strict ? new NoUnboundElementsBindHandler(BindHandler.DEFAULT) : BindHandler.DEFAULT;
        return binder.bind("jairouter.tracing", Bindable.of(TracingConfiguration.class), handler)
                .orElseThrow(() -> new AssertionError("未能把 " + LOCATION + " 绑到 TracingConfiguration"));
    }

    /** 读仓库里**真实**的 yml（不是测试专用夹具） */
    private static StandardEnvironment loadYml() {
        StandardEnvironment environment = new StandardEnvironment();
        MutablePropertySources sources = environment.getPropertySources();
        try {
            new YamlPropertySourceLoader()
                    .load("tracing-base", new ClassPathResource(LOCATION))
                    .forEach(sources::addFirst);
        } catch (Exception e) {
            throw new IllegalStateException("读取 " + LOCATION + " 失败", e);
        }
        return environment;
    }
}
