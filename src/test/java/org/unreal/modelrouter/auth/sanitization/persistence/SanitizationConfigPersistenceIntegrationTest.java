package org.unreal.modelrouter.auth.sanitization.persistence;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.util.ReflectionTestUtils;
import org.unreal.modelrouter.auth.sanitization.SanitizationRuleEngine;
import org.unreal.modelrouter.auth.sanitization.impl.DefaultSanitizationService;
import org.unreal.modelrouter.auth.security.config.properties.SecurityProperties;
import org.unreal.modelrouter.persistence.jpa.entity.SanitizationConfigEntity;
import org.unreal.modelrouter.persistence.jpa.repository.SanitizationConfigRepository;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import javax.sql.DataSource;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 脱敏配置持久化的 H2 集成测试（TA2）。
 *
 * <p>用真实 H2 + 真实 Flyway 脚本建表（不是 Hibernate 从实体推出来的表），再走
 * {@link SanitizationConfigStore} 的读写路径，最后重建一个 {@link DefaultSanitizationService}
 * 模拟「应用重启」——验证管理员保存的配置能派生出同样的规则。</p>
 *
 * <p>请求侧作用域已移除（issue #601）：V5 迁移删除历史 {@code request} 行与
 * {@code whitelist_users} 列，本测试同时断言这两点。</p>
 *
 * <p>连接串刻意与开发/生产 H2 一致（{@code MODE=MySQL;DATABASE_TO_UPPER=FALSE}），
 * 这样迁移脚本被验证的是它真正要跑的那个方言。</p>
 *
 * @author JAiRouter Team
 * @since 3.4.0
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "spring.jpa.hibernate.ddl-auto=none",
        "spring.flyway.enabled=false"
})
@DisplayName("脱敏配置持久化 H2 集成测试")
class SanitizationConfigPersistenceIntegrationTest {

    private static final String JDBC_URL =
            "jdbc:h2:mem:ta2_sanitization;DB_CLOSE_DELAY=-1;MODE=MySQL;DATABASE_TO_UPPER=FALSE";

