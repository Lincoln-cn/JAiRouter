package org.unreal.modelrouter.monitoring.alert;

import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.unreal.modelrouter.config.MonitoringProperties;
import org.unreal.modelrouter.monitoring.SlowQueryDetector;
import org.unreal.modelrouter.tracing.logger.StructuredLogger;
import org.unreal.modelrouter.util.ApplicationContextProvider;

/**
 * 慢查询告警自动配置
 * 
 * 根据配置条件自动装配慢查询告警相关的组件。
 * 
 * @author JAiRouter Team
 * @since 1.0.0
 */
@Configuration
@EnableConfigurationProperties(SlowQueryAlertProperties.class)
@ConditionalOnProperty(name = "jairouter.monitoring.slow-query-alert.enabled", havingValue = "true", matchIfMissing = true)
public class SlowQueryAlertAutoConfiguration {
    
    /**
     * 创建慢查询告警服务
     * 
     * @param monitoringProperties 监控配置属性
     * @param alertProperties 告警配置属性
     * @param structuredLogger 结构化日志记录器
     * @param meterRegistry 指标注册表
     * @param slowQueryDetector 慢查询检测器
     * @return 慢查询告警服务
     */
    @Bean
    @ConditionalOnProperty(name = "jairouter.monitoring.enabled", havingValue = "true", matchIfMissing = true)
    public SlowQueryAlertService slowQueryAlertService(
            MonitoringProperties monitoringProperties,
            SlowQueryAlertProperties alertProperties,
            StructuredLogger structuredLogger,
            MeterRegistry meterRegistry
            ) {
        
        return new SlowQueryAlertService(
                monitoringProperties,
                alertProperties,
                structuredLogger,
                meterRegistry
        );
    }
}