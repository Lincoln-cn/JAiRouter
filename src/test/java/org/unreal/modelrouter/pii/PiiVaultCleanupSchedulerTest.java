package org.unreal.modelrouter.pii;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("过期可逆脱敏映射的定时清理")
class PiiVaultCleanupSchedulerTest {

    @Mock
    private ReversiblePiiService reversiblePiiService;

    @InjectMocks
    private PiiVaultCleanupScheduler scheduler;

    @Test
    @DisplayName("到点就调用一次清理")
    void cleanupInvokesServiceOnce() {
        when(reversiblePiiService.cleanupExpired()).thenReturn(3);

        scheduler.cleanup();

        verify(reversiblePiiService).cleanupExpired();
    }

    @Test
    @DisplayName("清理抛异常不能影响调度线程")
    void exceptionIsSwallowed() {
        when(reversiblePiiService.cleanupExpired()).thenThrow(new IllegalStateException("db down"));

        scheduler.cleanup();

        verify(reversiblePiiService).cleanupExpired();
    }
}
