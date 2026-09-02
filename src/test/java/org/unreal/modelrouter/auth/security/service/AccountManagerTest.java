package org.unreal.modelrouter.auth.security.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
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
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.unreal.modelrouter.auth.security.config.properties.JwtAccountProperties;
import org.unreal.modelrouter.auth.security.config.properties.JwtConfig;
import org.unreal.modelrouter.auth.security.config.properties.SecurityProperties;
import org.unreal.modelrouter.persistence.jpa.entity.JwtAccountEntity;
import org.unreal.modelrouter.persistence.jpa.repository.JwtAccountRepository;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * AccountManager 单元测试
 *
 * <p>覆盖 YAML 静态账户优先 + DB 账户兜底的登录认证逻辑：
 * <ul>
 *   <li>YAML 账户登录（现有行为回归）</li>
 *   <li>YAML 未命中时回退到 jwt_accounts 表的 DB 账户</li>
 *   <li>DB 账户角色 JSON 反序列化与禁用/密码错误处理</li>
 * </ul>
 *
 * @author JAiRouter Team
 */
@DisplayName("AccountManager 登录认证测试")
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AccountManagerTest {

    private static final String TEST_SECRET = "test-secret-key-for-jwt-validation-min-32-chars";
    private static final String TEST_ISSUER = "jairouter-test";

    @Mock
    private SecurityProperties securityProperties;

    @Mock
    private JwtConfig jwtConfig;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private RolePermissionService rolePermissionService;

    @Mock
    private JwtAccountRepository jwtAccountRepository;

    @Spy
    private ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks
    private AccountManager accountManager;

    private SecretKey signingKey;

    @BeforeEach
    void setUp() {
        signingKey = Keys.hmacShaKeyFor(TEST_SECRET.getBytes(StandardCharsets.UTF_8));
        when(securityProperties.getJwt()).thenReturn(jwtConfig);
        when(jwtConfig.isEnabled()).thenReturn(true);
        when(jwtConfig.getSecret()).thenReturn(TEST_SECRET);
        when(jwtConfig.getIssuer()).thenReturn(TEST_ISSUER);
        when(jwtConfig.getExpirationMinutes()).thenReturn(30L);
    }

    // ==================== validateCredentials 凭据验证测试 ====================

    @Nested
    @DisplayName("validateCredentials 凭据验证")
    class ValidateCredentialsTests {

        @Test
        @DisplayName("YAML 账户密码正确登录成功（回归）")
        void yamlAccountLoginSucceeds() {
            // Given
            when(jwtConfig.getAccounts()).thenReturn(List.of(
                    yamlAccount("admin", "{noop}secret", true, List.of("ADMIN"))));
            when(passwordEncoder.matches("secret", "{noop}secret")).thenReturn(true);

            // When
            boolean result = accountManager.validateCredentials("admin", "secret");

            // Then
            assertTrue(result);
            verify(jwtAccountRepository, never()).findByUsername(anyString());
        }

        @Test
        @DisplayName("YAML 未命中 + DB 账户存在且密码正确 → 登录成功，JWT 含 DB 角色对应 permissions")
        void yamlMissDbAccountLoginSucceedsWithPermissions() {
            // Given
            when(jwtConfig.getAccounts()).thenReturn(List.of(
                    yamlAccount("yamluser", "{noop}pw", true, List.of("ADMIN"))));
            JwtAccountEntity entity =
                    dbAccount("dbuser", "{bcrypt}encoded", "[\"OPERATOR\",\"user\"]", true);
            when(jwtAccountRepository.findByUsername("dbuser")).thenReturn(Optional.of(entity));
            when(passwordEncoder.matches("dbpw", "{bcrypt}encoded")).thenReturn(true);
            when(rolePermissionService.getPermissionCodesForRoles(any()))
                    .thenReturn(List.of("CALL_HISTORY_READ"));

            // When
            assertTrue(accountManager.validateCredentials("dbuser", "dbpw"));
            Mono<String> tokenMono =
                    accountManager.authenticateAndGenerateToken("dbuser", "dbpw", securityProperties);

            // Then
            StepVerifier.create(tokenMono)
                    .assertNext(token -> {
                        Claims claims = parseClaims(token);
                        assertEquals(List.of("OPERATOR", "USER"), claims.get("roles", List.class));
                        assertEquals(List.of("CALL_HISTORY_READ"),
                                claims.get("permissions", List.class));
                    })
                    .verifyComplete();
        }

        @Test
        @DisplayName("DB 账户密码错误 → 登录失败")
        void dbAccountWrongPasswordFails() {
            // Given
            when(jwtConfig.getAccounts()).thenReturn(List.of());
            JwtAccountEntity entity =
                    dbAccount("dbuser", "{bcrypt}encoded", "[\"OPERATOR\"]", true);
            when(jwtAccountRepository.findByUsername("dbuser")).thenReturn(Optional.of(entity));
            when(passwordEncoder.matches("wrong", "{bcrypt}encoded")).thenReturn(false);

            // When
            boolean result = accountManager.validateCredentials("dbuser", "wrong");

            // Then
            assertFalse(result);
        }

        @Test
        @DisplayName("DB 账户禁用（enabled=false）→ 登录失败")
        void dbAccountDisabledFails() {
            // Given
            when(jwtConfig.getAccounts()).thenReturn(List.of());
            JwtAccountEntity entity =
                    dbAccount("dbuser", "{bcrypt}encoded", "[\"OPERATOR\"]", false);
            when(jwtAccountRepository.findByUsername("dbuser")).thenReturn(Optional.of(entity));

            // When
            boolean result = accountManager.validateCredentials("dbuser", "pw");

            // Then
            assertFalse(result);
            verify(passwordEncoder, never()).matches(anyString(), anyString());
        }

        @Test
        @DisplayName("YAML 与 DB 均无该用户 → 登录失败")
        void noAccountInYamlOrDbFails() {
            // Given
            when(jwtConfig.getAccounts()).thenReturn(List.of());
            when(jwtAccountRepository.findByUsername("ghost")).thenReturn(Optional.empty());

            // When
            boolean result = accountManager.validateCredentials("ghost", "pw");

            // Then
            assertFalse(result);
        }

        @Test
        @DisplayName("YAML 存在同名账户时以 YAML 结果为准，不查 DB")
        void yamlAccountTakesPriorityOverDb() {
            // Given
            when(jwtConfig.getAccounts()).thenReturn(List.of(
                    yamlAccount("admin", "{noop}yamlpw", true, List.of("ADMIN"))));
            when(passwordEncoder.matches("dbpw", "{noop}yamlpw")).thenReturn(false);

            // When
            boolean result = accountManager.validateCredentials("admin", "dbpw");

            // Then
            assertFalse(result);
            verify(jwtAccountRepository, never()).findByUsername(anyString());
        }

        @Test
        @DisplayName("DB 账户 enabled 为 null 时按启用处理（与 JwtAccountService 约定一致）")
        void dbAccountNullEnabledTreatedAsEnabled() {
            // Given
            when(jwtConfig.getAccounts()).thenReturn(List.of());
            JwtAccountEntity entity =
                    dbAccount("dbuser", "{bcrypt}encoded", "[\"OPERATOR\"]", null);
            when(jwtAccountRepository.findByUsername("dbuser")).thenReturn(Optional.of(entity));
            when(passwordEncoder.matches("pw", "{bcrypt}encoded")).thenReturn(true);

            // When
            boolean result = accountManager.validateCredentials("dbuser", "pw");

            // Then
            assertTrue(result);
        }
    }

    // ==================== loadUserByUsername 用户加载测试 ====================

    @Nested
    @DisplayName("loadUserByUsername 用户加载")
    class LoadUserByUsernameTests {

        @Test
        @DisplayName("DB 账户角色 JSON 反序列化正确并转为大写")
        void dbRolesDeserializedCorrectly() {
            // Given
            when(jwtConfig.getAccounts()).thenReturn(List.of());
            JwtAccountEntity entity =
                    dbAccount("dbuser", "{bcrypt}encoded", "[\"OPERATOR\",\"user\"]", true);
            when(jwtAccountRepository.findByUsername("dbuser")).thenReturn(Optional.of(entity));

            // When
            UserDetails details = accountManager.loadUserByUsername("dbuser");

            // Then
            assertEquals("dbuser", details.getUsername());
            assertEquals("{bcrypt}encoded", details.getPassword());
            assertEquals(List.of("OPERATOR", "USER"), authorities(details));
        }

        @Test
        @DisplayName("DB 账户角色 JSON 非法 → 降级为空角色，不抛异常")
        void dbInvalidRolesDegradeGracefully() {
            // Given
            when(jwtConfig.getAccounts()).thenReturn(List.of());
            JwtAccountEntity entity =
                    dbAccount("dbuser", "{bcrypt}encoded", "not-a-json", true);
            when(jwtAccountRepository.findByUsername("dbuser")).thenReturn(Optional.of(entity));

            // When
            UserDetails details = accountManager.loadUserByUsername("dbuser");

            // Then
            assertNotNull(details);
            assertTrue(details.getAuthorities().isEmpty());
        }

        @Test
        @DisplayName("DB 账户角色为 null → 空角色，不抛异常")
        void dbNullRolesDegradeGracefully() {
            // Given
            when(jwtConfig.getAccounts()).thenReturn(List.of());
            JwtAccountEntity entity =
                    dbAccount("dbuser", "{bcrypt}encoded", null, true);
            when(jwtAccountRepository.findByUsername("dbuser")).thenReturn(Optional.of(entity));

            // When
            UserDetails details = accountManager.loadUserByUsername("dbuser");

            // Then
            assertNotNull(details);
            assertTrue(details.getAuthorities().isEmpty());
        }

        @Test
        @DisplayName("DB 账户禁用 → 抛出 UsernameNotFoundException")
        void dbDisabledThrows() {
            // Given
            when(jwtConfig.getAccounts()).thenReturn(List.of());
            JwtAccountEntity entity =
                    dbAccount("dbuser", "{bcrypt}encoded", "[\"OPERATOR\"]", false);
            when(jwtAccountRepository.findByUsername("dbuser")).thenReturn(Optional.of(entity));

            // When & Then
            assertThrows(UsernameNotFoundException.class,
                    () -> accountManager.loadUserByUsername("dbuser"));
        }

        @Test
        @DisplayName("YAML 与 DB 均无该用户 → 抛出 UsernameNotFoundException")
        void noAccountThrows() {
            // Given
            when(jwtConfig.getAccounts()).thenReturn(List.of());
            when(jwtAccountRepository.findByUsername("ghost")).thenReturn(Optional.empty());

            // When & Then
            assertThrows(UsernameNotFoundException.class,
                    () -> accountManager.loadUserByUsername("ghost"));
        }

        @Test
        @DisplayName("YAML 账户优先于 DB 同名账户")
        void yamlAccountTakesPriority() {
            // Given
            when(jwtConfig.getAccounts()).thenReturn(List.of(
                    yamlAccount("admin", "{noop}secret", true, List.of("ADMIN"))));
            JwtAccountEntity entity =
                    dbAccount("admin", "{bcrypt}encoded", "[\"OPERATOR\"]", true);
            when(jwtAccountRepository.findByUsername("admin")).thenReturn(Optional.of(entity));

            // When
            UserDetails details = accountManager.loadUserByUsername("admin");

            // Then
            assertEquals("{noop}secret", details.getPassword());
            assertEquals(List.of("ADMIN"), authorities(details));
            verify(jwtAccountRepository, never()).findByUsername(anyString());
        }
    }

    private JwtAccountProperties yamlAccount(final String username, final String password,
                                             final boolean enabled, final List<String> roles) {
        JwtAccountProperties account = new JwtAccountProperties();
        account.setUsername(username);
        account.setPassword(password);
        account.setEnabled(enabled);
        account.setRoles(roles);
        return account;
    }

    private JwtAccountEntity dbAccount(final String username, final String password,
                                       final String rolesJson, final Boolean enabled) {
        return JwtAccountEntity.builder()
                .id(1L)
                .username(username)
                .password(password)
                .roles(rolesJson)
                .enabled(enabled)
                .build();
    }

    private List<String> authorities(final UserDetails details) {
        return details.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .toList();
    }

    private Claims parseClaims(final String token) {
        return Jwts.parser()
                .verifyWith(signingKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
