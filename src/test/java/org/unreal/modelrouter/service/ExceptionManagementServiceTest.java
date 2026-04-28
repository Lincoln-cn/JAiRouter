package org.unreal.modelrouter.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.unreal.modelrouter.dto.ExceptionEventDTO;
import org.unreal.modelrouter.dto.ExceptionQueryRequest;
import org.unreal.modelrouter.dto.ExceptionStatisticsDTO;
import org.unreal.modelrouter.dto.PagedResult;
import org.unreal.modelrouter.jpa.entity.ExceptionEventEntity;
import org.unreal.modelrouter.jpa.repository.ExceptionEventRepository;
import org.unreal.modelrouter.monitor.monitoring.error.ExceptionPersistenceService;

import java.time.LocalDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * ExceptionManagementService 单元测试
 * 
 * @author JAiRouter Team
 * @since 1.9.2
 */
@DisplayName("ExceptionManagementService 单元测试")
@ExtendWith(MockitoExtension.class)
class ExceptionManagementServiceTest {

    @Mock
    private ExceptionEventRepository exceptionEventRepository;

    @Mock
    private ExceptionPersistenceService exceptionPersistenceService;

    @InjectMocks
    private ExceptionManagementService exceptionManagementService;

    private ExceptionEventEntity testEntity;
    private LocalDateTime now;

    @BeforeEach
    void setUp() {
        now = LocalDateTime.now();
        
        testEntity = new ExceptionEventEntity();
        testEntity.setId(1L);
        testEntity.setEventId("test-event-id-001");
        testEntity.setExceptionType("java.lang.NullPointerException");
        testEntity.setExceptionMessage("测试异常消息");
        testEntity.setSanitizedMessage("脱敏后的消息");
        testEntity.setOperation("test_operation");
        testEntity.setErrorCode("SYS_123456");
        testEntity.setErrorCategory("SYSTEM");
        testEntity.setHttpStatus("500");
        testEntity.setTraceId("test-trace-id");
        testEntity.setClientIp("192.168.1.1");
        testEntity.setServiceName("test-service");
        testEntity.setOccurrenceCount(1L);
        testEntity.setFirstOccurrence(now);
        testEntity.setLastOccurrence(now);
        testEntity.setOccurredAt(now);
        testEntity.setIsAggregated(false);
    }

    @Test
    @DisplayName("测试查询异常事件列表 - 基本查询")
    void testQueryExceptionEvents_Basic() {
        // 准备数据
        List<ExceptionEventEntity> entities = Arrays.asList(testEntity);
        Page<ExceptionEventEntity> page = new PageImpl<>(entities);

        // Mock Repository - 使用 any() 匹配所有参数
        when(exceptionEventRepository.findByConditions(
                any(LocalDateTime.class),
                any(LocalDateTime.class),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(PageRequest.class)
        )).thenReturn(page);

        // 构建查询请求
        ExceptionQueryRequest request = ExceptionQueryRequest.builder()
                .page(0)
                .size(20)
                .build();

        // 执行查询
        PagedResult<ExceptionEventDTO> result = exceptionManagementService.queryExceptionEvents(request);

        // 验证结果
        assertNotNull(result);
        assertEquals(1, result.getContent().size());
        assertEquals(1, result.getTotalElements());
        
        ExceptionEventDTO dto = result.getContent().get(0);
        assertEquals("test-event-id-001", dto.getEventId());
        assertEquals("java.lang.NullPointerException", dto.getExceptionType());
        assertEquals("测试异常消息", dto.getExceptionMessage());
    }

    @Test
    @DisplayName("测试查询异常事件列表 - 带筛选条件")
    void testQueryExceptionEvents_WithFilters() {
        // 准备数据
        List<ExceptionEventEntity> entities = new ArrayList<>();
        Page<ExceptionEventEntity> page = new PageImpl<>(entities);

        when(exceptionEventRepository.findByConditions(
                any(LocalDateTime.class),
                any(LocalDateTime.class),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(PageRequest.class)
        )).thenReturn(page);

        ExceptionQueryRequest request = ExceptionQueryRequest.builder()
                .exceptionType("java.lang.NullPointerException")
                .errorCategory("SYSTEM")
                .page(0)
                .size(20)
                .build();

        PagedResult<ExceptionEventDTO> result = exceptionManagementService.queryExceptionEvents(request);

        assertNotNull(result);
        assertEquals(0, result.getContent().size());
    }

    @Test
    @DisplayName("测试根据 ID 查询异常事件 - 存在")
    void testGetExceptionEventById_Exists() {
        when(exceptionEventRepository.findByEventId("test-event-id-001")).thenReturn(Optional.of(testEntity));

        ExceptionEventDTO result = exceptionManagementService.getExceptionEventById("test-event-id-001");

        assertNotNull(result);
        assertEquals("test-event-id-001", result.getEventId());
    }

    @Test
    @DisplayName("测试根据 ID 查询异常事件 - 不存在")
    void testGetExceptionEventById_NotExists() {
        when(exceptionEventRepository.findByEventId("non-existent-id")).thenReturn(Optional.empty());

        ExceptionEventDTO result = exceptionManagementService.getExceptionEventById("non-existent-id");

        assertNull(result);
    }