    @DynamicPropertySource
    static void datasource(final DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> JDBC_URL);
        registry.add("spring.datasource.driver-class-name", () -> "org.h2.Driver");
        registry.add("spring.datasource.username", () -> "sa");
        registry.add("spring.datasource.password", () -> "");
    }

    @Autowired
    private SanitizationConfigRepository repository;

    @Autowired
    private DataSource dataSource;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private SanitizationConfigStore store;

    @BeforeEach
    void migrateAndClean() {
        // 真实迁移脚本建表；migrate() 幂等，后续用例只是在同一库上重复调用。
        Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration/h2")
                .load()
                .migrate();
        repository.deleteAll();
        store = new SanitizationConfigStore(repository, new com.fasterxml.jackson.databind.ObjectMapper());
    }

    @Test
    @DisplayName("Flyway 建出的 sanitization_config 列集合与实体一致，且 V5 已移除请求侧列")
    void migration_createsExpectedColumns() {
        // 注意：DATABASE_TO_UPPER=FALSE 下未加引号的 information_schema 会被当成小写标识符而报
        // 「Schema "information_schema" not found」，这里必须写带引号的大写形式（H2 实测）。
        List<Map<String, Object>> columns = jdbcTemplate.queryForList(
                "select \"COLUMN_NAME\", \"IS_NULLABLE\" from \"INFORMATION_SCHEMA\".\"COLUMNS\" "
                        + "where \"TABLE_NAME\" = 'sanitization_config'");
        Map<String, String> nullableByColumn = columns.stream().collect(
                java.util.stream.Collectors.toMap(
                        row -> String.valueOf(firstPresent(row, "COLUMN_NAME", "column_name")).toLowerCase(),
                        row -> String.valueOf(firstPresent(row, "IS_NULLABLE", "is_nullable"))));

        assertEquals(java.util.Set.of(
                        "config_scope", "enabled", "sensitive_words", "pii_patterns", "masking_char",
                        "preserve_json_structure", "log_sanitization", "fail_on_error",
                        "updated_at", "updated_by"),
                nullableByColumn.keySet());
        assertFalse(nullableByColumn.containsKey("whitelist_users"), "V5 必须删除请求侧专列");
        assertEquals("NO", nullableByColumn.get("config_scope"));
        assertEquals("NO", nullableByColumn.get("sensitive_words"));
        assertEquals("NO", nullableByColumn.get("pii_patterns"));
        assertEquals("YES", nullableByColumn.get("preserve_json_structure"));
        assertEquals("YES", nullableByColumn.get("updated_by"));

        Integer applied = jdbcTemplate.queryForObject(
                "select count(*) from flyway_schema_history where version = '5' and success = true",
                Integer.class);
        assertEquals(1, applied.intValue());
    }

    @Test
    @DisplayName("保存后重建 service 实例（模拟重启）：规则由 DB 的 response 配置派生")
    void persistedConfig_isLoadedByFreshServiceInstance() {
        SecurityProperties admin = new SecurityProperties();
        admin.getSanitization().getResponse().setEnabled(false);
        admin.getSanitization().getResponse().setSensitiveWords(List.of("db-resp-secret"));
        admin.getSanitization().getResponse().setPiiPatterns(List.of("\\d{11}"));
        admin.getSanitization().getResponse().setMaskingChar("#");

        store.persist(admin, "admin");
        repository.flush();

        // 模拟重启：一份全新的、只带 yml 默认值的属性对象
        SecurityProperties afterRestart = new SecurityProperties();
        SanitizationConfigStore.LoadResult loadResult = store.loadInto(afterRestart);
        assertTrue(loadResult.responseFromDatabase());
        assertEquals(List.of("db-resp-secret"),
                afterRestart.getSanitization().getResponse().getSensitiveWords());
        assertEquals("#", afterRestart.getSanitization().getResponse().getMaskingChar());
        assertFalse(afterRestart.getSanitization().getResponse().isEnabled());

        // 重建一个 service 实例，规则应当从 DB 配置派生出来
        SanitizationRuleEngine ruleEngine = mock(SanitizationRuleEngine.class);
        when(ruleEngine.compileRules(org.mockito.ArgumentMatchers.anyList())).thenReturn(Mono.empty());
        DefaultSanitizationService service = new DefaultSanitizationService(ruleEngine, afterRestart);
        ReflectionTestUtils.setField(service, "configStore", store);
        service.initializeRules();

        StepVerifier.create(service.getAllRules()
                        .map(all -> all.stream().map(rule -> rule.getName()).toList()))
                .assertNext(names -> {
                    assertTrue(names.contains("响应敏感词: db-resp-secret"));
                    assertTrue(names.stream().noneMatch(name -> name.startsWith("请求")));
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("DB 无行时回落 yml")
    void withoutRows_loadIntoKeepsYml() {
        SecurityProperties yml = new SecurityProperties();
        yml.getSanitization().getResponse().setSensitiveWords(List.of("yml-word"));

        SanitizationConfigStore.LoadResult result = store.loadInto(yml);

        assertFalse(result.responseFromDatabase());
        assertEquals(List.of("yml-word"), yml.getSanitization().getResponse().getSensitiveWords());
    }

    @Test
    @DisplayName("保存只写 response 行，不会凭空写请求侧行")
    void persist_writesOnlyResponseRow() {
        SecurityProperties props = new SecurityProperties();
        store.persist(props, "admin");
        repository.flush();

        assertTrue(repository.findById(SanitizationConfigStore.SCOPE_RESPONSE).isPresent());
        assertTrue(repository.findById("request").isEmpty());
        assertEquals(1, repository.count());
    }

    @Test
    @DisplayName("JSON 列损坏：装载抛异常且不改内存")
    void corruptRowInDatabase_throwsOnLoad() {
        jdbcTemplate.update("insert into sanitization_config (config_scope, enabled, sensitive_words, "
                        + "pii_patterns, masking_char, log_sanitization, fail_on_error, updated_at) "
                        + "values (?, ?, ?, ?, ?, ?, ?, current_timestamp)",
                SanitizationConfigStore.SCOPE_RESPONSE, true, "not-json", "[]", "*", true, false);

        SecurityProperties props = new SecurityProperties();
        props.getSanitization().getResponse().setSensitiveWords(List.of("yml-word"));

        assertThrows(IllegalStateException.class, () -> store.loadInto(props));
        assertEquals(List.of("yml-word"), props.getSanitization().getResponse().getSensitiveWords());
    }

    @Test
    @DisplayName("updated_at / updated_by 落库")
    void persist_writesProvenanceColumns() {
        SecurityProperties props = new SecurityProperties();
        store.persist(props, "alice");
        // JdbcTemplate 直连 JDBC，看不到同一事务里尚未 flush 的持久化上下文，必须先 flush
        repository.flush();

        Map<String, Object> row = jdbcTemplate.queryForMap(
                "select updated_by, updated_at from sanitization_config where config_scope = ?",
                SanitizationConfigStore.SCOPE_RESPONSE);
        assertEquals("alice", firstPresent(row, "UPDATED_BY", "updated_by"));
        assertNotNull(firstPresent(row, "UPDATED_AT", "updated_at"));
    }

    /** H2 的列标签大小写取决于 DATABASE_TO_UPPER 与查询方式，两种都接受。 */
    private static Object firstPresent(final Map<String, Object> row, final String upper, final String lower) {
        return row.containsKey(upper) ? row.get(upper) : row.get(lower);
    }

    @Test
    @DisplayName("实体主键即 scope：同一 scope 重复保存只保留一行")
    void persist_sameScopeTwice_keepsSingleRow() {
        SecurityProperties props = new SecurityProperties();
        props.getSanitization().getResponse().getSensitiveWords().add("first");
        store.persist(props, "admin");
        props.getSanitization().getResponse().setSensitiveWords(List.of("second"));
        store.persist(props, "admin");
        repository.flush();

        assertEquals(1, repository.count());
        SanitizationConfigEntity row =
                repository.findById(SanitizationConfigStore.SCOPE_RESPONSE).orElseThrow();
        assertTrue(row.getSensitiveWords().contains("second"));
    }
}
