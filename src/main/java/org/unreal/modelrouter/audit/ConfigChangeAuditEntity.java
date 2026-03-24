/**
 * 配置变更审计实体类
 */
package org.unreal.modelrouter.audit;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.LocalDateTime;

/**
 * 配置变更审计实体
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table("config_change_audit_log")
public class ConfigChangeAuditEntity {

    @Id
    private Long id;

    @Column("event_id")
    private String eventId;

    @Column("config_key")
    private String configKey;

    @Column("config_type")
    private String configType;

    @Column("operation")
    private String operation;

    @Column("operator")
    private String operator;

    @Column("operator_ip")
    private String operatorIp;

    @Column("old_value")
    private String oldValueJson;

    @Column("new_value")
    private String newValueJson;

    @Column("change_summary")
    private String changeSummary;

    @Column("reason")
    private String reason;

    @Column("timestamp")
    private LocalDateTime timestamp;

    @Column("trace_id")
    private String traceId;
}
