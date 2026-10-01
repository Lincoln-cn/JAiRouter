package org.unreal.modelrouter.persistence.jpa;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.unreal.modelrouter.persistence.jpa.entity.ApiCallHistoryEntity;
import org.unreal.modelrouter.persistence.jpa.entity.ExceptionEventEntity;
import org.unreal.modelrouter.persistence.jpa.entity.ServiceInstanceEntity;
import org.unreal.modelrouter.persistence.jpa.repository.ApiCallHistoryRepository;
import org.unreal.modelrouter.persistence.jpa.repository.ExceptionEventRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 真实 H2 集成测试（数据库可移植性硬化）
 *
 * <p>本类是仓库首个真实数据库集成测试，使用内存 H2 验证：
 * <ul>
 *   <li>{@code ApiCallHistoryEntity} 的 TEXT 大文本字段读写（原 CLOB → TEXT 改动）</li>
 *   <li>{@code ExceptionEventRepository.countByHour} 的 EXTRACT(HOUR FROM ...) HQL 在真实 H2 上可执行</li>
 *   <li>{@code ServiceInstanceEntity} 的 JSON 列实际物理类型（为改动 5 调查取证）</li>
 * </ul>
 *
 * @author JAiRouter Team
 * @since 2.9.8
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@DisplayName("数据库可移植性 H2 集成测试")
class DbPortabilityH2IntegrationTest {

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private ApiCallHistoryRepository apiCallHistoryRepository;

    @Autowired
    private ExceptionEventRepository exceptionEventRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    // ==================== 改动 2: TEXT 大文本字段读写 ====================

    @Test
    @DisplayName("ApiCallHistoryEntity 加密请求/响应体（TEXT 列）可正常写入并读出大文本")
    void encryptedBodyFields_roundTrip_largeText() {
        // 构造超过 255 字符的大文本，验证 TEXT 列不受 VARCHAR(255) 限制
        String largeText = "A".repeat(10_000);

        ApiCallHistoryEntity entity = ApiCallHistoryEntity.builder()
                .traceId("trace-text-001")
                .requestId("req-text-001")
                .requestMethod("POST")
                .requestPath("/v1/chat/completions")
                .serviceType("chat")
                .modelName("test-model")
                .promptTokens(0L)
                .completionTokens(0L)
                .totalTokens(0L)
                .isSuccess(true)
                .rateLimited(false)
                .circuitBroken(false)
                .requestBodyEncrypted(largeText)
                .responseBodyEncrypted(largeText + "-resp")
                .build();

        apiCallHistoryRepository.saveAndFlush(entity);

        entityManager.clear();

        ApiCallHistoryEntity loaded = apiCallHistoryRepository.findById(entity.getId()).orElseThrow();
        assertEquals(largeText, loaded.getRequestBodyEncrypted(), "加密请求体大文本应完整读出");
        assertEquals(largeText + "-resp", loaded.getResponseBodyEncrypted(), "加密响应体大文本应完整读出");
    }

    @Test
    @DisplayName("TEXT 列在 H2 中的实际类型为 CLOB（TEXT 是 CLOB 别名，向后兼容）")
    void textColumn_physicalType_isClobAlias() {
        List<String> types = jdbcTemplate.queryForList(
                "SELECT DATA_TYPE FROM INFORMATION_SCHEMA.COLUMNS "
                        + "WHERE LOWER(TABLE_NAME) = 'api_call_history' "
                        + "AND LOWER(COLUMN_NAME) IN ('request_body_encrypted', 'response_body_encrypted')",
                String.class);
        assertEquals(2, types.size(), "应查到两列");
        for (String type : types) {
            System.out.println("[调查] api_call_history encrypted column DATA_TYPE=" + type);
            // H2 中 TEXT 是 CLOB 的别名，物理类型可能显示为 CLOB 或 TEXT 或 CHARACTER LARGE OBJECT
            assertNotNull(type, "列类型不应为 null");
        }
    }

    // ==================== 改动 3: countByHour / EXTRACT(HOUR FROM ...) ====================

