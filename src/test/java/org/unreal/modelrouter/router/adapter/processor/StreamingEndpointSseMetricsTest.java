package org.unreal.modelrouter.router.adapter.processor;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.test.util.ReflectionTestUtils;
import org.unreal.modelrouter.monitor.monitoring.metrics.SseConnectionMetrics;
import org.unreal.modelrouter.router.adapter.transformer.ResponseTransformer;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * #947：流式端点的连接计数接线。
 *
 * <p>此前只有健康巡检那一个 SSE 端点接了计数，真正会堆积长连接的是业务流式转发
 * （{@code /v1/chat/completions} 与 legacy {@code /api/**} 面）。本类守住两件事：
 * 装配了指标时，端点标签下的活跃数随订阅增减；没装配指标时，流原样返回（计数不能成为
 * 流式链路的必要条件）。</p>
 *
 * @since 3.5.10
 */
class StreamingEndpointSseMetricsTest {

    private StreamingRequestProcessor processor;

    private SseConnectionMetrics metrics;

    @BeforeEach
    void setUp() {
        processor = new StreamingRequestProcessor(new ResponseTransformer());
        metrics = new SseConnectionMetrics(new SimpleMeterRegistry());
        ReflectionTestUtils.setField(processor, "sseConnectionMetrics", metrics);
    }

    private static Flux<ServerSentEvent<String>> twoChunks() {
        return Flux.just(ServerSentEvent.builder("a").build(), ServerSentEvent.builder("b").build());
    }

    @Test
    @DisplayName("装配指标后：订阅期间活跃数为 1，终止后归零")
    void tracksActiveConnections() {
        final Flux<ServerSentEvent<String>> tracked = processor.trackStream(twoChunks(), "v1-chat-completions");

        StepVerifier.create(tracked, 0)
                .thenRequest(1)
                .expectNextCount(1)
                .then(() -> assertThat(metrics.active("v1-chat-completions"))
                        .as("请求进行中应有 1 条活跃连接")
                        .isEqualTo(1L))
                .thenRequest(1)
                .expectNextCount(1)
                .verifyComplete();

        assertThat(metrics.active("v1-chat-completions"))
                .as("流结束后活跃数应归零")
                .isZero();
    }

    @Test
    @DisplayName("两种端点标签互不干扰：legacy 面的连接不计入 v1 面")
    void keepsEndpointsSeparate() {
        StepVerifier.create(processor.trackStream(twoChunks(), "chat-completions"), 0)
                .thenRequest(1)
                .expectNextCount(1)
                .then(() -> {
                    assertThat(metrics.active("chat-completions")).isEqualTo(1L);
                    assertThat(metrics.active("v1-chat-completions"))
                            .as("另一个端点不应被连带计数")
                            .isZero();
                })
                .thenRequest(1)
                .expectNextCount(1)
                .verifyComplete();
    }

    @Test
    @DisplayName("未装配指标时原样返回：流内容不变，也不抛异常")
    void passesThroughWithoutMetrics() {
        ReflectionTestUtils.setField(processor, "sseConnectionMetrics", null);

        StepVerifier.create(processor.trackStream(twoChunks(), "v1-chat-completions"))
                .expectNextMatches(event -> "a".equals(event.data()))
                .expectNextMatches(event -> "b".equals(event.data()))
                .verifyComplete();
    }

    @Test
    @DisplayName("下游错误同样释放计数（不能只认正常完成）")
    void releasesOnError() {
        final Flux<ServerSentEvent<String>> failing = Flux.concat(
                Flux.just(ServerSentEvent.builder("a").build()),
                Flux.error(new IllegalStateException("下游断开")));

        StepVerifier.create(processor.trackStream(failing, "chat-completions"))
                .expectNextCount(1)
                .verifyError(IllegalStateException.class);

        assertThat(metrics.active("chat-completions"))
                .as("异常终止后活跃数应归零")
                .isZero();
    }

    @Test
    @DisplayName("doOnSubscribe 在每次订阅各算一条（同时打开两个标签页就是两条）")
    void countsEachSubscription() {
        final Flux<ServerSentEvent<String>> tracked = processor.trackStream(
                Flux.interval(Duration.ofMillis(10)).map(i -> ServerSentEvent.builder("x").build()),
                "chat-completions").take(50);

        final var first = tracked.subscribe();
        final var second = tracked.subscribe();
        try {
            assertThat(metrics.active("chat-completions")).isEqualTo(2L);
        } finally {
            first.dispose();
            second.dispose();
        }
        assertThat(metrics.active("chat-completions"))
                .as("取消订阅后活跃数应归零")
                .isZero();
    }
}
