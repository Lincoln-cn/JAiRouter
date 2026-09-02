package org.unreal.modelrouter.auth.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.unreal.modelrouter.auth.security.authentication.impl.DefaultJwtTokenValidator;
import org.unreal.modelrouter.auth.security.config.properties.JwtAccountProperties;
import org.unreal.modelrouter.auth.security.config.properties.JwtConfig;
import org.unreal.modelrouter.auth.security.config.properties.SecurityProperties;
import org.unreal.modelrouter.auth.security.model.JwtAuthentication;
import org.unreal.modelrouter.auth.security.permission.PermissionCodes;
import org.unreal.modelrouter.auth.security.service.AccountManager;
import org.unreal.modelrouter.auth.security.service.RolePermissionService;
import org.unreal.modelrouter.persistence.jpa.repository.JwtAccountRepository;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * JWT permissions claim 生成与校验测试（v2.9.8 RBAC）
 *
 * 覆盖：
 * <ul>
 *   <li>AccountManager 签发令牌时写入 permissions claim（roles → 权限码）</li>
 *   <li>DefaultJwtTokenValidator 校验时提取 permissions，映射为无前缀 authority</li>
 *   <li>令牌刷新后 permissions claim 保留</li>
 * </ul>
 *
 * @author JAiRouter Team
 * @since 2.9.8
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("JWT permissions claim 测试")
class JwtPermissionsClaimTest {

    private static final String TEST_SECRET = "test-secret-key-for-jwt-validation-min-32-chars";
    private static final String TEST_ISSUER = "jairouter-test";
    private static final List<String> USER_PERMISSIONS = List.of(
            PermissionCodes.OVERVIEW_DASHBOARD_READ,
            PermissionCodes.CONFIG_SERVICES_READ,
            PermissionCodes.MONITORING_METRICS_READ);

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

    @Mock
    private ReactiveStringRedisTemplate redisTemplate;

    private SecretKey signingKey;

    @BeforeEach
    void setUp() {
        signingKey = Keys.hmacShaKeyFor(TEST_SECRET.getBytes(StandardCharsets.UTF_8));
        lenient().when(securityProperties.getJwt()).thenReturn(jwtConfig);
        lenient().when(jwtConfig.getSecret()).thenReturn(TEST_SECRET);
        lenient().when(jwtConfig.getIssuer()).thenReturn(TEST_ISSUER);
        lenient().when(jwtConfig.getExpirationMinutes()).thenReturn(30L);
        lenient().when(jwtConfig.getRefreshExpirationDays()).thenReturn(7L);
        lenient().when(jwtConfig.isBlacklistEnabled()).thenReturn(false);
    }

    @Nested
    @DisplayName("AccountManager 签发")
    class TokenGenerationTests {

        @Test
        @DisplayName("登录签发令牌包含 permissions claim（按账户角色查询）")
        void generatedTokenContainsPermissionsClaim() {
            // Given：admin 账户（roles=ADMIN+USER），RolePermissionService 返回模拟权限码
            JwtAccountProperties account = new JwtAccountProperties();
            account.setUsername("admin");
            account.setPassword("{noop}secret");
            account.setRoles(List.of("ADMIN", "USER"));
            account.setEnabled(true);
            when(jwtConfig.isEnabled()).thenReturn(true);
            when(jwtConfig.getAccounts()).thenReturn(List.of(account));
            when(passwordEncoder.matches("secret", "{noop}secret")).thenReturn(true);
            when(rolePermissionService.getPermissionCodesForRoles(any())).thenReturn(USER_PERMISSIONS);

            AccountManager accountManager =
                    new AccountManager(securityProperties, passwordEncoder, rolePermissionService,
                            jwtAccountRepository, objectMapper);

            // When
            Mono<String> tokenMono = accountManager.authenticateAndGenerateToken(
                    "admin", "secret", securityProperties);

            // Then
            StepVerifier.create(tokenMono)
                    .assertNext(token -> {
                        Claims claims = parseClaims(token);
                        assertEquals(List.of("ADMIN", "USER"), claims.get("roles", List.class));
                        assertEquals(USER_PERMISSIONS, claims.get("permissions", List.class));
                    })
                    .verifyComplete();
        }

