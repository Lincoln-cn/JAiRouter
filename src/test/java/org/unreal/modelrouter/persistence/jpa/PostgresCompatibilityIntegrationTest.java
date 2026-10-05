package org.unreal.modelrouter.persistence.jpa;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.metamodel.EntityType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.unreal.modelrouter.persistence.jpa.entity.ApiCallHistoryEntity;
import org.unreal.modelrouter.persistence.jpa.entity.ConfigVersionHistoryEntity;
import org.unreal.modelrouter.persistence.jpa.entity.ExceptionEventEntity;
import org.unreal.modelrouter.persistence.jpa.entity.QuotaLedgerEntity;
import org.unreal.modelrouter.persistence.jpa.entity.ServiceInstanceEntity;
import org.unreal.modelrouter.persistence.jpa.repository.ApiCallHistoryRepository;
import org.unreal.modelrouter.persistence.jpa.repository.ConfigVersionHistoryRepository;
import org.unreal.modelrouter.persistence.jpa.repository.ExceptionEventRepository;
import org.unreal.modelrouter.persistence.jpa.repository.QuotaLedgerRepository;
import org.unreal.modelrouter.persistence.migration.CompatibilitySchemaMigrator;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * PostgreSQL 跨库兼容集成测试（issue #160 阶段 B）。
 *
 * <p>用**真实 PostgreSQL** 验证从 H2 迁到外部共享库后可用。为什么必须用真 PG 而不是
 * H2 的 {@code MODE=PostgreSQL}：H2 的 PG 兼容模式只覆盖语法子集，**验不出**方言函数、
 * 列类型、DDL 生成这些真实差异——而那正是本 issue 要消除的 6 处断点所在。</p>
 *
 * <p>覆盖 #160 列出的跨库断点：
 * <ol>
 *   <li>全部实体建表（原 {@code columnDefinition="CLOB"} 在 PG 上会直接失败）</li>
 *   <li>{@code countByHour} 的 {@code EXTRACT(HOUR FROM …)}（原 {@code FUNCTION('HOUR', …)} PG 无此函数）</li>
 *   <li>{@code TEXT} 列大文本（原 CLOB 改动）</li>
 *   <li>{@code tags}/{@code headers} 的 JSON 列（原 {@code columnDefinition="JSON"} 的类型分歧点）</li>
 *   <li>IDENTITY 主键在 PG 上可用</li>
 *   <li>{@code QuotaLedgerRepository.accumulate} 的 {@code @Modifying} 原子自增</li>
 *   <li>HQL 扩展 {@code LIMIT :limit}</li>
 * </ol>
 *
 * <p><b>门控方式与仓库既有的 {@code QuotaDistributedRedisIntegrationTest} 一致</b>：
 * 仅在 {@code PG_TEST=true} 时执行，连接参数由环境变量提供，避免引入对 Docker 的隐式依赖。
 * 未设置该变量的环境（含本地离线跑）整类跳过，不会让门闩变红。</p>
 *
 * @author JAiRouter Team
 * @since 2.9.9
 */
@EnabledIfEnvironmentVariable(named = "PG_TEST", matches = "true")
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@DisplayName("PostgreSQL 跨库兼容集成测试（PG_TEST=true 时执行）")
class PostgresCompatibilityIntegrationTest {

    private static final String JDBC_URL =
            System.getenv().getOrDefault("PG_TEST_URL", "jdbc:postgresql://127.0.0.1:15432/postgres");
    private static final String USERNAME =
            System.getenv().getOrDefault("PG_TEST_USERNAME", "postgres");
    private static final String PASSWORD =
            System.getenv().getOrDefault("PG_TEST_PASSWORD", "postgres");

