package org.unreal.modelrouter.store.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import jakarta.persistence.*;

import java.time.LocalDateTime;

/**
 * 配置归档表实体 - 记录归档文件信息
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "config_archive")
public class ConfigArchiveEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "config_key")
    private String configKey;

    @Column(name = "archive_path")
    private String archivePath;

    @Column(name = "archive_type")
    private String archiveType; // ZIP, TAR.GZ

    @Column(name = "version_range_start")
    private Integer versionRangeStart;

    @Column(name = "version_range_end")
    private Integer versionRangeEnd;

    @Column(name = "archived_at")
    @CreatedDate
    private LocalDateTime archivedAt;

    @Column(name = "archived_by")
    private String archivedBy;

    @Column(name = "file_size_bytes")
    private Long fileSizeBytes;

    @Column(name = "checksum")
    private String checksum; // SHA-256 校验和

    @Column(name = "retention_days")
    private Integer retentionDays;

    @Column(name = "expiry_date")
    private LocalDateTime expiryDate;

    @Column(name = "status")
    private String status; // ACTIVE, EXPIRED, DELETED
}
