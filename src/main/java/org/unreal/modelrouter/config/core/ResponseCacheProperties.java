package org.unreal.modelrouter.config.core;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * v2.9.9: 响应缓存配置属性.
 *
 * <p>配置 LLM 完整响应缓存（Response Cache）的各项参数。
 * 默认全部关闭/保守值，缓存为纯增量 opt-in 能力，不改变任何默认行为。
 *
 * @author JAiRouter Team
 * @since 2.9.9
 */
@Data
@ConfigurationProperties(prefix = "jairouter.response-cache")
public final class ResponseCacheProperties {

    /**
     * 是否启用响应缓存（默认关闭，opt-in）
     */
    private boolean enabled = false;

    /**
     * 缓存条目有效期（默认 1h，对齐 liteLLM 默认）
     */
    private Duration ttl = Duration.ofHours(1);

    /**
     * 缓存最大条目数（默认 10000）
     */
    private int maxSize = 10000;

    /**
     * 仅缓存确定性请求（chat: temperature==0/null 且 n==1/null；默认 true）
     */
    private boolean onlyDeterministic = true;

    /**
     * 跳过流式请求（默认 true；P0 仅支持非流式缓存）
     */
    private boolean skipStreaming = true;
}
