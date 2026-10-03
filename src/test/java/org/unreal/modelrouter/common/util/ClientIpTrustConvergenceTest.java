package org.unreal.modelrouter.common.util;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.unreal.modelrouter.auth.security.util.ClientIpUtils;

import java.net.InetSocketAddress;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * #151 Stage 3 收敛回归：各客户端 IP 推导点必须共用同一份信任策略。
 *
 * <p>设计文档 §4 的立场是「信任决策统一到一个共享组件」。这里对已收敛的推导点各验一条
 * 「默认配置下伪造转发头不生效」——断言写错会让伪造的 XFF 被采信，属于安全性质。</p>
 *
 * @author JAiRouter Team
 * @since 3.2.6
 */
@DisplayName("#151：客户端 IP 推导点收敛到共享信任策略")
class ClientIpTrustConvergenceTest {

    private static final String PEER = "203.0.113.9";

    @AfterEach
    void resetSharedPolicy() {
        ClientIpResolver.setPolicy(TrustedProxyPolicy.empty());
    }

    private static MockServerHttpRequest.BaseBuilder<?> forgedRequest(final String peer) {
        return MockServerHttpRequest.get("/api/anything")
                .remoteAddress(new InetSocketAddress(peer, 40000))
                .header("X-Forwarded-For", "198.51.100.1")
                .header("X-Real-IP", "198.51.100.2");
    }

    @Test
    @DisplayName("IpUtils.getClientIp：默认配置下伪造转发头不生效（路由限流/会话亲和键的源头）")
    void ipUtilsIgnoresForgedHeadersByDefault() {
        assertEquals(PEER, IpUtils.getClientIp(forgedRequest(PEER).build()),
                "对端不可信时必须返回 remoteAddress，而不是转发头里的伪造值");
    }

    @Test
    @DisplayName("ClientIpUtils.getClientIpAddress：默认配置下伪造转发头不生效（JWT 审计 IP 与两个 controller 的来源）")
    void clientIpUtilsIgnoresForgedHeadersByDefault() {
        final MockServerWebExchange exchange = MockServerWebExchange.from(forgedRequest(PEER));
        assertEquals(PEER, ClientIpUtils.getClientIpAddress(exchange),
                "审计日志里的来源 IP 不得被客户端伪造的转发头改写");
    }

    @Test
    @DisplayName("配置了可信代理后，同一请求才按 XFF 取值")
    void forwardedHeaderHonouredOnlyForTrustedPeer() {
        ClientIpResolver.setPolicy(TrustedProxyPolicy.parse("10.0.0.1"));

        assertEquals("198.51.100.1", IpUtils.getClientIp(forgedRequest("10.0.0.1").build()),
                "直连对端可信时采信 XFF");
        assertEquals(PEER, IpUtils.getClientIp(forgedRequest(PEER).build()),
                "同一策略下，来自非可信对端的请求仍必须忽略转发头");
    }
}
