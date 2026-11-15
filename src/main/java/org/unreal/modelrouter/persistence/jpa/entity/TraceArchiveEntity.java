package org.unreal.modelrouter.persistence.jpa.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 异常链路归档实体（#1155）。
 *
 * <p>只归档「链路上任一 span 失败」的 trace：普通链路留在内存滚动区，异常链路落库，
 * 避免被控制台的高频请求挤出内存后就彻底消失。{@code spans_json} 保存完整 span 列表，
 * 供事后打开链路详情；{@code created_at} 是清理依据。</p>
 *
 * @author JAiRouter Team
 * @since 3.8.8
 */
@Data
@Entity
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "trace_archive",
        indexes = @Index(name = "idx_trace_archive_created_at", columnList = "created_at"))
public class TraceArchiveEntity {

    @Id
    @Column(name = "trace_id", length = 64, nullable = false)
    private String traceId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "service", length = 64)
    private String service;

    @Column(name = "operation", length = 255)
    private String operation;

    @Column(name = "duration_ms")
    private Double durationMs;

    @Column(name = "status_code", length = 16)
    private String statusCode;

    @Column(name = "error_count", nullable = false)
    private Integer errorCount;

    @Column(name = "spans_json", nullable = false, columnDefinition = "TEXT")
    private String spansJson;
}
