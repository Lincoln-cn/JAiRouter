package org.unreal.modelrouter.auth.security.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.unreal.modelrouter.common.dto.CreateJwtAccountRequest;
import org.unreal.modelrouter.common.dto.JwtAccountDTO;
import org.unreal.modelrouter.persistence.jpa.entity.JwtAccountEntity;
import org.unreal.modelrouter.persistence.jpa.repository.JwtAccountRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * JwtAccountService 单元测试
 *
 * <p>测试JWT账户管理功能</p>
 *
 * @version v2.10.0
 * @since 2026-05-24
 */
@DisplayName("JwtAccountService JWT账户服务测试")
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class JwtAccountServiceTest {

    @Mock
    private JwtAccountRepository jwtAccountRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Spy
    private ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks
    private JwtAccountService jwtAccountService;

    private JwtAccountEntity testEntity;
    private CreateJwtAccountRequest createRequest;

    @BeforeEach
    void setUp() {
        // 设置测试实体
        testEntity = JwtAccountEntity.builder()
                .id(1L)
                .username("testuser")
                .password("encoded-password")
                .roles("[\"ADMIN\",\"USER\"]")
                .enabled(true)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        // 设置创建请求
        createRequest = new CreateJwtAccountRequest();
        createRequest.setUsername("newuser");
        createRequest.setPassword("password123");
        createRequest.setRoles(List.of("USER"));
        createRequest.setEnabled(true);

        // 默认mock行为
        when(passwordEncoder.encode(anyString())).thenReturn("encoded-password");
        when(passwordEncoder.matches(anyString(), anyString())).thenReturn(true);
    }

    // ==================== 获取所有账户测试 ====================

    @Nested
    @DisplayName("获取所有账户测试")
    class GetAllAccountsTests {

        @Test
        @DisplayName("JWT-ACC-001: 获取所有账户成功")
        void testGetAllAccounts_Success() throws JsonProcessingException {
            // Given
            when(jwtAccountRepository.findAll()).thenReturn(List.of(testEntity));

            // When
            List<JwtAccountDTO> result = jwtAccountService.getAllAccounts();

            // Then
            assertNotNull(result);
            assertEquals(1, result.size());
            assertEquals("testuser", result.get(0).getUsername());
            verify(jwtAccountRepository).findAll();
        }

        @Test
        @DisplayName("JWT-ACC-002: 空账户列表返回空列表")
        void testGetAllAccounts_EmptyList() {
            // Given
            when(jwtAccountRepository.findAll()).thenReturn(List.of());

            // When
            List<JwtAccountDTO> result = jwtAccountService.getAllAccounts();

            // Then
            assertNotNull(result);
            assertTrue(result.isEmpty());
        }
    }

    // ==================== 获取单个账户测试 ====================

    @Nested
    @DisplayName("获取单个账户测试")
    class GetAccountTests {

        @Test
        @DisplayName("JWT-ACC-003: 获取存在的账户成功")
        void testGetAccount_Success() {
            // Given
            when(jwtAccountRepository.findByUsername("testuser")).thenReturn(Optional.of(testEntity));

            // When
            JwtAccountDTO result = jwtAccountService.getAccount("testuser");

            // Then
            assertNotNull(result);
            assertEquals("testuser", result.getUsername());
            assertTrue(result.getEnabled());
        }

        @Test
        @DisplayName("JWT-ACC-004: 获取不存在的账户抛出异常")
        void testGetAccount_NotFound() {
            // Given
            when(jwtAccountRepository.findByUsername("nonexistent")).thenReturn(Optional.empty());

            // When & Then
            assertThrows(RuntimeException.class, () -> jwtAccountService.getAccount("nonexistent"));
        }
    }

    // ==================== 创建账户测试 ====================

    @Nested
    @DisplayName("创建账户测试")
    class CreateAccountTests {

        @Test
        @DisplayName("JWT-ACC-005: 创建账户成功")
        void testCreateAccount_Success() {
            // Given
            when(jwtAccountRepository.existsByUsername("newuser")).thenReturn(false);
            when(jwtAccountRepository.save(any(JwtAccountEntity.class))).thenAnswer(invocation -> {
                JwtAccountEntity entity = invocation.getArgument(0);
                entity.setId(2L);
                entity.setCreatedAt(LocalDateTime.now());
                entity.setUpdatedAt(LocalDateTime.now());
                return entity;
            });

            // When
            JwtAccountDTO result = jwtAccountService.createAccount(createRequest);

            // Then
            assertNotNull(result);
            assertEquals("newuser", result.getUsername());
            verify(passwordEncoder).encode("password123");
            verify(jwtAccountRepository).save(any(JwtAccountEntity.class));
        }

        @Test
        @DisplayName("JWT-ACC-006: 创建已存在的用户名抛出异常")
        void testCreateAccount_UsernameExists() {
            // Given
            JwtAccountEntity existing = JwtAccountEntity.builder()
                    .id(9L)
                    .username("existinguser")
                    .password("encoded-password")
                    .roles("[\"USER\"]")
                    .enabled(true)
                    .build();
            when(jwtAccountRepository.findByUsername("existinguser")).thenReturn(Optional.of(existing));
            createRequest.setUsername("existinguser");

            // When & Then
            assertThrows(RuntimeException.class, () -> jwtAccountService.createAccount(createRequest));
            verify(jwtAccountRepository, never()).save(any());
        }

        @Test
        @DisplayName("JWT-ACC-007: 创建账户时enabled为null默认为true")
        void testCreateAccount_DefaultEnabled() {
            // Given
            createRequest.setEnabled(null);
            when(jwtAccountRepository.existsByUsername("newuser")).thenReturn(false);
            when(jwtAccountRepository.save(any(JwtAccountEntity.class))).thenAnswer(invocation -> {
                JwtAccountEntity entity = invocation.getArgument(0);
                entity.setId(2L);
                assertTrue(entity.getEnabled()); // 验证默认为true
                return entity;
            });

            // When
            JwtAccountDTO result = jwtAccountService.createAccount(createRequest);

            // Then
            assertNotNull(result);
        }
    }

    // ==================== 更新账户测试 ====================

    @Nested
    @DisplayName("更新账户测试")
    class UpdateAccountTests {

        @Test
        @DisplayName("JWT-ACC-008: 更新账户密码成功")
        void testUpdateAccount_UpdatePassword() {
            // Given
            CreateJwtAccountRequest updateRequest = new CreateJwtAccountRequest();
            updateRequest.setPassword("newpassword");

            when(jwtAccountRepository.findByUsername("testuser")).thenReturn(Optional.of(testEntity));
            when(jwtAccountRepository.save(any(JwtAccountEntity.class))).thenReturn(testEntity);

            // When
            JwtAccountDTO result = jwtAccountService.updateAccount("testuser", updateRequest);

            // Then
            assertNotNull(result);
            verify(passwordEncoder).encode("newpassword");
            verify(jwtAccountRepository).save(any(JwtAccountEntity.class));
        }

        @Test
        @DisplayName("JWT-ACC-009: 更新账户角色成功")
        void testUpdateAccount_UpdateRoles() {
            // Given
            CreateJwtAccountRequest updateRequest = new CreateJwtAccountRequest();
            updateRequest.setRoles(List.of("ADMIN", "SUPERUSER"));

            when(jwtAccountRepository.findByUsername("testuser")).thenReturn(Optional.of(testEntity));
            when(jwtAccountRepository.save(any(JwtAccountEntity.class))).thenReturn(testEntity);

            // When
            JwtAccountDTO result = jwtAccountService.updateAccount("testuser", updateRequest);

            // Then
            assertNotNull(result);
            verify(jwtAccountRepository).save(any(JwtAccountEntity.class));
        }

        @Test
        @DisplayName("JWT-ACC-010: 更新不存在的账户抛出异常")
        void testUpdateAccount_NotFound() {
            // Given
            when(jwtAccountRepository.findByUsername("nonexistent")).thenReturn(Optional.empty());

            // When & Then
            assertThrows(RuntimeException.class, () -> jwtAccountService.updateAccount("nonexistent", createRequest));
        }
    }

    // ==================== 删除账户测试 ====================

    @Nested
    @DisplayName("删除账户测试")
    class DeleteAccountTests {

        @Test
        @DisplayName("JWT-ACC-011: 删除账户为软删除（issue #789：行必须留着，否则 YAML 会复活）")
        void testDeleteAccount_Success() {
            // Given
            when(jwtAccountRepository.findByUsername("testuser")).thenReturn(Optional.of(testEntity));

            // When
            jwtAccountService.deleteAccount("testuser");

            // Then
            assertTrue(testEntity.getDeleted());
            verify(jwtAccountRepository).save(testEntity);
            verify(jwtAccountRepository, never()).delete(any(JwtAccountEntity.class));
        }

        @Test
        @DisplayName("JWT-ACC-012: 删除不存在的账户不执行删除")
        void testDeleteAccount_NotFound() {
            // Given
            when(jwtAccountRepository.findByUsername("nonexistent")).thenReturn(Optional.empty());

            // When
            jwtAccountService.deleteAccount("nonexistent");

            // Then
            verify(jwtAccountRepository, never()).delete(any());
        }
    }

    // ==================== 验证密码测试 ====================

    @Nested
    @DisplayName("验证密码测试")
    class VerifyPasswordTests {

        @Test
        @DisplayName("JWT-ACC-013: 密码验证成功")
        void testVerifyPassword_Success() {
            // Given
            when(jwtAccountRepository.findByUsername("testuser")).thenReturn(Optional.of(testEntity));
            when(passwordEncoder.matches("password123", "encoded-password")).thenReturn(true);

            // When
            boolean result = jwtAccountService.verifyPassword("testuser", "password123");

            // Then
            assertTrue(result);
        }

        @Test
        @DisplayName("JWT-ACC-014: 密码验证失败")
        void testVerifyPassword_WrongPassword() {
            // Given
            when(jwtAccountRepository.findByUsername("testuser")).thenReturn(Optional.of(testEntity));
            when(passwordEncoder.matches("wrongpassword", "encoded-password")).thenReturn(false);

            // When
            boolean result = jwtAccountService.verifyPassword("testuser", "wrongpassword");

            // Then
            assertFalse(result);
        }

        @Test
        @DisplayName("JWT-ACC-015: 用户不存在返回false")
        void testVerifyPassword_UserNotFound() {
            // Given
            when(jwtAccountRepository.findByUsername("nonexistent")).thenReturn(Optional.empty());

            // When
            boolean result = jwtAccountService.verifyPassword("nonexistent", "password");

            // Then
            assertFalse(result);
        }
    }

    // ==================== 切换账户状态测试 ====================

    @Nested
    @DisplayName("切换账户状态测试")
    class ToggleAccountStatusTests {

        @Test
        @DisplayName("JWT-ACC-016: 禁用账户成功")
        void testToggleAccountStatus_Disable() {
            // Given
            when(jwtAccountRepository.findByUsername("testuser")).thenReturn(Optional.of(testEntity));
            when(jwtAccountRepository.save(any(JwtAccountEntity.class))).thenAnswer(invocation -> {
                JwtAccountEntity entity = invocation.getArgument(0);
                assertFalse(entity.getEnabled());
                return entity;
            });

            // When
            JwtAccountDTO result = jwtAccountService.toggleAccountStatus("testuser", false);

            // Then
            assertNotNull(result);
            verify(jwtAccountRepository).save(any(JwtAccountEntity.class));
        }

        @Test
        @DisplayName("JWT-ACC-017: 启用账户成功")
        void testToggleAccountStatus_Enable() {
            // Given
            testEntity.setEnabled(false);
            when(jwtAccountRepository.findByUsername("testuser")).thenReturn(Optional.of(testEntity));
            when(jwtAccountRepository.save(any(JwtAccountEntity.class))).thenAnswer(invocation -> {
                JwtAccountEntity entity = invocation.getArgument(0);
                assertTrue(entity.getEnabled());
                return entity;
            });

            // When
            JwtAccountDTO result = jwtAccountService.toggleAccountStatus("testuser", true);

            // Then
            assertNotNull(result);
            verify(jwtAccountRepository).save(any(JwtAccountEntity.class));
        }

        @Test
        @DisplayName("JWT-ACC-018: 切换不存在账户状态抛出异常")
        void testToggleAccountStatus_NotFound() {
            // Given
            when(jwtAccountRepository.findByUsername("nonexistent")).thenReturn(Optional.empty());

            // When & Then
            assertThrows(RuntimeException.class, () -> jwtAccountService.toggleAccountStatus("nonexistent", false));
        }
    }

    // ==================== 角色解析测试 ====================

    @Nested
    @DisplayName("角色解析测试")
    class RoleParsingTests {

        @Test
        @DisplayName("JWT-ACC-019: 正确解析JSON角色")
        void testRoleParsing_ValidJson() {
            // Given
            when(jwtAccountRepository.findAll()).thenReturn(List.of(testEntity));

            // When
            List<JwtAccountDTO> result = jwtAccountService.getAllAccounts();

            // Then
            assertEquals(1, result.size());
            List<String> roles = result.get(0).getRoles();
            assertNotNull(roles);
            assertEquals(2, roles.size());
            assertTrue(roles.contains("ADMIN"));
            assertTrue(roles.contains("USER"));
        }

        @Test
        @DisplayName("JWT-ACC-020: 角色为null时返回空列表")
        void testRoleParsing_NullRoles() {
            // Given
            testEntity.setRoles(null);
            when(jwtAccountRepository.findAll()).thenReturn(List.of(testEntity));

            // When
            List<JwtAccountDTO> result = jwtAccountService.getAllAccounts();

            // Then
            assertEquals(1, result.size());
            List<String> roles = result.get(0).getRoles();
            assertNotNull(roles);
            assertTrue(roles.isEmpty());
        }
    }

    // ==================== issue #789：软删除与 enabled 写入 ====================

    @Nested
    @DisplayName("#789 软删除与启用状态写入")
    class SoftDeleteTests {

        @Test
        @DisplayName("#789-01: 已软删除的账户不出现在列表里")
        void deletedAccountIsHiddenFromList() {
            // Given
            JwtAccountEntity deleted = JwtAccountEntity.builder()
                    .id(3L)
                    .username("gone")
                    .password("encoded")
                    .roles("[\"USER\"]")
                    .enabled(false)
                    .deleted(Boolean.TRUE)
                    .build();
            when(jwtAccountRepository.findAll()).thenReturn(List.of(testEntity, deleted));

            // When
            List<JwtAccountDTO> result = jwtAccountService.getAllAccounts();

            // Then
            assertEquals(1, result.size());
            assertEquals("testuser", result.get(0).getUsername());
        }

        @Test
        @DisplayName("#789-02: 已软删除的账户按不存在处理（getAccount 抛异常）")
        void deletedAccountIsNotFound() {
            // Given
            testEntity.setDeleted(Boolean.TRUE);
            when(jwtAccountRepository.findByUsername("testuser")).thenReturn(Optional.of(testEntity));

            // When & Then
            assertThrows(RuntimeException.class, () -> jwtAccountService.getAccount("testuser"));
        }

        @Test
        @DisplayName("#789-03: 更新账户时 enabled 必须落库（此前被静默丢弃）")
        void updateAccountAppliesEnabled() {
            // Given
            when(jwtAccountRepository.findByUsername("testuser")).thenReturn(Optional.of(testEntity));
            when(jwtAccountRepository.save(any(JwtAccountEntity.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));
            CreateJwtAccountRequest request = new CreateJwtAccountRequest();
            request.setEnabled(false);

            // When
            JwtAccountDTO result = jwtAccountService.updateAccount("testuser", request);

            // Then
            assertFalse(testEntity.getEnabled());
            assertFalse(result.getEnabled());
            assertEquals("encoded-password", testEntity.getPassword());
        }

        @Test
        @DisplayName("#789-04: 重建同名账户时复活被软删除的行（不撞唯一键、按新口令启用）")
        void recreateRevivesSoftDeletedRow() {
            // Given
            JwtAccountEntity deleted = JwtAccountEntity.builder()
                    .id(4L)
                    .username("newuser")
                    .password("old-encoded")
                    .roles("[\"USER\"]")
                    .enabled(false)
                    .deleted(Boolean.TRUE)
                    .build();
            when(jwtAccountRepository.findByUsername("newuser")).thenReturn(Optional.of(deleted));
            when(jwtAccountRepository.save(any(JwtAccountEntity.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            // When
            JwtAccountDTO result = jwtAccountService.createAccount(createRequest);

            // Then
            assertFalse(deleted.getDeleted());
            assertEquals("encoded-password", deleted.getPassword());
            assertEquals("newuser", result.getUsername());
            assertTrue(result.getEnabled());
        }

        @Test
        @DisplayName("#789-05: 已软删除的账户不能靠 verifyPassword 通过")
        void deletedAccountFailsVerifyPassword() {
            // Given
            testEntity.setDeleted(Boolean.TRUE);
            when(jwtAccountRepository.findByUsername("testuser")).thenReturn(Optional.of(testEntity));

            // When & Then
            assertFalse(jwtAccountService.verifyPassword("testuser", "password123"));
            verify(passwordEncoder, never()).matches(anyString(), anyString());
        }
    }
}
