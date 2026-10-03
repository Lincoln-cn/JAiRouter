package org.unreal.modelrouter.common.util;

import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.web.server.ServerWebExchange;

import java.util.ArrayList;
import java.util.List;

/**
 * 客户端 IP 推导的<b>单一实现</b>（#151 Stage 3）。
 *
 * <p>设计文档 §4 的立场：信任决策必须统一到一个共享组件，逐站点私有拷贝不可接受。
 * 本类即该组件——所有需要「按信任策略推导客户端 IP」的地方都必须走它，
 * 否则会出现「限流按键用伪造 XFF、审计日志记真实 IP」这类不一致。</p>
 *
 * <h2>算法</h2>
 * <ol>
 *   <li>取直连对端地址（{@code remoteAddress}）并规范化。</li>
 *   <li><b>对端不可信 → 直接返回它，完全忽略任何转发头。</b>这是防伪造的核心：
 *       转发头由客户端随意填写，只有自家代理追加的部分才可采信。</li>
 *   <li>对端可信 → 解析 {@code X-Forwarded-For} 为跳列表，<b>从右往左</b>跳过仍然可信的代理跳，
 *       返回第一个非可信跳（真实客户端）。</li>
 *   <li>XFF 无可采信跳时退到 {@code X-Real-IP}，再退到直连对端。</li>
 * </ol>
 *
 * <p>第 3 步的「右向左走跳」是设计文档 §2.3 的决策：单纯取末跳只在「恰好一跳可信代理」时成立，
 * 链路上有第二个自家代理时末跳会得到那个代理而非客户端。伪造的前缀（{@code spoofed, client, proxy1}）
 * 同样会被跳过。</p>
 *
 * <p><b>策略来源</b>：由 {@link #setPolicy} 注入，生产环境由 Spring 配置在启动时设置一次；
 * 未设置时为空策略（不信任任何对端），与既有默认行为一致——不得为了让文档好看而放宽默认值。</p>
 *
 * @author JAiRouter Team
 * @since 3.2.6
 */
public final class ClientIpResolver {

    /** 空策略：不信任任何对端（与 historic 默认一致） */
    private static volatile TrustedProxyPolicy policy = TrustedProxyPolicy.empty();

    private ClientIpResolver() {
        // 工具类不应实例化
    }

    /**
     * 设置信任策略（生产环境由 Spring 配置在启动时调用一次）。
     *
     * @param newPolicy 新策略；{@code null} 视为空策略
     */
    public static void setPolicy(final TrustedProxyPolicy newPolicy) {
        policy = newPolicy != null ? newPolicy : TrustedProxyPolicy.empty();
    }

    /**
     * 当前生效的信任策略。
     *
     * @return 策略，绝不为 null
     */
    public static TrustedProxyPolicy currentPolicy() {
        return policy;
    }

    /**
     * 推导客户端 IP。
     *
     * @param exchange 请求交换对象，可为 null
     * @return 规范化后的客户端 IP；无法判定时返回 {@code "unknown"}
     */
    public static String resolve(final ServerWebExchange exchange) {
        return exchange == null ? "unknown" : resolve(exchange.getRequest());
    }

    /**
     * 推导客户端 IP。
     *
     * @param request 请求对象，可为 null
     * @return 规范化后的客户端 IP；无法判定时返回 {@code "unknown"}
     */
    public static String resolve(final ServerHttpRequest request) {
        if (request == null) {
            return "unknown";
        }
        final String peer = request.getRemoteAddress() != null
                ? request.getRemoteAddress().getAddress().getHostAddress()
                : null;
        return resolve(peer,
                request.getHeaders().getFirst("X-Forwarded-For"),
                request.getHeaders().getFirst("X-Real-IP"));
    }

    /**
     * 推导核心（与 Web 框架解耦，便于参数化测试）。
     *
     * @param peerAddress 直连对端地址，可为 null
     * @param forwardedFor {@code X-Forwarded-For} 原文，可为 null
     * @param realIp       {@code X-Real-IP} 原文，可为 null
     * @return 规范化后的客户端 IP；无法判定时返回 {@code "unknown"}
     */
    static String resolve(final String peerAddress,
                          final String forwardedFor,
                          final String realIp) {
        if (peerAddress == null || peerAddress.isBlank()) {
            // 取不到对端就无法建立信任基础——此时采信转发头等于无条件信任
            return "unknown";
        }
        final String peer = IpUtils.canonicalizeIp(peerAddress);
        if (!policy.trusts(peer)) {
            return peer;
        }

        // 对端可信：右向左走跳，跳过仍然可信的代理跳
        final List<String> hops = parseHops(forwardedFor);
        for (int i = hops.size() - 1; i >= 0; i--) {
            final String hop = IpUtils.canonicalizeIp(hops.get(i));
            if ("unknown".equals(hop)) {
                continue;
            }
            if (!policy.trusts(hop)) {
                return hop;
            }
        }

        final String fallback = IpUtils.canonicalizeIp(realIp);
        return "unknown".equals(fallback) ? peer : fallback;
    }

    /** 解析 XFF 为跳列表，剔除空白与 {@code unknown} 占位。 */
    private static List<String> parseHops(final String forwardedFor) {
        final List<String> hops = new ArrayList<>();
        if (forwardedFor == null || forwardedFor.isBlank()) {
            return hops;
        }
        for (String raw : forwardedFor.split(",")) {
            final String hop = raw.trim();
            if (!hop.isEmpty() && !"unknown".equalsIgnoreCase(hop)) {
                hops.add(hop);
            }
        }
        return hops;
    }
}
