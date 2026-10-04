package org.unreal.modelrouter.persistence.migration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 兼容迁移器的列类型方言选择（issue #190）。
 *
 * <p>「同一逻辑列按库的来历物理类型不同」是 Flyway + {@code ddl-auto: validate} 的硬阻塞：
 * 实体此前硬编码 {@code columnDefinition = "JSON"}（新库在 PG 上建成 {@code json}），
 * 而旧库升级路径由兼容迁移器补成 CLOB（PG 上是 {@code TEXT}）。现在实体不再硬编码类型、
 * 迁移器按方言产出 JSON 类型并对旧列收敛，这里把两个映射函数钉住。
 */
@DisplayName("兼容迁移器的列类型方言选择（issue #190）")
class CompatibilitySchemaMigratorTypeTest {

    @Test
    @DisplayName("JSON 列：PostgreSQL 用 jsonb，H2 / MySQL 用 json")
    void jsonTypeByProduct() {
        assertEquals("jsonb", CompatibilitySchemaMigrator.jsonTypeFor("postgresql"));
        assertEquals("json", CompatibilitySchemaMigrator.jsonTypeFor("h2"));
        assertEquals("json", CompatibilitySchemaMigrator.jsonTypeFor("mysql"));
    }

    @Test
    @DisplayName("CLOB 标记的等价类型：PG=text / MySQL=longtext / H2=clob")
    void clobTypeByProduct() {
        assertEquals("TEXT", CompatibilitySchemaMigrator.clobTypeFor("postgresql"));
        assertEquals("LONGTEXT", CompatibilitySchemaMigrator.clobTypeFor("mariadb"));
        assertEquals("CLOB", CompatibilitySchemaMigrator.clobTypeFor("h2"));
    }
}
