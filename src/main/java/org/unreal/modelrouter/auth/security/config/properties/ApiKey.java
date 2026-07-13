package org.unreal.modelrouter.auth.security.config.properties;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.datatype.jsr310.deser.LocalDateTimeDeserializer;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateTimeSerializer;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.unreal.modelrouter.auth.security.model.UsageStatistics;
import org.unreal.modelrouter.auth.security.util.ApiKeyHashUtil;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

/**
 * 统一的API Key数据模型
 * 合并了原来的ApiKeyProperties和ApiKeyInfo类
 * 
 * 安全改进：keyValue 使用 SHA-256 哈希存储（keyHash 字段）
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiKey {

    /**
     * API Key唯一标识符
     */
    private String keyId;

    /**
     * API Key值 - 仅在创建时返回，其他时候为null以确保安全
     *
     * @deprecated 明文存储 API Key 不安全，此字段仅为创建时返回给用户而保留。
     *             请使用 {@link #keyHash} 字段进行安全存储和验证。
     *             <p>安全说明：</p>
     *             <ul>
     *               <li>keyValue 是明文存储，存在安全风险</li>
     *               <li>keyHash 使用 SHA-256 + 盐值哈希存储，更安全</li>
     *               <li>验证时应使用 ApiKeyHashUtil.verifyKey() 方法</li>
     *             </ul>
     *             <p>迁移示例：</p>
     *             <pre>{@code
     *             // 旧代码 - 不安全
     *             if (apiKey.getKeyValue().equals(inputKey)) { ... }
     *             
     *             // 新代码 - 安全
     *             if (ApiKeyHashUtil.verifyKey(inputKey, apiKey.getKeyHash())) { ... }
     *             }</pre>
     *             <p>此字段将在 v3.0 版本中移除。</p>
     * @see #keyHash
     * @see org.unreal.modelrouter.auth.security.util.ApiKeyHashUtil
     * @since v2.5.2 完善废弃标注
     */
    @Deprecated(since = "2.5.2", forRemoval = true)
    @JsonProperty
    private String keyValue;

    /**
     * API Key 的哈希值（SHA-256 + 盐值）
     * 用于安全存储和验证
     */
    @JsonProperty
    private String keyHash;

    /**
     * API Key 前缀（如 "sk-"）
     * 用于识别密钥类型
     */
    private String keyPrefix;

    /**
     * 允许使用的 IP 白名单
     * 为空表示不限制
     */
    private List<String> allowedIpAddresses;

    /**
     * 每日请求上限（0 表示无限制）
     */
    @Builder.Default
    private long dailyRequestLimit = 0L;

    /**
     * 每日 Token 使用上限（0 表示无限制）
     * 统计 prompt_tokens + completion_tokens 总和
     */
    @Builder.Default
    private long dailyTokenLimit = 0L;

    /**
     * 每分钟请求速率上限（0 表示无限制）
     * 使用滑动窗口算法实现
     */
    @Builder.Default
    private int rateLimitPerMinute = 0;

    /**
     * 配额告警阈值（0.0-1.0），达到此比例时触发告警
     * 默认 0.8 表示使用量达到 80% 时告警
     */
    @Builder.Default
    private double quotaAlertThreshold = 0.8;

    /**
     * API Key描述信息
     */
    private String description;

    /**
     * 所有者ID - 用于多用户资源隔离
     * 管理员可以查看所有Key，普通用户只能查看自己的Key
     */
    private String ownerId;

    /**
     * 所有者角色
     */
    private String ownerRole;

    /**
     * 权限列表
     */
    private List<String> permissions;

    /**
     * 过期时间
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @JsonDeserialize(using = LocalDateTimeDeserializer.class)
    @JsonSerialize(using = LocalDateTimeSerializer.class)
    private LocalDateTime expiresAt;

    /**
     * 创建时间
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @JsonDeserialize(using = LocalDateTimeDeserializer.class)
    @JsonSerialize(using = LocalDateTimeSerializer.class)
    private LocalDateTime createdAt;

    /**
     * 创建者用户名
     */
    private String createdBy;

    /**
     * 创建者 IP 地址
     */
    private String creatorIpAddress;

    /**
     * 密钥轮换周期（天数），0 表示不自动轮换
     */
    @Builder.Default
    private int rotationPeriodDays = 0;

    /**
     * 上次轮换时间
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @JsonDeserialize(using = LocalDateTimeDeserializer.class)
    @JsonSerialize(using = LocalDateTimeSerializer.class)
    private LocalDateTime lastRotatedAt;

    /**
     * 是否启用
     */
    private boolean enabled;

    /**
     * 元数据信息
     */
    private Map<String, Object> metadata;

    /**
     * 使用统计信息
     */
    private UsageStatistics usage;

    /**
     * 检查API Key是否已过期
     *
     * @return 是否过期
     */
    @JsonProperty("expired")
    public boolean isExpired() {
        return expiresAt != null && LocalDateTime.now().isAfter(expiresAt);
    }

    /**
     * 用于反序列化expired字段的setter方法（无实际作用，仅用于兼容旧数据）
     */
    @JsonProperty("expired")
    public void setExpired(final boolean expired) {
        // 仅用于反序列化，实际值由expiresAt字段计算得出
    }

    /**
     * 检查API Key是否有效（启用且未过期）
     *
     * @return 是否有效
     */
    @JsonIgnore
    public boolean isValid() {
        return enabled && !isExpired();
    }

    /**
     * 检查是否具有指定权限（大小写不敏感）
     *
     * @param permission 权限名称
     * @return 是否具有权限
     */
    public boolean hasPermission(final String permission) {
        if (permissions == null || permission == null) {
            return false;
        }

        return permissions.stream()
                .anyMatch(p -> p != null && p.equalsIgnoreCase(permission));
    }

    /**
     * 检查指定 IP 是否在白名单中
     * 如果白名单为空，则允许所有 IP
     *
     * @param ipAddress IP 地址
     * @return 是否允许
     */
    public boolean isIpAllowed(final String ipAddress) {
        if (allowedIpAddresses == null || allowedIpAddresses.isEmpty()) {
            return true;
        }
        return allowedIpAddresses.contains(ipAddress);
    }

    /**
     * 检查是否超过每日请求限制
     *
     * @return 是否超限
     */
    public boolean isDailyLimitExceeded() {
        if (dailyRequestLimit <= 0) {
            return false;
        }
        if (usage == null) {
            return false;
        }
        String today = LocalDateTime.now().toLocalDate().toString();
        Long todayCount = usage.getDailyUsage() != null ? usage.getDailyUsage().get(today) : null;
        return todayCount != null && todayCount.longValue() >= dailyRequestLimit;
    }

    /**
     * 检查是否超过每日 Token 使用限制
     *
     * @return 是否超限
     */
    public boolean isDailyTokenLimitExceeded() {
        if (dailyTokenLimit <= 0) {
            return false;
        }
        if (usage == null || usage.getDailyTokenUsage() == null) {
            return false;
        }
        String today = LocalDateTime.now().toLocalDate().toString();
        Long todayTokens = usage.getDailyTokenUsage().get(today);
        return todayTokens != null && todayTokens.longValue() >= dailyTokenLimit;
    }

    /**
     * 获取今日 Token 使用量
     *
     * @return 今日 Token 使用量
     */
    public long getTodayTokenUsage() {
        if (usage == null || usage.getDailyTokenUsage() == null) {
            return 0L;
        }
        String today = LocalDateTime.now().toLocalDate().toString();
        return usage.getDailyTokenUsage().getOrDefault(today, 0L);
    }

    /**
     * 获取今日请求数
     *
     * @return 今日请求数
     */
    public long getTodayRequestCount() {
        if (usage == null || usage.getDailyUsage() == null) {
            return 0L;
        }
        String today = LocalDateTime.now().toLocalDate().toString();
        return usage.getDailyUsage().getOrDefault(today, 0L);
    }

    /**
     * 检查配额使用是否达到告警阈值
     *
     * @return 是否需要告警
     */
    public boolean isQuotaAlertTriggered() {
        if (quotaAlertThreshold <= 0 || quotaAlertThreshold >= 1.0) {
            return false;
        }
        if (dailyRequestLimit > 0) {
            long todayCount = getTodayRequestCount();
            if (todayCount >= dailyRequestLimit * quotaAlertThreshold) {
                return true;
            }
        }
        if (dailyTokenLimit > 0) {
            long todayTokens = getTodayTokenUsage();
            if (todayTokens >= dailyTokenLimit * quotaAlertThreshold) {
                return true;
            }
        }
        return false;
    }

    /**
     * 检查是否需要进行密钥轮换
     * 如果设置了轮换周期，且距离上次轮换已超过周期天数，则需要轮换
     *
     * @return 是否需要轮换
     */
    @JsonIgnore
    public boolean needsRotation() {
        if (rotationPeriodDays <= 0) {
            return false; // 不自动轮换
        }
        if (lastRotatedAt == null) {
            // 从未轮换过，检查创建时间
            if (createdAt == null) {
                return false;
            }
            long daysSinceCreation = ChronoUnit.DAYS.between(createdAt, LocalDateTime.now());
            return daysSinceCreation >= rotationPeriodDays;
        }
        long daysSinceLastRotation = ChronoUnit.DAYS.between(lastRotatedAt, LocalDateTime.now());
        return daysSinceLastRotation >= rotationPeriodDays;
    }

    /**
     * 验证提供的 API Key 是否匹配存储的哈希值
     *
     * @param providedKey 用户提供的原始 API Key
     * @return 是否匹配
     */
    public boolean verifyKey(final String providedKey) {
        if (keyHash != null && !keyHash.isEmpty()) {
            return ApiKeyHashUtil.verifyApiKey(providedKey, keyHash);
        }
        // 兼容旧数据：如果只有 keyValue（明文存储），则直接比较
        // 这种情况应该尽快迁移到哈希存储
        if (keyValue != null && !keyValue.isEmpty()) {
            return keyValue.equals(providedKey);
        }
        return false;
    }

    /**
     * 创建一个不包含敏感信息的安全副本，用于API响应
     *
     * @return 安全的ApiKey副本
     */
    public ApiKey createSecureCopy() {
        return ApiKey.builder()
                .keyId(this.keyId)
                .keyValue(null)
                .keyHash(null)
                .keyPrefix(this.keyPrefix)
                .allowedIpAddresses(this.allowedIpAddresses)
                .dailyRequestLimit(this.dailyRequestLimit)
                .dailyTokenLimit(this.dailyTokenLimit)
                .rateLimitPerMinute(this.rateLimitPerMinute)
                .quotaAlertThreshold(this.quotaAlertThreshold)
                .description(this.description)
                .ownerId(this.ownerId)
                .ownerRole(this.ownerRole)
                .permissions(this.permissions)
                .expiresAt(this.expiresAt)
                .createdAt(this.createdAt)
                .createdBy(this.createdBy)
                .creatorIpAddress(this.creatorIpAddress)
                .rotationPeriodDays(this.rotationPeriodDays)
                .lastRotatedAt(this.lastRotatedAt)
                .enabled(this.enabled)
                .metadata(this.metadata)
                .usage(this.usage)
                .build();
    }

    /**
     * 创建一个包含keyValue的副本，仅用于创建API Key时的响应
     * 注意：keyValue 仅在此方法中返回一次，其他任何时候都不应返回
     *
     * @param originalKeyValue 原始的未哈希的 keyValue（仅在创建时可用）
     * @return 包含keyValue的ApiKey副本
     */
    public ApiKey createCreationResponse(final String originalKeyValue) {
        return ApiKey.builder()
                .keyId(this.keyId)
                .keyValue(originalKeyValue)
                .keyHash(null)
                .keyPrefix(this.keyPrefix)
                .allowedIpAddresses(this.allowedIpAddresses)
                .dailyRequestLimit(this.dailyRequestLimit)
                .dailyTokenLimit(this.dailyTokenLimit)
                .rateLimitPerMinute(this.rateLimitPerMinute)
                .quotaAlertThreshold(this.quotaAlertThreshold)
                .description(this.description)
                .permissions(this.permissions)
                .expiresAt(this.expiresAt)
                .createdAt(this.createdAt)
                .createdBy(this.createdBy)
                .creatorIpAddress(this.creatorIpAddress)
                .rotationPeriodDays(this.rotationPeriodDays)
                .lastRotatedAt(this.lastRotatedAt)
                .enabled(this.enabled)
                .metadata(this.metadata)
                .usage(this.usage)
                .build();
    }

    /**
     * 生成安全的API Key值
     *
     * @param prefix API Key前缀（如"sk-"）
     * @param length API Key长度（不包括前缀）
     * @return 生成的API Key
     */
    public static String generateApiKey(final String prefix, final int length) {
        // 使用局部变量避免修改 final 参数
        final String actualPrefix = prefix != null ? prefix : "sk-";
        final int actualLength = length > 0 ? length : 32;

        SecureRandom random = new SecureRandom();
        StringBuilder sb = new StringBuilder(actualPrefix);
        String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";

        for (int i = 0; i < length; i++) {
            sb.append(chars.charAt(random.nextInt(chars.length())));
        }

        return sb.toString();
    }

    /**
     * 生成默认的API Key值（前缀为"sk-"，长度为32）
     *
     * @return 生成的API Key
     */
    public static String generateApiKey() {
        return generateApiKey("sk-", 32);
    }
}