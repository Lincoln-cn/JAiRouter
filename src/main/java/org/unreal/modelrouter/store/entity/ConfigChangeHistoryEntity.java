package org.unreal.modelrouter.store.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import jakarta.persistence.*;

import java.time.LocalDateTime;

/**
 * 配置变更历史表实体 - 记录所有配置变更操作（审计日志）
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "config_change_history")
public class ConfigChangeHistoryEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "config_key")
    private String configKey;

    @Column(name = "operation_type")
    private String operationType; // CREATE, UPDATE, DELETE, APPLY_VERSION

    @Column(name = "target_type")
    private String targetType; // CONFIG, SERVICE, INSTANCE

    @Column(name = "target_id")
    private String targetId;

    @Column(name = "old_value")
    private String oldValue; // JSON 格式

    @Column(name = "new_value")
    private String newValue; // JSON 格式

    @Column(name = "changed_by")
    private String changedBy;

    @Column(name = "changed_at")
    @CreatedDate
    private LocalDateTime changedAt;

    @Column(name = "description")
    private String description;

    @Column(name = "request_id")
    private String requestId;

    @Column(name = "client_ip")
    private String clientIp;
}