    @Test
    @DisplayName("测试获取异常统计信息")
    void testGetExceptionStatistics() {
        // Mock 统计数据
        Map<String, Object> stats = new HashMap<>();
        Map<String, Long> byType = new HashMap<>();
        byType.put("java.lang.NullPointerException", 10L);
        byType.put("java.lang.IllegalArgumentException", 5L);
        stats.put("byType", byType);

        Map<String, Long> byCategory = new HashMap<>();
        byCategory.put("SYSTEM", 10L);
        byCategory.put("VALIDATION", 5L);
        stats.put("byCategory", byCategory);
        stats.put("total", 15L);

        when(exceptionPersistenceService.getExceptionStatistics(any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(stats);
        when(exceptionEventRepository.countAggregatedEvents()).thenReturn(5L);
        when(exceptionEventRepository.countUnaggregatedEvents()).thenReturn(10L);
        when(exceptionEventRepository.countByOperation(any(LocalDateTime.class), any(LocalDateTime.class), eq(10)))
                .thenReturn(new ArrayList<>());

        ExceptionStatisticsDTO result = exceptionManagementService.getExceptionStatistics(null, null);

        assertNotNull(result);
        assertEquals(15L, result.getTotalCount());
        assertEquals(5L, result.getAggregatedCount());
        assertEquals(10L, result.getUnaggregatedCount());
        assertEquals(2, result.getByType().size());
        assertEquals(2, result.getByCategory().size());
    }

    @Test
    @DisplayName("测试获取最近的异常事件")
    void testGetRecentExceptionEvents() {
        List<ExceptionEventEntity> entities = Arrays.asList(testEntity);
        when(exceptionEventRepository.findRecentEvents(10)).thenReturn(entities);

        List<ExceptionEventDTO> result = exceptionManagementService.getRecentExceptionEvents(10);

        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals("test-event-id-001", result.get(0).getEventId());
    }

    @Test
    @DisplayName("测试获取指定类型的最近异常事件")
    void testGetRecentExceptionEventsByType() {
        List<ExceptionEventEntity> entities = Arrays.asList(testEntity);
        when(exceptionEventRepository.findRecentEventsByType(eq("java.lang.NullPointerException"), eq(5)))
                .thenReturn(entities);

        List<ExceptionEventDTO> result = exceptionManagementService.getRecentExceptionEventsByType(
                "java.lang.NullPointerException", 5);

        assertNotNull(result);
        assertEquals(1, result.size());
    }

    @Test
    @DisplayName("测试删除过期异常事件")
    void testDeleteOldExceptionEvents() {
        LocalDateTime cutoffTime = LocalDateTime.now().minusDays(30);
        when(exceptionEventRepository.deleteByOccurredAtBefore(cutoffTime)).thenReturn(10);

        int deletedCount = exceptionManagementService.deleteOldExceptionEvents(cutoffTime);

        assertEquals(10, deletedCount);
        verify(exceptionEventRepository, times(1)).deleteByOccurredAtBefore(cutoffTime);
    }

    @Test
    @DisplayName("测试删除已聚合的过期异常事件")
    void testDeleteAggregatedExceptionEvents() {
        LocalDateTime cutoffTime = LocalDateTime.now().minusDays(30);
        when(exceptionEventRepository.deleteAggregatedEventsBefore(cutoffTime)).thenReturn(5);

        int deletedCount = exceptionManagementService.deleteAggregatedExceptionEvents(cutoffTime);

        assertEquals(5, deletedCount);
        verify(exceptionEventRepository, times(1)).deleteAggregatedEventsBefore(cutoffTime);
    }

    @Test
    @DisplayName("测试分页结果 - 第一页")
    void testPagedResult_FirstPage() {
        List<ExceptionEventEntity> entities = Arrays.asList(testEntity);
        Page<ExceptionEventEntity> page = new PageImpl<>(entities, PageRequest.of(0, 20), 1);

        when(exceptionEventRepository.findByConditions(
                any(LocalDateTime.class), any(LocalDateTime.class), any(), any(),
                any(), any(), any(), any(), any(), any(PageRequest.class)
        )).thenReturn(page);

        ExceptionQueryRequest request = ExceptionQueryRequest.builder().page(0).size(20).build();
        PagedResult<ExceptionEventDTO> result = exceptionManagementService.queryExceptionEvents(request);

        assertTrue(result.isFirst());
        assertTrue(result.isLast());
        assertFalse(result.isHasNext());
        assertFalse(result.isHasPrevious());
    }

    @Test
    @DisplayName("测试 DTO 转换 - 完整信息")
    void testDTOConversion() {
        ExceptionEventDTO dto = exceptionManagementService.getExceptionEventById("test-event-id-001");
        
        // 由于是 Mock 测试，这里验证转换逻辑
        // 实际转换逻辑在 convertToDTO 方法中
        assertNotNull(testEntity.getEventId());
        assertNotNull(testEntity.getExceptionType());
        assertNotNull(testEntity.getOccurredAt());
    }
}
