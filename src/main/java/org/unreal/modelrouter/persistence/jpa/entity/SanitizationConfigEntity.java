package org.unreal.modelrouter.persistence.jpa.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 脱敏配置快照实体（TA2 / v3.4.0）。
 *
 * <p>一行 = 一个 scope（{@code request} / {@code response}）当前生效的脱敏配置。
 * 规则本身**不落库**：{@code DefaultSanitizationService} 在启动时由配置确定性派生规则
 * （sensitive-words → SENSITIVE_WORD、pii-patterns → PII_PATTERN），所以只要配置一致，
 * 重启后规则集与规则 ID 也一致。</p>
 *
 * <p>主键是自然键 {@code config_scope}（取值见
 * {@code org.unreal.modelrouter.auth.sanitization.persistence.SanitizationConfigStore} 的
 * {@code SCOPE_REQUEST} / {@code SCOPE_RESPONSE}），天然保证「每个 scope 最多一行」，
 * 不需要自增 ID，也就不存在重启后 ID 复用的问题。</p>
 *
 * <p>两个 scope 的字段集不同（request 有 {@code whitelist_users}，response 有
 * {@code preserve_json_structure}），因此用**按 scope 各建一行、差异列可空**的形态：
 * 空值表示「该 scope 没有这个概念」，读取侧按各自默认值处理。</p>
 *
 * <p>列表列用 TEXT 存 JSON（由 {@code SanitizationConfigStore} 用项目既有
 * {@code ObjectMapper} 序列化），不用 json / jsonb：快照是运维排障对象，
 * 纯文本在任何 SQL 客户端里都可直接查看与手工修复，也避免 Hibernate validate
 * 在两种方言上对 JSON 类型的不同期待。</p>
 *
 * @author JAiRouter Team
 * @since 3.4.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "sanitization_config")
public class SanitizationConfigEntity {

    /**
     * 配置作用域（request / response）
     */
    @Id
    @Column(name = "config_scope", nullable = false, length = 16)
    private String scope;

    /**
     * 该 scope 是否启用脱敏
     */
    @Column(name = "enabled", nullable = false)
    private boolean enabled;

    /**
     * 敏感词列表（JSON 数组文本）
     */
    @Column(name = "sensitive_words", nullable = false, columnDefinition = "TEXT")
    private String sensitiveWords;

    /**
     * PII 正则列表（JSON 数组文本）
     */
    @Column(name = "pii_patterns", nullable = false, columnDefinition = "TEXT")
    private String piiPatterns;

    /**
     * 掩码字符
     */
    @Column(name = "masking_char", nullable = false, length = 5)
    private String maskingChar;

    /**
     * 白名单用户列表（JSON 数组文本，仅 request scope）
     */
    @Column(name = "whitelist_users", columnDefinition = "TEXT")
    private String whitelistUsers;

    /**
     * 是否保留 JSON 结构（仅 response scope；null = 沿用默认值 true）
     */
    @Column(name = "preserve_json_structure")
    private Boolean preserveJsonStructure;

    /**
     * 是否记录脱敏操作
     */
    @Column(name = "log_sanitization", nullable = false)
    private boolean logSanitization;

    /**
     * 脱敏失败时是否中断请求处理
     */
    @Column(name = "fail_on_error", nullable = false)
    private boolean failOnError;

    /**
     * 最后一次保存时间
     */
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    /**
     * 最后一次保存的操作者（无认证上下文时为 system）
     */
    @Column(name = "updated_by", length = 255)
    private String updatedBy;
}
