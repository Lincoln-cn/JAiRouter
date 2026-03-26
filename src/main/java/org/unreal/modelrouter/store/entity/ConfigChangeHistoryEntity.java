package org.unreal.modelrouter.store.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.LocalDateTime;

/**
 * 配置变更历史表实体 - 记录所有配置变更操作（审计日志）
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table("config_change_history")
public class ConfigChangeHistoryEntity {

    @Id
    private Long id;

    @Column("config_key")
    private String configKey;

    @Column("operation_type")
    private String operationType; // CREATE, UPDATE, DELETE, APPLY_VERSION

    @Column("target_type")
    private String targetType; // CONFIG, SERVICE, INSTANCE

    @Column("target_id")
    private String targetId;

    @Column("old_value")
    private String oldValue; // JSON 格式

    @Column("new_value")
    private String newValue; // JSON 格式

    @Column("changed_by")
    private String changedBy;

    @Column("changed_at")
    @CreatedDate
    private LocalDateTime changedAt;

    @Column("description")
    private String description;

    @Column("request_id")
    private String requestId;

    @Column("client_ip")
    private String clientIp;
}
