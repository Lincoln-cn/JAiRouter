package org.unreal.modelrouter.router.http;

import io.netty.resolver.AddressResolver;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.ImmediateEventExecutor;
import io.netty.util.concurrent.Promise;

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.List;

/**
 * 测试用委托解析器：绕开真实 DNS，直接返回指定地址。
 *
 * <p>#266 的验证需要「同一主机名在不同时刻解析出不同地址」——真实 DNS 给不出这种应答，
 * 因此由它扮演解析器，把可控地址交给 {@link SsrfGuardedAddressResolverGroup}。</p>
 */
final class StubAddressResolvers {

    private StubAddressResolvers() {
    }

    /**
     * 固定返回给定地址：{@code resolve} 取首个，{@code resolveAll} 返回全部。
     *
     * @param resolved 解析结果（不可为空）
     * @return 委托解析器
     */
    static AddressResolver<InetSocketAddress> fixed(final List<InetSocketAddress> resolved) {
        return new AddressResolver<>() {
            @Override
            public boolean isSupported(final SocketAddress address) {
                return true;
            }

            @Override
            public boolean isResolved(final SocketAddress address) {
                return false;
            }

            @Override
            public Future<InetSocketAddress> resolve(final SocketAddress address) {
                return ImmediateEventExecutor.INSTANCE.newSucceededFuture(resolved.get(0));
            }

            @Override
            public Future<InetSocketAddress> resolve(final SocketAddress address,
                                                     final Promise<InetSocketAddress> promise) {
                promise.setSuccess(resolved.get(0));
                return promise;
            }

            @Override
            public Future<List<InetSocketAddress>> resolveAll(final SocketAddress address) {
                return ImmediateEventExecutor.INSTANCE.newSucceededFuture(resolved);
            }

            @Override
            public Future<List<InetSocketAddress>> resolveAll(final SocketAddress address,
                                                              final Promise<List<InetSocketAddress>> promise) {
                promise.setSuccess(resolved);
                return promise;
            }

            @Override
            public void close() {
                // 无底层资源
            }
        };
    }

    /**
     * 解析失败（模拟 DNS 故障）。
     *
     * @param cause 失败原因
     * @return 委托解析器
     */
    static AddressResolver<InetSocketAddress> failing(final Throwable cause) {
        return new AddressResolver<>() {
            @Override
            public boolean isSupported(final SocketAddress address) {
                return true;
            }

            @Override
            public boolean isResolved(final SocketAddress address) {
                return false;
            }

            @Override
            public Future<InetSocketAddress> resolve(final SocketAddress address) {
                return ImmediateEventExecutor.INSTANCE.newFailedFuture(cause);
            }

            @Override
            public Future<InetSocketAddress> resolve(final SocketAddress address,
                                                     final Promise<InetSocketAddress> promise) {
                promise.setFailure(cause);
                return promise;
            }

            @Override
            public Future<List<InetSocketAddress>> resolveAll(final SocketAddress address) {
                return ImmediateEventExecutor.INSTANCE.newFailedFuture(cause);
            }

            @Override
            public Future<List<InetSocketAddress>> resolveAll(final SocketAddress address,
                                                              final Promise<List<InetSocketAddress>> promise) {
                promise.setFailure(cause);
                return promise;
            }

            @Override
            public void close() {
                // 无底层资源
            }
        };
    }
}
