package org.unreal.modelrouter.common.config;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.unreal.modelrouter.common.util.ClientIpResolver;
import org.unreal.modelrouter.common.util.TrustedProxyPolicy;

/**
 * 把 {@code jairouter.security.rate-limit.trusted-proxies} 注入到共享的客户端 IP 推导组件（#151）。
 *
 * <p>配置只在启动时读取一次：信任边界属于部署事实，运行期变更会带来「一部分请求按旧策略、
 * 一部分按新策略」的窗口，比不支持热更新更危险。</p>
 *
 * @author JAiRouter Team
 * @since 3.2.6
 */
@Slf4j
@Configuration
public class ClientIpTrustConfiguration {

    private final String trustedProxiesRaw;

    public ClientIpTrustConfiguration(
            @Value("${jairouter.security.rate-limit.trusted-proxies:}") final String trustedProxiesRaw) {
        this.trustedProxiesRaw = trustedProxiesRaw;
    }

    /**
     * 启动时装配信任策略，并对常见误配置给出显式告警。
     */
    @PostConstruct
    public void initPolicy() {
        final TrustedProxyPolicy parsed = TrustedProxyPolicy.parse(trustedProxiesRaw);
        ClientIpResolver.setPolicy(parsed);

        if (parsed.isEmpty()) {
            log.info("客户端 IP 信任策略：未配置 trusted-proxies，不采信任何转发头"
                    + "（反向代理之后的部署必须配置，否则限流与审计会以代理 IP 为准）");
        } else {
            log.info("客户端 IP 信任策略已加载，可信代理条目: {}", parsed.entries());
        }
    }
}
