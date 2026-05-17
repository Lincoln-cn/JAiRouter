package org.unreal.modelrouter.auth.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.unreal.modelrouter.auth.security.service.JwtAccountService;
import org.unreal.modelrouter.common.controller.response.RouterResponse;
import org.unreal.modelrouter.common.dto.CreateJwtAccountRequest;
import org.unreal.modelrouter.common.dto.JwtAccountDTO;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * JwtAccountController 单元测试
 *
 * <p>测试 JWT 账户管理功能</p>
 *
 * @version v2.7.6
 * @since 2025-01-17
 */
@DisplayName("JwtAccountController RESTful 接口测试")
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class JwtAccountControllerTest {

    @Mock
    private JwtAccountService jwtAccountService;

    @InjectMocks
    private JwtAccountController controller;

    private JwtAccountDTO testAccount;
    private CreateJwtAccountRequest testRequest;

    @BeforeEach
    void setUp() {
        testAccount = JwtAccountDTO.builder()
                .id(1L)
                .username("testuser")
                .roles(List.of("USER"))
                .enabled(true)
                .createdAt(LocalDateTime.now())
                .build();

        testRequest = CreateJwtAccountRequest.builder()
                .username("testuser")
                .password("password123")
                .roles(List.of("USER"))
                .build();
    }

    // ==================== 获取所有账户测试 ====================

    @Nested
    @DisplayName("GET /api/security/jwt/accounts - 获取所有账户测试")
    class GetAllAccountsTests {

        @Test
        @DisplayName("JWT-001: 成功获取所有账户")
        void testGetAllAccounts_success() {
            // Given
            when(jwtAccountService.getAllAccounts()).thenReturn(List.of(testAccount));

            // When
            ResponseEntity<RouterResponse<List<JwtAccountDTO>>> result = controller.getAllAccounts();

            // Then
            assertEquals(HttpStatus.OK, result.getStatusCode());
            assertTrue(result.getBody().isSuccess());
            assertEquals(1, result.getBody().getData().size());
        }
    }

    // ==================== 获取单个账户测试 ====================

    @Nested
    @DisplayName("GET /api/security/jwt/accounts/{username} - 获取单个账户测试")
    class GetAccountTests {

        @Test
        @DisplayName("JWT-002: 成功获取账户")
        void testGetAccount_success() {
            // Given
            when(jwtAccountService.getAccount("testuser")).thenReturn(testAccount);

            // When
            ResponseEntity<RouterResponse<JwtAccountDTO>> result = controller.getAccount("testuser");

            // Then
            assertEquals(HttpStatus.OK, result.getStatusCode());
            assertTrue(result.getBody().isSuccess());
            assertEquals("testuser", result.getBody().getData().getUsername());
        }
    }

    // ==================== 创建账户测试 ====================

    @Nested
    @DisplayName("POST /api/security/jwt/accounts - 创建账户测试")
    class CreateAccountTests {

        @Test
        @DisplayName("JWT-003: 成功创建账户")
        void testCreateAccount_success() {
            // Given
            when(jwtAccountService.createAccount(any())).thenReturn(testAccount);

            // When
            ResponseEntity<RouterResponse<JwtAccountDTO>> result = controller.createAccount(testRequest);

            // Then
            assertEquals(HttpStatus.OK, result.getStatusCode());
            assertTrue(result.getBody().isSuccess());
            assertEquals("testuser", result.getBody().getData().getUsername());
        }
    }

    // ==================== 更新账户测试 ====================

    @Nested
    @DisplayName("PUT /api/security/jwt/accounts/{username} - 更新账户测试")
    class UpdateAccountTests {

        @Test
        @DisplayName("JWT-004: 成功更新账户")
        void testUpdateAccount_success() {
            // Given
            JwtAccountDTO updated = JwtAccountDTO.builder()
                    .id(1L)
                    .username("testuser")
                    .roles(List.of("ADMIN"))
                    .enabled(true)
                    .build();
            when(jwtAccountService.updateAccount(anyString(), any())).thenReturn(updated);

            // When
            ResponseEntity<RouterResponse<JwtAccountDTO>> result = controller.updateAccount("testuser", testRequest);

            // Then
            assertEquals(HttpStatus.OK, result.getStatusCode());
            assertTrue(result.getBody().isSuccess());
            assertTrue(result.getBody().getData().getRoles().contains("ADMIN"));
        }
    }

    // ==================== 删除账户测试 ====================

    @Nested
    @DisplayName("DELETE /api/security/jwt/accounts/{username} - 删除账户测试")
    class DeleteAccountTests {

        @Test
        @DisplayName("JWT-005: 成功删除账户")
        void testDeleteAccount_success() {
            // Given
            doNothing().when(jwtAccountService).deleteAccount("testuser");

            // When
            ResponseEntity<RouterResponse<Void>> result = controller.deleteAccount("testuser");

            // Then
            assertEquals(HttpStatus.OK, result.getStatusCode());
            assertTrue(result.getBody().isSuccess());
            verify(jwtAccountService, times(1)).deleteAccount("testuser");
        }
    }

    // ==================== 验证密码测试 ====================

    @Nested
    @DisplayName("POST /api/security/jwt/accounts/{username}/verify - 验证密码测试")
    class VerifyPasswordTests {

        @Test
        @DisplayName("JWT-006: 密码验证成功")
        void testVerifyPassword_valid() {
            // Given
            when(jwtAccountService.verifyPassword("testuser", "password123")).thenReturn(true);

            // When
            ResponseEntity<RouterResponse<Boolean>> result = controller.verifyPassword("testuser", 
                    Map.of("password", "password123"));

            // Then
            assertEquals(HttpStatus.OK, result.getStatusCode());
            assertTrue(result.getBody().getData());
        }

        @Test
        @DisplayName("JWT-007: 密码验证失败")
        void testVerifyPassword_invalid() {
            // Given
            when(jwtAccountService.verifyPassword("testuser", "wrongpassword")).thenReturn(false);

            // When
            ResponseEntity<RouterResponse<Boolean>> result = controller.verifyPassword("testuser", 
                    Map.of("password", "wrongpassword"));

            // Then
            assertEquals(HttpStatus.OK, result.getStatusCode());
            assertFalse(result.getBody().getData());
        }
    }

    // ==================== 切换账户状态测试 ====================

    @Nested
    @DisplayName("PATCH /api/security/jwt/accounts/{username}/status - 切换账户状态测试")
    class ToggleAccountStatusTests {

        @Test
        @DisplayName("JWT-008: 成功禁用账户")
        void testToggleAccountStatus_disable() {
            // Given
            JwtAccountDTO disabled = JwtAccountDTO.builder()
                    .id(1L)
                    .username("testuser")
                    .enabled(false)
                    .build();
            when(jwtAccountService.toggleAccountStatus(anyString(), anyBoolean())).thenReturn(disabled);

            // When
            ResponseEntity<RouterResponse<JwtAccountDTO>> result = controller.toggleAccountStatus("testuser", false);

            // Then
            assertEquals(HttpStatus.OK, result.getStatusCode());
            assertFalse(result.getBody().getData().getEnabled());
        }

        @Test
        @DisplayName("JWT-009: 成功启用账户")
        void testToggleAccountStatus_enable() {
            // Given
            when(jwtAccountService.toggleAccountStatus(anyString(), anyBoolean())).thenReturn(testAccount);

            // When
            ResponseEntity<RouterResponse<JwtAccountDTO>> result = controller.toggleAccountStatus("testuser", true);

            // Then
            assertEquals(HttpStatus.OK, result.getStatusCode());
            assertTrue(result.getBody().getData().getEnabled());
        }
    }
}
