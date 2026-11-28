package org.unreal.modelrouter.router.handler;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.unreal.modelrouter.router.ratelimit.ConcurrentStreamLimiter;
import reactor.core.publisher.Flux;

/**
 * 并发名额与响应终止的绑定测试（issue #1246）。
 *
 * <p>要守住的关键点只有一个：<b>流式响应不能在响应头就绪时归还名额</b>。否则限制器看到的并发数
 * 永远等于「正在建立响应的请求数」，与真实在途连接数无关，限流形同虚设。</p>
 */
@DisplayName("ServiceRequestHandler 的并发名额归还时机")
class ServiceRequestHandlerConcurrencyLeaseTest {

    @Test
    @DisplayName("非流式响应在响应就绪时立即归还名额")
    void nonStreamingBodyReleasesImmediately() {
        final ConcurrentStreamLimiter limiter = new ConcurrentStreamLimiter(1);
        final ConcurrentStreamLimiter.Lease lease = limiter.acquire("key-a");
        assertTrue(lease.granted());
        assertEquals(1, limiter.activeCount("key-a"));

        final ResponseEntity<?> released = ServiceRequestHandler.releaseConcurrencyAfter(
                ResponseEntity.ok(Map.of("ok", true)), lease);

        assertEquals(HttpStatus.OK, released.getStatusCode());
        assertEquals(0, limiter.activeCount("key-a"), "非流式响应应在返回前就归还名额");
    }

    @Test
    @DisplayName("流式响应只在流终止后归还名额，订阅取消也算终止")
    void streamingBodyKeepsSlotUntilTermination() {
        final ConcurrentStreamLimiter limiter = new ConcurrentStreamLimiter(1);
        final ConcurrentStreamLimiter.Lease lease = limiter.acquire("key-a");
        assertTrue(lease.granted());

        final ResponseEntity<?> wrapped = ServiceRequestHandler.releaseConcurrencyAfter(
                ResponseEntity.ok(Flux.never()), lease);

        assertTrue(wrapped.getBody() instanceof Flux<?>, "正文应仍是 Flux");
        assertEquals(1, limiter.activeCount("key-a"),
                "Mono 完成时流还没开始读，名额必须继续占着");
        assertFalse(limiter.acquire("key-a").granted(), "在途期间第二个请求应被拒绝");

        // 订阅后取消：对长连接来说等价于客户端断开
        ((Flux<?>) wrapped.getBody()).subscribe().dispose();

        assertEquals(0, limiter.activeCount("key-a"), "流终止（取消）后应归还名额");
        assertTrue(limiter.acquire("key-a").granted(), "归还后应能重新申请");
    }

    @Test
    @DisplayName("流正常结束时也归还名额")
    void streamingBodyReleasesOnComplete() {
        final ConcurrentStreamLimiter limiter = new ConcurrentStreamLimiter(1);
        final ConcurrentStreamLimiter.Lease lease = limiter.acquire("key-a");

        final ResponseEntity<?> wrapped = ServiceRequestHandler.releaseConcurrencyAfter(
                ResponseEntity.ok(Flux.empty()), lease);

        ((Flux<?>) wrapped.getBody()).subscribe();

        assertEquals(0, limiter.activeCount("key-a"));
    }

    @Test
    @DisplayName("未注入限制器（租约为 null）时响应原样返回")
    void nullLeaseIsPassThrough() {
        final ResponseEntity<?> original = ResponseEntity.ok(Flux.never());

        final ResponseEntity<?> returned = ServiceRequestHandler.releaseConcurrencyAfter(original, null);
        assertSame(original, returned);

        ServiceRequestHandler.closeConcurrencyLease(null);
    }
}
