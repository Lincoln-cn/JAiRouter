package org.unreal.modelrouter.monitor.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.jpa.domain.Specification;
import org.unreal.modelrouter.auth.security.permission.DataScope;
import org.unreal.modelrouter.monitor.dto.ExceptionEventDTO;
import org.unreal.modelrouter.monitor.dto.ExceptionStatisticsDTO;
import org.unreal.modelrouter.monitor.monitoring.error.ExceptionPersistenceService;
import org.unreal.modelrouter.persistence.jpa.entity.ExceptionEventEntity;
import org.unreal.modelrouter.persistence.jpa.repository.ExceptionEventRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * 异常事件的数据范围（issue #1189）。
 *
 * <p>异常记录此前没有归属字段，{@code monitoring:exceptions:self:read} 是死码；
 * 补上 {@code user_id}/{@code api_key_id} 后，本人视角必须只看得到自己触发的异常，
 * 并且越界详情要返回 null（由控制器转 404，不泄露「存在但不可见」）。</p>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ExceptionDataScopeTest {

    @Mock
    private ExceptionEventRepository exceptionEventRepository;

    @Mock
    private ExceptionPersistenceService exceptionPersistenceService;

    private ExceptionManagementService service;

    private static ExceptionEventEntity row(String userId, String apiKeyId) {
        ExceptionEventEntity entity = new ExceptionEventEntity();
        entity.setEventId("e-" + userId + "-" + apiKeyId);
        entity.setExceptionType("NullPointerException");
        entity.setErrorCategory("SYSTEM");
        entity.setOperation("chat");
        entity.setHttpStatus("500");
        entity.setClientIp("10.0.0.1");
        entity.setOccurredAt(LocalDateTime.of(2026, 11, 19, 10, 30));
        entity.setIsAggregated(false);
        entity.setUserId(userId);
        entity.setApiKeyId(apiKeyId);
        return entity;
    }

    @BeforeEach
    void setUp() {
        service = new ExceptionManagementService(exceptionEventRepository, exceptionPersistenceService);
    }

    @Test
    @DisplayName("本人视角：详情越界返回 null（不泄露存在性）")
    void detailOutOfScopeReturnsNull() {
        when(exceptionEventRepository.findByEventId("e-bob-key-b")).thenReturn(Optional.of(row("bob", "key-b")));

        assertNull(service.getExceptionEventById("e-bob-key-b", DataScope.of("alice").withApiKeyIds(Set.of("key-a"))));
        assertNotNull(service.getExceptionEventById("e-bob-key-b", DataScope.unrestricted()));
    }

    @Test
    @DisplayName("本人视角：按 userId 命中即可见")
    void detailInScopeByUserId() {
        when(exceptionEventRepository.findByEventId("e-alice-key-b")).thenReturn(Optional.of(row("alice", "key-b")));

        assertNotNull(service.getExceptionEventById("e-alice-key-b", DataScope.of("alice")));
    }

    @Test
    @DisplayName("本人视角：最近异常只保留自己或自己密钥触发的那些")
    void recentFilteredByOwnership() {
        when(exceptionEventRepository.findAll(any(Specification.class), any(org.springframework.data.domain.Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(row("alice", "key-b"), row("bob", "key-b"), row("carol", "key-a"))));

        List<ExceptionEventDTO> events = service.getRecentExceptionEvents(
                10, DataScope.of("alice").withApiKeyIds(Set.of("key-a")));

        // alice 的两条（userId 命中 + 本人密钥命中），bob 的那条不在范围内
        assertEquals(2, events.size());
        assertTrue(events.stream().noneMatch(event -> "bob".equals(event.getUserId())));
    }

    @Test
    @DisplayName("本人视角：统计只算自己的行，且口径与全局版一致")
    void statisticsAggregatedFromOwnRows() {
        when(exceptionEventRepository.findAll(any(Specification.class), any(org.springframework.data.domain.Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(row("alice", "key-a"), row("alice", "key-a"), row("bob", "key-b"))));

        ExceptionStatisticsDTO stats = service.getExceptionStatistics(
                LocalDateTime.now().minusDays(7), LocalDateTime.now(),
                DataScope.of("alice").withApiKeyIds(Set.of("key-a")));

        assertEquals(2L, stats.getTotalCount());
        assertEquals(2L, stats.getByType().get("NullPointerException"));
        assertEquals(0L, stats.getUnaggregatedCount() - 2L);
        assertEquals(2L, stats.getAggregatedCount() + 2L);
        // 小时分布按 "HH" 归桶，两条都在 10 点
        assertNotNull(stats.getHourlyDistribution());
        assertEquals(1, stats.getHourlyDistribution().size());
    }

    @Test
    @DisplayName("不受限视角：不受归属字段影响")
    void unrestrictedSeesEverything() {
        when(exceptionEventRepository.findAll(any(Specification.class), any(org.springframework.data.domain.Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(row("alice", "key-a"), row("bob", "key-b"))));

        ExceptionStatisticsDTO stats = service.getExceptionStatistics(
                LocalDateTime.now().minusDays(7), LocalDateTime.now(), DataScope.unrestricted());

        // 不受限走库内聚合（mock 未配置，返回 0），这里只断言没有走进内存分支
        assertEquals(0L, stats.getTotalCount());
    }
}
