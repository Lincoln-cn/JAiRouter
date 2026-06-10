package org.unreal.modelrouter.common.exceptionhandler;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ServerWebInputException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import org.unreal.modelrouter.common.controller.response.RouterResponse;
import org.unreal.modelrouter.monitor.monitoring.error.ErrorTracker;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * ServerExceptionHandler 单元测试
 */
@ExtendWith(MockitoExtension.class)
class ServerExceptionHandlerTest {

    @Mock
    private ErrorTracker errorTracker;

    private ServerExceptionHandler handler;

    @BeforeEach
    void setUp() {
        handler = new ServerExceptionHandler(errorTracker);
    }

    @Nested
    @DisplayName("ServerWebInputException 处理测试")
    class ServerWebInputExceptionTests {

        @Test
        @DisplayName("处理请求体读取异常 - 返回400")
        void testHandleServerWebInputException() {
            // Arrange
            ServerWebInputException ex = new ServerWebInputException("Invalid request body");

            // Act
            RouterResponse<Void> response = handler.handleException(ex);

            // Assert
            assertNotNull(response);
            assertEquals("400", response.getErrorCode());
            assertTrue(response.getMessage().contains("请求体无效或缺失"));
            verify(errorTracker).trackError(any(), anyString(), any());
        }
    }

    @Nested
    @DisplayName("ResponseStatusException 处理测试")
    class ResponseStatusExceptionTests {

        @Test
        @DisplayName("处理404异常")
        void testHandleResponseStatusException_NotFound() {
            // Arrange
            ResponseStatusException ex = new ResponseStatusException(HttpStatus.NOT_FOUND, "Resource not found");

            // Act
            RouterResponse<Void> response = handler.handleException(ex);

            // Assert
            assertNotNull(response);
            assertEquals("404", response.getErrorCode());
            assertTrue(response.getMessage().contains("请求处理失败"));
            verify(errorTracker).trackError(any(), anyString(), any());
        }

        @Test
        @DisplayName("处理500异常")
        void testHandleResponseStatusException_InternalError() {
            // Arrange
            ResponseStatusException ex = new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Server error");

            // Act
            RouterResponse<Void> response = handler.handleException(ex);

            // Assert
            assertNotNull(response);
            assertEquals("500", response.getErrorCode());
        }
    }

    @Nested
    @DisplayName("通用异常处理测试")
    class GeneralExceptionTests {

        @Test
        @DisplayName("处理RuntimeException")
        void testHandleRuntimeException() {
            // Arrange
            RuntimeException ex = new RuntimeException("Something went wrong");

            // Act
            RouterResponse<Void> response = handler.handleException(ex);

            // Assert
            assertNotNull(response);
            assertEquals("500", response.getErrorCode());
            assertTrue(response.getMessage().contains("系统异常"));
            verify(errorTracker).trackError(any(), anyString(), any());
        }

        @Test
        @DisplayName("处理空消息异常")
        void testHandleExceptionWithNullMessage() {
            // Arrange
            RuntimeException ex = new RuntimeException((String) null);

            // Act
            RouterResponse<Void> response = handler.handleException(ex);

            // Assert
            assertNotNull(response);
            assertEquals("500", response.getErrorCode());
            // 当消息为null时，应返回类名
            assertTrue(response.getMessage().contains("RuntimeException"));
        }

        @Test
        @DisplayName("处理Exception类型")
        void testHandleException() {
            // Arrange
            Exception ex = new Exception("General exception");

            // Act
            RouterResponse<Void> response = handler.handleException(ex);

            // Assert
            assertNotNull(response);
            assertEquals("500", response.getErrorCode());
        }
    }

    @Nested
    @DisplayName("ErrorTracker 错误记录测试")
    class ErrorTrackerTests {

        @Test
        @DisplayName("验证错误追踪被调用")
        void testErrorTrackerIsCalled() {
            // Arrange
            RuntimeException ex = new RuntimeException("Test error");

            // Act
            handler.handleException(ex);

            // Assert
            verify(errorTracker).trackError(eq(ex), eq("global_exception_handling"), any());
        }

        @Test
        @DisplayName("错误追踪失败不影响异常处理")
        void testErrorTrackerFailureDoesNotAffectHandling() {
            // Arrange
            doThrow(new RuntimeException("Tracker error")).when(errorTracker).trackError(any(), anyString(), any());
            RuntimeException ex = new RuntimeException("Test error");

            // Act
            RouterResponse<Void> response = handler.handleException(ex);

            // Assert
            assertNotNull(response);
            assertEquals("500", response.getErrorCode());
        }
    }
}
