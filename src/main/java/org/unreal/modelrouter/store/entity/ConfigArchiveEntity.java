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
 * 配置归档表实体 - 记录归档文件信息
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table("config_archive")
public class ConfigArchiveEntity {

    @Id
    private Long id;

    @Column("config_key")
    private String configKey;

    @Column("archive_path")
    private String archivePath;

    @Column("archive_type")
    private String archiveType; // ZIP, TAR.GZ

    @Column("version_range_start")
    private Integer versionRangeStart;

    @Column("version_range_end")
    private Integer versionRangeEnd;

    @Column("archived_at")
    @CreatedDate
    private LocalDateTime archivedAt;

    @Column("archived_by")
    private String archivedBy;

    @Column("file_size_bytes")
    private Long fileSizeBytes;

    @Column("checksum")
    private String checksum; // SHA-256 校验和

    @Column("retention_days")
    private Integer retentionDays;

    @Column("expiry_date")
    private LocalDateTime expiryDate;

    @Column("status")
    private String status; // ACTIVE, EXPIRED, DELETED
}
