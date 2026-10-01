package org.unreal.modelrouter.router.ratelimit;

import java.util.Locale;

/**
 * 限流的<b>键维度</b>：限流器按什么标识来区分被限流对象（#161）。
 *
 * <p>默认沿用历史上的客户端 IP 维度。但在 Kubernetes 集群 + 无 L7 Ingress 的部署下，
 * {@code remoteAddress} 是<b>节点 IP</b>而非真实客户端 IP——按 IP 计数等于按垃圾数据计数，
 * 要么把整个节点的流量算作一个客户端，要么在反代后全部算作网关。此时应改用
 * {@link #API_KEY} 或 {@link #TENANT} 维度。</p>
 *
 * @author JAiRouter Team
 * @since 3.2.4
 */
public enum RateLimitKeyDimension {

    /** 按客户端 IP 区分（历史默认）。 */
    CLIENT_IP("client-ip"),

    /** 按 API Key ID 区分；缺失时回退客户端 IP 并告警。 */
    API_KEY("api-key"),

    /** 按租户区分：API Key ID 优先，缺失时回退客户端 IP（两者同为空才算缺失）。 */
    TENANT("tenant");

    private final String configValue;

    RateLimitKeyDimension(final String configValue) {
        this.configValue = configValue;
    }

    /**
     * 配置文件中使用的取值。
     *
     * @return 配置取值，如 {@code client-ip}
     */
    public String configValue() {
        return configValue;
    }

    /**
     * 解析配置取值，无法识别时回退 {@link #CLIENT_IP}。
     *
     * <p>回退而非报错是有意的：维度取值写错不应让限流失效或让应用起不来，
     * 保持与历史默认一致的行为最安全。</p>
     *
     * @param raw 配置原文，大小写与连字符/下划线不敏感，可为 null
     * @return 解析结果，绝不返回 null
     */
    public static RateLimitKeyDimension parse(final String raw) {
        if (raw == null || raw.trim().isEmpty()) {
            return CLIENT_IP;
        }
        final String normalized = raw.trim().toLowerCase(Locale.ROOT).replace('_', '-');
        for (RateLimitKeyDimension dimension : values()) {
            if (dimension.configValue.equals(normalized)) {
                return dimension;
            }
        }
        return CLIENT_IP;
    }
}
