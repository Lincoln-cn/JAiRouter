package org.unreal.modelrouter.auth.security.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.unreal.modelrouter.auth.security.audit.ExtendedSecurityAuditService;
import org.unreal.modelrouter.auth.security.config.properties.ApiKey;
import org.unreal.modelrouter.auth.security.dto.ApiKeyBatchExportVO;
import org.unreal.modelrouter.auth.security.dto.ApiKeyBatchImportRequest;
import org.unreal.modelrouter.auth.security.dto.ApiKeyBatchImportResult;
import org.unreal.modelrouter.auth.security.dto.ApiKeyCreationVO;
import org.unreal.modelrouter.auth.security.model.UsageStatistics;
import org.unreal.modelrouter.auth.security.util.ApiKeyHashUtil;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * API Key 批量操作服务
 * 负责 API Key 的批量导入、导出、轮换和清理
 *
 * @since v2.14.1
 */
@Slf4j
@Service
public class ApiKeyBatchService {

    private static final DateTimeFormatter EXPIRES_AT_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    // 审计服务（用于记录批量操作）
    @Autowired(required = false)
    private ExtendedSecurityAuditService extendedAuditService;

    // 持久化服务（用于保存变更）
    @Autowired(required = false)
    private ApiKeyPersistenceService persistenceService;

    /**
     * 批量导出 API Key 配置
     * 导出的数据不包含 keyValue 和 keyHash，仅包含可恢复的配置信息
     *
     * @param apiKeyCache API Key 缓存（keyHash -> ApiKey）
     * @return 导出响应 VO
     */
    public Mono<ApiKeyBatchExportVO> exportApiKeys(final Map<String, ApiKey> apiKeyCache) {
        return Mono.fromCallable(() -> {
            List<ApiKeyBatchExportVO.ExportedKey> exportedKeys = apiKeyCache.values().stream()
                    .map(this::convertToExportedKey)
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
     * @param request       导入请求
     * @param apiKeyCache   API Key 缓存（keyHash -> ApiKey）
     * @param keyIdIndex    API Key ID 索引（keyId -> keyHash）
     * @param importedBy    导入操作者
     * @param ipAddress     操作者 IP
     * @return 导入结果 VO
     */
    public Mono<ApiKeyBatchImportResult> importApiKeys(final ApiKeyBatchImportRequest request,
                                                        final Map<String, ApiKey> apiKeyCache,
                                                        final Map<String, String> keyIdIndex,
                                                        final String importedBy,
                                                        final String ipAddress) {
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
                    ImportResult result = importSingleKey(item, apiKeyCache, keyIdIndex,
                            importedBy, ipAddress,
                            request.getMode() == ApiKeyBatchImportRequest.ImportMode.MERGE);

                    if (result.isSuccess()) {
                        importedKeys.add(result.getCreationVO());
                        successCount++;
                        log.info("批量导入成功: {}", result.getKeyId());
                    } else {
                        errors.add(ApiKeyBatchImportResult.ImportError.builder()
                                .keyId(item.getKeyId())
                                .reason(result.getErrorMessage())
                                .build());
                        failureCount++;
                        log.error("批量导入失败: {}", item.getKeyId());
                    }
                } catch (Exception e) {
                    errors.add(ApiKeyBatchImportResult.ImportError.builder()
                            .keyId(item.getKeyId())
                            .reason("导入失败: " + e.getMessage())
                            .build());
                    failureCount++;
                    log.error("批量导入失败: {}", item.getKeyId(), e);
                }
            }

            // 持久化变更
            if (persistenceService != null) {
                persistenceService.saveApiKeysToStore(apiKeyCache);
            }

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

    /**
     * 轮换所有需要轮换的密钥
     *
     * @param apiKeyCache   API Key 缓存（keyHash -> ApiKey）
     * @param keyIdIndex    API Key ID 索引（keyId -> keyHash）
     * @return 轮换的密钥数量
     */
    public Mono<Integer> rotateExpiredKeys(final Map<String, ApiKey> apiKeyCache,
                                            final Map<String, String> keyIdIndex) {
        return Mono.fromCallable(() -> {
            int rotatedCount = 0;
            LocalDateTime now = LocalDateTime.now();

            for (ApiKey apiKey : apiKeyCache.values()) {
                if (apiKey.needsRotation() && apiKey.isEnabled()) {
                    try {
                        rotateKeyInternal(apiKey, apiKeyCache, keyIdIndex, now);
                        rotatedCount++;
                        log.info("自动轮换密钥: {}", apiKey.getKeyId());
                    } catch (Exception e) {
                        log.error("轮换密钥失败: {}", apiKey.getKeyId(), e);
                    }
                }
            }

            if (rotatedCount > 0 && persistenceService != null) {
                persistenceService.saveApiKeysToStore(apiKeyCache);
            }

            return rotatedCount;
        });
    }

    /**
     * 强制轮换指定密钥
     *
     * @param keyId         API Key ID
     * @param apiKeyCache   API Key 缓存
     * @param keyIdIndex    ID 索引
     * @param rotatedBy     轮换操作者
     * @return 创建响应 VO（包含新的 keyValue）
     */
    public Mono<ApiKeyCreationVO> forceRotateKey(final String keyId,
                                                  final Map<String, ApiKey> apiKeyCache,
                                                  final Map<String, String> keyIdIndex,
                                                  final String rotatedBy) {
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
            if (persistenceService != null) {
                persistenceService.saveApiKeysToStore(apiKeyCache);
            }

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
     * 清理过期密钥
     * 自动禁用已过期但尚未禁用的密钥
     *
     * @param apiKeyCache   API Key 缓存
     * @return 处理的密钥数量
     */
    public Mono<Integer> cleanupExpiredKeys(final Map<String, ApiKey> apiKeyCache) {
        return Mono.fromCallable(() -> {
            int cleanedCount = 0;

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

            if (cleanedCount > 0 && persistenceService != null) {
                persistenceService.saveApiKeysToStore(apiKeyCache);
            }

            return cleanedCount;
        });
    }

    /**
     * 获取密钥轮换统计信息
     *
     * @param apiKeyCache   API Key 缓存
     * @return 轮换统计
     */
    public Mono<RotationStats> getRotationStats(final Map<String, ApiKey> apiKeyCache) {
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
                if (apiKey.getLastRotatedAt() != null
                        && !apiKey.getLastRotatedAt().isBefore(todayStart)) {
                    rotatedToday++;
                }
            }

            return new RotationStats(totalKeys, keysWithRotation, keysNeedingRotation, rotatedToday);
        });
    }