    @DynamicPropertySource
    static void datasourceProperties(final DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> JDBC_URL);
        registry.add("spring.datasource.username", () -> USERNAME);
        registry.add("spring.datasource.password", () -> PASSWORD);
        // application-test.yml 里硬编码了 org.h2.Driver，必须一并覆盖，否则 PG URL 会配错驱动
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
    }

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ApiCallHistoryRepository apiCallHistoryRepository;

    @Autowired
    private ExceptionEventRepository exceptionEventRepository;

    @Autowired
    private ConfigVersionHistoryRepository configVersionHistoryRepository;

    @Autowired
    private QuotaLedgerRepository quotaLedgerRepository;

    // ==================== 1. 全部实体建表 ====================

    @Test
    @DisplayName("全部实体在 PostgreSQL 上建表成功（元模型驱动，新增实体自动纳入）")
    void allEntityTables_existOnPostgres() {
        Set<String> expected = new TreeSet<>();
        for (EntityType<?> entityType : entityManagerFactory.getMetamodel().getEntities()) {
            Table table = entityType.getJavaType().getAnnotation(Table.class);
            String name = (table != null && !table.name().isEmpty()) ? table.name() : entityType.getName();
            expected.add(name.toLowerCase());
        }

        assertTrue(expected.size() >= 20,
                "应至少有 20 个实体，实际 " + expected.size() + "：" + expected);

        List<String> missing = new ArrayList<>();
        for (String tableName : expected) {
            Integer count = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM information_schema.tables "
                            + "WHERE table_schema = 'public' AND lower(table_name) = ?",
                    Integer.class, tableName);
            if (count == null || count == 0) {
                missing.add(tableName);
            }
        }
        assertTrue(missing.isEmpty(), "PostgreSQL 上缺失这些表：" + missing);
        System.out.println("[PG] 共建成 " + expected.size() + " 张表：" + expected);
    }

    // ==================== 1b. 索引/约束名全局唯一（PG 的 schema 级命名空间）====================

    @Test
    @DisplayName("索引与唯一约束名跨表全局唯一（PostgreSQL 的 relation 名是 schema 级）")
    void indexAndConstraintNames_areGloballyUnique() {
        Map<String, List<String>> owners = new HashMap<>();
        for (EntityType<?> entityType : entityManagerFactory.getMetamodel().getEntities()) {
            Table table = entityType.getJavaType().getAnnotation(Table.class);
            if (table == null) {
                continue;
            }
            for (Index index : table.indexes()) {
                owners.computeIfAbsent(index.name(), key -> new ArrayList<>()).add(table.name());
            }
            for (UniqueConstraint constraint : table.uniqueConstraints()) {
                owners.computeIfAbsent(constraint.name(), key -> new ArrayList<>()).add(table.name());
            }
        }

        assertFalse(owners.isEmpty(), "应至少收集到一个索引/约束声明");

        List<String> duplicates = new ArrayList<>();
        owners.forEach((name, tables) -> {
            if (tables.size() > 1) {
                duplicates.add(name + " -> " + tables);
            }
        });
        duplicates.sort(null);

        assertTrue(duplicates.isEmpty(),
                "以下索引/约束名在多个表上重复。PostgreSQL 的 relation 名（含索引）是 schema 级"
                        + "命名空间（H2/MySQL 为表级），重名会使 CREATE INDEX 失败而 Hibernate 只记 WARN，"
                        + "索引被静默丢失：" + duplicates);
        System.out.println("[PG] 索引/约束名全局唯一，共 " + owners.size() + " 个");
    }

    @Test
    @DisplayName("实体声明的每个索引在 PostgreSQL 上真实存在（证明没有被静默丢弃）")
    void declaredIndexes_existOnPostgres() {
        Set<String> declared = new TreeSet<>();
        for (EntityType<?> entityType : entityManagerFactory.getMetamodel().getEntities()) {
            Table table = entityType.getJavaType().getAnnotation(Table.class);
            if (table == null) {
                continue;
            }
            for (Index index : table.indexes()) {
                declared.add(index.name());
            }
        }
        assertFalse(declared.isEmpty(), "应至少收集到一个 @Index 声明");

        List<String> missing = new ArrayList<>();
        for (String indexName : declared) {
            Integer count = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM pg_indexes WHERE schemaname = 'public' AND indexname = ?",
                    Integer.class, indexName);
            if (count == null || count == 0) {
                missing.add(indexName);
            }
        }
        assertTrue(missing.isEmpty(), "PostgreSQL 上缺失这些已声明的索引（被静默丢弃）：" + missing);
        System.out.println("[PG] 声明的 " + declared.size() + " 个索引全部存在");
    }

    // ==================== 2. EXTRACT(HOUR FROM …)（原 FUNCTION('HOUR', …)）====================

    @Test
    @DisplayName("countByHour 的 EXTRACT(HOUR FROM …) 在 PostgreSQL 上可执行并正确分组")
    void countByHour_executesOnPostgres() {
        LocalDateTime base = LocalDateTime.of(2025, 6, 15, 10, 0, 0);
        saveExceptionEvent("pg-h-1", base.withHour(10).withMinute(5));
        saveExceptionEvent("pg-h-2", base.withHour(10).withMinute(25));
        saveExceptionEvent("pg-h-3", base.withHour(11).withMinute(0));
        saveExceptionEvent("pg-h-4", base.withHour(14).withMinute(45));

        LocalDateTime start = base.withHour(0);
        LocalDateTime end = base.withHour(23).withMinute(59).withSecond(59);

        List<Object[]> results = exceptionEventRepository.countByHour(start, end);

        assertNotNull(results);
        assertEquals(3, results.size(), "应有 3 个小时分组（10/11/14）");
        assertEquals(10, ((Number) results.get(0)[0]).intValue());
        assertEquals(2L, ((Number) results.get(0)[1]).longValue());
        assertEquals(11, ((Number) results.get(1)[0]).intValue());
        assertEquals(1L, ((Number) results.get(1)[1]).longValue());
        assertEquals(14, ((Number) results.get(2)[0]).intValue());
        assertEquals(1L, ((Number) results.get(2)[1]).longValue());
    }

    // ==================== 3. TEXT 大文本 ====================

    @Test
    @DisplayName("加密请求/响应体（TEXT 列）在 PostgreSQL 上可存取大文本")
    void textColumns_largeTextRoundTripOnPostgres() {
        String largeText = "Z".repeat(20_000);

        ApiCallHistoryEntity saved = apiCallHistoryRepository.saveAndFlush(
                ApiCallHistoryEntity.builder()
                        .traceId("pg-trace-001")
                        .requestId("pg-req-001")
                        .requestMethod("POST")
                        .requestPath("/v1/chat/completions")
                        .serviceType("chat")
                        .modelName("pg-test-model")
                        .promptTokens(0L)
                        .completionTokens(0L)
                        .totalTokens(0L)
                        .isSuccess(true)
                        .rateLimited(false)
                        .circuitBroken(false)
                        .requestBodyEncrypted(largeText)
                        .responseBodyEncrypted(largeText + "-resp")
                        .build());

        entityManager.clear();

        ApiCallHistoryEntity loaded = apiCallHistoryRepository.findById(saved.getId()).orElseThrow();
        assertEquals(largeText, loaded.getRequestBodyEncrypted());
        assertEquals(largeText + "-resp", loaded.getResponseBodyEncrypted());
    }

    // ==================== 4. JSON 列（#167 延后到阶段 B 的断点 5）====================

    @Test
    @DisplayName("service_instance 的 tags/headers（JSON 映射）在 PostgreSQL 上可存取，并输出实际列类型")
    void jsonColumns_roundTripOnPostgres_andReportTypes() {
        List<Map<String, Object>> cols = jdbcTemplate.queryForList(
                "SELECT column_name, data_type, udt_name FROM information_schema.columns "
                        + "WHERE table_schema = 'public' AND lower(table_name) = 'service_instance' "
                        + "AND lower(column_name) IN ('tags', 'headers')");
        assertEquals(2, cols.size(), "应查到 tags 与 headers 两列");
        for (Map<String, Object> col : cols) {
            System.out.println("[PG 取证] service_instance." + col.get("column_name")
                    + " data_type=" + col.get("data_type") + " udt_name=" + col.get("udt_name"));
        }

        ServiceInstanceEntity entity = ServiceInstanceEntity.builder()
                .serviceConfigId(1L)
                .instanceName("pg-instance")
                .instanceId("pg-uuid-001")
                .baseUrl("http://localhost:8080")
                .weight(100)
                .status("active")
                .tags(Map.of("gpu", "a100", "region", "cn-north"))
                .headers(Map.of("Authorization", "Bearer pg-test"))
                .build();

        entityManager.persist(entity);
        entityManager.flush();
        entityManager.clear();

        ServiceInstanceEntity loaded = entityManager.find(ServiceInstanceEntity.class, entity.getId());
        assertNotNull(loaded);
        assertEquals(Map.of("gpu", "a100", "region", "cn-north"), loaded.getTags(), "tags 应完整读出");
        assertEquals(Map.of("Authorization", "Bearer pg-test"), loaded.getHeaders(), "headers 应完整读出");
    }

    // ==================== 5. IDENTITY 主键 ====================

    @Test
    @DisplayName("IDENTITY 主键在 PostgreSQL 上正常生成（非空且单调）")
    void identityKeys_generatedOnPostgres() {
        ConfigVersionHistoryEntity first = configVersionHistoryRepository.saveAndFlush(
                version("v-pg-1", Instant.now().minusSeconds(60)));
        ConfigVersionHistoryEntity second = configVersionHistoryRepository.saveAndFlush(
                version("v-pg-2", Instant.now()));

        assertNotNull(first.getId(), "第一次插入应返回生成的 id");
        assertNotNull(second.getId(), "第二次插入应返回生成的 id");
        assertTrue(second.getId() > first.getId(),
                "IDENTITY 主键应单调递增：" + first.getId() + " -> " + second.getId());
    }

    // ==================== 6. @Modifying 原子自增 ====================

    @Test
    @DisplayName("QuotaLedgerRepository.accumulate 的原子自增在 PostgreSQL 上正确累加")
    void quotaLedger_accumulateOnPostgres() {
        LocalDateTime windowStart = LocalDateTime.of(2025, 6, 15, 0, 0);
        QuotaLedgerEntity ledger = QuotaLedgerEntity.builder()
                .tenantId("t1")
                .apiKeyId("k1")
                .userId("u1")
                .serviceType("chat")
                .model("m1")
                .windowType("DAY")
                .windowStart(windowStart)
                .requestCount(0L)
                .tokenCount(0L)
                .updatedAt(LocalDateTime.now())
                .build();
        entityManager.persist(ledger);
        entityManager.flush();
        entityManager.clear();

        int updated = quotaLedgerRepository.accumulate(
                "t1", "k1", "u1", "chat", "m1", "DAY", windowStart,
                5L, 500L, LocalDateTime.now());
        assertEquals(1, updated, "应命中 1 行");
        updated = quotaLedgerRepository.accumulate(
                "t1", "k1", "u1", "chat", "m1", "DAY", windowStart,
                3L, 300L, LocalDateTime.now());
        assertEquals(1, updated, "第二次也应命中 1 行");

        entityManager.clear();
        QuotaLedgerEntity reloaded = quotaLedgerRepository.findById(ledger.getId()).orElseThrow();
        assertEquals(8L, reloaded.getRequestCount(), "请求数应累加为 8");
        assertEquals(800L, reloaded.getTokenCount(), "token 数应累加为 800");

        // 不存在的维度应返回 0（即「账本行不存在」，调用方需插入）
        int none = quotaLedgerRepository.accumulate(
                "t-nope", "k-nope", "u-nope", "chat", "m1", "DAY", windowStart,
                1L, 1L, LocalDateTime.now());
        assertEquals(0, none, "不存在的维度应返回 0");
    }

    // ==================== 7. HQL 扩展 LIMIT ====================

    @Test
    @DisplayName("HQL 扩展 LIMIT :limit 在 PostgreSQL 上可执行且按序截断")
    void limitHql_executesOnPostgres() {
        Instant now = Instant.now();
        configVersionHistoryRepository.saveAndFlush(version("v-old", now.minusSeconds(300)));
        configVersionHistoryRepository.saveAndFlush(version("v-mid", now.minusSeconds(200)));
        configVersionHistoryRepository.saveAndFlush(version("v-new", now.minusSeconds(100)));

        List<ConfigVersionHistoryEntity> latest = configVersionHistoryRepository.findLatest(2);

        assertEquals(2, latest.size(), "LIMIT 2 应只返回 2 条");
        assertEquals("v-new", latest.get(0).getVersionNumber(), "应按 timestamp 倒序");
        assertEquals("v-mid", latest.get(1).getVersionNumber());

        Optional<ConfigVersionHistoryEntity> byVersion =
                configVersionHistoryRepository.findByVersionNumber("v-old");
        assertTrue(byVersion.isPresent());
        assertFalse(configVersionHistoryRepository.existsByVersionNumber("v-missing"));
    }

    // ==================== 7. JSON 列的物理类型（issue #190）====================

    @Test
    @DisplayName("service_instance 的 JSON 列在 PG 上应为 jsonb（新库路径：实体不硬编码 columnDefinition）")
    void jsonColumns_areJsonbOnPostgres() {
        for (String column : List.of("tags", "headers")) {
            assertEquals("jsonb", columnTypeOf("service_instance", column),
                    "列 " + column + " 在 PostgreSQL 上应为 jsonb —— 实体用 @JdbcTypeCode(SqlTypes.JSON)、"
                            + "物理类型交给方言；此前硬编码 columnDefinition=\"JSON\" 会让新库建成 json，"
                            + "与旧库升级路径（CLOB→TEXT）不一致（issue #190）");
        }
    }

    /**
     * 注意（issue #192）：生产上这条收敛在 **PostgreSQL** 路径已改由
     * {@code db/migration/V2__legacy_convergence.sql} 承担 —— 本组件现在只在
     * **未启用版本化迁移**时注册（即 H2 路径）。这里直接构造组件调用，钉住的是组件
     * 自身的收敛能力；PG 路径「新库 / 老库收敛到同一 schema」由
     * {@code PostgresFlywayMigrationEquivalenceTest} 覆盖。
     */
    @Test
    @DisplayName("旧库的 tags/headers 为 text 时，兼容迁移器应把它们收敛为 jsonb（issue #190）")
    void legacyTextJsonColumns_areConvergedToJsonb() {
        for (String column : List.of("tags", "headers")) {
            jdbcTemplate.execute("ALTER TABLE service_instance ALTER COLUMN " + column
                    + " TYPE text USING " + column + "::text");
            assertEquals("text", columnTypeOf("service_instance", column),
                    "前置：应已把 " + column + " 改成 text，以模拟旧库由兼容迁移补列后的物理类型");
        }

        new CompatibilitySchemaMigrator(jdbcTemplate.getDataSource(), jdbcTemplate).run(null);

        for (String column : List.of("tags", "headers")) {
            assertEquals("jsonb", columnTypeOf("service_instance", column),
                    "兼容迁移器应把旧库的 " + column + " 收敛为 jsonb，"
                            + "否则后续切 ddl-auto: validate 时会因物理类型不一致而启动失败");
        }
    }

    // ==================== helpers ====================

    /** 读某列在 PG 上的物理类型（issue #190：断言物理类型，而不是「能读写」） */
    private String columnTypeOf(final String table, final String column) {
        return jdbcTemplate.queryForObject(
                "SELECT data_type FROM information_schema.columns "
                        + "WHERE table_schema = 'public' AND lower(table_name) = ? AND lower(column_name) = ?",
                String.class, table.toLowerCase(), column.toLowerCase());
    }

    private ConfigVersionHistoryEntity version(final String versionNumber, final Instant timestamp) {
        ConfigVersionHistoryEntity entity = new ConfigVersionHistoryEntity();
        entity.setVersionNumber(versionNumber);
        entity.setServiceType("chat");
        entity.setUserId("tester");
        entity.setConfigSnapshot("{\"k\":\"v\"}");
        entity.setDescription("pg integration test");
        entity.setTimestamp(timestamp);
        entity.setStatus("ACTIVE");
        return entity;
    }

    private void saveExceptionEvent(final String eventId, final LocalDateTime occurredAt) {
        entityManager.persist(ExceptionEventEntity.builder()
                .eventId(eventId)
                .exceptionType("java.lang.RuntimeException")
                .exceptionMessage("pg test exception")
                .operation("pg-test-operation")
                .occurrenceCount(1L)
                .firstOccurrence(occurredAt)
                .lastOccurrence(occurredAt)
                .occurredAt(occurredAt)
                .isAggregated(false)
                .build());
        entityManager.flush();
    }
}
