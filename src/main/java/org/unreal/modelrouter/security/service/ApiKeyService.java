package org.unreal.modelrouter.security.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;
import org.unreal.modelrouter.exception.AuthenticationException;
import org.unreal.modelrouter.security.config.properties.ApiKey;
import org.unreal.modelrouter.security.config.properties.SecurityProperties;
import org.unreal.modelrouter.security.model.UsageStatistics;
import org.unreal.modelrouter.store.StoreManager;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * API Key管理服务实现类 提供API Key的CRUD操作、验证功能和缓存机制
 */
@Slf4j
@Primary
@Service
public class ApiKeyService {

    private static final String API_KEYS_STORE_KEY = "security.api-keys";
    public static final String STORE_API_KEYS = "apiKeys";

    private final StoreManager storeManager;
    private final ObjectMapper objectMapper;
    private final SecurityProperties securityProperties;

    // API Key缓存：keyValue -> ApiKey
    private final Map<String, ApiKey> apiKeyCache = new ConcurrentHashMap<>();
    // API Key ID索引：keyId -> keyValue
    private final Map<String, String> keyIdIndex = new ConcurrentHashMap<>();

    // 审计服务（用于记录API Key操作）
    @Autowired(required = false)
    private ExtendedSecurityAuditService auditService;

    @Autowired
    public ApiKeyService(StoreManager storeManager,
                         ObjectMapper objectMapper,
                         SecurityProperties securityProperties) {
        this.storeManager = storeManager;
        this.objectMapper = objectMapper;
        this.securityProperties = securityProperties;
    }


    public Mono<ApiKey> validateApiKey(String keyValue) {
        return validateApiKey(keyValue, null, null);
    }
    
    public Mono<ApiKey> validateApiKey(String keyValue, String endpoint, String ipAddress) {
        return Mono.defer(() -> {
            try {
                if (keyValue == null || keyValue.trim().isEmpty()) {
                    // 记录缺少API Key的审计
                    if (auditService != null) {
                        auditService.auditSecurityEvent("API_KEY_MISSING", 
                            "请求缺少API Key", null, ipAddress)
                            .onErrorResume(ex -> {
                                log.warn("记录API Key缺失审计失败: {}", ex.getMessage());
                                return Mono.empty();
                            })
                            .subscribe();
                    }
                    return Mono.error(AuthenticationException.missingApiKey());
                }

                ApiKey apiKey = apiKeyCache.get(keyValue);
                if (apiKey == null) {
                    // 记录无效API Key的审计
                    if (auditService != null) {
                        auditService.auditSecurityEvent("API_KEY_INVALID", 
                            "使用了无效的API Key", null, ipAddress)
                            .onErrorResume(ex -> {
                                log.warn("记录无效API Key审计失败: {}", ex.getMessage());
                                return Mono.empty();
                            })
                            .subscribe();
                    }
                    return Mono.error(AuthenticationException.invalidApiKey());
                }

                if (!apiKey.isEnabled()) {
                    updateUsageStatistics(apiKey.getKeyId(), false); // 统计失败
                    // 记录禁用API Key使用审计
                    if (auditService != null) {
                        auditService.auditApiKeyUsed(apiKey.getKeyId(), endpoint, ipAddress, false)
                            .onErrorResume(ex -> {
                                log.warn("记录禁用API Key使用审计失败: {}", ex.getMessage());
                                return Mono.empty();
                            })
                            .subscribe();
                    }
                    return Mono.error(new AuthenticationException("API Key已被禁用", AuthenticationException.INVALID_API_KEY));
                }

                if (apiKey.getExpiresAt() != null && apiKey.getExpiresAt().isBefore(LocalDateTime.now())) {
                    updateUsageStatistics(apiKey.getKeyId(), false); // 统计失败
                    // 记录过期API Key使用审计
                    if (auditService != null) {
                        auditService.auditApiKeyUsed(apiKey.getKeyId(), endpoint, ipAddress, false)
                            .onErrorResume(ex -> {
                                log.warn("记录过期API Key使用审计失败: {}", ex.getMessage());
                                return Mono.empty();
                            })
                            .subscribe();
                    }
                    return Mono.error(AuthenticationException.expiredApiKey());
                }

                // 验证成功，统计一次成功请求
                updateUsageStatistics(apiKey.getKeyId(), true);
                
                // 记录成功使用API Key的审计
                if (auditService != null) {
                    auditService.auditApiKeyUsed(apiKey.getKeyId(), endpoint, ipAddress, true)
                        .onErrorResume(ex -> {
                            log.warn("记录API Key使用审计失败: {}", ex.getMessage());
                            return Mono.empty();
                        })
                        .subscribe();
                }

                log.debug("API Key验证成功: {}", apiKey.getKeyId());
                return Mono.just(apiKey.createSecureCopy()); // 返回安全副本，不包含keyValue
            } catch (Exception e) {
                log.error("API Key验证过程中发生错误: {}", e.getMessage(), e);
                return Mono.error(e);
            }
        });
    }

