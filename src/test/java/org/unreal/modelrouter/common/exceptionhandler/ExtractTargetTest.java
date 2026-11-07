package org.unreal.modelrouter.common.exceptionhandler;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * {@link ReactiveGlobalExceptionHandler#extractTarget} 提取行为的测试守护（issue #1011）。
 *
 * <p>该方法把日志里的目标地址（host:port）从异常链消息中抠出来，只进日志不进响应体
 * （#682 边界）。用例钉住四种输入形态：域名、IPv4（Netty 消息带前导 {@code /}）、
 * 提取不到时回 {@code unknown}、以及沿 cause 链逐层找（含环形 cause 不死循环）。</p>
 */
@DisplayName("下游失败日志目标提取（#1011）")
class ExtractTargetTest {

    @Test
    @DisplayName("EXT-100 域名 host:port 形态")
    void domainHostPort() {
        assertEquals("api.example.com:8443",
                ReactiveGlobalExceptionHandler.extractTarget(
                        new RuntimeException("connect failed: api.example.com:8443")));
    }

    @Test
    @DisplayName("EXT-101 Netty 消息 IPv4 形态（前导斜杠不带出）")
    void nettyIpv4Form() {
        assertEquals("127.0.0.1:8080",
                ReactiveGlobalExceptionHandler.extractTarget(
                        new RuntimeException("Connection refused: /127.0.0.1:8080")));
    }

    @Test
    @DisplayName("EXT-102 消息无目标地址回 unknown")
    void noTargetFallsBack() {
        assertEquals("unknown",
                ReactiveGlobalExceptionHandler.extractTarget(
                        new RuntimeException("something broke")));
    }

    @Test
    @DisplayName("EXT-103 沿 cause 链逐层提取（外层无、内层有）")
    void walksCauseChain() {
        final RuntimeException cause = new RuntimeException("Connection refused: /10.0.0.5:9200");
        final RuntimeException wrapped = new RuntimeException("upstream error", cause);
        assertEquals("10.0.0.5:9200",
                ReactiveGlobalExceptionHandler.extractTarget(wrapped));
    }

    @Test
    @DisplayName("EXT-104 环形 cause 链不死循环，未命中回 unknown")
    void cyclicCauseTerminates() {
        final RuntimeException b = new RuntimeException("no target here");
        final RuntimeException a = new RuntimeException("also none", b);
        b.initCause(a);
        assertEquals("unknown", ReactiveGlobalExceptionHandler.extractTarget(a));
    }
}
