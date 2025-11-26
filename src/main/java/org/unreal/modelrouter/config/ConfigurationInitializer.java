package org.unreal.modelrouter.config;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.unreal.modelrouter.model.ModelServiceRegistry;

import java.util.HashMap;
import java.util.Map;

/**
 * 配置初始化器
 * 在Spring Boot应用启动完成后执行配置初始化工作
 * 确保ModelServiceRegistry与ConfigurationService正确关联
 */
@Slf4j
@Component
@RequiredArgsConstructor
@org.springframework.context.annotation.DependsOn("h2DatabaseInitializer")
public class ConfigurationInitializer {

    private final ConfigurationService configurationService;
    private final ModelServiceRegistry modelServiceRegistry;

    @PostConstruct
    public void initConfigurations() throws Exception {
        log.info("开始执行配置初始化...");

        try {
            if (configurationService.getActualCurrentVersion() == 0) {
                // 1. 设置ModelServiceRegistry引用到ConfigurationService
                // 避免循环依赖问题
                configurationService.setModelServiceRegistry(modelServiceRegistry);
                log.debug("已设置ModelServiceRegistry引用到ConfigurationService");

                // 2. 触发配置合并和初始化
                // ModelServiceRegistry在@PostConstruct中已经执行了初始化
                // 这里确保配置服务知道运行时注册表的状态
                log.info("配置初始化检查...");

                // 3. 输出初始化状态信息
                logInitializationStatus();

                log.info("配置初始化完成");

                Map<String, Object> currentConfig = configurationService.getAllConfigurations();
                // 添加版本元数据
                Map<String, Object> metadata = new HashMap<>();
                metadata.put("operation", "createService");
                metadata.put("operationDetail", "初始化系统配置");
                metadata.put("serviceType", "all");
                metadata.put("timestamp", System.currentTimeMillis());
                currentConfig.put("_metadata", metadata);

                // 4. 保存初始版本为 0
                configurationService.saveAsNewVersion(currentConfig, "初始化配置", "System");

                log.info("已将初始配置保存为版本 0");
            }
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
            log.info("可用服务类型: {}", availableServiceTypes);

            // 获取所有实例信息
            var allInstances = modelServiceRegistry.getAllInstances();
            int totalInstanceCount = 0;

            for (var entry : allInstances.entrySet()) {
                var serviceType = entry.getKey();
                var instances = entry.getValue();
                totalInstanceCount += instances.size();

                log.info("服务 {} - {} 个实例:", serviceType, instances.size());
                for (var instance : instances) {
                    log.info("  - {} @ {} (权重: {})",
                            instance.getName(),
                            instance.getBaseUrl(),
                            instance.getWeight());
                }
            }

            log.info("总计: {} 个服务类型, {} 个实例", availableServiceTypes.size(), totalInstanceCount);

            // 检查是否存在持久化配置
            boolean hasPersistedConfig = configurationService instanceof ConfigurationService &&
                    configurationService.hasPersistedConfig();
            log.info("持久化配置状态: {}", hasPersistedConfig ? "存在" : "仅使用YAML配置");

        } catch (Exception e) {
            log.warn("记录初始化状态时发生错误: {}", e.getMessage());
        }
    }
}