    public Mono<ApiKey> createApiKey(ApiKey apiKey) {
        return createApiKey(apiKey, "system", null);
    }
    
    public Mono<ApiKey> createApiKey(ApiKey apiKey, String createdBy, String ipAddress) {
        return Mono.fromCallable(() -> {
            if (apiKey.getKeyId() == null || apiKey.getKeyId().trim().isEmpty()) {
                throw new IllegalArgumentException("API Key ID不能为空");
            }

            if (apiKey.getKeyValue() == null || apiKey.getKeyValue().trim().isEmpty()) {
                throw new IllegalArgumentException("API Key值不能为空");
            }

            if (keyIdIndex.containsKey(apiKey.getKeyId())) {
                throw new IllegalArgumentException("API Key ID已存在: " + apiKey.getKeyId());
            }

            if (apiKeyCache.containsKey(apiKey.getKeyValue())) {
                throw new IllegalArgumentException("API Key值已存在");
            }

            // 设置创建时间
            if (apiKey.getCreatedAt() == null) {
                apiKey.setCreatedAt(LocalDateTime.now());
            }

            // 初始化使用统计
            if (apiKey.getUsage() == null) {
                apiKey.setUsage(UsageStatistics.builder()
                        .totalRequests(0L)
                        .successfulRequests(0L)
                        .failedRequests(0L)
                        .dailyUsage(new HashMap<>())
                        .build());
            }

            // 更新缓存
            apiKeyCache.put(apiKey.getKeyValue(), apiKey);
            keyIdIndex.put(apiKey.getKeyId(), apiKey.getKeyValue());

            // 持久化到存储
            saveApiKeysToStore();
            
            // 记录API Key创建审计
            if (auditService != null) {
                auditService.auditApiKeyCreated(apiKey.getKeyId(), createdBy, ipAddress)
                    .onErrorResume(ex -> {
                        log.warn("记录API Key创建审计失败: {}", ex.getMessage());
                        return Mono.empty();
                    })
                    .subscribe();
            }

            log.info("创建API Key成功: {}", apiKey.getKeyId());
            return apiKey; // 返回完整的ApiKey，包含keyValue用于创建响应
        });
    }

    public Mono<ApiKey> updateApiKey(String keyId, ApiKey updateInfo) {
        return Mono.fromCallable(() -> {
            String keyValue = keyIdIndex.get(keyId);
            if (keyValue == null) {
                throw new IllegalArgumentException("API Key不存在: " + keyId);
            }

            ApiKey existingKey = apiKeyCache.get(keyValue);
            if (existingKey == null) {
                throw new IllegalArgumentException("API Key缓存不一致: " + keyId);
            }

            // 更新字段（保持keyId和keyValue不变）
            existingKey.setDescription(updateInfo.getDescription());
            existingKey.setEnabled(updateInfo.isEnabled());
            existingKey.setExpiresAt(updateInfo.getExpiresAt());
            existingKey.setPermissions(updateInfo.getPermissions());
            existingKey.setMetadata(updateInfo.getMetadata());

            // 持久化到存储
            saveApiKeysToStore();

            log.info("更新API Key成功: {}", keyId);
            return existingKey.createSecureCopy(); // 返回安全副本，不包含keyValue
        });
    }

    public Mono<Void> deleteApiKey(String keyId) {
        return deleteApiKey(keyId, "system");
    }
    