    /**
     * 获取过期密钥统计信息
     *
     * @param apiKeyCache   API Key 缓存
     * @return 过期统计
     */
    public Mono<ExpirationStats> getExpirationStats(final Map<String, ApiKey> apiKeyCache) {
        return Mono.fromCallable(() -> {
            int totalKeys = apiKeyCache.size();
            int expiredKeys = 0;
            int expiringToday = 0;
            int disabledKeys = 0;
            LocalDateTime todayEnd = LocalDateTime.now().toLocalDate().atTime(23, 59, 59);

            for (ApiKey apiKey : apiKeyCache.values()) {
                if (apiKey.isExpired()) {
                    expiredKeys++;
                } else if (apiKey.getExpiresAt() != null
                        && !apiKey.getExpiresAt().isAfter(todayEnd)) {
                    expiringToday++;
                }
                if (!apiKey.isEnabled()) {
                    disabledKeys++;
                }
            }

            return new ExpirationStats(totalKeys, expiredKeys, expiringToday, disabledKeys);
        });
    }

    // ============ 内部方法 ============

    /**
     * 导入单个密钥
     */
    private ImportResult importSingleKey(final ApiKeyBatchImportRequest.ApiKeyImportItem item,
                                          final Map<String, ApiKey> apiKeyCache,
                                          final Map<String, String> keyIdIndex,
                                          final String importedBy,
                                          final String ipAddress,
                                          final boolean mergeMode) {
        // 生成 keyId
        String keyId = item.getKeyId() != null && !item.getKeyId().trim().isEmpty()
                ? item.getKeyId()
                : "key-" + UUID.randomUUID().toString().substring(0, 8);

        // 检查 keyId 是否已存在（MERGE 模式）
        if (mergeMode && keyIdIndex.containsKey(keyId)) {
            return ImportResult.failure(keyId, "API Key ID已存在");
        }

        // 生成原始 keyValue
        String originalKeyValue = ApiKey.generateApiKey("sk-", 32);
        String keyHash = ApiKeyHashUtil.hashApiKey(originalKeyValue);

        // 解析过期时间
        LocalDateTime expiresAt = parseExpiresAt(item.getExpiresAt());

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
        ApiKeyCreationVO creationVO = ApiKeyCreationVO.builder()
                .keyId(keyId)
                .keyValue(originalKeyValue)  // 仅此一次返回原始值
                .description(apiKey.getDescription())
                .permissions(apiKey.getPermissions())
                .enabled(apiKey.isEnabled())
                .createdAt(apiKey.getCreatedAt())
                .expiresAt(apiKey.getExpiresAt())
                .warning("密钥值只会显示一次，请妥善保存！")
                .build();

        return ImportResult.success(keyId, creationVO);
    }

