package org.unreal.modelrouter.monitor.callhistory;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.unreal.modelrouter.monitor.callhistory.config.CallHistoryProperties;

import static org.mockito.Mockito.*;

/**
 * ApiCallHistoryCleanupScheduler 单元测试
 *
 * @author JAiRouter Team
 * @since 2.7.8
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ApiCallHistoryCleanupScheduler 测试")
class ApiCallHistoryCleanupSchedulerTest {

    @Mock
    private ApiCallHistoryService service;

    @Mock
    private CallHistoryProperties properties;

    @InjectMocks
    private ApiCallHistoryCleanupScheduler scheduler;

    @Test
    @DisplayName("清理过期数据成功")
    void testCleanupSuccess() {
        when(properties.getRetentionDays()).thenReturn(30);
        when(service.cleanupByRetentionDays()).thenReturn(10);

        scheduler.cleanup();

        verify(service).cleanupByRetentionDays();
    }

    @Test
    @DisplayName("清理时异常不抛出")
    void testCleanupException() {
        when(service.cleanupByRetentionDays()).thenThrow(new RuntimeException("Database error"));

        scheduler.cleanup();

        verify(service).cleanupByRetentionDays();
    }
}
