package org.unreal.modelrouter.store.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.LocalDateTime;

/**
 * 配置主表实体 - 存储配置的整体元信息和版本控制
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table("config_main")
public class ConfigMainEntity {

    @Id
    private Long id;

    @Column("config_key")
    private String configKey;

    @Column("current_version")
    private Integer currentVersion;

    @Column("initial_version")
    private Integer initialVersion;

    @Column("created_at")
    @CreatedDate
    private LocalDateTime createdAt;

    @Column("updated_at")
    @LastModifiedDate
    private LocalDateTime updatedAt;

    @Column("created_by")
    private String createdBy;

    @Column("updated_by")
    private String updatedBy;

    @Column("description")
    private String description;
}
