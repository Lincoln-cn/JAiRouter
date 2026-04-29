package org.unreal.modelrouter.config.core;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.unreal.modelrouter.router.model.ModelServiceRegistry;

/**
 * 配置初始化器
 * 在 Spring Boot 应用启动完成后执行配置初始化工作
 * 确保 ModelServiceRegistry 与 ConfigurationService 正确关联
 * 
 * V1.4.4 重构版：数据库化配置管理
 * 由 ConfigInitializer 负责数据库表初始化和数据迁移
 * 本类仅负责设置 ModelServiceRegistry 引用并记录状态
 */
@Slf4j
@Component
@RequiredArgsConstructor
@org.springframework.context.annotation.DependsOn("jpaDatabaseInitializer")
public class ConfigurationInitializer {

    private final ConfigurationService configurationService;
    private final ModelServiceRegistry modelServiceRegistry;

    @PostConstruct
    public void initConfigurations() {
        log.info("开始执行配置初始化...");

        try {
            // V1.4.4: 数据库化配置管理，由 ConfigInitializer 负责数据库初始化
            // 此处仅设置 ModelServiceRegistry 引用并记录状态
            configurationService.setModelServiceRegistry(modelServiceRegistry);
            log.debug("已设置 ModelServiceRegistry 引用到 ConfigurationService");
            
            // 输出初始化状态信息
            logInitializationStatus();
            
            log.info("配置初始化完成（V1.4.4 数据库模式）");
            
        } catch (Exception e) {
            log.error("配置初始化过程中发生错误", e);
            throw e;
        }
    }

    /**
     * 记录初始化状态信息
     */
    private void logInitializationStatus() {
        try {
            // 获取可用服务类型
            var availableServiceTypes = modelServiceRegistry.getAvailableServiceTypes();
            log.info("可用服务类型：{}", availableServiceTypes);

            // 获取所有实例信息
            var allInstances = modelServiceRegistry.getAllInstances();
            int totalInstanceCount = 0;

            for (var entry : allInstances.entrySet()) {
                var serviceType = entry.getKey();
                var instances = entry.getValue();
                totalInstanceCount += instances.size();

                log.info("服务 {} - {} 个实例:", serviceType, instances.size());
                for (var instance : instances) {
                    log.info("  - {} @ {} (权重：{})",
                            instance.getName(),
                            instance.getBaseUrl(),
                            instance.getWeight());
                }
            }

            log.info("总计：{} 个服务类型，{} 个实例", availableServiceTypes.size(), totalInstanceCount);

        } catch (Exception e) {
            log.warn("记录初始化状态时发生错误：{}", e.getMessage());
        }
    }
}
