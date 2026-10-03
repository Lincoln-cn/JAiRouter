package org.unreal.modelrouter.common.util;

import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 可信代理策略（#151 §3）：判断「直连对端是否是可采信其转发头（XFF/X-Real-IP）的代理」。
 *
 * <p>这是<b>信任决策的唯一来源</b>。设计文档 §4 的立场是：任何客户端 IP 推导点都必须复用本类，
 * 不允许各自写一份判断——否则会出现「限流按键用的是伪造 XFF、而审计日志记的是真实 IP」这类
 * 不一致，且伪造面仍然存在。</p>
 *
 * <p>匹配语义（§3.2–§3.5）：</p>
 * <ul>
 *   <li><b>IPv4 支持 CIDR</b>：{@code 10.0.0.0/8}、{@code 192.168.1.0/24}、{@code 10.0.0.1}（等于 /32）。
 *       云原生下代理常以网段部署（Pod IP 随扩缩容漂移），精确 IP 不可运维。</li>
 *   <li><b>IPv6 仅精确匹配</b>：IPv6 CIDR 不在本阶段范围，配置里出现会 WARN 且该条目不参与匹配
 *       （不会误匹配）。</li>
 *   <li>比较前两侧统一走 {@link IpUtils#canonicalizeIp}：剥端口/方括号、IPv6 压缩归一、
 *       {@code ::1} → {@code 127.0.0.1}。</li>
 * </ul>
 *
 * @author JAiRouter Team
 * @since 3.2.6
 */
@Slf4j
public final class TrustedProxyPolicy {

    /** 空策略：谁也不信（默认配置的语义）。 */
    private static final TrustedProxyPolicy EMPTY = new TrustedProxyPolicy(Collections.emptyList());

    private final List<String> entries;

    private TrustedProxyPolicy(final List<String> entries) {
        this.entries = Collections.unmodifiableList(entries);
    }

    /**
     * 空策略（不信任任何对端）。默认配置即此语义——这是安全默认，不得为了文档好看而放宽。
     *
     * @return 空策略
     */
    public static TrustedProxyPolicy empty() {
        return EMPTY;
    }

    /**
     * 解析逗号分隔的配置值。
     *
     * @param raw 形如 {@code "10.0.0.0/8, 203.0.113.50"}，可为 null/空白
     * @return 策略；{@code raw} 为空时返回 {@link #empty()}
     */
    public static TrustedProxyPolicy parse(final String raw) {
        if (raw == null || raw.isBlank()) {
            return EMPTY;
        }
        final List<String> parsed = new ArrayList<>();
        for (String token : raw.split(",")) {
            final String trimmed = token.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            if (trimmed.indexOf('/') >= 0) {
                final String[] parts = trimmed.split("/", 2);
                final String cidrIp = IpUtils.canonicalizeIp(parts[0]);
                if (cidrIp.indexOf(':') >= 0) {
                    // §3.4：IPv6 CIDR 不支持——显式告警而不是静默永不匹配
                    log.warn("trusted-proxies 中的 IPv6 CIDR 暂不支持，该条目不参与匹配: {}", trimmed);
                    continue;
                }
                parsed.add(cidrIp + "/" + parts[1].trim());
            } else {
                parsed.add(IpUtils.canonicalizeIp(trimmed));
            }
        }
        return parsed.isEmpty() ? EMPTY : new TrustedProxyPolicy(parsed);
    }

    /**
     * 是否为空策略。
     *
     * @return 是否不信任任何对端
     */
    public boolean isEmpty() {
        return entries.isEmpty();
    }

    /**
     * 规范化后的条目（供日志与诊断使用）。
     *
     * @return 不可变列表
     */
    public List<String> entries() {
        return entries;
    }

    /**
     * 直连对端是否可信。
     *
     * @param peerAddress 直连对端地址（通常来自 {@code remoteAddress.getHostAddress()}），可为 null
     * @return 是否可采信该对端转发的 XFF/X-Real-IP
     */
    public boolean trusts(final String peerAddress) {
        if (entries.isEmpty() || peerAddress == null || peerAddress.isBlank()) {
            return false;
        }
        final String peer = IpUtils.canonicalizeIp(peerAddress);
        for (String entry : entries) {
            if (entry.indexOf('/') >= 0) {
                if (IpUtils.matchesCidr(peer, entry)) {
                    return true;
                }
            } else if (entry.equals(peer)) {
                return true;
            }
        }
        return false;
    }
}
