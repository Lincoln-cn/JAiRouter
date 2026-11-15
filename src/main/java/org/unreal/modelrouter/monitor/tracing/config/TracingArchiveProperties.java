package org.unreal.modelrouter.monitor.tracing.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 异常链路归档配置（#1155）。
 *
 * <p>只归档失败链路，普通链路仍然只在内存滚动。归档写入走异步线程，不阻塞请求主链路；
 * 过期数据每小时清理一次。</p>
 *
 * @author JAiRouter Team
 * @since 3.8.8
 */
@Data
@Component
@ConfigurationProperties(prefix = "jairouter.tracing.archive")
public class TracingArchiveProperties {

    /** 是否启用归档；关闭后异常链路只保留在内存保留区。 */
    private boolean enabled = true;

    /** 归档保留天数，默认 7 天。 */
    private int retentionDays = 7;
}
