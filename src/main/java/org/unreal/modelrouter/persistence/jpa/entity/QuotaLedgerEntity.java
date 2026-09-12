package org.unreal.modelrouter.persistence.jpa.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 配额账本实体（v3.1 PR-1）。
 *
 * <p>一行 = 一个维度在指定窗口内的一份用量快照。维度列（tenant_id / api_key_id / user_id /
 * service_type / model）声明为 NOT NULL，缺省维度写入空串哨兵值（见
 * {@code org.unreal.modelrouter.auth.security.quota.QuotaDimension#SENTINEL}）。</p>
 *
 * <p>唯一约束覆盖「五个维度列 + 窗口类型 + 窗口起点」共七列，保证同一维度在同一窗口内只有一行，
 * 累加落库（{@code UPDATE ... SET request_count = request_count + :delta}）才不会重复计数。</p>
 *
 * <p>该表由 Hibernate {@code ddl-auto} 自动创建（与 {@link ApiCallHistoryEntity} 一致），
 * 无需在 {@code CompatibilitySchemaMigrator} 中登记。</p>
 *
 * <p>列长度取舍：五个维度列合计 625 字符，可使七列唯一索引在 utf8mb4 下不超过
 * PostgreSQL btree 的 2704 字节上限（H2 无此限制，但保留跨库可移植性）。</p>
 *
 * @author JAiRouter Team
 * @since 3.1.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "quota_ledger",
    uniqueConstraints = @UniqueConstraint(
        name = "uk_quota_ledger_dimension_window",
        columnNames = {"tenant_id", "api_key_id", "user_id", "service_type", "model",
            "window_type", "window_start"}),
    indexes = {
        @Index(name = "idx_quota_ledger_api_key", columnList = "api_key_id"),
        @Index(name = "idx_quota_ledger_window", columnList = "window_type, window_start")
    })
public class QuotaLedgerEntity {

    /**
     * 主键
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * 租户 ID（缺省为空串哨兵值）
     */
    @Column(name = "tenant_id", nullable = false, length = 64)
    @Builder.Default
    private String tenantId = "";

    /**
     * API Key ID（缺省为空串哨兵值）
     */
    @Column(name = "api_key_id", nullable = false, length = 128)
    @Builder.Default
    private String apiKeyId = "";

    /**
     * 用户 ID（缺省为空串哨兵值）
     */
    @Column(name = "user_id", nullable = false, length = 128)
    @Builder.Default
    private String userId = "";

    /**
     * 服务类型（chat / embedding / rerank / tts / stt，缺省为空串哨兵值）
     */
    @Column(name = "service_type", nullable = false, length = 50)
    @Builder.Default
    private String serviceType = "";

    /**
     * 模型名称（缺省为空串哨兵值）
     */
    @Column(name = "model", nullable = false, length = 255)
    @Builder.Default
    private String model = "";

    /**
     * 窗口类型（MINUTE / HOUR / DAY / MONTH）
     */
    @Column(name = "window_type", nullable = false, length = 20)
    private String windowType;

    /**
     * 窗口起点（由窗口类型对齐截断得到）
     */
    @Column(name = "window_start", nullable = false)
    private LocalDateTime windowStart;

    /**
     * 窗口内累计请求数
     */
    @Column(name = "request_count", nullable = false)
    @Builder.Default
    private Long requestCount = 0L;

    /**
     * 窗口内累计 token 数
     */
    @Column(name = "token_count", nullable = false)
    @Builder.Default
    private Long tokenCount = 0L;

    /**
     * 最近一次更新时间
     */
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    /**
     * 落库前补齐时间字段与维度哨兵值。
     */
    @PrePersist
    public void prePersist() {
        if (updatedAt == null) {
            updatedAt = LocalDateTime.now();
        }
        if (tenantId == null) {
            tenantId = "";
        }
        if (apiKeyId == null) {
            apiKeyId = "";
        }
        if (userId == null) {
            userId = "";
        }
        if (serviceType == null) {
            serviceType = "";
        }
        if (model == null) {
            model = "";
        }
    }
}
