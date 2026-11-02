package org.unreal.modelrouter.monitor.monitoring.metrics;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.web.embedded.netty.NettyReactiveWebServerFactory;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.ReactorResourceFactory;
import reactor.netty.resources.LoopResources;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * 事件循环排队延迟指标（#966）。
 *
 * <p>Reactor Netty 的事件循环（{@code reactor-http-nio-*} / Linux 上 {@code reactor-http-epoll-*}）
 * 一旦被同步调用或排队压住，表现出来只是 p99 上升、CPU 不高，面板上看不到任何直接信号。
 * 本配置把事件循环换成一份显式构造的 {@link LoopResources}（参数与框架默认一致），
 * 每秒向每个事件循环投递一个空任务并测量「投递到执行」的耗时，暴露为
 * {@value #METRIC} 的 Timer。
 *
 * <p>几个刻意的选择：
 * <ul>
 *   <li><b>线程前缀保持 {@code reactor-http}</b>：与 Spring Boot 默认一致，排障脚本、日志
 *       里的线程名都不变；换前缀会让既有 runbook 的 grep 全部失效。</li>
 *   <li><b>worker 数与默认取同一表达式</b>：{@code max(4, CPU 核数)}，与 reactor 的
 *       {@code DEFAULT_IO_WORKER_COUNT} 一致。</li>
 *   <li><b>探针任务本身极轻</b>：每秒每 loop 一个空任务，投递失败只吞掉，绝不影响业务。</li>
 *   <li><b>可关</b>：{@code jairouter.metrics.eventloop.enabled=false} 退回框架默认行为。</li>
 * </ul>
 *
 * <p>已知边界：这个读数覆盖的是「事件循环这一层」的排队，不含业务线程池与上游等待；
 * 判读时先看 {@code hikaricp_connections_pending} 与出站 inflight，再看本指标。
 *
 * @since 3.6.1
 */
@Configuration
@ConditionalOnProperty(prefix = "jairouter.metrics.eventloop", name = "enabled",
        havingValue = "true", matchIfMissing = true)
public class EventLoopMetricsConfiguration {

    /** 指标名：事件循环「投递到执行」的延迟。 */
    public static final String METRIC = "jairouter.eventloop.delay";

    /** 线程名前缀，刻意与 Spring Boot 默认一致。 */
    static final String THREAD_PREFIX = "reactor-http";

    @Bean(destroyMethod = "")
    public ReactorResourceFactory eventLoopMetricsResourceFactory(final MeterRegistry registry) {
        final LoopResources loopResources = LoopResources.create(THREAD_PREFIX, defaultWorkerCount(), true);
        final EventLoopDelayProbe probe = new EventLoopDelayProbe(loopResources, registry);
        probe.start();
        final ReactorResourceFactory factory = new ReactorResourceFactory();
        factory.setUseGlobalResources(false);
        factory.setLoopResources(loopResources);
        return factory;
    }

    @Bean
    public WebServerFactoryCustomizer<NettyReactiveWebServerFactory> eventLoopMetricsWebServerCustomizer(
            final ReactorResourceFactory eventLoopMetricsResourceFactory) {
        return webServerFactory -> webServerFactory.setResourceFactory(eventLoopMetricsResourceFactory);
    }

    /** 与 reactor 的 {@code DEFAULT_IO_WORKER_COUNT} 取同一表达式。 */
    static int defaultWorkerCount() {
        return Math.max(4, Runtime.getRuntime().availableProcessors());
    }

    /**
     * 每秒向每个事件循环投递空任务，测量排队 + 执行延迟。
     *
     * <p>写成独立类而不是内联在配置里，是为了能在测试中单独驱动（配置类依赖 Spring 上下文）。
     */
    static final class EventLoopDelayProbe implements DisposableBean {

        private final LoopResources loopResources;
        private final Timer delay;
        private final ScheduledExecutorService scheduler;

        EventLoopDelayProbe(final LoopResources loopResources, final MeterRegistry registry) {
            this.loopResources = loopResources;
            this.delay = Timer.builder(METRIC).register(registry);
            this.scheduler = Executors.newSingleThreadScheduledExecutor(runnable -> {
                final Thread thread = new Thread(runnable, "eventloop-delay-probe");
                thread.setDaemon(true);
                return thread;
            });
        }

        void start() {
            scheduler.scheduleAtFixedRate(this::sample, 3, 1, TimeUnit.SECONDS);
        }

        /** 采一轮：每提交成功一次就记一条样本。 */
        void sample() {
            try {
                for (final var eventLoop : loopResources.onServer(true)) {
                    final long submittedAt = System.nanoTime();
                    eventLoop.execute(() ->
                            delay.record(System.nanoTime() - submittedAt, TimeUnit.NANOSECONDS));
                }
            } catch (RuntimeException ignored) {
                // 探针自身不能成为故障源：事件循环已释放或平台不支持 native 传输时静默跳过
            }
        }

        @Override
        public void destroy() {
            scheduler.shutdownNow();
        }
    }
}
