package org.unreal.modelrouter.config.core;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.unreal.modelrouter.config.core.dto.ServiceConfiguration;
import org.unreal.modelrouter.common.dto.*;
import org.unreal.modelrouter.persistence.jpa.entity.ServiceConfigEntity;
import org.unreal.modelrouter.persistence.jpa.repository.ServiceConfigRepository;
import org.unreal.modelrouter.persistence.jpa.repository.ServiceInstanceRepository;
import org.unreal.modelrouter.config.sync.converter.ServiceConfigConverter;
import org.unreal.modelrouter.config.sync.merger.ConfigMerger;
import org.unreal.modelrouter.config.sync.repository.StoreConfigRepository;
import org.unreal.modelrouter.config.sync.validator.ServiceConfigValidator;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * 服务配置管理器 - 协调层 (Facade)
 *
 * 协调各组件完成服务配置管理：
 * - ServiceConfigValidator: 配置验证
 * - ConfigMerger: 配置合并
 * - ServiceConfigConverter: DTO 转换
 * - ServiceConfigRepository: 数据访问（StoreManager）
 *
 * 同时保留 JPA 数据库操作以支持现有 API。
 *
 * @author JAiRouter Team
 * @since v2.2.6
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ServiceConfigManager {

    // 新组件依赖
    private final ServiceConfigValidator validator;
    private final ConfigMerger merger;
    private final ServiceConfigConverter converter;
    private final StoreConfigRepository configRepository;

    // JPA Repository (保留现有数据库操作)
    private final ServiceConfigRepository jpaRepository;
    private final ServiceInstanceRepository instanceRepository;

    // ========== API 层方法 (DTO 方式) ==========

    /**
     * 获取所有服务配置 (API 用)
     */
    public List<ServiceConfigDTO> getAllServiceConfigs() {
        List<ServiceConfigEntity> entities = jpaRepository.findAllByIsLatestTrue();
        return entities.stream()
                .map(converter::toDTO)
                .collect(Collectors.toList());
    }

    /**
     * 获取指定服务类型的配置 (API 用)
     */
    public Optional<ServiceConfigDTO> getServiceConfig(final String serviceType) {
        return jpaRepository
                .findFirstByServiceTypeAndIsLatestTrue(serviceType)
                .map(converter::toDTO);
    }

    /**
     * 保存服务配置 (API 用)
     */
    @Transactional
    public ServiceConfigDTO saveServiceConfig(final String serviceType, final CreateServiceConfigRequest request) {
        log.info("保存服务配置：serviceType={}", serviceType);

        // 1. 验证服务类型
        validator.validateServiceType(serviceType);

        // 2. 转换请求为领域对象
        ServiceConfiguration config = converter.fromCreateRequest(request);

        // 3. 验证配置
        validator.validateConfiguration(config);

        // 4. 保存到数据库
        ServiceConfigEntity entity = ServiceConfigEntity.builder()
                .configKey("model-router-config")
                .serviceType(serviceType)
                .adapter(request.getAdapter())
                .loadBalanceType(request.getLoadBalanceType())
                .version(1)
                .isLatest(true)
                .build();

        ServiceConfigEntity saved = jpaRepository.save(entity);
        log.info("服务配置保存成功：id={}", saved.getId());

        // 5. 同步到 StoreManager
        try {
            configRepository.save(serviceType, config);
        } catch (Exception e) {
            log.warn("同步配置到 StoreManager 失败：error={}", e.getMessage());
        }

        return converter.toDTO(saved);
    }

    /**
     * 删除服务配置 (API 用)
     */
    @Transactional
    public void deleteServiceConfig(final String serviceType) {
        log.info("删除服务配置：serviceType={}", serviceType);

        // 1. 从 StoreManager 删除
        try {
            configRepository.delete(serviceType);
        } catch (Exception e) {
            log.warn("从 StoreManager 删除失败：error={}", e.getMessage());
        }

        // 2. 从数据库删除
        jpaRepository.deleteAllByServiceType(serviceType);
    }

    /**
     * 更新服务配置（不含实例）(API 用)
     */
    @Transactional
    public void updateServiceConfig(final String serviceType, final UpdateServiceConfigRequest request) {
        log.info("更新服务配置：serviceType={}, adapter={}", serviceType, request.getAdapter());

        // 1. 获取现有配置（从 StoreManager）
        Optional<ServiceConfiguration> existingOpt = configRepository.findById(serviceType);

        ServiceConfiguration updated;
        if (existingOpt.isPresent()) {
            // 2. 部分更新
            ServiceConfiguration existing = existingOpt.get();
            updated = converter.fromUpdateRequest(existing, request);

            // 3. 验证
            validator.validateConfiguration(updated);

            // 4. 保存到 StoreManager
            configRepository.save(serviceType, updated);
        } else {
            // 创建新配置
            updated = converter.fromUpdateRequest(ServiceConfiguration.defaultConfig(), request);
            validator.validateConfiguration(updated);
            configRepository.save(serviceType, updated);
        }

        // 5. 同步到数据库
        updateDatabaseEntity(serviceType, request);
        log.info("服务配置更新成功：serviceType={}", serviceType);
    }

    // ========== 业务层方法 (领域对象方式) ==========

    /**
     * 获取服务配置 (业务用)
     */
    public ServiceConfiguration getServiceConfiguration(final String serviceType) {
        return configRepository.findById(serviceType)
                .orElseThrow(() -> new NotFoundException("服务配置不存在：" + serviceType));
    }

    /**
     * 获取所有服务配置 (业务用)
     */
    public Map<String, ServiceConfiguration> getAllServiceConfigurations() {
        return configRepository.findAll();
    }

    /**
     * 创建服务配置 (业务用)
     */
    @Transactional
    public void createService(final String serviceType, final ServiceConfiguration config) {
        log.info("创建服务配置：serviceType={}", serviceType);

        // 1. 验证服务类型
        validator.validateServiceType(serviceType);

        // 2. 验证配置
        validator.validateConfiguration(config);

        // 3. 验证实例
        if (config.instances() != null) {
            validator.validateInstances(config.instances());
        }

        // 4. 检查是否已存在
        if (configRepository.exists(serviceType)) {
            throw new IllegalArgumentException("服务类型已存在：" + serviceType);
        }

        // 5. 保存
        configRepository.save(serviceType, config);
        log.info("服务配置创建成功：serviceType={}", serviceType);
    }

    /**
     * 更新服务配置 (业务用)
     */
    @Transactional
    public ServiceConfiguration updateServiceConfig(
            final String serviceType,
            final ServiceConfiguration updates) {

        log.info("更新服务配置：serviceType={}", serviceType);

        // 1. 获取现有配置
        ServiceConfiguration existing = configRepository.findById(serviceType)
                .orElseThrow(() -> new NotFoundException("服务配置不存在：" + serviceType));

        // 2. 合并配置
        ServiceConfiguration merged = merger.merge(existing, updates);

        // 3. 验证
        validator.validateConfiguration(merged);

        // 4. 验证实例
        if (merged.instances() != null) {
            validator.validateInstances(merged.instances());
        }

        // 5. 保存
        configRepository.save(serviceType, merged);
        log.info("服务配置更新成功：serviceType={}", serviceType);

        return merged;
    }

    /**
     * 删除服务 (业务用)
     */
    @Transactional
    public void deleteService(final String serviceType) {
        log.info("删除服务：serviceType={}", serviceType);

        // 1. 检查是否存在
        if (!configRepository.exists(serviceType)) {
            throw new NotFoundException("服务配置不存在：" + serviceType);
        }

        // 2. 删除
        configRepository.delete(serviceType);
        log.info("服务删除成功：serviceType={}", serviceType);
    }

    // ==================== 辅助方法 ====================

    /**
     * 更新数据库实体
     */
    private void updateDatabaseEntity(final String serviceType, final UpdateServiceConfigRequest request) {
        ServiceConfigEntity entity = jpaRepository
                .findFirstByServiceTypeAndIsLatestTrue(serviceType)
                .orElseGet(() -> ServiceConfigEntity.builder()
                        .configKey("model-router-config")
                        .serviceType(serviceType)
                        .version(1)
                        .isLatest(true)
                        .build());

        // 更新适配器
        if (request.getAdapter() != null) {
            entity.setAdapter(request.getAdapter());
        }

        // 更新负载均衡类型
        LoadBalanceConfig lb = request.getLoadBalance();
        if (lb != null && lb.getType() != null) {
            entity.setLoadBalanceType(lb.getType());
        }

        jpaRepository.save(entity);
    }

    /**
     * 未找到异常
     */
    public static class NotFoundException extends RuntimeException {
        public NotFoundException(final String message) {
            super(message);
        }
    }
}