    public Mono<Void> deleteApiKey(String keyId, String revokedBy) {
        return Mono.fromRunnable(() -> {
            String keyValue = keyIdIndex.get(keyId);
            if (keyValue == null) {
                throw new IllegalArgumentException("API Key不存在: " + keyId);
            }

            // 从缓存中移除
            apiKeyCache.remove(keyValue);
            keyIdIndex.remove(keyId);

            // 持久化到存储
            saveApiKeysToStore();
            
            // 记录API Key撤销审计
            if (auditService != null) {
                auditService.auditApiKeyRevoked(keyId, "手动删除", revokedBy)
                    .onErrorResume(ex -> {
                        log.warn("记录API Key撤销审计失败: {}", ex.getMessage());
                        return Mono.empty();
                    })
                    .subscribe();
            }

            log.info("删除API Key成功: {}", keyId);
        });
    }

    public Mono<List<ApiKey>> getAllApiKeys() {
        return Mono.fromCallable(() -> apiKeyCache.values().stream()
                .map(ApiKey::createSecureCopy) // 返回安全副本，不包含keyValue
                .toList());
    }

    public Mono<ApiKey> getApiKeyById(String keyId) {
        return Mono.fromCallable(() -> {
            String keyValue = keyIdIndex.get(keyId);
            if (keyValue == null) {
                throw new IllegalArgumentException("API Key不存在: " + keyId);
            }

            ApiKey apiKey = apiKeyCache.get(keyValue);
            if (apiKey == null) {
                throw new IllegalArgumentException("API Key缓存不一致: " + keyId);
            }

            return apiKey.createSecureCopy(); // 返回安全副本，不包含keyValue
        });
    }

    public void updateUsageStatistics(String keyId, boolean success) {
        String keyValue = keyIdIndex.get(keyId);
        if (keyValue == null) {
            log.warn("API Key不存在: {}", keyId);
            return;
        }
        ApiKey apiKey = apiKeyCache.get(keyValue);
        if (apiKey == null || apiKey.getUsage() == null) {
            log.warn("API Key或usage不存在: {}", keyId);
            return;
        }

        UsageStatistics stats = apiKey.getUsage();

        // 更新统计数据
        stats.setTotalRequests(stats.getTotalRequests() + 1);
        if (success) {
            stats.setSuccessfulRequests(stats.getSuccessfulRequests() + 1);
        } else {
            stats.setFailedRequests(stats.getFailedRequests() + 1);
        }
        stats.setLastUsedAt(LocalDateTime.now());

        String today = LocalDateTime.now().toLocalDate().toString();
        Map<String, Long> dailyUsage = stats.getDailyUsage();
        if (dailyUsage == null) {
            dailyUsage = new HashMap<>();
            stats.setDailyUsage(dailyUsage);
        }
        dailyUsage.put(today, dailyUsage.getOrDefault(today, 0L) + 1);

        // 持久化到存储
        saveApiKeysToStore(); // 只保存 API Key 相关数据
        log.debug("更新API Key使用统计: {} (成功: {})", keyId, success);
    }

    public Mono<UsageStatistics> getUsageStatistics(String keyId) {
        return Mono.fromCallable(() -> {
            String keyValue = keyIdIndex.get(keyId);
            if (keyValue == null) {
                throw new IllegalArgumentException("API Key不存在: " + keyId);
            }
            ApiKey apiKey = apiKeyCache.get(keyValue);
            if (apiKey == null || apiKey.getUsage() == null) {
                throw new IllegalArgumentException("API Key或usage不存在: " + keyId);
            }
            return apiKey.getUsage();
        });
    }

    /**
     * 保存API Key数据到存储
     */
    private void saveApiKeysToStore() {
        try {
            List<ApiKey> apiKeys = new ArrayList<>(apiKeyCache.values());
            Map<String, Object> config = new HashMap<>();
            config.put("apiKeys", apiKeys);
            storeManager.saveConfig(API_KEYS_STORE_KEY, config);
            log.debug("保存了 {} 个API Key到存储", apiKeys.size());
        } catch (Exception e) {
            log.error("保存API Key数据失败", e);
        }
    }


    /**
     * 检查是否存在持久化ApiKey配置
     * @return true如果存在持久化配置
     */
    public boolean hasPersistedAccountConfig() {
        try {
            List<Integer> versions = storeManager.getConfigVersions(API_KEYS_STORE_KEY);
            if (!versions.isEmpty()) {
                return true;
            }
            return storeManager.exists(API_KEYS_STORE_KEY);
        } catch (Exception e) {
            log.warn("检查持久化ApiKey存在性时发生错误: {}", e.getMessage());
            return false;
        }
    }

