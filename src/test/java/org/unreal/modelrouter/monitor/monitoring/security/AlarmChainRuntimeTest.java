package org.unreal.modelrouter.monitor.monitoring.security;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.unreal.modelrouter.auth.security.audit.AuditEntityMapper;
import org.unreal.modelrouter.auth.security.audit.ExtendedSecurityAuditService;
import org.unreal.modelrouter.auth.security.audit.ExtendedSecurityAuditServiceImpl;
import org.unreal.modelrouter.auth.security.audit.SecurityReportGenerator;
import org.unreal.modelrouter.auth.security.model.SecurityAuditEvent;
import org.unreal.modelrouter.common.dto.AuditEventType;
import org.unreal.modelrouter.common.scheduling.ScheduledTaskLock;
import org.unreal.modelrouter.persistence.jpa.entity.SecurityAuditEventEntity;
import org.unreal.modelrouter.persistence.jpa.repository.SecurityAuditEventRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

/**
 * 告警链路的**真实运行态**端到端验证（issue #390，承接 #304 的告警那一半）。
 *
 * <p>用真实装配而不是 mock：真实 H2 + JPA 仓库（{@link ExtendedSecurityAuditServiceImpl} 是默认生效的
 * {@code SecurityAuditService} 实现）、真实 {@link SecurityMetrics}（真实 Micrometer 注册表）、
 * 真实 {@link SecurityAlertService} 与 {@link ScheduledTaskLock}；只把与四个出口**无关**的
 * {@link SecurityReportGenerator}（仅报告生成用）换成 stub，避免把整棵报告依赖树拖进切片。</p>
 *
 * <p>判据 = 「造真实事件 → 触发检查 → 四个出口一致」：
 * ① 指标 {@code jairouter.security.violations}（tag {@code violation_type}/{@code severity}）
 * ② WARN 日志（{@code "安全告警触发: type=…"}）
 * ③ {@code SECURITY_ALERT} 审计事件（H2 里的真实行，{@code action=alertType}）
 * ④ {@link SecurityAlertService#countAlertsLast24Hours()}（概览页 {@code alertsLast24Hours} 的数据源）。
 * 另有一条**未达阈值不触发**的反向用例，用来证明断言不是恒真。</p>
 *
 * <p>⚠️ 关掉测试事务（{@code NOT_SUPPORTED}）：{@code SecurityAlertService.triggerAlert} 走
 * {@code boundedElastic} 线程，写审计事件发生在**另一个线程**上 —— 若沿用 {@code @DataJpaTest} 默认的
 * 事务包裹，那个写既看不见也不会提交。</p>
 *
 * <p>多副本冷却用例需要 Redis（{@code ScheduledTaskLock} 无 Redis 时 fail-open），故**不在本类**，
 * 见 issue #390 的说明（CI 的 {@code test} job 有 redis:7-alpine）。</p>
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@org.springframework.test.context.ActiveProfiles("test")
// 必须显式指向真实应用类：本包之上存在测试用的 @SpringBootConfiguration（`TestMonitoringConfiguration`，
// 声明包名为 org.unreal.modelrouter.monitor.monitoring，且**没有** @EnableAutoConfiguration），
// Boot 的切片测试会先命中它 ⇒ EntityScan 基线包拿不到、上下文起不来。
@org.springframework.test.context.ContextConfiguration(
        classes = {org.unreal.modelrouter.ModelRouterApplication.class, AlarmChainRuntimeTest.Stubs.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@EnableConfigurationProperties(SecurityAlertProperties.class)
@Import({ExtendedSecurityAuditServiceImpl.class, AuditEntityMapper.class, SecurityReportGenerator.class,
        SecurityAlertService.class, SecurityMetrics.class, ScheduledTaskLock.class})
@org.springframework.test.context.TestPropertySource(properties = {
        // 阈值下调到 2（默认 10），避免为一条用例造 10 条事件；窗口与冷却沿用默认
        "jairouter.security.monitoring.alerts.thresholds.suspicious-ip-count=2",
        "jairouter.security.monitoring.alerts.enabled=true"
})
@DisplayName("告警链路真实运行态 e2e（issue #390）：造事件 → 四个出口一致")
class AlarmChainRuntimeTest {

    private static final String PROBE_IP = "203.0.113.77";

    @Autowired
    private SecurityAlertService alertService;

    @Autowired
    private ExtendedSecurityAuditService auditService;

    @Autowired
    private SecurityAuditEventRepository auditRepository;

    @Autowired
    private MeterRegistry meterRegistry;

    @TestConfiguration
    static class Stubs {
        /** 真实 Micrometer 注册表（生产用 Prometheus 注册表；断言口径一致） */
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

    private void recordAuthFailure(final String clientIp) {
        auditService.recordEvent(SecurityAuditEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .eventType(AuditEventType.AUTHENTICATION_FAILED.name())
                .clientIp(clientIp)
                .success(false)
                .timestamp(LocalDateTime.now())
                .build()).block();
    }

    private double violationCount() {
        return meterRegistry.find("jairouter.security.violations")
                .tag("violation_type", "SUSPICIOUS_IP_ACTIVITY")
                .counter() == null ? 0d
                : meterRegistry.find("jairouter.security.violations")
                        .tag("violation_type", "SUSPICIOUS_IP_ACTIVITY").counter().count();
    }

    private List<SecurityAuditEventEntity> alertRows() {
        return auditRepository.findAll().stream()
                .filter(e -> AuditEventType.SECURITY_ALERT.name().equals(e.getEventType() == null
                        ? null : e.getEventType().name()))
                .toList();
    }

    @Test
    @DisplayName("达到阈值 ⇒ 指标 / WARN 日志 / SECURITY_ALERT 审计行 / 近 24h 计数 四者一致")
    void suspiciousIpSpike_triggersAllFourOutlets() {
        Logger logger = (Logger) LoggerFactory.getLogger(SecurityAlertService.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            recordAuthFailure(PROBE_IP);
            recordAuthFailure(PROBE_IP);

            alertService.checkSuspiciousIpAlert(PROBE_IP).block();

            // ① 指标
            assertEquals(1.0d, violationCount(), 0.0001d, "指标应 +1（tag violation_type=SUSPICIOUS_IP_ACTIVITY）");
            // ② WARN 日志
            assertTrue(appender.list.stream().anyMatch(e -> e.getLevel() == Level.WARN
                            && e.getFormattedMessage().contains("安全告警触发")
                            && e.getFormattedMessage().contains("SUSPICIOUS_IP_ACTIVITY")),
                    "应有 WARN 日志且点名告警类型：" + appender.list);
            // ③ 审计事件（H2 真实行）
            List<SecurityAuditEventEntity> rows = alertRows();
            assertEquals(1, rows.size(), "应落一条 SECURITY_ALERT 审计事件");
            assertEquals("SUSPICIOUS_IP_ACTIVITY", rows.get(0).getAction(), "审计事件的 action 应为告警类型");
            // ④ 近 24h 计数（概览页 alertsLast24Hours 的数据源）
            assertEquals(1L, alertService.countAlertsLast24Hours().block(), "近 24h 告警数应为 1");
        } finally {
            logger.detachAppender(appender);
        }
    }

    @Test
    @DisplayName("未达阈值 ⇒ 四个出口都不动（证明上面的断言不是恒真）")
    void belowThreshold_doesNotTrigger() {
        Logger logger = (Logger) LoggerFactory.getLogger(SecurityAlertService.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            recordAuthFailure(PROBE_IP); // 阈值是 2，这里只造 1 条

            alertService.checkSuspiciousIpAlert(PROBE_IP).block();

            assertEquals(0.0d, violationCount(), 0.0001d, "未达阈值不得记指标");
            assertTrue(appender.list.stream().noneMatch(e -> e.getLevel() == Level.WARN
                            && e.getFormattedMessage().contains("安全告警触发")),
                    "未达阈值不得告警：" + appender.list);
            assertTrue(alertRows().isEmpty(), "未达阈值不得落 SECURITY_ALERT");
            assertEquals(0L, alertService.countAlertsLast24Hours().block(), "近 24h 告警数应为 0");
        } finally {
            logger.detachAppender(appender);
        }
    }
}
