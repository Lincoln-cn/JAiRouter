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
 * 配置版本表实体 - 存储每个版本的完整配置快照（JSON 格式）
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table("config_version")
public class ConfigVersionEntity {

    @Id
    private Long id;

    @Column("config_key")
    private String configKey;

    @Column("version")
    private Integer version;

    @Column("config_data")
    private String configData; // JSON 格式的完整配置数据

    @Column("created_at")
    @CreatedDate
    private LocalDateTime createdAt;

    @Column("created_by")
    private String createdBy;

    @Column("description")
    private String description;

    @Column("change_type")
    private String changeType; // CREATE, UPDATE, DELETE, ROLLBACK

    @Column("is_current")
    private Boolean isCurrent;

    @Column("archive_path")
    private String archivePath;

    @Column("is_archived")
    private Boolean isArchived;
}
