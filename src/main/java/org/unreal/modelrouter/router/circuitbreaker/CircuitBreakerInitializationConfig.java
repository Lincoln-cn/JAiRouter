package org.unreal.modelrouter.router.circuitbreaker;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.DependsOn;
import org.unreal.modelrouter.config.service.CircuitBreakerConfigService;

import lombok.RequiredArgsConstructor;

/**
 * 熔断器配置初始化
 * 
 * 在应用启动后初始化熔断器管理器和自适应阈值管理器的依赖关系
 * 
 * @author JAiRouter Team
 * @since v2.6.12
 */
@Configuration
@RequiredArgsConstructor
@DependsOn({"circuitBreakerManager", "adaptiveThresholdManager", "circuitBreakerConfigService"})
public class CircuitBreakerInitializationConfig implements ApplicationListener<ApplicationReadyEvent> {

    private static final Logger log = LoggerFactory.getLogger(CircuitBreakerInitializationConfig.class);

    private final CircuitBreakerManager circuitBreakerManager;
    private final AdaptiveThresholdManager adaptiveThresholdManager;
    private final CircuitBreakerConfigService circuitBreakerConfigService;

    @Override
    public void onApplicationEvent(final ApplicationReadyEvent event) {
        log.info("初始化熔断器组件依赖关系...");

        // 设置依赖关系
        circuitBreakerManager.setAdaptiveThresholdManager(adaptiveThresholdManager);

        // 同步自适应阈值启用状态
        boolean adaptiveEnabled = circuitBreakerConfigService.getGlobalConfig()
                .getAdaptiveThresholdEnabled() != null 
                && circuitBreakerConfigService.getGlobalConfig().getAdaptiveThresholdEnabled();
        adaptiveThresholdManager.setAdaptiveEnabled(adaptiveEnabled);

        log.info("熔断器组件初始化完成，自适应阈值调整: {}", adaptiveEnabled ? "已启用" : "已禁用");
    }
}
