package org.unreal.modelrouter.config.sync.repository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.unreal.modelrouter.config.core.dto.ServiceConfiguration;
import org.unreal.modelrouter.persistence.store.StoreManager;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * 服务配置仓库（基于 StoreManager）
 *
 * 负责服务配置的数据访问，封装 StoreManager 的底层操作。
 * 提供强类型的 ServiceConfiguration 访问接口。
 *
 * @author JAiRouter Team
 * @since v2.2.6
 */
@Component("storeConfigRepository")
public class StoreConfigRepository {

    private static final Logger logger = LoggerFactory.getLogger(StoreConfigRepository.class);
    private static final String CONFIG_KEY = "model-router-config";

    private final StoreManager storeManager;

    public StoreConfigRepository(final StoreManager storeManager) {
        this.storeManager = storeManager;
    }

    /**
     * 获取所有服务配置
     *
     * @return 服务配置 Map，key 为 serviceType
     */
    public Map<String, ServiceConfiguration> findAll() {
        try {
            Map<String, Object> config = storeManager.getConfig(CONFIG_KEY);
            if (config == null || !config.containsKey("services")) {
                return Map.of();
            }

            @SuppressWarnings("unchecked")
            Map<String, Object> services = (Map<String, Object>) config.get("services");
            return ServiceConfiguration.fromServicesMap(services);

        } catch (Exception e) {
            logger.error("获取所有服务配置失败：error={}", e.getMessage(), e);
            return Map.of();
        }
    }

    /**
     * 获取指定服务配置
     *
     * @param serviceType 服务类型
     * @return 服务配置，不存在返回 Optional.empty()
     */
    public Optional<ServiceConfiguration> findById(final String serviceType) {
        try {
            Map<String, Object> config = storeManager.getConfig(CONFIG_KEY);
            if (config == null || !config.containsKey("services")) {
                return Optional.empty();
            }

            @SuppressWarnings("unchecked")
            Map<String, Object> services = (Map<String, Object>) config.get("services");
            @SuppressWarnings("unchecked")
            Map<String, Object> serviceMap = (Map<String, Object>) services.get(serviceType);

            return Optional.ofNullable(serviceMap != null ? ServiceConfiguration.fromMap(serviceMap) : null);

        } catch (Exception e) {
            logger.error("获取服务配置失败：serviceType={}, error={}", serviceType, e.getMessage(), e);
            return Optional.empty();
        }
    }

    /**
     * 保存服务配置
     *
     * @param serviceType 服务类型
     * @param config 服务配置
     */
    public void save(final String serviceType, final ServiceConfiguration config) {
        logger.info("保存服务配置：serviceType={}", serviceType);

        try {
            Map<String, Object> currentConfig = getCurrentConfig();
            @SuppressWarnings("unchecked")
            Map<String, Object> services = (Map<String, Object>) currentConfig.get("services");

            // 保存配置
            services.put(serviceType, config.toMap());

            // 添加版本元数据
            addMetadata(currentConfig, "save", "保存服务配置：" + serviceType, serviceType);

            storeManager.saveConfig(CONFIG_KEY, currentConfig);
            logger.info("服务配置保存成功：serviceType={}", serviceType);

        } catch (Exception e) {
            logger.error("保存服务配置失败：serviceType={}, error={}", serviceType, e.getMessage(), e);
            throw new RuntimeException("保存服务配置失败：" + e.getMessage(), e);
        }
    }

    /**
     * 删除服务配置
     *
     * @param serviceType 服务类型
     */
    public void delete(final String serviceType) {
        logger.info("删除服务配置：serviceType={}", serviceType);

        try {
            Map<String, Object> currentConfig = getCurrentConfig();
            @SuppressWarnings("unchecked")
            Map<String, Object> services = (Map<String, Object>) currentConfig.get("services");

            if (!services.containsKey(serviceType)) {
                logger.warn("删除不存在的服配置：serviceType={}", serviceType);
                return;
            }

            services.remove(serviceType);

            // 添加版本元数据
            addMetadata(currentConfig, "delete", "删除服务配置：" + serviceType, serviceType);

            storeManager.saveConfig(CONFIG_KEY, currentConfig);
            logger.info("服务配置删除成功：serviceType={}", serviceType);

        } catch (Exception e) {
            logger.error("删除服务配置失败：serviceType={}, error={}", serviceType, e.getMessage(), e);
            throw new RuntimeException("删除服务配置失败：" + e.getMessage(), e);
        }
    }

    /**
     * 检查服务是否存在
     *
     * @param serviceType 服务类型
     * @return 是否存在
     */
    public boolean exists(final String serviceType) {
        return findById(serviceType).isPresent();
    }

    /**
     * 获取配置并转换为 Map
     *
     * @return 配置 Map
     */
    public Map<String, Object> getConfigAsMap() {
        return storeManager.getConfig(CONFIG_KEY);
    }

    /**
     * 保存配置 Map
     *
     * @param config 配置 Map
     */
    public void saveConfigMap(final Map<String, Object> config) {
        storeManager.saveConfig(CONFIG_KEY, config);
    }

    // ==================== 辅助方法 ====================

    /**
     * 获取当前配置
     */
    private Map<String, Object> getCurrentConfig() {
        Map<String, Object> config = storeManager.getConfig(CONFIG_KEY);
        if (config == null) {
            config = new HashMap<>();
            config.put("services", new HashMap<String, Object>());
        }
        if (!config.containsKey("services")) {
            config.put("services", new HashMap<String, Object>());
        }
        return config;
    }

    /**
     * 添加版本元数据
     */
    private void addMetadata(final Map<String, Object> config, final String operation,
                             final String operationDetail, final String serviceType) {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("operation", operation);
        metadata.put("operationDetail", operationDetail);
        metadata.put("serviceType", serviceType);
        metadata.put("timestamp", System.currentTimeMillis());
        config.put("_metadata", metadata);
    }
}
