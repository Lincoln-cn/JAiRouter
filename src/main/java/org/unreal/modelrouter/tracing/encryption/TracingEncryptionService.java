package org.unreal.modelrouter.tracing.encryption;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.unreal.modelrouter.tracing.TracingContext;
import org.unreal.modelrouter.tracing.config.TracingConfiguration;
import org.unreal.modelrouter.tracing.logger.StructuredLogger;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * 追踪数据加密存储服务
 * 
 * 负责处理分布式追踪数据的加密存储和安全管理，包括：
 * - 敏感追踪信息的加密传输
 * - 追踪数据的加密存储
 * - 追踪数据的保留策略管理
 * - 追踪数据的安全清理
 * - 密钥管理和轮换
 * 
 * @author JAiRouter Team
 * @since 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TracingEncryptionService {
    
    private final TracingConfiguration tracingConfiguration;
    private final @Lazy StructuredLogger structuredLogger;
    
    // 加密算法常量
    private static final String ENCRYPTION_ALGORITHM = "AES";
    private static final String CIPHER_TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int GCM_IV_LENGTH = 12;
    private static final int GCM_TAG_LENGTH = 16;
    private static final int KEY_LENGTH = 256;
    
    // 加密密钥存储
    private final Map<String, SecretKey> encryptionKeys = new ConcurrentHashMap<>();
    
    // 追踪数据保留策略
    private final Map<String, TraceRetentionPolicy> retentionPolicies = new ConcurrentHashMap<>();
    
    // 加密追踪数据缓存
    private final Map<String, EncryptedTraceData> encryptedTraceCache = new ConcurrentHashMap<>();
    
    // 定期清理任务
    private final ScheduledExecutorService cleanupScheduler = Executors.newScheduledThreadPool(2);
    
    /**
     * 初始化加密服务
     */
    public void initialize() {
        log.info("初始化追踪数据加密服务");
        
        try {
            // 生成默认加密密钥
            generateDefaultEncryptionKey();
            
            // 初始化保留策略
            initializeRetentionPolicies();
            
            // 启动定期清理任务
            startCleanupTasks();
            
            log.info("追踪数据加密服务初始化完成");
        } catch (Exception e) {
            log.error("追踪数据加密服务初始化失败", e);
            throw new RuntimeException("加密服务初始化失败", e);
        }
    }
    
    /**
     * 加密敏感追踪数据
     * 
     * @param data 原始数据
     * @param traceId 追踪ID
     * @param dataType 数据类型
     * @return 加密后的数据
     */
    public Mono<String> encryptTraceData(String data, String traceId, String dataType) {
        if (data == null || data.isEmpty()) {
            return Mono.empty();
        }
        
        return Mono.fromCallable(() -> {
            try {
                SecretKey key = getOrCreateEncryptionKey(traceId);
                
                // 生成随机IV
                byte[] iv = new byte[GCM_IV_LENGTH];
                new SecureRandom().nextBytes(iv);
                
                // 创建加密器
                Cipher cipher = Cipher.getInstance(CIPHER_TRANSFORMATION);
                GCMParameterSpec gcmSpec = new GCMParameterSpec(GCM_TAG_LENGTH * 8, iv);
                cipher.init(Cipher.ENCRYPT_MODE, key, gcmSpec);
                
                // 加密数据
                byte[] encryptedData = cipher.doFinal(data.getBytes(StandardCharsets.UTF_8));
                
                // 组合IV和加密数据
                byte[] result = new byte[iv.length + encryptedData.length];
                System.arraycopy(iv, 0, result, 0, iv.length);
                System.arraycopy(encryptedData, 0, result, iv.length, encryptedData.length);
                
                // Base64编码
                String encryptedString = Base64.getEncoder().encodeToString(result);
                
                // 缓存加密数据信息
                cacheEncryptedTraceData(traceId, dataType, encryptedString);
                
                // 记录加密操作审计日志
                recordEncryptionAudit(traceId, dataType, "encrypt", true, null);
                
                return encryptedString;
                
            } catch (Exception e) {
                log.error("加密追踪数据失败: traceId={}, dataType={}", traceId, dataType, e);
                recordEncryptionAudit(traceId, dataType, "encrypt", false, e.getMessage());
                throw new RuntimeException("加密失败", e);
            }
        });
    }
    
    /**
     * 解密追踪数据
     * 
     * @param encryptedData 加密数据
     * @param traceId 追踪ID
     * @param dataType 数据类型
     * @return 解密后的数据
     */
    public Mono<String> decryptTraceData(String encryptedData, String traceId, String dataType) {
        if (encryptedData == null || encryptedData.isEmpty()) {
            return Mono.empty();
        }
        
        return Mono.fromCallable(() -> {
            try {
                SecretKey key = getEncryptionKey(traceId);
                if (key == null) {
                    throw new RuntimeException("未找到解密密钥: " + traceId);
                }
                
                // Base64解码
                byte[] data = Base64.getDecoder().decode(encryptedData);
                
                // 提取IV和加密数据
                byte[] iv = new byte[GCM_IV_LENGTH];
                byte[] encrypted = new byte[data.length - GCM_IV_LENGTH];
                System.arraycopy(data, 0, iv, 0, GCM_IV_LENGTH);
                System.arraycopy(data, GCM_IV_LENGTH, encrypted, 0, encrypted.length);
                
                // 创建解密器
                Cipher cipher = Cipher.getInstance(CIPHER_TRANSFORMATION);
                GCMParameterSpec gcmSpec = new GCMParameterSpec(GCM_TAG_LENGTH * 8, iv);
                cipher.init(Cipher.DECRYPT_MODE, key, gcmSpec);
                
                // 解密数据
                byte[] decryptedData = cipher.doFinal(encrypted);
                String result = new String(decryptedData, StandardCharsets.UTF_8);
                
                // 记录解密操作审计日志
                recordEncryptionAudit(traceId, dataType, "decrypt", true, null);
                
                return result;
                
            } catch (Exception e) {
                log.error("解密追踪数据失败: traceId={}, dataType={}", traceId, dataType, e);
                recordEncryptionAudit(traceId, dataType, "decrypt", false, e.getMessage());
                throw new RuntimeException("解密失败", e);
            }
        });
    }
    
    /**
     * 应用保留策略清理过期数据
     * 
     * @param traceId 追踪ID
     * @return 清理结果
     */
    public Mono<Boolean> applyRetentionPolicy(String traceId) {
        return Mono.fromCallable(() -> {
            EncryptedTraceData traceData = encryptedTraceCache.get(traceId);
            if (traceData == null) {
                return false;
            }
            
            TraceRetentionPolicy policy = getRetentionPolicy(traceData.getDataType());
            if (policy.shouldDelete(traceData.getCreatedAt())) {
                // 清理追踪数据
                cleanupTraceData(traceId);
                return true;
            }
            
            return false;
        });
    }
    
    /**
     * 安全清理追踪数据
     * 
     * @param traceId 追踪ID
     * @return 清理结果
     */
    public Mono<Void> secureCleanupTraceData(String traceId) {
        return Mono.fromRunnable(() -> {
            try {
                // 清理加密数据缓存
                encryptedTraceCache.remove(traceId);
                
                // 清理加密密钥
                encryptionKeys.remove(traceId);
                
                // 记录清理操作审计日志
                recordCleanupAudit(traceId, true, null);
                
                log.debug("安全清理追踪数据完成: {}", traceId);
                
            } catch (Exception e) {
                log.error("安全清理追踪数据失败: {}", traceId, e);
                recordCleanupAudit(traceId, false, e.getMessage());
                throw new RuntimeException("清理失败", e);
            }
        });
    }
    
    /**
     * 批量清理过期数据
     * 
     * @return 清理的数据数量
     */
    public Mono<Integer> cleanupExpiredData() {
        return Flux.fromIterable(encryptedTraceCache.entrySet())
                .flatMap(entry -> {
                    String traceId = entry.getKey();
                    EncryptedTraceData traceData = entry.getValue();
                    
                    TraceRetentionPolicy policy = getRetentionPolicy(traceData.getDataType());
                    if (policy.shouldDelete(traceData.getCreatedAt())) {
                        return secureCleanupTraceData(traceId).thenReturn(1);
                    }
                    return Mono.just(0);
                })
                .reduce(0, Integer::sum);
    }
    
    /**
     * 轮换加密密钥
     * 
     * @param traceId 追踪ID
     * @return 轮换结果
     */
    public Mono<Boolean> rotateEncryptionKey(String traceId) {
        return Mono.fromCallable(() -> {
            try {
                // 生成新密钥
                SecretKey newKey = generateSecretKey();
                
                // 如果存在旧数据，重新加密
                EncryptedTraceData oldData = encryptedTraceCache.get(traceId);
                if (oldData != null) {
                    // 解密旧数据
                    String decryptedData = decryptTraceData(oldData.getEncryptedData(), traceId, oldData.getDataType()).block();
                    
                    // 更新密钥
                    encryptionKeys.put(traceId, newKey);
                    
                    // 用新密钥重新加密
                    String reencryptedData = encryptTraceData(decryptedData, traceId, oldData.getDataType()).block();
                    
                    // 更新缓存
                    oldData.setEncryptedData(reencryptedData);
                    oldData.setUpdatedAt(Instant.now());
                } else {
                    // 只更新密钥
                    encryptionKeys.put(traceId, newKey);
                }
                
                // 记录密钥轮换审计日志
                recordKeyRotationAudit(traceId, true, null);
                
                return true;
                
            } catch (Exception e) {
                log.error("轮换加密密钥失败: {}", traceId, e);
                recordKeyRotationAudit(traceId, false, e.getMessage());
                return false;
            }
        });
    }
    
    /**
     * 获取或创建加密密钥
     */
    private SecretKey getOrCreateEncryptionKey(String traceId) throws Exception {
        return encryptionKeys.computeIfAbsent(traceId, k -> {
            try {
                return generateSecretKey();
            } catch (Exception e) {
                throw new RuntimeException("生成密钥失败", e);
            }
        });
    }
    
    /**
     * 获取加密密钥
     */
    private SecretKey getEncryptionKey(String traceId) {
        return encryptionKeys.get(traceId);
    }
    
    /**
     * 生成加密密钥
     */
    private SecretKey generateSecretKey() throws Exception {
        KeyGenerator keyGenerator = KeyGenerator.getInstance(ENCRYPTION_ALGORITHM);
        keyGenerator.init(KEY_LENGTH);
        return keyGenerator.generateKey();
    }
    
    /**
     * 生成默认加密密钥
     */
    private void generateDefaultEncryptionKey() throws Exception {
        SecretKey defaultKey = generateSecretKey();
        encryptionKeys.put("default", defaultKey);
    }
    
    /**
     * 缓存加密追踪数据
     */
    private void cacheEncryptedTraceData(String traceId, String dataType, String encryptedData) {
        EncryptedTraceData traceData = new EncryptedTraceData(
                traceId, dataType, encryptedData, Instant.now(), Instant.now()
        );
        encryptedTraceCache.put(traceId, traceData);
    }
    
    /**
     * 初始化保留策略
     */
    private void initializeRetentionPolicies() {
        // 默认保留策略：30天
        retentionPolicies.put("default", new TraceRetentionPolicy("default", Duration.ofDays(30)));
        
        // 敏感数据保留策略：7天
        retentionPolicies.put("sensitive", new TraceRetentionPolicy("sensitive", Duration.ofDays(7)));
        
        // 错误数据保留策略：90天
        retentionPolicies.put("error", new TraceRetentionPolicy("error", Duration.ofDays(90)));
        
        // 性能数据保留策略：60天
        retentionPolicies.put("performance", new TraceRetentionPolicy("performance", Duration.ofDays(60)));
    }
    
    /**
     * 获取保留策略
     */
    private TraceRetentionPolicy getRetentionPolicy(String dataType) {
        return retentionPolicies.getOrDefault(dataType, retentionPolicies.get("default"));
    }
    
    /**
     * 启动定期清理任务
     */
    private void startCleanupTasks() {
        // 每小时执行一次过期数据清理
        cleanupScheduler.scheduleAtFixedRate(() -> {
            cleanupExpiredData().subscribe(
                    count -> log.info("定期清理完成，清理数据数量: {}", count),
                    error -> log.error("定期清理失败", error)
            );
        }, 1, 1, TimeUnit.HOURS);
        
        // 每24小时执行一次密钥轮换（对于长期存在的追踪）
        cleanupScheduler.scheduleAtFixedRate(() -> {
            encryptedTraceCache.entrySet().stream()
                    .filter(entry -> Duration.between(entry.getValue().getUpdatedAt(), Instant.now()).toDays() >= 1)
                    .forEach(entry -> {
                        rotateEncryptionKey(entry.getKey()).subscribe(
                                success -> log.debug("密钥轮换结果: {} -> {}", entry.getKey(), success),
                                error -> log.error("密钥轮换失败: {}", entry.getKey(), error)
                        );
                    });
        }, 24, 24, TimeUnit.HOURS);
    }
    
    /**
     * 清理追踪数据
     */
    private void cleanupTraceData(String traceId) {
        encryptedTraceCache.remove(traceId);
        encryptionKeys.remove(traceId);
    }
    
    /**
     * 记录加密操作审计日志
     */
    private void recordEncryptionAudit(String traceId, String dataType, String operation, 
                                     boolean success, String error) {
        try {
            Map<String, Object> auditData = new HashMap<>();
            auditData.put("traceId", traceId);
            auditData.put("dataType", dataType);
            auditData.put("operation", operation);
            auditData.put("success", success);
            auditData.put("error", error);
            auditData.put("timestamp", Instant.now().toString());
            
            structuredLogger.logSystemEvent("trace_encryption", "INFO", auditData, null);
            
        } catch (Exception e) {
            log.warn("记录加密操作审计日志失败", e);
        }
    }
    
    /**
     * 记录清理操作审计日志
     */
    private void recordCleanupAudit(String traceId, boolean success, String error) {
        try {
            Map<String, Object> auditData = new HashMap<>();
            auditData.put("traceId", traceId);
            auditData.put("operation", "cleanup");
            auditData.put("success", success);
            auditData.put("error", error);
            auditData.put("timestamp", Instant.now().toString());
            
            structuredLogger.logSystemEvent("trace_cleanup", "INFO", auditData, null);
            
        } catch (Exception e) {
            log.warn("记录清理操作审计日志失败", e);
        }
    }
    
    /**
     * 记录密钥轮换审计日志
     */
    private void recordKeyRotationAudit(String traceId, boolean success, String error) {
        try {
            Map<String, Object> auditData = new HashMap<>();
            auditData.put("traceId", traceId);
            auditData.put("operation", "key_rotation");
            auditData.put("success", success);
            auditData.put("error", error);
            auditData.put("timestamp", Instant.now().toString());
            
            structuredLogger.logSystemEvent("trace_key_rotation", "INFO", auditData, null);
            
        } catch (Exception e) {
            log.warn("记录密钥轮换审计日志失败", e);
        }
    }
    
    /**
     * 加密追踪数据实体
     */
    public static class EncryptedTraceData {
        private final String traceId;
        private final String dataType;
        private String encryptedData;
        private final Instant createdAt;
        private Instant updatedAt;
        
        public EncryptedTraceData(String traceId, String dataType, String encryptedData, 
                                Instant createdAt, Instant updatedAt) {
            this.traceId = traceId;
            this.dataType = dataType;
            this.encryptedData = encryptedData;
            this.createdAt = createdAt;
            this.updatedAt = updatedAt;
        }
        
        // Getters and Setters
        public String getTraceId() { return traceId; }
        public String getDataType() { return dataType; }
        public String getEncryptedData() { return encryptedData; }
        public void setEncryptedData(String encryptedData) { this.encryptedData = encryptedData; }
        public Instant getCreatedAt() { return createdAt; }
        public Instant getUpdatedAt() { return updatedAt; }
        public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
    }
    
    /**
     * 追踪数据保留策略
     */
    public static class TraceRetentionPolicy {
        private final String policyName;
        private final Duration retentionDuration;
        
        public TraceRetentionPolicy(String policyName, Duration retentionDuration) {
            this.policyName = policyName;
            this.retentionDuration = retentionDuration;
        }
        
        public boolean shouldDelete(Instant createdAt) {
            return Duration.between(createdAt, Instant.now()).compareTo(retentionDuration) > 0;
        }
        
        public String getPolicyName() { return policyName; }
        public Duration getRetentionDuration() { return retentionDuration; }
    }
}