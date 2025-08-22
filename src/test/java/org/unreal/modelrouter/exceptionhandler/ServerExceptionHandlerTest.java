package org.unreal.modelrouter.exceptionhandler;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.unreal.modelrouter.controller.response.RouterResponse;
import org.unreal.modelrouter.monitoring.error.ErrorTracker;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class ServerExceptionHandlerTest {

    @Mock
    private ErrorTracker errorTracker;

    private ServerExceptionHandler exceptionHandler;

    @BeforeEach
    public void setUp() {
        exceptionHandler = new ServerExceptionHandler(errorTracker);
    }

    @Test
    public void testHandleGenericException() {
        // Given
        Exception exception = new Exception("Test exception");

        // When
        RouterResponse<Void> result = exceptionHandler.handleException(exception);

        // Then
        assertNotNull(result);
        assertFalse(result.isSuccess());
        assertEquals("系统异常:Test exception", result.getMessage());
        assertEquals("500", result.getErrorCode());

        verify(errorTracker, times(1)).trackError(any(Exception.class), anyString(), anyMap());
    }
}