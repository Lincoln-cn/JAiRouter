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
 * 配置实体类
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table("config_data")
public class ConfigEntity {

    @Id
    private Long id;

    @Column("config_key")
    private String configKey;

    @Column("config_value")
    private String configValue;

    @Column("version")
    private Integer version;

    @Column("created_at")
    private LocalDateTime createdAt;

    @Column("updated_at")
    private LocalDateTime updatedAt;

    @Column("is_latest")
    private Boolean isLatest;
}
