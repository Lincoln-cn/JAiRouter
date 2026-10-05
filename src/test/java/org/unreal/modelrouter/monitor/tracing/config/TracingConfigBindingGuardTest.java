package org.unreal.modelrouter.monitor.tracing.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.BindHandler;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.bind.handler.NoUnboundElementsBindHandler;
import org.springframework.boot.context.properties.source.ConfigurationPropertySources;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.ClassPathResource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 「绑定了但没人读」的配置键守护（issue #215）。
 *
 * <p>起因：{@code tracing-base.yml} 的 {@code performance.async.*} 等 8 个键**没有任何字段接它们**，
 * 而 Spring 默认 {@code ignoreUnknownFields=true} 不报错 ⇒ 运维照着 yml 调参**静默不生效**。
 * 这类缺陷靠读代码看不出来，只能靠「把真实 yml 绑一遍」来暴露。</p>
 *
 * <p>做法是用 Spring 自己的 {@link NoUnboundElementsBindHandler}：它会把「在
 * {@code jairouter.tracing} 前缀下存在、但类里没有对应字段」的键收集起来并让绑定失败。
 * 于是**以后往这个 yml 里加键却不加字段（或写错键名）会在这里红**，而不是等运维发现自己调参没用。</p>
 *
 * <p>范围只覆盖 {@code config/tracing/tracing-base.yml}。同一手法可以推广到别的模块配置，
 * 但那要先清掉它们各自的死键，属另外的工作。</p>
 */
@DisplayName("tracing-base.yml 的键必须都能绑上（issue #215）")
class TracingConfigBindingGuardTest {

    private static final String LOCATION = "config/tracing/tracing-base.yml";

    @Test
    @DisplayName("真实 yml 能绑到 TracingConfiguration，且没有「配了没人读」的键")
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

    /** 读仓库里**真实**的 yml（不是测试专用夹具），再按 Spring 的宽松绑定规则绑一遍 */
    private static TracingConfiguration bindRealYml() {
        StandardEnvironment environment = new StandardEnvironment();
        MutablePropertySources sources = environment.getPropertySources();
        try {
            new YamlPropertySourceLoader()
                    .load("tracing-base", new ClassPathResource(LOCATION))
                    .forEach(sources::addFirst);
        } catch (Exception e) {
            throw new IllegalStateException("读取 " + LOCATION + " 失败", e);
        }

        return new Binder(ConfigurationPropertySources.get(environment))
                .bind("jairouter.tracing", Bindable.of(TracingConfiguration.class),
                        new NoUnboundElementsBindHandler(BindHandler.DEFAULT))
                .orElseThrow(() -> new AssertionError("未能把 " + LOCATION + " 绑到 TracingConfiguration"));
    }
}
