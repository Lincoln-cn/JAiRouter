package org.unreal.modelrouter.common.config;

import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.boot.actuate.autoconfigure.metrics.MeterRegistryCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.unreal.modelrouter.common.util.InstanceIdentity;

/**
 * 给所有指标打上 {@code instance} 公共标签（#181）。
 *
 * <p>多副本下同一个指标名会有 N 条时间序列，没有实例维度就无法回答「这条曲线是哪个副本」，
 * 也无法判断某个副本是否掉队。加上该标签后，Prometheus 侧可直接按 {@code instance} 聚合或下钻。</p>
 *
 * <p>取值来自 {@link InstanceIdentity#id()}——与事件广播、归档文件命名用的是同一个副本标识，
 * 避免出现「日志里叫 A、指标里叫 B」的混乱。</p>
 *
 * @author JAiRouter Team
 * @since 3.2.7
 */
@Configuration
public class InstanceMetricsConfiguration {

    /** 公共标签名：与 Prometheus 既有的 instance 约定一致。 */
    private static final String TAG_INSTANCE = "instance";

    /**
     * 注册实例维度公共标签，作用于所有 {@link MeterRegistry}。
     *
     * @return 度量注册表定制器
     */
    @Bean
    public MeterRegistryCustomizer<MeterRegistry> instanceDimensionCommonTag() {
        return registry -> registry.config().commonTags(TAG_INSTANCE, InstanceIdentity.id());
    }
}
