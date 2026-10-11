package org.unreal.modelrouter.router.http;

import io.netty.resolver.AddressResolver;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.ImmediateEventExecutor;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.net.UnknownHostException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * #266：解析守卫必须对「即将用于建连的地址」再判一次，且不得改变既有的放行范围与解析失败语义。
 */
@DisplayName("出站解析守卫：判定与委托语义")
class SsrfGuardedAddressResolverGroupTest {

    private static final int PORT = 8080;

    private static final SocketAddress UNRESOLVED = InetSocketAddress.createUnresolved("rebind-test.invalid", PORT);

    @Test
    @DisplayName("解析到云元数据 169.254.169.254 → SecurityException，不返回可用地址")
    void resolve_metadataAddress_isRejected() throws UnknownHostException {
        final Future<InetSocketAddress> result = guarded(List.of(address("169.254.169.254"))).resolve(UNRESOLVED);

        result.awaitUninterruptibly();

        assertFalse(result.isSuccess(), "危险地址不得作为解析结果交给建连流程");
        assertInstanceOf(SecurityException.class, result.cause());
    }

    @Test
    @DisplayName("解析到链路本地 169.254.x.x / fe80:: / 云元数据 IPv6 → 拒绝")
    void resolve_linkLocalAddresses_areRejected() throws UnknownHostException {
        for (final String literal : List.of("169.254.10.10", "fe80::1", "fd00:ec2::254")) {
            final Future<InetSocketAddress> result = guarded(List.of(address(literal))).resolve(UNRESOLVED);
            result.awaitUninterruptibly();

            assertFalse(result.isSuccess(), literal + " 应被拒绝");
            assertInstanceOf(SecurityException.class, result.cause(), literal + " 应给出 SecurityException");
        }
    }

    @Test
    @DisplayName("解析到内网 / 回环地址 → 放行（保持原有放行范围，适配器需连内网 AI 服务）")
    void resolve_safeAddresses_arePassedThrough() throws UnknownHostException {
        for (final String literal : List.of("10.0.0.5", "172.16.3.4", "192.168.1.9", "127.0.0.1")) {
            final InetSocketAddress target = address(literal);
            final Future<InetSocketAddress> result = guarded(List.of(target)).resolve(UNRESOLVED);
            result.awaitUninterruptibly();

            assertTrue(result.isSuccess(), literal + " 应放行");
            assertEquals(target, result.getNow());
        }
    }

    @Test
    @DisplayName("resolveAll 剔除危险地址、保留安全地址")
    void resolveAll_dropsDangerousAndKeepsSafe() throws UnknownHostException {
        final InetSocketAddress safe = address("10.0.0.5");

        final Future<List<InetSocketAddress>> result =
                guarded(List.of(address("169.254.169.254"), safe)).resolveAll(UNRESOLVED);
        result.awaitUninterruptibly();

        assertTrue(result.isSuccess(), "同批存在安全地址时不应整体失败");
        assertEquals(List.of(safe), result.getNow());
    }

    @Test
    @DisplayName("resolveAll 全部为危险地址 → SecurityException")
    void resolveAll_allDangerous_isRejected() throws UnknownHostException {
        final Future<List<InetSocketAddress>> result =
                guarded(List.of(address("169.254.169.254"), address("fe80::1"))).resolveAll(UNRESOLVED);
        result.awaitUninterruptibly();

        assertFalse(result.isSuccess());
        assertInstanceOf(SecurityException.class, result.cause());
    }

    @Test
    @DisplayName("解析失败原样透传 —— DNS 故障不被伪装成安全拒绝，错误面不变")
    void resolve_delegateFailure_isPropagated() {
        final UnknownHostException cause = new UnknownHostException("rebind-test.invalid");
        final AddressResolver<InetSocketAddress> resolver = SsrfGuardedAddressResolverGroup
                .withDelegate(executor -> StubAddressResolvers.failing(cause))
                .getResolver(ImmediateEventExecutor.INSTANCE);

        final Future<InetSocketAddress> result = resolver.resolve(UNRESOLVED);
        result.awaitUninterruptibly();

        assertFalse(result.isSuccess());
        assertSame(cause, result.cause());
    }

    @Test
    @DisplayName("isSupported / isResolved 委托给底层解析器")
    void delegatingQueries_areForwarded() throws UnknownHostException {
        final AddressResolver<InetSocketAddress> resolver = guarded(List.of(address("10.0.0.5")));

        assertTrue(resolver.isSupported(UNRESOLVED));
        assertFalse(resolver.isResolved(UNRESOLVED));
    }

    private static AddressResolver<InetSocketAddress> guarded(final List<InetSocketAddress> resolvedByDelegate) {
        return SsrfGuardedAddressResolverGroup
                .withDelegate(executor -> StubAddressResolvers.fixed(resolvedByDelegate))
                .getResolver(ImmediateEventExecutor.INSTANCE);
    }

    private static InetSocketAddress address(final String literal) throws UnknownHostException {
        return new InetSocketAddress(InetAddress.getByName(literal), PORT);
    }
}
