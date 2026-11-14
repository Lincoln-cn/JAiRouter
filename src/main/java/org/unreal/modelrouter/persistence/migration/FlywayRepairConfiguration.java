package org.unreal.modelrouter.persistence.migration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.flyway.autoconfigure.FlywayMigrationStrategy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 启动时先 repair 再 migrate（升级用逃生舱）。
 *
 * <p><b>为什么需要它</b>：Flyway 按校验和判断已发布的迁移脚本有没有被改动。一旦改动发生在
 * 发布之后（例如 v3.8.6 把 {@code config_sequences} 写进了 {@code V1__baseline.sql}，v3.8.7 又
 * 把它移回 {@code V12__config_sequences.sql}），用旧版本建过库的部署会在升级时直接失败：
 * {@code Validate failed: Migration checksum mismatch for migration version 1}，进程起不来。</p>
 *
 * <p>标准解法是先用 Flyway CLI 执行 {@code repair}，但很多客户环境没有装它。Spring Boot 的
 * {@code spring.flyway.*} 属性里<b>没有</b> {@code repair-on-migrate}（{@code FlywayProperties}
 * 只有 {@code baselineOnMigrate} / {@code validateOnMigrate}），所以本类用
 * {@link FlywayMigrationStrategy} 在 {@code migrate()} 之前插入一次 {@code repair()}</p>
 *
 * <p><b>默认关闭</b>，必须显式传参才生效：</p>
 * <pre>{@code
 * java -jar model-router.jar --jairouter.migration.repair-on-start=true
 * }</pre>
 *
 * <p>repair 只重算 {@code flyway_schema_history} 里的校验和，不重跑迁移、不改业务数据，
 * 所以用它升级是安全的；但每次启动都 repair 会掩盖「迁移文件被误改」这类问题，
 * 因此不要把它固化进启动脚本——修完一次就应移除。</p>
 */
@Configuration
@ConditionalOnProperty(prefix = "jairouter.migration", name = "repair-on-start", havingValue = "true")
public class FlywayRepairConfiguration {

    private static final Logger log = LoggerFactory.getLogger(FlywayRepairConfiguration.class);

    @Bean
    public FlywayMigrationStrategy repairBeforeMigrate() {
        return flyway -> {
            log.warn("jairouter.migration.repair-on-start=true：先执行 Flyway repair 再迁移。"
                    + "该开关只用于修复迁移脚本校验和漂移，修完请移除。");
            flyway.repair();
            flyway.migrate();
        };
    }
}
