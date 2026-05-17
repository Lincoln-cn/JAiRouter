package org.unreal.modelrouter.auth.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import org.unreal.modelrouter.auth.security.service.JwtAccountService;
import org.unreal.modelrouter.common.controller.response.RouterResponse;
import org.unreal.modelrouter.common.dto.CreateJwtAccountRequest;
import org.unreal.modelrouter.common.dto.JwtAccountDTO;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doNothing;
import static org.junit.jupiter.api.Assertions.*;

/**
 * JwtAccountController RESTful 接口测试
 * 
 * 测试范围：
 * - GET /api/security/jwt/accounts - 获取所有账户
 * - GET /api/security/jwt/accounts/{username} - 获取单个账户
 * - POST /api/security/jwt/accounts - 创建账户
 * - PUT /api/security/jwt/accounts/{username} - 更新账户
 * - DELETE /api/security/jwt/accounts/{username} - 删除账户
 * - POST /api/security/jwt/accounts/{username}/verify - 验证密码
 * - PATCH /api/security/jwt/accounts/{username}/status - 切换状态
 * 
 * v2.7.6: 使用 Mockito 单元测试方式（避免 WebFluxTest 上下文加载问题）
 */
@DisplayName("JwtAccountController RESTful 接口测试")
@ExtendWith(MockitoExtension.class)
class JwtAccountControllerIntegrationTest {

    @Mock
    private JwtAccountService jwtAccountService;

    @InjectMocks
    private JwtAccountController controller;

    private JwtAccountDTO adminAccount;
    private JwtAccountDTO userAccount;

    @BeforeEach
    void setUp() {
        adminAccount = new JwtAccountDTO();
        adminAccount.setUsername("admin");
        adminAccount.setEnabled(true);
        adminAccount.setRoles(Arrays.asList("ADMIN", "USER"));

        userAccount = new JwtAccountDTO();
        userAccount.setUsername("testuser");
        userAccount.setEnabled(true);
        userAccount.setRoles(Arrays.asList("USER"));
    }

    // ==================== 获取账户列表测试 ====================

    @Nested
    @DisplayName("GET /api/security/jwt/accounts - 获取账户列表测试")
    class GetAllAccountsTests {

        @Test
        @DisplayName("ACC-001: 获取所有账户成功")
        void testGetAllAccounts_success() {
            // Given
            List<JwtAccountDTO> accounts = Arrays.asList(adminAccount, userAccount);
            when(jwtAccountService.getAllAccounts()).thenReturn(accounts);

            // When
            ResponseEntity<RouterResponse<List<JwtAccountDTO>>> response = controller.getAllAccounts();

            // Then
            assertTrue(response.getStatusCode().is2xxSuccessful());
            assertNotNull(response.getBody());
            assertTrue(response.getBody().isSuccess());
            assertEquals(2, response.getBody().getData().size());
            assertEquals("admin", response.getBody().getData().get(0).getUsername());
            assertEquals("testuser", response.getBody().getData().get(1).getUsername());
            assertEquals("获取账户列表成功", response.getBody().getMessage());
        }

        @Test
        @DisplayName("ACC-002: 空账户列表")
        void testGetAllAccounts_empty() {
            // Given
            when(jwtAccountService.getAllAccounts()).thenReturn(Arrays.asList());

            // When
            ResponseEntity<RouterResponse<List<JwtAccountDTO>>> response = controller.getAllAccounts();

            // Then
            assertTrue(response.getStatusCode().is2xxSuccessful());
            assertNotNull(response.getBody());
            assertTrue(response.getBody().isSuccess());
            assertTrue(response.getBody().getData().isEmpty());
        }
    }

    // ==================== 获取单个账户测试 ====================

    @Nested
    @DisplayName("GET /api/security/jwt/accounts/{username} - 获取单个账户测试")
    class GetAccountTests {

