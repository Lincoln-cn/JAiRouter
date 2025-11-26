package org.unreal.modelrouter.store.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.LocalDateTime;

/**
 * 安全审计实体类
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table("security_audit")
public class SecurityAuditEntity {

    @Id
    private Long id;

    @Column("event_id")
    private String eventId;

    @Column("event_type")
    private String eventType;

    @Column("user_id")
    private String userId;

    @Column("client_ip")
    private String clientIp;

    @Column("user_agent")
    private String userAgent;

    @Column("timestamp")
    private LocalDateTime timestamp;

    @Column("resource")
    private String resource;

    @Column("action")
    private String action;

    @Column("success")
    private Boolean success;

    @Column("failure_reason")
    private String failureReason;

    @Column("additional_data")
    private String additionalData; // JSON 格式存储

    @Column("request_id")
    private String requestId;

    @Column("session_id")
    private String sessionId;
}
