package org.unreal.modelrouter.security.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;
import org.unreal.modelrouter.exception.AuthenticationException;
import org.unreal.modelrouter.security.audit.ExtendedSecurityAuditService;
import org.unreal.modelrouter.security.config.properties.ApiKey;
import org.unreal.modelrouter.security.config.properties.SecurityProperties;
import org.unreal.modelrouter.security.dto.*;
import org.unreal.modelrouter.security.model.UsageStatistics;
import org.unreal.modelrouter.security.util.ApiKeyHashUtil;
import org.unreal.modelrouter.store.StoreManager;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * API Key 管理服务实现类
 * 提供 API Key 的 CRUD 操作、验证功能和缓存机制
 * 
 * 安全改进：
 * 1. keyValue 使用 SHA-256 + 盐值哈希存储
 * 2. 仅在创建时返回原始 keyValue
 * 3. 支持 IP 白名单限制
 * 4. 支持每日请求限制
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
    
    // API Key 配置管理器（用于版本管理）
    @Autowired(required = false)
    private ApiKeyConfigManager apiKeyConfigManager;

    // API Key 缓存：keyHash -> ApiKey（使用哈希值作为 key）
    private final Map<String, ApiKey> apiKeyCache = new ConcurrentHashMap<>();
    // API Key ID 索引：keyId -> keyHash
    private final Map<String, String> keyIdIndex = new ConcurrentHashMap<>();

    // 审计服务（用于记录 API Key 操作）
    @Autowired(required = false)
    private ExtendedSecurityAuditService extendedAuditService;

    @Autowired
    public ApiKeyService(StoreManager storeManager,
                         ObjectMapper objectMapper,
                         SecurityProperties securityProperties) {
        this.storeManager = storeManager;
        this.objectMapper = objectMapper;
        this.securityProperties = securityProperties;
    }

    /**
     * 验证 API Key（使用哈希验证）
     *
     * @param keyValue 用户提供的原始 API Key 值
     * @return 验证成功返回 ApiKey 信息
     */
    public Mono<ApiKey> validateApiKey(String keyValue) {
        return validateApiKey(keyValue, null, null);
    }

    /**
     * 验证 API Key（使用哈希验证，包含审计记录）
     *
     * @param keyValue  用户提供的原始 API Key 值
     * @param endpoint  请求的端点
     * @param ipAddress 客户端 IP 地址
     * @return 验证成功返回 ApiKey 信息
     */
    public Mono<ApiKey> validateApiKey(String keyValue, String endpoint, String ipAddress) {
        return Mono.defer(() -> {
            try {
                if (keyValue == null || keyValue.trim().isEmpty()) {
                    auditSecurityEvent("API_KEY_MISSING", "请求缺少API Key", null, ipAddress);
                    return Mono.error(AuthenticationException.missingApiKey());
                }

                // 遍历所有 API Key，使用哈希验证
                ApiKey matchedApiKey = null;
                for (ApiKey apiKey : apiKeyCache.values()) {
                    if (apiKey.verifyKey(keyValue)) {
                        matchedApiKey = apiKey;
                        break;
                    }
                }

                if (matchedApiKey == null) {
                    auditSecurityEvent("API_KEY_INVALID", "使用了无效的API Key", null, ipAddress);
                    return Mono.error(AuthenticationException.invalidApiKey());
                }

                // 检查是否启用
                if (!matchedApiKey.isEnabled()) {
                    updateUsageStatistics(matchedApiKey.getKeyId(), false);
                    auditApiKeyUsed(matchedApiKey.getKeyId(), endpoint, ipAddress, false);
                    return Mono.error(new AuthenticationException("API Key已被禁用", 
                            AuthenticationException.INVALID_API_KEY));
                }

                // 检查是否过期
                if (matchedApiKey.isExpired()) {
                    updateUsageStatistics(matchedApiKey.getKeyId(), false);
                    auditApiKeyUsed(matchedApiKey.getKeyId(), endpoint, ipAddress, false);
                    return Mono.error(AuthenticationException.expiredApiKey());
                }

                // 检查 IP 白名单
                if (!matchedApiKey.isIpAllowed(ipAddress)) {
                    updateUsageStatistics(matchedApiKey.getKeyId(), false);
                    auditSecurityEvent("API_KEY_IP_BLOCKED", 
                            "IP地址不在白名单中: " + ipAddress, matchedApiKey.getKeyId(), ipAddress);
                    return Mono.error(new AuthenticationException("IP地址不允许访问", 
                            AuthenticationException.INVALID_API_KEY));
                }

                // 检查每日请求限制
                if (matchedApiKey.isDailyLimitExceeded()) {
                    updateUsageStatistics(matchedApiKey.getKeyId(), false);
                    auditSecurityEvent("API_KEY_DAILY_LIMIT", 
                            "超过每日请求限制", matchedApiKey.getKeyId(), ipAddress);
                    return Mono.error(new AuthenticationException("超过每日请求限制", 
                            AuthenticationException.INVALID_API_KEY));
                }

                // 验证成功，更新统计
                updateUsageStatistics(matchedApiKey.getKeyId(), true);
                auditApiKeyUsed(matchedApiKey.getKeyId(), endpoint, ipAddress, true);

                log.debug("API Key验证成功: {}", matchedApiKey.getKeyId());
                return Mono.just(matchedApiKey.createSecureCopy());
            } catch (Exception e) {
                log.error("API Key验证过程中发生错误: {}", e.getMessage(), e);
                return Mono.error(e);
            }
        });
    }

    /**
     * 创建新的 API Key
     *
     * @param request 创建请求 DTO
     * @return 创建响应 VO（包含原始 keyValue，仅此一次显示）
     */
    public Mono<ApiKeyCreationVO> createApiKey(ApiKeyCreateRequest request) {
        return createApiKey(request, "system", null);
    }

    /**
     * 创建新的 API Key（带审计信息）
     *
     * @param request   创建请求 DTO
     * @param createdBy 创建者
     * @param ipAddress 创建者 IP
     * @return 创建响应 VO
     */
    public Mono<ApiKeyCreationVO> createApiKey(ApiKeyCreateRequest request, String createdBy, String ipAddress) {
        return Mono.fromCallable(() -> {
            // 生成 keyId
            String keyId = request.getKeyId() != null && !request.getKeyId().trim().isEmpty()
                    ? request.getKeyId()
                    : "key-" + UUID.randomUUID().toString().substring(0, 8);

            // 检查 keyId 是否已存在
            if (keyIdIndex.containsKey(keyId)) {
                throw new IllegalArgumentException("API Key ID已存在: " + keyId);
            }

            // 生成原始 keyValue
            String originalKeyValue = ApiKey.generateApiKey("sk-", 32);
            
            // 计算 keyValue 的哈希值
            String keyHash = ApiKeyHashUtil.hashApiKey(originalKeyValue);

            // 构建 ApiKey 实体（存储哈希值）
            ApiKey apiKey = ApiKey.builder()
                    .keyId(keyId)
                    .keyHash(keyHash)  // 存储哈希值
                    .keyValue(null)    // 不存储原始值
                    .keyPrefix("sk-")
                    .description(request.getDescription())
                    .permissions(request.getPermissions())
                    .enabled(request.getEnabled() != null ? request.getEnabled() : true)
                    .expiresAt(request.getExpiresAt())
                    .createdAt(LocalDateTime.now())
                    .createdBy(createdBy)
                    .creatorIpAddress(ipAddress)
                    .rotationPeriodDays(request.getRotationPeriodDays() != null ? request.getRotationPeriodDays() : 0)
                    .allowedIpAddresses(request.getAllowedIpAddresses())
                    .dailyRequestLimit(request.getDailyRequestLimit() != null ? request.getDailyRequestLimit() : 0L)
                    .usage(UsageStatistics.builder()
                            .totalRequests(0L)
                            .successfulRequests(0L)
                            .failedRequests(0L)
                            .dailyUsage(new HashMap<>())
                            .build())
                    .build();

            // 更新缓存（使用哈希值作为 key）
            apiKeyCache.put(keyHash, apiKey);
            keyIdIndex.put(keyId, keyHash);

            // 持久化到存储
            saveApiKeysToStore();

            // 记录审计
            auditApiKeyCreated(keyId, createdBy, ipAddress);

            log.info("创建API Key成功: {}, 哈希存储已完成", keyId);
            
            // 构建创建响应 VO（返回原始 keyValue）
            return ApiKeyCreationVO.builder()
                    .keyId(keyId)
                    .keyValue(originalKeyValue)  // 仅此一次返回原始值
                    .description(apiKey.getDescription())
                    .permissions(apiKey.getPermissions())
                    .enabled(apiKey.isEnabled())
                    .createdAt(apiKey.getCreatedAt())
                    .expiresAt(apiKey.getExpiresAt())
                    .warning("密钥值只会显示一次，请妥善保存！")
                    .build();
        });
    }

    /**
     * 更新 API Key
     *
     * @param keyId   API Key ID
     * @param request 更新请求 DTO
     * @return 更新后的 API Key VO
     */
    public Mono<ApiKeyVO> updateApiKey(String keyId, ApiKeyUpdateRequest request) {
        return Mono.fromCallable(() -> {
            String keyHash = keyIdIndex.get(keyId);
            if (keyHash == null) {
                throw new IllegalArgumentException("API Key不存在: " + keyId);
            }

            ApiKey existingKey = apiKeyCache.get(keyHash);
            if (existingKey == null) {
                throw new IllegalArgumentException("API Key缓存不一致: " + keyId);
            }

            // 更新可修改的字段
            if (request.getDescription() != null) {
                existingKey.setDescription(request.getDescription());
            }
            if (request.getEnabled() != null) {
                existingKey.setEnabled(request.getEnabled());
            }
            if (request.getExpiresAt() != null) {
                existingKey.setExpiresAt(request.getExpiresAt());
            }
            if (request.getPermissions() != null) {
                existingKey.setPermissions(request.getPermissions());
            }
            if (request.getAllowedIpAddresses() != null) {
                existingKey.setAllowedIpAddresses(request.getAllowedIpAddresses());
            }
            if (request.getDailyRequestLimit() != null) {
                existingKey.setDailyRequestLimit(request.getDailyRequestLimit());
            }
            if (request.getRotationPeriodDays() != null) {
                existingKey.setRotationPeriodDays(request.getRotationPeriodDays());
            }

            // 持久化到存储
            saveApiKeysToStore();

            log.info("更新API Key成功: {}", keyId);
            return convertToVO(existingKey);
        });
    }

    /**
     * 删除 API Key
     *
     * @param keyId API Key ID
     * @return Mono<Void>
     */
    public Mono<Void> deleteApiKey(String keyId) {
        return deleteApiKey(keyId, "system");
    }

    /**
     * 删除 API Key（带审计信息）
     *
     * @param keyId     API Key ID
     * @param revokedBy 删除者
     * @return Mono<Void>
     */
    public Mono<Void> deleteApiKey(String keyId, String revokedBy) {
        return Mono.fromRunnable(() -> {
            String keyHash = keyIdIndex.get(keyId);
            if (keyHash == null) {
                throw new IllegalArgumentException("API Key不存在: " + keyId);
            }

            // 从缓存中移除
            apiKeyCache.remove(keyHash);
            keyIdIndex.remove(keyId);

            // 持久化到存储
            saveApiKeysToStore();

            // 记录审计
            auditApiKeyRevoked(keyId, "手动删除", revokedBy);

            log.info("删除API Key成功: {}", keyId);
        });
    }

    /**
     * 获取所有 API Key 列表
     *
     * @return API Key 列表 VO
     */
    public Mono<ApiKeyListVO> getAllApiKeysVO() {
        return Mono.fromCallable(() -> {
            List<ApiKeyVO> items = apiKeyCache.values().stream()
                    .map(this::convertToVO)
                    .sorted(Comparator.comparing(ApiKeyVO::getCreatedAt).reversed())
                    .toList();

            // 统计状态
            int enabledCount = 0;
            int disabledCount = 0;
            int expiredCount = 0;
            long todayTotalRequests = 0L;
            long todaySuccessfulRequests = 0L;
            long todayFailedRequests = 0L;
            String today = LocalDateTime.now().toLocalDate().toString();

            for (ApiKey apiKey : apiKeyCache.values()) {
                if (apiKey.isEnabled()) {
                    enabledCount++;
                } else {
                    disabledCount++;
                }
                if (apiKey.isExpired()) {
                    expiredCount++;
                }
                UsageStatistics usage = apiKey.getUsage();
                if (usage != null) {
                    Map<String, Long> dailyUsage = usage.getDailyUsage();
                    if (dailyUsage != null) {
                        Long todayCount = dailyUsage.get(today);
                        if (todayCount != null) {
                            todayTotalRequests += todayCount;
                        }
                    }
                    todaySuccessfulRequests += usage.getSuccessfulRequests();
                    todayFailedRequests += usage.getFailedRequests();
                }
            }

            return ApiKeyListVO.builder()
                    .items(items)
                    .total(items.size())
                    .enabledCount(enabledCount)
                    .disabledCount(disabledCount)
                    .expiredCount(expiredCount)
                    .summary(ApiKeyListVO.Summary.builder()
                            .todayTotalRequests(todayTotalRequests)
                            .todaySuccessfulRequests(todaySuccessfulRequests)
                            .todayFailedRequests(todayFailedRequests)
                            .build())
                    .build();
        });
    }

    /**
     * 获取单个 API Key 详情
     *
     * @param keyId API Key ID
     * @return API Key VO
     */
    public Mono<ApiKeyVO> getApiKeyByIdVO(String keyId) {
        return Mono.fromCallable(() -> {
            String keyHash = keyIdIndex.get(keyId);
            if (keyHash == null) {
                throw new IllegalArgumentException("API Key不存在: " + keyId);
            }

            ApiKey apiKey = apiKeyCache.get(keyHash);
            if (apiKey == null) {
                throw new IllegalArgumentException("API Key缓存不一致: " + keyId);
            }

            return convertToVO(apiKey);
        });
    }

    /**
     * 启用 API Key
     *
     * @param keyId API Key ID
     * @return API Key VO
     */
    public Mono<ApiKeyVO> enableApiKey(String keyId) {
        return Mono.fromCallable(() -> {
            String keyHash = keyIdIndex.get(keyId);
            if (keyHash == null) {
                throw new IllegalArgumentException("API Key不存在: " + keyId);
            }

            ApiKey apiKey = apiKeyCache.get(keyHash);
            if (apiKey == null) {
                throw new IllegalArgumentException("API Key缓存不一致: " + keyId);
            }

            apiKey.setEnabled(true);
            saveApiKeysToStore();

            log.info("启用API Key成功: {}", keyId);
            return convertToVO(apiKey);
        });
    }

    /**
     * 禁用 API Key
     *
     * @param keyId API Key ID
     * @return API Key VO
     */
    public Mono<ApiKeyVO> disableApiKey(String keyId) {
        return Mono.fromCallable(() -> {
            String keyHash = keyIdIndex.get(keyId);
            if (keyHash == null) {
                throw new IllegalArgumentException("API Key不存在: " + keyId);
            }

            ApiKey apiKey = apiKeyCache.get(keyHash);
            if (apiKey == null) {
                throw new IllegalArgumentException("API Key缓存不一致: " + keyId);
            }

            apiKey.setEnabled(false);
            saveApiKeysToStore();

            log.info("禁用API Key成功: {}", keyId);
            return convertToVO(apiKey);
        });
    }

    /**
     * 将 ApiKey 实体转换为 VO
     */
    private ApiKeyVO convertToVO(ApiKey apiKey) {
        ApiKeyVO vo = ApiKeyVO.builder()
                .keyId(apiKey.getKeyId())
                .description(apiKey.getDescription())
                .permissions(apiKey.getPermissions())
                .enabled(apiKey.isEnabled())
                .expired(apiKey.isExpired())
                .createdAt(apiKey.getCreatedAt())
                .createdBy(apiKey.getCreatedBy())
                .creatorIpAddress(apiKey.getCreatorIpAddress())
                .rotationPeriodDays(apiKey.getRotationPeriodDays())
                .lastRotatedAt(apiKey.getLastRotatedAt())
                .needsRotation(apiKey.needsRotation())
                .expiresAt(apiKey.getExpiresAt())
                .build();

        // 添加使用统计信息
        if (apiKey.getUsage() != null) {
            vo.setTotalRequests(apiKey.getUsage().getTotalRequests());
            vo.setSuccessfulRequests(apiKey.getUsage().getSuccessfulRequests());
            vo.setFailedRequests(apiKey.getUsage().getFailedRequests());
            vo.setLastUsedAt(apiKey.getUsage().getLastUsedAt());
        }

        // 计算剩余天数
        vo.calculateRemainingDays();

        return vo;
    }

    /**
     * 更新使用统计
     */
    public void updateUsageStatistics(String keyId, boolean success) {
        String keyHash = keyIdIndex.get(keyId);
        if (keyHash == null) {
            log.warn("API Key不存在: {}", keyId);
            return;
        }
        ApiKey apiKey = apiKeyCache.get(keyHash);
        if (apiKey == null || apiKey.getUsage() == null) {
            log.warn("API Key或usage不存在: {}", keyId);
            return;
        }

        UsageStatistics stats = apiKey.getUsage();
        stats.incrementRequest(success);

        String today = LocalDateTime.now().toLocalDate().toString();
        Map<String, Long> dailyUsage = stats.getDailyUsage();
        if (dailyUsage == null) {
            dailyUsage = new HashMap<>();
            stats.setDailyUsage(dailyUsage);
        }
        dailyUsage.put(today, dailyUsage.getOrDefault(today, 0L) + 1);

        saveApiKeysToStore();
        log.debug("更新API Key使用统计: {} (成功: {})", keyId, success);
    }

    /**
     * 保存 API Key 数据到存储
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

    // ============ 审计辅助方法 ============

    private void auditSecurityEvent(String eventType, String message, String keyId, String ipAddress) {
        if (extendedAuditService != null) {
            extendedAuditService.auditSecurityEvent(eventType, message, keyId, ipAddress)
                    .onErrorResume(ex -> {
                        log.warn("记录安全审计失败: {}", ex.getMessage());
                        return Mono.empty();
                    })
                    .subscribe();
        }
    }

    private void auditApiKeyCreated(String keyId, String createdBy, String ipAddress) {
        if (extendedAuditService != null) {
            extendedAuditService.auditApiKeyCreated(keyId, createdBy, ipAddress)
                    .onErrorResume(ex -> {
                        log.warn("记录API Key创建审计失败: {}", ex.getMessage());
                        return Mono.empty();
                    })
                    .subscribe();
        }
    }

    private void auditApiKeyUsed(String keyId, String endpoint, String ipAddress, boolean success) {
        if (extendedAuditService != null) {
            extendedAuditService.auditApiKeyUsed(keyId, endpoint, ipAddress, success)
                    .onErrorResume(ex -> {
                        log.warn("记录API Key使用审计失败: {}", ex.getMessage());
                        return Mono.empty();
                    })
                    .subscribe();
        }
    }

    private void auditApiKeyRevoked(String keyId, String reason, String revokedBy) {
        if (extendedAuditService != null) {
            extendedAuditService.auditApiKeyRevoked(keyId, reason, revokedBy)
                    .onErrorResume(ex -> {
                        log.warn("记录API Key撤销审计失败: {}", ex.getMessage());
                        return Mono.empty();
                    })
                    .subscribe();
        }
    }

    // ============ 配置加载和持久化方法 ============

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

    public void initializeApiKeyFromYaml() {
        log.info("首次启动，将YAML API Key配置保存为版本1");

        try {
            Map<String, Object> defaultConfig = getVersionConfig(0);
            saveNewVersion(defaultConfig);

            List<ApiKey> keys = (List<ApiKey>) defaultConfig.get(STORE_API_KEYS);
            keys.forEach(item -> {
                // 对旧的明文 keyValue 进行哈希迁移
                if (item.getKeyValue() != null && !ApiKeyHashUtil.isHashedFormat(item.getKeyValue())) {
                    String keyHash = ApiKeyHashUtil.hashApiKey(item.getKeyValue());
                    item.setKeyHash(keyHash);
                    item.setKeyValue(null);  // 清除明文
                    item.setKeyPrefix("sk-");
                    log.info("迁移API Key {} 从明文存储到哈希存储", item.getKeyId());
                }
                
                if (item.getKeyHash() != null && !apiKeyCache.containsKey(item.getKeyHash())) {
                    apiKeyCache.put(item.getKeyHash(), item);
                    keyIdIndex.put(item.getKeyId(), item.getKeyHash());
                }
            });

            // 保存迁移后的配置
            saveApiKeysToStore();
            log.info("YAML API Key配置已保存为版本1，并完成哈希迁移");
        } catch (Exception e) {
            log.error("从YAML配置初始化API Key配置失败", e);
            throw new RuntimeException("Failed to initialize API keys from YAML config", e);
        }
    }

    public void loadLatestApiKeyConfig() {
        log.info("发现持久化API Key配置，加载最新版本");

        try {
            int currentVersion = getCurrentVersion();
            Map<String, Object> versionConfig = getVersionConfig(currentVersion);

            List<Map<String, Object>> keys = (List<Map<String, Object>>) versionConfig.get(STORE_API_KEYS);
            log.debug("从版本 {} 加载了 {} 个API Key配置", currentVersion, keys != null ? keys.size() : 0);
            
            // 如果持久化数据为空或无效，从 YAML 重新初始化
            if (keys == null || keys.isEmpty()) {
                log.warn("持久化API Key配置为空，从YAML重新初始化");
                initializeApiKeyFromYaml();
                return;
            }
            
            keys.stream()
                    .map(item -> objectMapper.convertValue(item, ApiKey.class))
                    .forEach(item -> {
                        // 对旧的明文 keyValue 进行哈希迁移
                        if (item.getKeyValue() != null && !ApiKeyHashUtil.isHashedFormat(item.getKeyValue())) {
                            String keyHash = ApiKeyHashUtil.hashApiKey(item.getKeyValue());
                            item.setKeyHash(keyHash);
                            item.setKeyValue(null);
                            item.setKeyPrefix("sk-");
                            log.info("迁移API Key {} 从明文存储到哈希存储", item.getKeyId());
                        }
                        
                        if (item.getKeyHash() != null && !apiKeyCache.containsKey(item.getKeyHash())) {
                            apiKeyCache.put(item.getKeyHash(), item);
                            keyIdIndex.put(item.getKeyId(), item.getKeyHash());
                        } else if (item.getKeyHash() == null && item.getKeyValue() == null) {
                            log.warn("API Key {} 没有有效的keyHash或keyValue，跳过", item.getKeyId());
                        }
                    });

            // 如果缓存仍然为空，说明数据无效，从YAML重新初始化
            if (apiKeyCache.isEmpty()) {
                log.warn("加载后的API Key缓存为空，从YAML重新初始化");
                initializeApiKeyFromYaml();
                return;
            }

            // 保存迁移后的配置
            saveApiKeysToStore();
            log.info("已加载API Key配置版本 {}，共 {} 个密钥", currentVersion, apiKeyCache.size());
        } catch (Exception e) {
            log.error("加载持久化API Key配置失败", e);
            throw new RuntimeException("Failed to load persisted API key config", e);
        }
    }

    /**
     * 保存API Key配置为新版本
     *
     * @deprecated 建议使用 {@link ApiKeyConfigManager#saveNewVersion(Map)}。
     *             <p>迁移说明：</p>
     *             <ul>
     *               <li>ApiKeyConfigManager 专门负责 API Key 配置的版本管理</li>
     *               <li>新方法提供更好的职责分离和代码组织</li>
     *             </ul>
     *             <p>迁移示例：</p>
     *             <pre>{@code
     *             // 旧代码
     *             int version = apiKeyService.saveNewVersion(config);
     *             
     *             // 新代码
     *             int version = apiKeyConfigManager.saveNewVersion(config);
     *             }</pre>
     *             此方法将在 v3.0 版本中移除。
     * @see ApiKeyConfigManager#saveNewVersion(Map)
     * @since v2.5.5 标注废弃
     */
    @Deprecated(since = "2.5.5", forRemoval = true)
    public int saveNewVersion(Map<String, Object> config) {
        if (apiKeyConfigManager != null) {
            return apiKeyConfigManager.saveNewVersion(config);
        }
        // 兼容旧实现
        int version = getNextAccountVersion();
        storeManager.saveConfigVersion(API_KEYS_STORE_KEY, config, version);
        log.info("已保存API Key配置为新版本：{}", version);
        return version;
    }

    /**
     * 获取所有版本列表
     *
     * @deprecated 建议使用 {@link ApiKeyConfigManager#getAllVersions()}。
     *             <p>迁移说明：</p>
     *             <ul>
     *               <li>ApiKeyConfigManager 专门负责 API Key 配置的版本管理</li>
     *             </ul>
     *             此方法将在 v3.0 版本中移除。
     * @see ApiKeyConfigManager#getAllVersions()
     * @since v2.5.5 标注废弃
     */
    @Deprecated(since = "2.5.5", forRemoval = true)
    public List<Integer> getAllVersions() {
        if (apiKeyConfigManager != null) {
            return apiKeyConfigManager.getAllVersions();
        }
        return storeManager.getConfigVersions(API_KEYS_STORE_KEY);
    }

    /**
     * 获取当前版本号
     *
     * @deprecated 建议使用 {@link ApiKeyConfigManager#getCurrentVersion()}。
     *             <p>迁移说明：</p>
     *             <ul>
     *               <li>ApiKeyConfigManager 专门负责 API Key 配置的版本管理</li>
     *             </ul>
     *             此方法将在 v3.0 版本中移除。
     * @see ApiKeyConfigManager#getCurrentVersion()
     * @since v2.5.5 标注废弃
     */
    @Deprecated(since = "2.5.5", forRemoval = true)
    public int getCurrentVersion() {
        if (apiKeyConfigManager != null) {
            return apiKeyConfigManager.getCurrentVersion();
        }
        List<Integer> versions = getAllVersions();
        return versions.isEmpty() ? 0 : versions.stream().mapToInt(Integer::intValue).max().orElse(0);
    }

    private int getNextAccountVersion() {
        return getAllVersions().stream().max(Integer::compareTo).orElse(0) + 1;
    }

    /**
     * 获取指定版本的配置
     *
     * @deprecated 建议使用 {@link ApiKeyConfigManager#getVersionConfig(int)}。
     *             <p>迁移说明：</p>
     *             <ul>
     *               <li>ApiKeyConfigManager 专门负责 API Key 配置的版本管理</li>
     *             </ul>
     *             此方法将在 v3.0 版本中移除。
     * @see ApiKeyConfigManager#getVersionConfig(int)
     * @since v2.5.5 标注废弃
     */
    @Deprecated(since = "2.5.5", forRemoval = true)
    public Map<String, Object> getVersionConfig(int version) {
        if (apiKeyConfigManager != null) {
            return apiKeyConfigManager.getVersionConfig(version);
        }
        if (version == 0) {
            Map<String, Object> config = new HashMap<>();
            config.put(STORE_API_KEYS, loadApiKeysFromConfig());
            return config;
        }
        return storeManager.getConfigByVersion(API_KEYS_STORE_KEY, version);
    }

    private List<ApiKey> loadApiKeysFromConfig() {
        return securityProperties.getApiKey().getKeys().stream().peek(item -> {
            if (item.getCreatedAt() == null) {
                item.setCreatedAt(LocalDateTime.now());
            }
            if (item.getUsage() == null) {
                item.setUsage(UsageStatistics.builder()
                        .dailyUsage(new HashMap<>())
                        .failedRequests(0L)
                        .lastUsedAt(LocalDateTime.now())
                        .successfulRequests(0L)
                        .totalRequests(0L)
                        .build());
            }
            if (item.getKeyPrefix() == null) {
                item.setKeyPrefix("sk-");
            }
            if (item.getDailyRequestLimit() == 0) {
                item.setDailyRequestLimit(0L);
            }
        }).toList();
    }

    // ============ 密钥轮换方法 ============

    /**
     * 轮换所有需要轮换的密钥
     *
     * @return 轮换的密钥数量
     */
    public Mono<Integer> rotateExpiredKeys() {
        return Mono.fromCallable(() -> {
            int rotatedCount = 0;
            LocalDateTime now = LocalDateTime.now();

            for (ApiKey apiKey : apiKeyCache.values()) {
                if (apiKey.needsRotation() && apiKey.isEnabled()) {
                    try {
                        rotateKeyInternal(apiKey, now);
                        rotatedCount++;
                        log.info("自动轮换密钥: {}", apiKey.getKeyId());
                    } catch (Exception e) {
                        log.error("轮换密钥失败: {}", apiKey.getKeyId(), e);
                    }
                }
            }

            if (rotatedCount > 0) {
                saveApiKeysToStore();
            }

            return rotatedCount;
        });
    }

    /**
     * 强制轮换指定密钥
     *
     * @param keyId    API Key ID
     * @param rotatedBy 轮换操作者
     * @return 创建响应 VO（包含新的 keyValue）
     */
    public Mono<ApiKeyCreationVO> forceRotateKey(String keyId, String rotatedBy) {
        return Mono.fromCallable(() -> {
            String keyHash = keyIdIndex.get(keyId);
            if (keyHash == null) {
                throw new IllegalArgumentException("API Key不存在: " + keyId);
            }

            ApiKey apiKey = apiKeyCache.get(keyHash);
            if (apiKey == null) {
                throw new IllegalArgumentException("API Key缓存不一致: " + keyId);
            }

            // 生成新的 keyValue
            String originalKeyValue = ApiKey.generateApiKey("sk-", 32);
            String newKeyHash = ApiKeyHashUtil.hashApiKey(originalKeyValue);

            // 更新缓存：移除旧的 keyHash，添加新的
            apiKeyCache.remove(keyHash);
            apiKey.setKeyHash(newKeyHash);
            apiKey.setLastRotatedAt(LocalDateTime.now());
            apiKeyCache.put(newKeyHash, apiKey);
            keyIdIndex.put(keyId, newKeyHash);

            // 持久化
            saveApiKeysToStore();

            // 记录审计
            auditApiKeyRotated(keyId, rotatedBy);

            log.info("强制轮换密钥成功: {}, 操作者: {}", keyId, rotatedBy);

            return ApiKeyCreationVO.builder()
                    .keyId(keyId)
                    .keyValue(originalKeyValue)  // 仅此一次返回原始值
                    .description(apiKey.getDescription())
                    .permissions(apiKey.getPermissions())
                    .enabled(apiKey.isEnabled())
                    .createdAt(apiKey.getCreatedAt())
                    .expiresAt(apiKey.getExpiresAt())
                    .lastRotatedAt(apiKey.getLastRotatedAt())
                    .warning("密钥已轮换，新的密钥值仅显示一次，请妥善保存！")
                    .build();
        });
    }

    /**
     * 内部轮换密钥方法
     */
    private void rotateKeyInternal(ApiKey apiKey, LocalDateTime now) {
        String keyId = apiKey.getKeyId();
        String oldKeyHash = apiKey.getKeyHash();

        // 生成新的 keyValue
        String originalKeyValue = ApiKey.generateApiKey("sk-", 32);
        String newKeyHash = ApiKeyHashUtil.hashApiKey(originalKeyValue);

        // 更新缓存
        apiKeyCache.remove(oldKeyHash);
        apiKey.setKeyHash(newKeyHash);
        apiKey.setLastRotatedAt(now);
        apiKeyCache.put(newKeyHash, apiKey);
        keyIdIndex.put(keyId, newKeyHash);
    }

    /**
     * 获取密钥轮换统计信息
     *
     * @return 轮换统计
     */
    public Mono<RotationStats> getRotationStats() {
        return Mono.fromCallable(() -> {
            int totalKeys = apiKeyCache.size();
            int keysWithRotation = 0;
            int keysNeedingRotation = 0;
            int rotatedToday = 0;
            LocalDateTime todayStart = LocalDateTime.now().toLocalDate().atStartOfDay();

            for (ApiKey apiKey : apiKeyCache.values()) {
                if (apiKey.getRotationPeriodDays() > 0) {
                    keysWithRotation++;
                }
                if (apiKey.needsRotation()) {
                    keysNeedingRotation++;
                }
                if (apiKey.getLastRotatedAt() != null &&
                        !apiKey.getLastRotatedAt().isBefore(todayStart)) {
                    rotatedToday++;
                }
            }

            return new RotationStats(totalKeys, keysWithRotation, keysNeedingRotation, rotatedToday);
        });
    }

    /**
     * 密钥轮换统计内部类
     */
    public static class RotationStats {
        private final int totalKeys;
        private final int keysWithRotation;
        private final int keysNeedingRotation;
        private final int rotatedToday;

        public RotationStats(int totalKeys, int keysWithRotation, int keysNeedingRotation, int rotatedToday) {
            this.totalKeys = totalKeys;
            this.keysWithRotation = keysWithRotation;
            this.keysNeedingRotation = keysNeedingRotation;
            this.rotatedToday = rotatedToday;
        }

        public int getTotalKeys() { return totalKeys; }
        public int getKeysWithRotation() { return keysWithRotation; }
        public int getKeysNeedingRotation() { return keysNeedingRotation; }
        public int getRotatedToday() { return rotatedToday; }
    }

    private void auditApiKeyRotated(String keyId, String rotatedBy) {
        if (extendedAuditService != null) {
            extendedAuditService.auditSecurityEvent("API_KEY_ROTATED",
                    "密钥已轮换", keyId, null)
                    .onErrorResume(ex -> {
                        log.warn("记录密钥轮换审计失败: {}", ex.getMessage());
                        return Mono.empty();
                    })
                    .subscribe();
        }
    }

    // ============ 过期密钥清理方法 ============

    /**
     * 清理过期密钥
     * 自动禁用已过期但尚未禁用的密钥
     *
     * @return 处理的密钥数量
     */
    public Mono<Integer> cleanupExpiredKeys() {
        return Mono.fromCallable(() -> {
            int cleanedCount = 0;
            LocalDateTime now = LocalDateTime.now();

            for (ApiKey apiKey : apiKeyCache.values()) {
                // 检查是否过期且仍启用
                if (apiKey.isExpired() && apiKey.isEnabled()) {
                    try {
                        apiKey.setEnabled(false);
                        cleanedCount++;
                        log.info("自动禁用过期密钥: {}", apiKey.getKeyId());
                        auditApiKeyExpired(apiKey.getKeyId());
                    } catch (Exception e) {
                        log.error("禁用过期密钥失败: {}", apiKey.getKeyId(), e);
                    }
                }
            }

            if (cleanedCount > 0) {
                saveApiKeysToStore();
            }

            return cleanedCount;
        });
    }

    /**
     * 获取过期密钥统计信息
     *
     * @return 过期统计
     */
    public Mono<ExpirationStats> getExpirationStats() {
        return Mono.fromCallable(() -> {
            int totalKeys = apiKeyCache.size();
            int expiredKeys = 0;
            int expiringToday = 0;
            int disabledKeys = 0;
            LocalDateTime todayEnd = LocalDateTime.now().toLocalDate().atTime(23, 59, 59);

            for (ApiKey apiKey : apiKeyCache.values()) {
                if (apiKey.isExpired()) {
                    expiredKeys++;
                } else if (apiKey.getExpiresAt() != null &&
                        !apiKey.getExpiresAt().isAfter(todayEnd)) {
                    expiringToday++;
                }
                if (!apiKey.isEnabled()) {
                    disabledKeys++;
                }
            }

            return new ExpirationStats(totalKeys, expiredKeys, expiringToday, disabledKeys);
        });
    }

    /**
     * 过期密钥统计内部类
     */
    public static class ExpirationStats {
        private final int totalKeys;
        private final int expiredKeys;
        private final int expiringToday;
        private final int disabledKeys;

        public ExpirationStats(int totalKeys, int expiredKeys, int expiringToday, int disabledKeys) {
            this.totalKeys = totalKeys;
            this.expiredKeys = expiredKeys;
            this.expiringToday = expiringToday;
            this.disabledKeys = disabledKeys;
        }

        public int getTotalKeys() { return totalKeys; }
        public int getExpiredKeys() { return expiredKeys; }
        public int getExpiringToday() { return expiringToday; }
        public int getDisabledKeys() { return disabledKeys; }
    }

    private void auditApiKeyExpired(String keyId) {
        if (extendedAuditService != null) {
            extendedAuditService.auditSecurityEvent("API_KEY_EXPIRED",
                    "密钥已过期，自动禁用", keyId, null)
                    .onErrorResume(ex -> {
                        log.warn("记录密钥过期审计失败: {}", ex.getMessage());
                        return Mono.empty();
                    })
                    .subscribe();
        }
    }

    // ============ 批量导入/导出方法 ============

    /**
     * 批量导出 API Key 配置
     * 导出的数据不包含 keyValue 和 keyHash，仅包含可恢复的配置信息
     *
     * @return 导出响应 VO
     */
    public Mono<ApiKeyBatchExportVO> exportApiKeys() {
        return Mono.fromCallable(() -> {
            List<ApiKeyBatchExportVO.ExportedKey> exportedKeys = apiKeyCache.values().stream()
                    .map(apiKey -> ApiKeyBatchExportVO.ExportedKey.builder()
                            .keyId(apiKey.getKeyId())
                            .description(apiKey.getDescription())
                            .permissions(apiKey.getPermissions())
                            .enabled(apiKey.isEnabled())
                            .createdAt(apiKey.getCreatedAt())
                            .createdBy(apiKey.getCreatedBy())
                            .expiresAt(apiKey.getExpiresAt())
                            .allowedIpAddresses(apiKey.getAllowedIpAddresses())
                            .dailyRequestLimit(apiKey.getDailyRequestLimit())
                            .rotationPeriodDays(apiKey.getRotationPeriodDays())
                            .lastRotatedAt(apiKey.getLastRotatedAt())
                            .build())
                    .sorted(Comparator.comparing(ApiKeyBatchExportVO.ExportedKey::getCreatedAt).reversed())
                    .toList();

            return ApiKeyBatchExportVO.builder()
                    .exportTime(LocalDateTime.now())
                    .total(exportedKeys.size())
                    .keys(exportedKeys)
                    .build();
        });
    }

    /**
     * 批量导入 API Key
     *
     * @param request   导入请求
     * @param importedBy 导入操作者
     * @param ipAddress  操作者 IP
     * @return 导入结果 VO
     */
    public Mono<ApiKeyBatchImportResult> importApiKeys(ApiKeyBatchImportRequest request,
                                                        String importedBy, String ipAddress) {
        return Mono.fromCallable(() -> {
            List<ApiKeyCreationVO> importedKeys = new ArrayList<>();
            List<ApiKeyBatchImportResult.ImportError> errors = new ArrayList<>();
            int successCount = 0;
            int failureCount = 0;

            // 如果是替换模式，先清除所有现有密钥
            if (request.getMode() == ApiKeyBatchImportRequest.ImportMode.REPLACE) {
                apiKeyCache.clear();
                keyIdIndex.clear();
                log.info("批量导入：REPLACE 模式，已清除所有现有密钥");
            }

            // 导入每个密钥
            for (ApiKeyBatchImportRequest.ApiKeyImportItem item : request.getKeys()) {
                try {
                    // 生成 keyId
                    String keyId = item.getKeyId() != null && !item.getKeyId().trim().isEmpty()
                            ? item.getKeyId()
                            : "key-" + UUID.randomUUID().toString().substring(0, 8);

                    // 检查 keyId 是否已存在（MERGE 模式）
                    if (request.getMode() == ApiKeyBatchImportRequest.ImportMode.MERGE
                            && keyIdIndex.containsKey(keyId)) {
                        errors.add(ApiKeyBatchImportResult.ImportError.builder()
                                .keyId(keyId)
                                .reason("API Key ID已存在")
                                .build());
                        failureCount++;
                        continue;
                    }

                    // 生成原始 keyValue
                    String originalKeyValue = ApiKey.generateApiKey("sk-", 32);
                    String keyHash = ApiKeyHashUtil.hashApiKey(originalKeyValue);

                    // 解析过期时间
                    LocalDateTime expiresAt = null;
                    if (item.getExpiresAt() != null && !item.getExpiresAt().trim().isEmpty()) {
                        try {
                            expiresAt = LocalDateTime.parse(item.getExpiresAt(),
                                    java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
                        } catch (Exception e) {
                            log.warn("解析过期时间失败: {}", item.getExpiresAt());
                        }
                    }

                    // 构建 ApiKey 实体
                    ApiKey apiKey = ApiKey.builder()
                            .keyId(keyId)
                            .keyHash(keyHash)
                            .keyValue(null)
                            .keyPrefix("sk-")
                            .description(item.getDescription())
                            .permissions(item.getPermissions())
                            .enabled(item.getEnabled() != null ? item.getEnabled() : true)
                            .expiresAt(expiresAt)
                            .createdAt(LocalDateTime.now())
                            .createdBy(importedBy)
                            .creatorIpAddress(ipAddress)
                            .rotationPeriodDays(item.getRotationPeriodDays() != null ? item.getRotationPeriodDays() : 0)
                            .allowedIpAddresses(item.getAllowedIpAddresses())
                            .dailyRequestLimit(item.getDailyRequestLimit() != null ? item.getDailyRequestLimit() : 0L)
                            .usage(UsageStatistics.builder()
                                    .totalRequests(0L)
                                    .successfulRequests(0L)
                                    .failedRequests(0L)
                                    .dailyUsage(new HashMap<>())
                                    .build())
                            .build();

                    // 更新缓存
                    apiKeyCache.put(keyHash, apiKey);
                    keyIdIndex.put(keyId, keyHash);

                    // 构建响应
                    importedKeys.add(ApiKeyCreationVO.builder()
                            .keyId(keyId)
                            .keyValue(originalKeyValue)  // 仅此一次返回原始值
                            .description(apiKey.getDescription())
                            .permissions(apiKey.getPermissions())
                            .enabled(apiKey.isEnabled())
                            .createdAt(apiKey.getCreatedAt())
                            .expiresAt(apiKey.getExpiresAt())
                            .warning("密钥值只会显示一次，请妥善保存！")
                            .build());

                    successCount++;
                    log.info("批量导入成功: {}", keyId);

                } catch (Exception e) {
                    errors.add(ApiKeyBatchImportResult.ImportError.builder()
                            .keyId(item.getKeyId())
                            .reason("导入失败: " + e.getMessage())
                            .build());
                    failureCount++;
                    log.error("批量导入失败: {}", item.getKeyId(), e);
                }
            }

            // 持久化
            saveApiKeysToStore();

            // 记录审计
            auditBatchImport(importedBy, ipAddress, successCount, failureCount);

            return ApiKeyBatchImportResult.builder()
                    .totalAttempted(request.getKeys().size())
                    .successCount(successCount)
                    .failureCount(failureCount)
                    .importedKeys(importedKeys)
                    .errors(errors)
                    .build();
        });
    }

    private void auditBatchImport(String importedBy, String ipAddress, int successCount, int failureCount) {
        if (extendedAuditService != null) {
            extendedAuditService.auditSecurityEvent("API_KEY_BATCH_IMPORT",
                    "批量导入完成: 成功 " + successCount + ", 失败 " + failureCount, null, ipAddress)
                    .onErrorResume(ex -> {
                        log.warn("记录批量导入审计失败: {}", ex.getMessage());
                        return Mono.empty();
                    })
                    .subscribe();
        }
    }
}