        @Test
        @DisplayName("ACC-003: 获取存在的账户成功")
        void testGetAccount_success() {
            // Given
            when(jwtAccountService.getAccount("admin")).thenReturn(adminAccount);

            // When
            ResponseEntity<RouterResponse<JwtAccountDTO>> response = controller.getAccount("admin");

            // Then
            assertTrue(response.getStatusCode().is2xxSuccessful());
            assertNotNull(response.getBody());
            assertTrue(response.getBody().isSuccess());
            assertEquals("admin", response.getBody().getData().getUsername());
            assertTrue(response.getBody().getData().getEnabled());
            assertEquals("获取账户信息成功", response.getBody().getMessage());
        }

        @Test
        @DisplayName("ACC-004: 获取不存在的账户 - 抛出异常")
        void testGetAccount_notFound() {
            // Given
            when(jwtAccountService.getAccount("nonexistent"))
                    .thenThrow(new RuntimeException("Account not found"));

            // When & Then
            assertThrows(RuntimeException.class, () -> {
                controller.getAccount("nonexistent");
            });
        }
    }

    // ==================== 创建账户测试 ====================

    @Nested
    @DisplayName("POST /api/security/jwt/accounts - 创建账户测试")
    class CreateAccountTests {

        @Test
        @DisplayName("ACC-005: 创建账户成功")
        void testCreateAccount_success() {
            // Given
            CreateJwtAccountRequest request = new CreateJwtAccountRequest();
            request.setUsername("newuser");
            request.setPassword("password123");
            request.setRoles(Arrays.asList("USER"));

            JwtAccountDTO createdAccount = new JwtAccountDTO();
            createdAccount.setUsername("newuser");
            createdAccount.setEnabled(true);
            createdAccount.setRoles(Arrays.asList("USER"));

            when(jwtAccountService.createAccount(any())).thenReturn(createdAccount);

            // When
            ResponseEntity<RouterResponse<JwtAccountDTO>> response = controller.createAccount(request);

            // Then
            assertTrue(response.getStatusCode().is2xxSuccessful());
            assertNotNull(response.getBody());
            assertTrue(response.getBody().isSuccess());
            assertEquals("newuser", response.getBody().getData().getUsername());
            assertEquals("账户创建成功", response.getBody().getMessage());
        }

        @Test
        @DisplayName("ACC-006: 创建账户失败 - 用户名已存在")
        void testCreateAccount_duplicateUsername() {
            // Given
            CreateJwtAccountRequest request = new CreateJwtAccountRequest();
            request.setUsername("admin");
            request.setPassword("password123");
            request.setRoles(Arrays.asList("USER"));

            when(jwtAccountService.createAccount(any()))
                    .thenThrow(new RuntimeException("Username already exists"));

            // When & Then
            assertThrows(RuntimeException.class, () -> {
                controller.createAccount(request);
            });
        }
    }

    // ==================== 更新账户测试 ====================

    @Nested
    @DisplayName("PUT /api/security/jwt/accounts/{username} - 更新账户测试")
    class UpdateAccountTests {

        @Test
        @DisplayName("ACC-008: 更新账户成功")
        void testUpdateAccount_success() {
            // Given
            CreateJwtAccountRequest request = new CreateJwtAccountRequest();
            request.setPassword("newpassword123");
            request.setRoles(Arrays.asList("USER", "ADMIN"));

            JwtAccountDTO updatedAccount = new JwtAccountDTO();
            updatedAccount.setUsername("admin");
            updatedAccount.setEnabled(true);
            updatedAccount.setRoles(Arrays.asList("USER", "ADMIN"));

            when(jwtAccountService.updateAccount(anyString(), any())).thenReturn(updatedAccount);

            // When
            ResponseEntity<RouterResponse<JwtAccountDTO>> response = controller.updateAccount("admin", request);

            // Then
            assertTrue(response.getStatusCode().is2xxSuccessful());
            assertNotNull(response.getBody());
            assertTrue(response.getBody().isSuccess());
            assertEquals("admin", response.getBody().getData().getUsername());
            assertEquals("账户更新成功", response.getBody().getMessage());
        }
    }

    // ==================== 删除账户测试 ====================

    @Nested
    @DisplayName("DELETE /api/security/jwt/accounts/{username} - 删除账户测试")
    class DeleteAccountTests {