    @Test
    @DisplayName("countByHour 使用 EXTRACT(HOUR FROM ...) 在真实 H2 上可执行并返回正确分组")
    void countByHour_executesOnRealH2_returnsExpectedGroups() {
        LocalDateTime base = LocalDateTime.of(2025, 6, 15, 10, 0, 0);

        // 插入 3 条 hour=10、1 条 hour=11、2 条 hour=14
        saveExceptionEvent("evt-h-1", base.withHour(10).withMinute(5));
        saveExceptionEvent("evt-h-2", base.withHour(10).withMinute(15));
        saveExceptionEvent("evt-h-3", base.withHour(10).withMinute(55));
        saveExceptionEvent("evt-h-4", base.withHour(11).withMinute(0));
        saveExceptionEvent("evt-h-5", base.withHour(14).withMinute(30));
        saveExceptionEvent("evt-h-6", base.withHour(14).withMinute(45));

        LocalDateTime start = base.withHour(0);
        LocalDateTime end = base.withHour(23).withMinute(59).withSecond(59);

        List<Object[]> results = exceptionEventRepository.countByHour(start, end);

        assertNotNull(results, "countByHour 不应返回 null");
        assertEquals(3, results.size(), "应有 3 个小时分组（10, 11, 14）");

        // 按 ORDER BY EXTRACT(HOUR ...) 升序验证
        assertEquals(10, ((Number) results.get(0)[0]).intValue(), "第 1 组应为 hour=10");
        assertEquals(3L, ((Number) results.get(0)[1]).longValue(), "hour=10 应有 3 条");
        assertEquals(11, ((Number) results.get(1)[0]).intValue(), "第 2 组应为 hour=11");
        assertEquals(1L, ((Number) results.get(1)[1]).longValue(), "hour=11 应有 1 条");
        assertEquals(14, ((Number) results.get(2)[0]).intValue(), "第 3 组应为 hour=14");
        assertEquals(2L, ((Number) results.get(2)[1]).longValue(), "hour=14 应有 2 条");
    }

    @Test
    @DisplayName("countByHour 时间范围外的数据不计入")
    void countByHour_respectsTimeRange() {
        LocalDateTime base = LocalDateTime.of(2025, 6, 16, 10, 0, 0);
        saveExceptionEvent("evt-in-range", base.withHour(9));
        saveExceptionEvent("evt-out-range", base.withHour(20));

        LocalDateTime start = base.withHour(8);
        LocalDateTime end = base.withHour(12);

        List<Object[]> results = exceptionEventRepository.countByHour(start, end);
        assertEquals(1, results.size(), "时间范围内应只有 1 个分组");
        assertEquals(9, ((Number) results.get(0)[0]).intValue());
        assertEquals(1L, ((Number) results.get(0)[1]).longValue());
    }

    // ==================== 改动 5 调查: service_instance.tags/headers 实际物理类型 ====================

    @Test
    @DisplayName("调查: service_instance.tags/headers 在 H2 上的实际列类型")
    void investigate_serviceInstance_jsonColumnTypes() {
        List<Map<String, Object>> cols = jdbcTemplate.queryForList(
                "SELECT COLUMN_NAME, DATA_TYPE "
                        + "FROM INFORMATION_SCHEMA.COLUMNS "
                        + "WHERE LOWER(TABLE_NAME) = 'service_instance' "
                        + "AND LOWER(COLUMN_NAME) IN ('tags', 'headers')");

        assertEquals(2, cols.size(), "应查到 tags 和 headers 两列");
        for (Map<String, Object> col : cols) {
            String colName = (String) col.get("COLUMN_NAME");
            Object dataType = col.get("DATA_TYPE");
            assertNotNull(dataType, "列 " + colName + " 的 DATA_TYPE 不应为 null");
            System.out.println("[调查] service_instance." + colName
                    + " DATA_TYPE=" + dataType);
        }
    }

    @Test
    @DisplayName("调查: service_instance.tags 可写入并读出 JSON 字符串内容")
    void investigate_serviceInstance_tagsRoundTrip() {
        ServiceInstanceEntity entity = ServiceInstanceEntity.builder()
                .serviceConfigId(1L)
                .instanceName("test-instance")
                .instanceId("uuid-001")
                .baseUrl("http://localhost:8080")
                .weight(100)
                .status("active")
                .tags(Map.of("gpu", "a100", "region", "cn-north"))
                .headers(Map.of("Authorization", "Bearer test"))
                .build();

        entityManager.persist(entity);
        entityManager.flush();
        entityManager.clear();

        ServiceInstanceEntity loaded = entityManager.find(ServiceInstanceEntity.class, entity.getId());
        assertNotNull(loaded);
        assertEquals(Map.of("gpu", "a100", "region", "cn-north"), loaded.getTags(), "tags 应完整读出");
        assertEquals(Map.of("Authorization", "Bearer test"), loaded.getHeaders(), "headers 应完整读出");
    }

    // ==================== helpers ====================

    private void saveExceptionEvent(String eventId, LocalDateTime occurredAt) {
        ExceptionEventEntity event = ExceptionEventEntity.builder()
                .eventId(eventId)
                .exceptionType("java.lang.RuntimeException")
                .exceptionMessage("test exception")
                .operation("test-operation")
                .occurrenceCount(1L)
                .firstOccurrence(occurredAt)
                .lastOccurrence(occurredAt)
                .occurredAt(occurredAt)
                .isAggregated(false)
                .build();
        entityManager.persist(event);
        entityManager.flush();
    }
}
