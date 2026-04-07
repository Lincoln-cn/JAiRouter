package org.unreal.modelrouter.store.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import jakarta.persistence.*;

import java.time.LocalDateTime;

/**
 * 配置版本表实体 - 存储每个版本的完整配置快照（JSON 格式）
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "config_version")
public class ConfigVersionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "config_key")
    private String configKey;

    @Column(name = "version")
    private Integer version;

    @Column(name = "config_data")
    private String configData; // JSON 格式的完整配置数据

    @Column(name = "created_at")
    @CreatedDate
    private LocalDateTime createdAt;

    @Column(name = "created_by")
    private String createdBy;

    @Column(name = "description")
    private String description;

    @Column(name = "change_type")
    private String changeType; // CREATE, UPDATE, DELETE, ROLLBACK

    @Column(name = "is_current")
    private Boolean isCurrent;

    @Column(name = "archive_path")
    private String archivePath;

    @Column(name = "is_archived")
    private Boolean isArchived;
}