        @Test
        @DisplayName("ACC-010: 删除账户成功")
        void testDeleteAccount_success() {
            // Given
            doNothing().when(jwtAccountService).deleteAccount("testuser");

            // When
            ResponseEntity<RouterResponse<Void>> response = controller.deleteAccount("testuser");

            // Then
            assertTrue(response.getStatusCode().is2xxSuccessful());
            assertNotNull(response.getBody());
            assertTrue(response.getBody().isSuccess());
            assertEquals("账户删除成功", response.getBody().getMessage());
        }
    }

    // ==================== 验证密码测试 ====================

    @Nested
    @DisplayName("POST /api/security/jwt/accounts/{username}/verify - 验证密码测试")
    class VerifyPasswordTests {

        @Test
        @DisplayName("ACC-012: 密码验证成功")
        void testVerifyPassword_success() {
            // Given
            Map<String, String> credentials = Map.of("password", "correctpassword");
            when(jwtAccountService.verifyPassword("admin", "correctpassword")).thenReturn(true);

            // When
            ResponseEntity<RouterResponse<Boolean>> response = controller.verifyPassword("admin", credentials);

            // Then
            assertTrue(response.getStatusCode().is2xxSuccessful());
            assertNotNull(response.getBody());
            assertTrue(response.getBody().isSuccess());
            assertTrue(response.getBody().getData());
            assertEquals("密码验证完成", response.getBody().getMessage());
        }

        @Test
        @DisplayName("ACC-013: 密码验证失败")
        void testVerifyPassword_wrongPassword() {
            // Given
            Map<String, String> credentials = Map.of("password", "wrongpassword");
            when(jwtAccountService.verifyPassword("admin", "wrongpassword")).thenReturn(false);

            // When
            ResponseEntity<RouterResponse<Boolean>> response = controller.verifyPassword("admin", credentials);

            // Then
            assertTrue(response.getStatusCode().is2xxSuccessful());
            assertNotNull(response.getBody());
            assertTrue(response.getBody().isSuccess());
            assertFalse(response.getBody().getData());
        }
    }

    // ==================== 切换账户状态测试 ====================

    @Nested
    @DisplayName("PATCH /api/security/jwt/accounts/{username}/status - 切换状态测试")
    class ToggleStatusTests {

        @Test
        @DisplayName("ACC-014: 禁用账户成功")
        void testToggleStatus_disable() {
            // Given
            JwtAccountDTO disabledAccount = new JwtAccountDTO();
            disabledAccount.setUsername("testuser");
            disabledAccount.setEnabled(false);
            disabledAccount.setRoles(Arrays.asList("USER"));

            when(jwtAccountService.toggleAccountStatus("testuser", false)).thenReturn(disabledAccount);

            // When
            ResponseEntity<RouterResponse<JwtAccountDTO>> response = controller.toggleAccountStatus("testuser", false);

            // Then
            assertTrue(response.getStatusCode().is2xxSuccessful());
            assertNotNull(response.getBody());
            assertTrue(response.getBody().isSuccess());
            assertFalse(response.getBody().getData().getEnabled());
            assertEquals("账户已禁用", response.getBody().getMessage());
        }

        @Test
        @DisplayName("ACC-015: 启用账户成功")
        void testToggleStatus_enable() {
            // Given
            JwtAccountDTO enabledAccount = new JwtAccountDTO();
            enabledAccount.setUsername("testuser");
            enabledAccount.setEnabled(true);
            enabledAccount.setRoles(Arrays.asList("USER"));

            when(jwtAccountService.toggleAccountStatus("testuser", true)).thenReturn(enabledAccount);

            // When
            ResponseEntity<RouterResponse<JwtAccountDTO>> response = controller.toggleAccountStatus("testuser", true);

            // Then
            assertTrue(response.getStatusCode().is2xxSuccessful());
            assertNotNull(response.getBody());
            assertTrue(response.getBody().isSuccess());
            assertTrue(response.getBody().getData().getEnabled());
            assertEquals("账户已启用", response.getBody().getMessage());
        }
    }
}