    /**
     * 解析过期时间
     */
    private LocalDateTime parseExpiresAt(final String expiresAtStr) {
        if (expiresAtStr == null || expiresAtStr.trim().isEmpty()) {
            return null;
        }
        try {
            return LocalDateTime.parse(expiresAtStr, EXPIRES_AT_FORMATTER);
        } catch (Exception e) {
            log.warn("解析过期时间失败: {}", expiresAtStr);
            return null;
        }
    }

    /**
     * 内部轮换密钥方法
     */
    private void rotateKeyInternal(final ApiKey apiKey,
                                   final Map<String, ApiKey> apiKeyCache,
                                   final Map<String, String> keyIdIndex,
                                   final LocalDateTime now) {
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
     * 转换为导出格式
     */
    private ApiKeyBatchExportVO.ExportedKey convertToExportedKey(final ApiKey apiKey) {
        return ApiKeyBatchExportVO.ExportedKey.builder()
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
                .build();
    }

    // ============ 审计方法 ============

    private void auditBatchImport(final String importedBy, final String ipAddress,
                                  final int successCount, final int failureCount) {
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

    private void auditApiKeyRotated(final String keyId, final String rotatedBy) {
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

    private void auditApiKeyExpired(final String keyId) {
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

    // ============ 统计内部类 ============

    /**
     * 密钥轮换统计
     */
    public static class RotationStats {
        private final int totalKeys;
        private final int keysWithRotation;
        private final int keysNeedingRotation;
        private final int rotatedToday;

        public RotationStats(final int totalKeys, final int keysWithRotation,
                             final int keysNeedingRotation, final int rotatedToday) {
            this.totalKeys = totalKeys;
            this.keysWithRotation = keysWithRotation;
            this.keysNeedingRotation = keysNeedingRotation;
            this.rotatedToday = rotatedToday;
        }

        public int getTotalKeys() {
            return totalKeys;
        }

        public int getKeysWithRotation() {
            return keysWithRotation;
        }

        public int getKeysNeedingRotation() {
            return keysNeedingRotation;
        }

        public int getRotatedToday() {
            return rotatedToday;
        }
    }

    /**
     * 过期密钥统计
     */
    public static class ExpirationStats {
        private final int totalKeys;
        private final int expiredKeys;
        private final int expiringToday;
        private final int disabledKeys;

        public ExpirationStats(final int totalKeys, final int expiredKeys,
                               final int expiringToday, final int disabledKeys) {
            this.totalKeys = totalKeys;
            this.expiredKeys = expiredKeys;
            this.expiringToday = expiringToday;
            this.disabledKeys = disabledKeys;
        }

        public int getTotalKeys() {
            return totalKeys;
        }

        public int getExpiredKeys() {
            return expiredKeys;
        }

        public int getExpiringToday() {
            return expiringToday;
        }

        public int getDisabledKeys() {
            return disabledKeys;
        }
    }

    // ============ 导入结果内部类 ============

    /**
     * 单个密钥导入结果
     */
    private static class ImportResult {
        private final boolean success;
        private final String keyId;
        private final ApiKeyCreationVO creationVO;
        private final String errorMessage;

        private ImportResult(final boolean success, final String keyId,
                             final ApiKeyCreationVO creationVO, final String errorMessage) {
            this.success = success;
            this.keyId = keyId;
            this.creationVO = creationVO;
            this.errorMessage = errorMessage;
        }

        public static ImportResult success(final String keyId, final ApiKeyCreationVO creationVO) {
            return new ImportResult(true, keyId, creationVO, null);
        }

        public static ImportResult failure(final String keyId, final String errorMessage) {
            return new ImportResult(false, keyId, null, errorMessage);
        }

        public boolean isSuccess() {
            return success;
        }

        public String getKeyId() {
            return keyId;
        }

        public ApiKeyCreationVO getCreationVO() {
            return creationVO;
        }

        public String getErrorMessage() {
            return errorMessage;
        }
    }
}