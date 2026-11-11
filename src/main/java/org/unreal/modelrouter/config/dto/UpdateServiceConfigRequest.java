package org.unreal.modelrouter.config.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.unreal.modelrouter.common.dto.LoadBalanceConfig;
import org.unreal.modelrouter.common.dto.RateLimitConfig;
import org.unreal.modelrouter.common.dto.CircuitBreakerConfig;
import org.unreal.modelrouter.common.dto.StickyConfig;

/**
 * 更新服务配置请求 DTO
 * 用于前端更新服务配置（不含实例）
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public final class UpdateServiceConfigRequest {

    /**
     * 适配器类型
     */
    private String adapter;

    /**
     * 负载均衡配置
     */
    private LoadBalanceConfig loadBalance;

    /**
     * 限流配置
     */
    private RateLimitConfig rateLimit;

    /**
     * 熔断器配置
     */
    private CircuitBreakerConfig circuitBreaker;

    /**
     * 会话粘性配置（issue #1095）
     *
     * <p>补这个字段之前，粘性只能通过 YAML 的 {@code model.services.<type>.sticky} 设置，
     * 而它的默认值是开启——多实例场景下任何负载均衡策略都不会真正分流，
     * 且控制台没有任何入口可以关闭。null 表示不修改。
     */
    private StickyConfig sticky;

    /**
     * 回退配置
     */
    private Object fallback;
}