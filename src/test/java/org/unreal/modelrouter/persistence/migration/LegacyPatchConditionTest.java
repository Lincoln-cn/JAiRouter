package org.unreal.modelrouter.persistence.migration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.unreal.modelrouter.persistence.jpa.DatabaseMigrationService;

import javax.sql.DataSource;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;

/**
 * 启动期兼容补丁的注册条件（issue #192）。
 *
 * <p>不变量：这两个 Java 补丁**只在没有版本化迁移时**存在。PostgreSQL 路径由 Flyway
 * （{@code spring.flyway.enabled=true}）接管，同一批语义已由
 * {@code db/migration/V2__legacy_convergence.sql} 表达 —— 此时两个组件**不应被注册**，
 * 否则同一方言上就有两处 schema 权威。H2 路径 Flyway 关闭，补丁照旧兜底。</p>
 *
 * <p>{@code matchIfMissing = true} 那一条也要钉住：属性缺省时必须**保留**补丁，
 * 免得将来某个装配路径漏设属性时静默丢掉 H2 的兜底网。</p>
 */
@DisplayName("启动期兼容补丁的注册条件（issue #192）")
class LegacyPatchConditionTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withBean(DataSource.class, () -> mock(DataSource.class))
            .withBean(JdbcTemplate.class, () -> mock(JdbcTemplate.class))
            .withUserConfiguration(CompatibilitySchemaMigrator.class, DatabaseMigrationService.class);

    @Test
    @DisplayName("Flyway 打开（PostgreSQL 路径）：两个补丁组件都不注册")
    void patchesAreAbsentWhenFlywayEnabled() {
        runner.withPropertyValues("spring.flyway.enabled=true").run(context -> {
            assertNull(context.getBeanProvider(CompatibilitySchemaMigrator.class).getIfAvailable(),
                    "Flyway 已接管 schema 时不应再注册 CompatibilitySchemaMigrator");
            assertNull(context.getBeanProvider(DatabaseMigrationService.class).getIfAvailable(),
                    "Flyway 已接管 schema 时不应再注册 DatabaseMigrationService");
        });
    }

    @Test
    @DisplayName("Flyway 关闭（H2 路径）：两个补丁组件都注册")
    void patchesArePresentWhenFlywayDisabled() {
        runner.withPropertyValues("spring.flyway.enabled=false").run(context -> {
            assertNotNull(context.getBeanProvider(CompatibilitySchemaMigrator.class).getIfAvailable());
            assertNotNull(context.getBeanProvider(DatabaseMigrationService.class).getIfAvailable());
        });
    }

    @Test
    @DisplayName("属性缺省：按「没有版本化迁移」处理，保留补丁兜底")
    void patchesArePresentWhenPropertyMissing() {
        runner.run(context -> {
            assertNotNull(context.getBeanProvider(CompatibilitySchemaMigrator.class).getIfAvailable());
            assertNotNull(context.getBeanProvider(DatabaseMigrationService.class).getIfAvailable());
        });
    }
}
