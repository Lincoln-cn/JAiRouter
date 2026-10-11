package org.unreal.modelrouter.router.http;

import io.netty.channel.socket.nio.NioDatagramChannel;
import io.netty.resolver.AddressResolver;
import io.netty.resolver.AddressResolverGroup;
import io.netty.resolver.dns.DefaultDnsServerAddressStreamProvider;
import io.netty.resolver.dns.DnsAddressResolverGroup;
import io.netty.util.concurrent.EventExecutor;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.GenericFutureListener;
import io.netty.util.concurrent.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.unreal.modelrouter.common.util.UrlSecurityValidator;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * 出站解析守卫：把 SSRF 判据绑定到「实际用于建立连接的那次解析结果」上（#266）。
 *
 * <p>背景：{@link UrlSecurityValidator#validateOutboundBaseUrl(String)} 在写入侧 / 出站前解析一次主机名并校验，
 * 之后 WebClient 会<b>再自行解析一次</b>来建立连接。两次解析相互独立，因此当攻击者控制域名且应答可变
 * （TTL=0 或按查询次数返回不同结果）时，校验通过的主机名可以在连接时解析到链路本地 / 云元数据地址 ——
 * 即 DNS 重绑定绕过面。</p>
 *
 * <p>本类替换 {@link reactor.netty.http.client.HttpClient} 的 {@link AddressResolverGroup}，
 * 在连接期解析完成、建立 TCP 连接之前，对<b>即将被使用的地址</b>复用
 * {@link UrlSecurityValidator#isDangerousResolvedAddress(InetAddress)} 再判一次：命中即让本次解析失败
 * （{@link SecurityException}），连接不会建立。解析因此只发生一次，校验与连接看到的是同一组地址，
 * 不存在「校验之后应答再变」的窗口。</p>
 *
 * <p>两点刻意保持原语义：<br>
 * ① 解析本身失败（如 {@code UnknownHostException}）按原样透传，仍由连接阶段报错，错误面不变；<br>
 * ② {@code resolveAll} 只剔除危险地址，全为危险时才失败 —— 与 {@code resolve} 的「一个都不能危险」一致，
 * 但不会因为同批里恰有一个危险地址就误伤正常解析。</p>
 *
 * @author JAiRouter Team
 */
public final class SsrfGuardedAddressResolverGroup extends AddressResolverGroup<InetSocketAddress> {

    private static final Logger logger = LoggerFactory.getLogger(SsrfGuardedAddressResolverGroup.class);

    /**
     * 进程级共享实例：底层 DNS 解析默认值（NioDatagramChannel + 平台 DNS 服务器）与替换前一致。
     *
     * <p>不随单个 {@link WebClientPool} 释放而关闭：它是进程级共享的解析器，连接池可以有多份。</p>
     */
    private static final DnsAddressResolverGroup DEFAULT_DNS =
            new DnsAddressResolverGroup(NioDatagramChannel.class, DefaultDnsServerAddressStreamProvider.INSTANCE);

    private static final SsrfGuardedAddressResolverGroup INSTANCE =
            new SsrfGuardedAddressResolverGroup(DEFAULT_DNS::getResolver);

    private final Function<EventExecutor, AddressResolver<InetSocketAddress>> delegateFactory;

    private SsrfGuardedAddressResolverGroup(
            final Function<EventExecutor, AddressResolver<InetSocketAddress>> delegateFactory) {
        this.delegateFactory = delegateFactory;
    }

    /**
     * @return 接入出站 {@link reactor.netty.http.client.HttpClient} 的共享实例
     */
    public static SsrfGuardedAddressResolverGroup instance() {
        return INSTANCE;
    }

    /**
     * 用可控的委托解析器构造一份守卫（包内可见，供测试模拟「同一主机名解析出不同地址」）。
     *
     * @param delegateFactory 按 event loop 提供委托解析器
     * @return 守卫实例
     */
    static SsrfGuardedAddressResolverGroup withDelegate(
            final Function<EventExecutor, AddressResolver<InetSocketAddress>> delegateFactory) {
        return new SsrfGuardedAddressResolverGroup(delegateFactory);
    }

    @Override
    protected AddressResolver<InetSocketAddress> newResolver(final EventExecutor executor) {
        return new GuardedAddressResolver(delegateFactory.apply(executor), executor);
    }

    private static boolean isBlocked(final InetSocketAddress address) {
        final InetAddress resolved = address.getAddress();
        return resolved != null && UrlSecurityValidator.isDangerousResolvedAddress(resolved);
    }

    private static SecurityException blockedError(final InetSocketAddress address) {
        final InetAddress resolved = address.getAddress();
        final String target = resolved == null ? address.toString() : resolved.getHostAddress();
        logger.warn("出站解析命中危险地址，连接被拒绝: {}", target);
        return new SecurityException("Outbound connection target resolves to a blocked address: " + target);
    }

    /**
     * 委托 + 校验：解析结果先过硬规则，再交给上层建立连接。
     */
    private static final class GuardedAddressResolver implements AddressResolver<InetSocketAddress> {

        private final AddressResolver<InetSocketAddress> delegate;
        private final EventExecutor executor;

        GuardedAddressResolver(final AddressResolver<InetSocketAddress> delegate, final EventExecutor executor) {
            this.delegate = delegate;
            this.executor = executor;
        }

        @Override
        public boolean isSupported(final SocketAddress address) {
            return delegate.isSupported(address);
        }

        @Override
        public boolean isResolved(final SocketAddress address) {
            return delegate.isResolved(address);
        }

        @Override
        public Future<InetSocketAddress> resolve(final SocketAddress address) {
            return resolve(address, executor.newPromise());
        }

        @Override
        public Future<InetSocketAddress> resolve(final SocketAddress address,
                                                 final Promise<InetSocketAddress> promise) {
            final GenericFutureListener<Future<InetSocketAddress>> guard =
                    resolved -> completeSingle(promise, resolved);
            delegate.resolve(address).addListener(guard);
            return promise;
        }

        @Override
        public Future<List<InetSocketAddress>> resolveAll(final SocketAddress address) {
            return resolveAll(address, executor.newPromise());
        }

        @Override
        public Future<List<InetSocketAddress>> resolveAll(final SocketAddress address,
                                                          final Promise<List<InetSocketAddress>> promise) {
            final GenericFutureListener<Future<List<InetSocketAddress>>> guard =
                    resolved -> completeAll(promise, resolved);
            delegate.resolveAll(address).addListener(guard);
            return promise;
        }

        @Override
        public void close() {
            delegate.close();
        }

        private static void completeSingle(final Promise<InetSocketAddress> promise,
                                           final Future<InetSocketAddress> resolved) {
            if (!resolved.isSuccess()) {
                promise.tryFailure(resolved.cause());
                return;
            }
            final InetSocketAddress target = resolved.getNow();
            if (isBlocked(target)) {
                promise.tryFailure(blockedError(target));
                return;
            }
            promise.trySuccess(target);
        }

        private static void completeAll(final Promise<List<InetSocketAddress>> promise,
                                        final Future<List<InetSocketAddress>> resolved) {
            if (!resolved.isSuccess()) {
                promise.tryFailure(resolved.cause());
                return;
            }
            final List<InetSocketAddress> all = resolved.getNow();
            final List<InetSocketAddress> safe = new ArrayList<>(all.size());
            for (final InetSocketAddress address : all) {
                if (!isBlocked(address)) {
                    safe.add(address);
                }
            }
            if (safe.isEmpty() && !all.isEmpty()) {
                promise.tryFailure(blockedError(all.get(0)));
                return;
            }
            promise.trySuccess(safe);
        }
    }
}
