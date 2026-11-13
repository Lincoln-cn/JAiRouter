package org.unreal.modelrouter.persistence.jpa.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 配置版本号分配器实体（#1135）。
 *
 * <p>每个配置键一行，{@code next_version} 表示该键已经分配到的最大版本号。分配时在事务内
 * 对该行执行 {@code next_version = next_version + 1} 再读回，由数据库行锁保证跨副本并发
 * 分配不会拿到相同版本号。</p>
 *
 * @author JAiRouter Team
 * @since 3.8.6
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "config_sequences")
public class ConfigSequenceEntity {

    /** 配置键；与 {@code config_data.config_key} 同一取值空间。 */
    @Id
    @Column(name = "config_key", nullable = false, length = 255)
    private String configKey;

    /** 该配置键已分配到的最大版本号。 */
    @Column(name = "next_version", nullable = false)
    private Long nextVersion;
}
