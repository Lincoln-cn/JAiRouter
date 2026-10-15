package org.unreal.modelrouter.auth.security.audit;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.unreal.modelrouter.auth.security.model.SecurityAuditEvent;
import org.unreal.modelrouter.common.dto.AuditEventType;
import org.unreal.modelrouter.persistence.jpa.entity.SecurityAuditEventEntity;
import org.unreal.modelrouter.persistence.jpa.repository.SecurityAuditEventRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 审计管道自观测的**真实运行态**守护（issue #396）。
 *
 * <p>背景：这些指标原先只有 {@code EnhancedSecurityAuditService}（内存缓冲实现）在写，
 * 而该实现默认不是 Bean、开启还会启动失败（issue #395）⇒ 指标**注册了却恒为 0**。
 * 现在指标写入已并入默认实现 {@link ExtendedSecurityAuditServiceImpl} 的唯一漏斗
 * {@code persist(..)}，本类钉住这一点：**真实装配下写一条审计事件 ⇒ 落库与指标同时发生**。</p>
 *
 * <p>⚠️ 三处非显然点（都是本仓实测踩到的）：① 本仓无 {@code @SpringBootTest}，用 JPA 切片 +
 * 显式 {@code @ContextConfiguration}（避开测试用 {@code @SpringBootConfiguration} 的劫持，issue #393）；
 * ② 显式指定 config 会关掉嵌套 {@code @TestConfiguration} 的自动探测 ⇒ stub 要一并列出；
 * ③ 关掉测试事务后**数据与指标会在同上下文的用例间累积** ⇒ 断言必须是**增量**、并在每例前清库。</p>
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@org.springframework.test.context.ActiveProfiles("test")
@org.springframework.test.context.ContextConfiguration(classes = {
        org.unreal.modelrouter.ModelRouterApplication.class,
        AuditMetricsRuntimeTest.Stubs.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@Import({ExtendedSecurityAuditServiceImpl.class, AuditEntityMapper.class, SecurityReportGenerator.class,
        AuditMetricsService.class})
@DisplayName("审计指标真实运行态守护（issue #396）：落库与指标在同一漏斗里发生")
class AuditMetricsRuntimeTest {

    private static final String TOTAL = "jairouter_audit_events_total";
    private static final String SUCCESS = "jairouter_audit_events_success_total";
    private static final String FAILURE = "jairouter_audit_events_failure_total";
    private static final String WRITE_TIMER = "jairouter_audit_event_write_duration_seconds";
    private static final String TOTAL_GAUGE = "jairouter_audit_total_events";

    @Autowired
    private ExtendedSecurityAuditService auditService;

    @Autowired
    private SecurityAuditEventRepository auditRepository;

    @Autowired
    private MeterRegistry meterRegistry;

    @TestConfiguration
    static class Stubs {
        @Bean
        MeterRegistry meterRegistry() {
            return new SimpleMeterRegistry();
        }

        /** JPA 切片不含 Jackson 自动配置，而 AuditEntityMapper 需要它（生产由 Boot 提供） */
        @Bean
        com.fasterxml.jackson.databind.ObjectMapper objectMapper() {
            return new com.fasterxml.jackson.databind.ObjectMapper();
        }
    }

    @BeforeEach
    void cleanAuditRows() {
        // 事务已关（NOT_SUPPORTED）⇒ 同上下文内上一例的数据会留着，逐例清掉
        auditRepository.deleteAll();
    }

    private double counter(final String name) {
        var counter = meterRegistry.find(name).counter();
        return counter == null ? 0d : counter.count();
    }

    private double gauge(final String name) {
        var gauge = meterRegistry.find(name).gauge();
        return gauge == null ? 0d : gauge.value();
    }

    private long timerCount() {
        var timer = meterRegistry.find(WRITE_TIMER).timer();
        return timer == null ? 0L : timer.count();
    }

    private SecurityAuditEvent event(final boolean success) {
        return SecurityAuditEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .eventType(AuditEventType.AUTHENTICATION_FAILED.name())
                .success(success)
                .timestamp(LocalDateTime.now())
                .clientIp("203.0.113.99")
                .build();
    }

    @Test
    @DisplayName("写成功事件 ⇒ 落库 1 行 + 总数/成功计数与 Gauge/Timer 同步推进，失败计数不动")
    void successfulWrite_feedsPersistenceAndMetrics() {
        double totalBefore = counter(TOTAL);
        double successBefore = counter(SUCCESS);
        double failureBefore = counter(FAILURE);
        double gaugeBefore = gauge(TOTAL_GAUGE);
        long timerBefore = timerCount();

        auditService.recordEvent(event(true)).block();

        List<SecurityAuditEventEntity> rows = auditRepository.findAll();
        assertEquals(1, rows.size(), "审计事件必须真的落库（不是只进内存）");
        assertTrue(rows.get(0).getSuccess(), "落库的成功标志应与写入一致");

        assertEquals(totalBefore + 1, counter(TOTAL), 0.0001d, "总数计数器应 +1");
        assertEquals(successBefore + 1, counter(SUCCESS), 0.0001d, "成功计数器应 +1");
        assertEquals(failureBefore, counter(FAILURE), 0.0001d, "失败计数器不得变化");
        assertEquals(gaugeBefore + 1, gauge(TOTAL_GAUGE), 0.0001d, "总事件 Gauge 应 +1");
        assertEquals(timerBefore + 1, timerCount(), "写入耗时应每个漏斗调用记录一次");
    }

    @Test
    @DisplayName("写失败事件 ⇒ 失败计数 +1、成功计数不动（证明成功/失败分流不是恒真）")
    void failedWrite_incrementsFailureCounter() {
        double totalBefore = counter(TOTAL);
        double successBefore = counter(SUCCESS);
        double failureBefore = counter(FAILURE);

        auditService.recordEvent(event(false)).block();

        assertEquals(totalBefore + 1, counter(TOTAL), 0.0001d);
        assertEquals(failureBefore + 1, counter(FAILURE), 0.0001d, "失败计数器应 +1");
        assertEquals(successBefore, counter(SUCCESS), 0.0001d, "成功计数器不得变化");
        assertEquals(1, auditRepository.findAll().size(), "失败事件同样要落库");
    }
}
