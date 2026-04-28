package org.unreal.modelrouter.api;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import org.unreal.modelrouter.auth.controller.JwtAccountController;
import org.unreal.modelrouter.controller.response.RouterResponse;
import org.unreal.modelrouter.dto.CreateJwtAccountRequest;
import org.unreal.modelrouter.dto.JwtAccountDTO;
import org.unreal.modelrouter.auth.security.service.JwtAccountService;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * JwtAccountController API 测试
 * v1.5.3: 验证 JWT 账户 API 可用性（使用 RouterResponse 包装）
 */
@ExtendWith(MockitoExtension.class)
class JwtAccountApiTest {

    @Mock
    private JwtAccountService jwtAccountService;

    @InjectMocks
    private JwtAccountController jwtAccountController;

    @Test
    void shouldGetAllAccounts() {
        // Given
        List<JwtAccountDTO> accounts = Arrays.asList(
                createJwtAccountDTO(1L, "admin", Arrays.asList("ADMIN")),
                createJwtAccountDTO(2L, "user", Arrays.asList("USER"))
        );
        when(jwtAccountService.getAllAccounts()).thenReturn(accounts);

        // When
        ResponseEntity<RouterResponse<List<JwtAccountDTO>>> response = jwtAccountController.getAllAccounts();

        // Then
        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().isSuccess()).isTrue();
        assertThat(response.getBody().getData()).hasSize(2);
    }

    @Test
    void shouldGetAccountByUsername() {
        // Given
        JwtAccountDTO account = createJwtAccountDTO(1L, "admin", Arrays.asList("ADMIN"));
        when(jwtAccountService.getAccount("admin")).thenReturn(account);

        // When
        ResponseEntity<RouterResponse<JwtAccountDTO>> response = jwtAccountController.getAccount("admin");

        // Then
        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().isSuccess()).isTrue();
        assertThat(response.getBody().getData()).isNotNull();
        assertThat(response.getBody().getData().getUsername()).isEqualTo("admin");
    }

    @Test
    void shouldCreateAccount() {
        // Given
        CreateJwtAccountRequest request = CreateJwtAccountRequest.builder()
                .username("newuser")
                .password("password123")
                .roles(Arrays.asList("USER"))
                .build();
        JwtAccountDTO created = createJwtAccountDTO(1L, "newuser", Arrays.asList("USER"));
        when(jwtAccountService.createAccount(any(CreateJwtAccountRequest.class))).thenReturn(created);

        // When
        ResponseEntity<RouterResponse<JwtAccountDTO>> response = jwtAccountController.createAccount(request);

        // Then
        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().isSuccess()).isTrue();
        assertThat(response.getBody().getData()).isNotNull();
    }

    @Test
    void shouldUpdateAccount() {
        // Given
        CreateJwtAccountRequest request = CreateJwtAccountRequest.builder()
                .username("admin")
                .password("newpassword")
                .roles(Arrays.asList("ADMIN", "USER"))
                .build();
        JwtAccountDTO updated = createJwtAccountDTO(1L, "admin", Arrays.asList("ADMIN", "USER"));
        when(jwtAccountService.updateAccount(eq("admin"), any(CreateJwtAccountRequest.class)))
                .thenReturn(updated);

        // When
        ResponseEntity<RouterResponse<JwtAccountDTO>> response = jwtAccountController.updateAccount("admin", request);

        // Then
        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().isSuccess()).isTrue();
        assertThat(response.getBody().getData()).isNotNull();
    }

    @Test
    void shouldDeleteAccount() {
        // Given
        doNothing().when(jwtAccountService).deleteAccount("user");

        // When
        ResponseEntity<RouterResponse<Void>> response = jwtAccountController.deleteAccount("user");

        // Then
        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().isSuccess()).isTrue();
        verify(jwtAccountService).deleteAccount("user");
    }

    @Test
    void shouldVerifyPassword() {
        // Given
        Map<String, String> credentials = new HashMap<>();
        credentials.put("password", "password123");
        when(jwtAccountService.verifyPassword("admin", "password123")).thenReturn(true);

        // When
        ResponseEntity<RouterResponse<Boolean>> response = jwtAccountController.verifyPassword("admin", credentials);

        // Then
        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().isSuccess()).isTrue();
        assertThat(response.getBody().getData()).isTrue();
    }

    @Test
    void shouldReturnFalseForInvalidPassword() {
        // Given
        Map<String, String> credentials = new HashMap<>();
        credentials.put("password", "wrongpassword");
        when(jwtAccountService.verifyPassword("admin", "wrongpassword")).thenReturn(false);

        // When
        ResponseEntity<RouterResponse<Boolean>> response = jwtAccountController.verifyPassword("admin", credentials);

        // Then
        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().isSuccess()).isTrue();
        assertThat(response.getBody().getData()).isFalse();
    }

    private JwtAccountDTO createJwtAccountDTO(Long id, String username, List<String> roles) {
        return JwtAccountDTO.builder()
                .id(id)
                .username(username)
                .roles(roles)
                .enabled(true)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
    }
}