    /**
     * 从YAML配置初始化ApiKey配置持久化存储
     */
    public void initializeApiKeyFromYaml() {
        log.info("首次启动，将YAML ApkKey配置保存为版本1");

        try {
            // 获取YAML默认ApkKey配置
            Map<String, Object> defaultAccountConfig = getVersionConfig(0);

            // 保存为第一个版本
            saveNewVersion(defaultAccountConfig);

            List<ApiKey> keys = (List<ApiKey>) defaultAccountConfig.get(STORE_API_KEYS);

            keys.forEach(item -> {
                        // 只有当密钥不在配置中时才添加（避免重复）
                        if (!apiKeyCache.containsKey(item.getKeyValue())) {
                            apiKeyCache.put(item.getKeyValue(), item);
                            keyIdIndex.put(item.getKeyId(), item.getKeyValue());
                        }
                    }
            );
            log.info("YAML ApkKey配置已保存为版本1");

        } catch (Exception e) {
            log.error("从YAML配置初始化ApkKey配置失败", e);
            throw new RuntimeException("Failed to initialize JWT accounts from YAML config", e);
        }
    }

    /**
     * 保存当前Apikey配置为新版本
     * @param config 配置内容
     * @return 新版本号
     */
    public int saveNewVersion(Map<String, Object> config) {
        int version = getNextAccountVersion();
        storeManager.saveConfigVersion(API_KEYS_STORE_KEY, config, version);
        log.info("已保存JWT账户配置为新版本：{}", version);
        return version;
    }

    /**
     * 加载最新的持久化ApiKey配置
     */
    public void loadLatestApiKeyConfig() {
        log.info("发现持久化ApkKey配置，加载最新版本");

        try {
            int currentVersion = getCurrentVersion();
            Map<String, Object> versionConfig = getVersionConfig(currentVersion);

            List<Map<String, Object>> keys = (List<Map<String, Object>>) versionConfig.get(STORE_API_KEYS);
            keys.stream().map(item -> objectMapper.convertValue(item, ApiKey.class))
                    .forEach(item -> {
                                // 只有当密钥不在配置中时才添加（避免重复）
                                if (!apiKeyCache.containsKey(item.getKeyValue())) {
                                    apiKeyCache.put(item.getKeyValue(), item);
                                    keyIdIndex.put(item.getKeyId(), item.getKeyValue());
                                }
                            }
                    );
            log.info("已加载ApkKey配置版本 {}", currentVersion);

        } catch (Exception e) {
            log.error("加载持久化ApkKey配置失败", e);
            throw new RuntimeException("Failed to load persisted JWT account config", e);
        }
    }

    /**
     * 获取所有ApkKey配置版本号
     * @return 版本号列表
     */
    public List<Integer> getAllVersions() {
        return storeManager.getConfigVersions(API_KEYS_STORE_KEY);
    }

    /**
     * 获取当前最新ApkKey配置版本号
     * @return 当前版本号
     */
    public int getCurrentVersion() {
        List<Integer> versions = getAllVersions();
        return versions.isEmpty() ? 0 : versions.stream().mapToInt(Integer::intValue).max().orElse(0);
    }

    /**
     * 获取下一个版本号
     * @return 下一个版本号
     */
    private int getNextAccountVersion() {
        return getAllVersions().stream().max(Integer::compareTo).orElse(0) + 1;
    }

    public Map<String, Object> getVersionConfig(int version) {
        if (version == 0) {
            Map<String, Object> config = new HashMap<>();
            config.put(STORE_API_KEYS, loadApiKeysFromConfig());
            return config; // YAML 原始配置
        }
        return storeManager.getConfigByVersion(API_KEYS_STORE_KEY, version);
    }

    /**
     * 从配置文件加载API Key
     */
    private List<ApiKey> loadApiKeysFromConfig() {
        return securityProperties.getApiKey().getKeys().stream().peek(item -> {
            if (item.getCreatedAt() == null) {
                item.setCreatedAt(LocalDateTime.now());
            }

            if (item.getUsage() == null) {
                item.setUsage(UsageStatistics.builder()
                        .dailyUsage(new HashMap<>())
                        .failedRequests(0)
                        .lastUsedAt(LocalDateTime.now())
                        .successfulRequests(0)
                        .totalRequests(0)
                        .build());
            }
        }).toList();
    }

}
