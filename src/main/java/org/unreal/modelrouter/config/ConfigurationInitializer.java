package org.unreal.modelrouter.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.unreal.modelrouter.model.ModelServiceRegistry;

/**
 * 配置初始化器
 * 在Spring Boot应用启动完成后执行配置初始化工作
 * 确保ModelServiceRegistry与ConfigurationService正确关联
 */
@Component
@Order(1) // 确保在其他CommandLineRunner之前执行
public class ConfigurationInitializer implements CommandLineRunner {

    private static final Logger logger = LoggerFactory.getLogger(ConfigurationInitializer.class);

    private final ConfigurationService configurationService;
    private final ModelServiceRegistry modelServiceRegistry;

    @Autowired
    public ConfigurationInitializer(ConfigurationService configurationService,
                                    ModelServiceRegistry modelServiceRegistry) {
        this.configurationService = configurationService;
        this.modelServiceRegistry = modelServiceRegistry;
    }

    @Override
    public void run(String... args) throws Exception {
        logger.info("开始执行配置初始化...");

        try {
            // 1. 设置ModelServiceRegistry引用到ConfigurationService
            // 避免循环依赖问题
            configurationService.setModelServiceRegistry(modelServiceRegistry);
            logger.debug("已设置ModelServiceRegistry引用到ConfigurationService");

            // 2. 触发配置合并和初始化
            // ModelServiceRegistry在@PostConstruct中已经执行了初始化
            // 这里确保配置服务知道运行时注册表的状态
            logger.info("配置初始化检查...");

            // 3. 输出初始化状态信息
            logInitializationStatus();

            logger.info("配置初始化完成");

        } catch (Exception e) {
            logger.error("配置初始化过程中发生错误", e);
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
            logger.info("可用服务类型: {}", availableServiceTypes);

            // 获取所有实例信息
            var allInstances = modelServiceRegistry.getAllInstances();
            int totalInstanceCount = 0;

            for (var entry : allInstances.entrySet()) {
                var serviceType = entry.getKey();
                var instances = entry.getValue();
                totalInstanceCount += instances.size();

                logger.info("服务 {} - {} 个实例:", serviceType, instances.size());
                for (var instance : instances) {
                    logger.info("  - {} @ {} (权重: {})",
                            instance.getName(),
                            instance.getBaseUrl(),
                            instance.getWeight());
                }
            }

            logger.info("总计: {} 个服务类型, {} 个实例", availableServiceTypes.size(), totalInstanceCount);

            // 检查是否存在持久化配置
            boolean hasPersistedConfig = configurationService instanceof ConfigurationService &&
                    ((ConfigurationService) configurationService).hasPersistedConfig();
            logger.info("持久化配置状态: {}", hasPersistedConfig ? "存在" : "仅使用YAML配置");

        } catch (Exception e) {
            logger.warn("记录初始化状态时发生错误: {}", e.getMessage());
        }
    }
}