        @Test
        @DisplayName("无角色账户签发令牌 permissions claim 为空列表")
        void generatedTokenPermissionsEmptyWhenNoRoles() {
            JwtAccountProperties account = new JwtAccountProperties();
            account.setUsername("ghost");
            account.setPassword("{noop}secret");
            account.setRoles(List.of());
            account.setEnabled(true);
            when(jwtConfig.isEnabled()).thenReturn(true);
            when(jwtConfig.getAccounts()).thenReturn(List.of(account));
            when(passwordEncoder.matches("secret", "{noop}secret")).thenReturn(true);
            when(rolePermissionService.getPermissionCodesForRoles(any())).thenReturn(List.of());

            AccountManager accountManager =
                    new AccountManager(securityProperties, passwordEncoder, rolePermissionService,
                            jwtAccountRepository, objectMapper);

            StepVerifier.create(accountManager.authenticateAndGenerateToken(
                            "ghost", "secret", securityProperties))
                    .assertNext(token -> {
                        Claims claims = parseClaims(token);
                        assertEquals(List.of(), claims.get("permissions", List.class));
                    })
                    .verifyComplete();
        }
    }

    @Nested
    @DisplayName("DefaultJwtTokenValidator 校验")
    class TokenValidationTests {

        @Test
        @DisplayName("校验令牌时 permissions 映射为无前缀 authority，roles 映射为 ROLE_ 前缀")
        void validateTokenMapsPermissionsToPlainAuthorities() {
            DefaultJwtTokenValidator validator =
                    new DefaultJwtTokenValidator(securityProperties, redisTemplate);
            String token = validator.generateToken("user", List.of("USER"),
                    java.util.Map.of("permissions", USER_PERMISSIONS));

            Mono<Authentication> result = validator.validateToken(token);

            StepVerifier.create(result)
                    .assertNext(auth -> {
                        assertTrue(auth instanceof JwtAuthentication);
                        List<String> authorities = auth.getAuthorities().stream()
                                .map(GrantedAuthority::getAuthority)
                                .toList();
                        // 角色 → ROLE_ 前缀
                        assertTrue(authorities.contains("ROLE_USER"));
                        // 权限码 → 无前缀，且不加 ROLE_
                        assertTrue(authorities.contains(PermissionCodes.OVERVIEW_DASHBOARD_READ));
                        assertTrue(authorities.contains(PermissionCodes.CONFIG_SERVICES_READ));
                        assertTrue(authorities.contains(PermissionCodes.MONITORING_METRICS_READ));
                        assertTrue(authorities.stream()
                                .noneMatch(a -> a.startsWith("ROLE_" + PermissionCodes.OVERVIEW_DASHBOARD_READ)));
                    })
                    .verifyComplete();
        }

        @Test
        @DisplayName("旧令牌（无 permissions claim）校验返回空权限，不影响角色")
        void validateTokenWithoutPermissionsClaimKeepsRoles() {
            DefaultJwtTokenValidator validator =
                    new DefaultJwtTokenValidator(securityProperties, redisTemplate);
            String token = validator.generateToken("user", List.of("ADMIN"), java.util.Map.of());

            Mono<Authentication> result = validator.validateToken(token);

            StepVerifier.create(result)
                    .assertNext(auth -> {
                        assertTrue(auth instanceof JwtAuthentication);
                        List<String> authorities = auth.getAuthorities().stream()
                                .map(GrantedAuthority::getAuthority)
                                .toList();
                        assertTrue(authorities.contains("ROLE_ADMIN"));
                        assertTrue(authorities.stream()
                                .noneMatch(a -> !a.startsWith("ROLE_")));
                    })
                    .verifyComplete();
        }

        @Test
        @DisplayName("刷新后的令牌保留 permissions claim")
        void refreshedTokenPreservesPermissionsClaim() {
            DefaultJwtTokenValidator validator =
                    new DefaultJwtTokenValidator(securityProperties, redisTemplate);
            String token = validator.generateToken("user", List.of("USER"),
                    java.util.Map.of("permissions", USER_PERMISSIONS));

            Mono<String> refreshed = validator.refreshToken(token);

            StepVerifier.create(refreshed)
                    .assertNext(newToken -> {
                        Claims claims = parseClaims(newToken);
                        assertEquals(USER_PERMISSIONS, claims.get("permissions", List.class));
                        assertEquals(List.of("USER"), claims.get("roles", List.class));
                    })
                    .verifyComplete();
        }
    }

    private Claims parseClaims(final String token) {
        return Jwts.parser()
                .verifyWith(signingKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
