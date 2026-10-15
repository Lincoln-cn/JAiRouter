package org.unreal.modelrouter.monitor.monitoring.security;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.connection.ReactiveRedisConnectionFactory;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.test.context.TestPropertySource;
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

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 告警**跨副本冷却**的真实运行态验证（issue #390 的第二个验收子项）。
 *
 * <p>要证明的语义：同一告警类型在冷却窗口内**只产生一次**外部可观测副作用 —— 否则多副本下
 * 指标 / WARN 日志 / {@code SECURITY_ALERT} 审计事件会被 N 个副本各写一遍（概览页告警数虚高 N 倍）。
 * 该语义由 {@link ScheduledTaskLock#tryAcquire} 的 Redis {@code SET NX + TTL} 提供。</p>
 *
 * <p><b>默认跳过</b>（与 {@code RedisSlidingWindowRateLimiterRedisIntegrationTest} 同一口径）：
 * 仅当环境变量 {@code REDIS_TEST=true} 时执行；连接参数可用 {@code REDIS_TEST_HOST}（默认 {@code 127.0.0.1}）
 * 与 {@code REDIS_TEST_PORT}（默认 {@code 6379}）。CI 的 {@code test} job 有 {@code redis:7-alpine}
 * 且设置了该变量（并在「校验门控测试未被跳过」清单里登记本类），因此**在 CI 里是真跑的**。</p>
 *
 * <p>为什么必须用真 Redis：无 Redis 时 {@code ScheduledTaskLock} **fail-open（恒返回可执行）**，
 * 那时「冷却生效」与「锁降级放行」在观测上无法区分。</p>
 */
@EnabledIfEnvironmentVariable(named = "REDIS_TEST", matches = "true")
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@org.springframework.test.context.ActiveProfiles("test")
@EnableConfigurationProperties(SecurityAlertProperties.class)
@Import({ExtendedSecurityAuditServiceImpl.class, AuditEntityMapper.class, SecurityReportGenerator.class,
        SecurityAlertService.class, SecurityMetrics.class, ScheduledTaskLock.class,
        AlarmChainCooldownRedisTest.RedisStubs.class})
@TestPropertySource(properties = {
        "jairouter.security.monitoring.alerts.enabled=true",
        "jairouter.security.monitoring.alerts.thresholds.suspicious-ip-count=2",
        "jairouter.security.monitoring.alerts.thresholds.cooldown-minutes=15"
})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@DisplayName("告警跨副本冷却（issue #390）：同类型在冷却窗口内只触发一次")
class AlarmChainCooldownRedisTest {

    private static final String PROBE_IP = "203.0.113.88";

    /** 完整锁 key = {@code ScheduledTaskLock.KEY_PREFIX} + {@code "security-alert-cooldown:" + alertType} */
    private static final String COOLDOWN_KEY =
            "jairouter:sched:lock:security-alert-cooldown:SUSPICIOUS_IP_ACTIVITY";

    @Autowired
    private SecurityAlertService alertService;

    @Autowired
    private ExtendedSecurityAuditService auditService;

    @Autowired
    private SecurityAuditEventRepository auditRepository;

    @Autowired
    private MeterRegistry meterRegistry;

    @Autowired
    private ReactiveStringRedisTemplate redisTemplate;

    @TestConfiguration
    static class RedisStubs {
        @Bean
        MeterRegistry meterRegistry() {
            return new SimpleMeterRegistry();
        }

        /** JPA 切片不含 Jackson 自动配置，而 AuditEntityMapper 需要它（生产由 Boot 提供） */
        @Bean
        com.fasterxml.jackson.databind.ObjectMapper objectMapper() {
            return new com.fasterxml.jackson.databind.ObjectMapper();
        }

        /** 指向真实 Redis（CI 的 service；本机设 REDIS_TEST=true 且本地有 Redis 时才跑） */
        @Bean
        ReactiveRedisConnectionFactory reactiveRedisConnectionFactory() {
            final String host = System.getenv().getOrDefault("REDIS_TEST_HOST", "127.0.0.1");
            final int port = Integer.parseInt(System.getenv().getOrDefault("REDIS_TEST_PORT", "6379"));
            return new LettuceConnectionFactory(new RedisStandaloneConfiguration(host, port));
        }

        @Bean
        ReactiveStringRedisTemplate reactiveStringRedisTemplate(final ReactiveRedisConnectionFactory factory) {
            return new ReactiveStringRedisTemplate(factory);
        }
    }

    @AfterEach
    void releaseCooldownKey() {
        // 不把冷却键留给后续用例/本地环境
        try {
            redisTemplate.delete(COOLDOWN_KEY).block(Duration.ofSeconds(5));
        } catch (RuntimeException ignored) {
            // 清理失败不应掩盖断言结果
        }
    }

    private void recordAuthFailure() {
        auditService.recordEvent(SecurityAuditEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .eventType(AuditEventType.AUTHENTICATION_FAILED.name())
                .clientIp(PROBE_IP)
                .success(false)
                .timestamp(LocalDateTime.now())
                .build()).block();
    }

    private double violationCount() {
        var counter = meterRegistry.find("jairouter.security.violations")
                .tag("violation_type", "SUSPICIOUS_IP_ACTIVITY").counter();
        return counter == null ? 0d : counter.count();
    }

    private List<SecurityAuditEventEntity> alertRows() {
        return auditRepository.findAll().stream()
                .filter(e -> AuditEventType.SECURITY_ALERT.equals(e.getEventType()))
                .toList();
    }

    @Test
    @DisplayName("第一次触发 ⇒ 四个出口各一次；同窗口内第二次 ⇒ 被冷却抑制；删掉冷却键后 ⇒ 又能触发（对照）")
    void cooldownSuppressesSecondTrigger_andKeyDeletionUnblocks() {
        recordAuthFailure();
        recordAuthFailure();   // 阈值 = 2

        // 第一次：应触发
        alertService.checkSuspiciousIpAlert(PROBE_IP).block();
        assertEquals(1, alertRows().size(), "第一次应落一条 SECURITY_ALERT");
        assertEquals(1.0d, violationCount(), 0.0001d, "第一次应记一次指标");
        assertTrue(Boolean.TRUE.equals(redisTemplate.hasKey(COOLDOWN_KEY).block()),
                "触发后冷却键应存在于 Redis（多副本共享）");

        // 第二次（同窗口、同类型）：应被冷却抑制 —— 这正是「多副本只触发一次」的语义
        alertService.checkSuspiciousIpAlert(PROBE_IP).block();
        assertEquals(1, alertRows().size(), "冷却窗口内不得再落 SECURITY_ALERT");
        assertEquals(1.0d, violationCount(), 0.0001d, "冷却窗口内不得再记指标");

        // 对照：删掉冷却键后应能再次触发 —— 证明抑制确实来自这把锁，而不是阈值/事件被消耗等别的原因
        redisTemplate.delete(COOLDOWN_KEY).block(Duration.ofSeconds(5));
        alertService.checkSuspiciousIpAlert(PROBE_IP).block();
        assertEquals(2, alertRows().size(), "冷却键被清掉后应能再次触发");
        assertEquals(2.0d, violationCount(), 0.0001d, "冷却键被清掉后应再记一次指标");
    }
}
