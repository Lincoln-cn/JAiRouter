package org.unreal.modelrouter.security.authentication.impl;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.core.ReactiveValueOperations;
import org.unreal.modelrouter.security.config.SecurityProperties;
import org.unreal.modelrouter.exception.AuthenticationException;
import org.unreal.modelrouter.security.model.JwtAuthentication;
import org.unreal.modelrouter.security.model.JwtPrincipal;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import javax.crypto.SecretKey;
import java.time.Duration;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

/**
 * DefaultJwtTokenValidator单元测试
 */
@ExtendWith(MockitoExtension.class)
class DefaultJwtTokenValidatorTest {
    
    @Mock
    private ReactiveStringRedisTemplate redisTemplate;
    
    @Mock
    private ReactiveValueOperations<String, String> valueOperations;
    
    private SecurityProperties securityProperties;
    private DefaultJwtTokenValidator jwtTokenValidator;
    private SecretKey signingKey;
    
    @BeforeEach
    void setUp() {
        // 初始化安全配置
        securityProperties = new SecurityProperties();
        SecurityProperties.JwtConfig jwtConfig = new SecurityProperties.JwtConfig();
        jwtConfig.setEnabled(true);
        jwtConfig.setSecret("test-secret-key-that-is-long-enough-for-hmac-sha256-algorithm");
        jwtConfig.setAlgorithm("HS256");
        jwtConfig.setExpirationMinutes(60);
        jwtConfig.setRefreshExpirationDays(7);
        jwtConfig.setIssuer("jairouter-test");
        jwtConfig.setBlacklistEnabled(true);
        securityProperties.setJwt(jwtConfig);
        
        // 初始化签名密钥
        signingKey = Keys.hmacShaKeyFor(jwtConfig.getSecret().getBytes());
        
        // 模拟Redis操作
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(redisTemplate.hasKey(anyString())).thenReturn(Mono.just(false));
        when(valueOperations.set(anyString(), anyString(), any(Duration.class))).thenReturn(Mono.just(true));
        
        // 创建测试对象
        jwtTokenValidator = new DefaultJwtTokenValidator(securityProperties, redisTemplate);
    }
    
    @Test
    void testValidateToken_ValidToken_ShouldReturnAuthentication() {
        // 准备测试数据
        String subject = "testuser";
        List<String> roles = Arrays.asList("user", "admin");
        String token = createValidToken(subject, roles);
        
        // 执行测试
        StepVerifier.create(jwtTokenValidator.validateToken(token))
            .assertNext(authentication -> {
                assertThat(authentication).isInstanceOf(JwtAuthentication.class);
                assertThat(authentication.isAuthenticated()).isTrue();
                assertThat(authentication.getName()).isEqualTo(subject);
                assertThat(authentication.getAuthorities()).hasSize(2);
                
                JwtPrincipal principal = (JwtPrincipal) authentication.getDetails();
                assertThat(principal.getSubject()).isEqualTo(subject);
                assertThat(principal.getRoles()).containsExactlyInAnyOrder("user", "admin");
                assertThat(principal.getIssuer()).isEqualTo("jairouter-test");
            })
            .verifyComplete();
    }
    
    @Test
    void testValidateToken_ExpiredToken_ShouldReturnError() {
        // 准备过期的令牌
        String subject = "testuser";
        List<String> roles = Arrays.asList("user");
        String token = createExpiredToken(subject, roles);
        
        // 执行测试
        StepVerifier.create(jwtTokenValidator.validateToken(token))
            .expectErrorMatches(throwable -> 
                throwable instanceof AuthenticationException &&
                ((AuthenticationException) throwable).getErrorCode().equals("JWT_EXPIRED"))
            .verify();
    }
    
    @Test
    void testValidateToken_InvalidSignature_ShouldReturnError() {
        // 准备使用错误密钥签名的令牌
        SecretKey wrongKey = Keys.hmacShaKeyFor("wrong-secret-key-for-testing-purposes-only".getBytes());
        String token = Jwts.builder()
            .setSubject("testuser")
            .setIssuer("jairouter-test")
            .setIssuedAt(new Date())
            .setExpiration(new Date(System.currentTimeMillis() + 3600000))
            .claim("roles", Arrays.asList("user"))
            .signWith(wrongKey, SignatureAlgorithm.HS256)
            .compact();
        
        // 执行测试
        StepVerifier.create(jwtTokenValidator.validateToken(token))
            .expectErrorMatches(throwable -> 
                throwable instanceof AuthenticationException &&
                ((AuthenticationException) throwable).getErrorCode().equals("JWT_INVALID"))
            .verify();
    }
    
    @Test
    void testValidateToken_BlacklistedToken_ShouldReturnError() {
        // 准备测试数据
        String subject = "testuser";
        List<String> roles = Arrays.asList("user");
        String token = createValidToken(subject, roles);
        
        // 模拟令牌在黑名单中
        when(redisTemplate.hasKey(anyString())).thenReturn(Mono.just(true));
        
        // 执行测试
        StepVerifier.create(jwtTokenValidator.validateToken(token))
            .expectErrorMatches(throwable -> 
                throwable instanceof AuthenticationException &&
                ((AuthenticationException) throwable).getErrorCode().equals("JWT_BLACKLISTED"))
            .verify();
    }
    
    @Test
    void testRefreshToken_ValidToken_ShouldReturnNewToken() {
        // 准备测试数据
        String subject = "testuser";
        List<String> roles = Arrays.asList("user", "admin");
        String oldToken = createValidToken(subject, roles);
        
        // 执行测试
        StepVerifier.create(jwtTokenValidator.refreshToken(oldToken))
            .assertNext(newToken -> {
                assertThat(newToken).isNotNull();
                assertThat(newToken).isNotEqualTo(oldToken);
                
                // 验证新令牌的有效性
                StepVerifier.create(jwtTokenValidator.validateToken(newToken))
                    .assertNext(authentication -> {
                        assertThat(authentication.getName()).isEqualTo(subject);
                        assertThat(authentication.getAuthorities()).hasSize(2);
                    })
                    .verifyComplete();
            })
            .verifyComplete();
    }
    
    @Test
    void testRefreshToken_ExpiredTokenBeyondRefreshWindow_ShouldReturnError() {
        // 准备超出刷新窗口的过期令牌
        String subject = "testuser";
        List<String> roles = Arrays.asList("user");
        
        Date issuedAt = new Date(System.currentTimeMillis() - Duration.ofDays(10).toMillis());
        Date expiration = new Date(System.currentTimeMillis() - Duration.ofDays(8).toMillis());
        
        String token = Jwts.builder()
            .setSubject(subject)
            .setIssuer("jairouter-test")
            .setIssuedAt(issuedAt)
            .setExpiration(expiration)
            .setId(UUID.randomUUID().toString())
            .claim("roles", roles)
            .signWith(signingKey, SignatureAlgorithm.HS256)
            .compact();
        
        // 执行测试
        StepVerifier.create(jwtTokenValidator.refreshToken(token))
            .expectErrorMatches(throwable -> 
                throwable instanceof AuthenticationException &&
                ((AuthenticationException) throwable).getErrorCode().equals("JWT_REFRESH_EXPIRED"))
            .verify();
    }
    
    @Test
    void testIsTokenBlacklisted_TokenNotInBlacklist_ShouldReturnFalse() {
        // 准备测试数据
        String token = createValidToken("testuser", Arrays.asList("user"));
        
        // 模拟Redis返回false
        when(redisTemplate.hasKey(anyString())).thenReturn(Mono.just(false));
        
        // 执行测试
        StepVerifier.create(jwtTokenValidator.isTokenBlacklisted(token))
            .expectNext(false)
            .verifyComplete();
    }
    
    @Test
    void testIsTokenBlacklisted_TokenInBlacklist_ShouldReturnTrue() {
        // 准备测试数据
        String token = createValidToken("testuser", Arrays.asList("user"));
        
        // 模拟Redis返回true
        when(redisTemplate.hasKey(anyString())).thenReturn(Mono.just(true));
        
        // 执行测试
        StepVerifier.create(jwtTokenValidator.isTokenBlacklisted(token))
            .expectNext(true)
            .verifyComplete();
    }
    
    @Test
    void testBlacklistToken_ValidToken_ShouldComplete() {
        // 准备测试数据
        String token = createValidToken("testuser", Arrays.asList("user"));
        
        // 执行测试
        StepVerifier.create(jwtTokenValidator.blacklistToken(token))
            .verifyComplete();
    }
    
    @Test
    void testExtractUserId_ValidToken_ShouldReturnUserId() {
        // 准备测试数据
        String subject = "testuser";
        String token = createValidToken(subject, Arrays.asList("user"));
        
        // 执行测试
        StepVerifier.create(jwtTokenValidator.extractUserId(token))
            .expectNext(subject)
            .verifyComplete();
    }
    
    @Test
    void testExtractUserId_TokenWithUserIdClaim_ShouldReturnUserIdClaim() {
        // 准备包含userId声明的令牌
        String subject = "testuser";
        String userId = "user123";
        
        String token = Jwts.builder()
            .setSubject(subject)
            .setIssuer("jairouter-test")
            .setIssuedAt(new Date())
            .setExpiration(new Date(System.currentTimeMillis() + 3600000))
            .setId(UUID.randomUUID().toString())
            .claim("roles", Arrays.asList("user"))
            .claim("userId", userId)
            .signWith(signingKey, SignatureAlgorithm.HS256)
            .compact();
        
        // 执行测试
        StepVerifier.create(jwtTokenValidator.extractUserId(token))
            .expectNext(userId)
            .verifyComplete();
    }
    
    @Test
    void testGenerateToken_ValidParameters_ShouldReturnValidToken() {
        // 准备测试数据
        String subject = "testuser";
        List<String> roles = Arrays.asList("user", "admin");
        
        // 执行测试
        String token = jwtTokenValidator.generateToken(subject, roles, null);
        
        // 验证生成的令牌
        assertThat(token).isNotNull();
        assertThat(token).isNotEmpty();
        
        // 验证令牌可以被正确解析
        StepVerifier.create(jwtTokenValidator.validateToken(token))
            .assertNext(authentication -> {
                assertThat(authentication.getName()).isEqualTo(subject);
                assertThat(authentication.getAuthorities()).hasSize(2);
            })
            .verifyComplete();
    }
    
    /**
     * 创建有效的JWT令牌
     */
    private String createValidToken(String subject, List<String> roles) {
        return Jwts.builder()
            .setSubject(subject)
            .setIssuer("jairouter-test")
            .setIssuedAt(new Date())
            .setExpiration(new Date(System.currentTimeMillis() + 3600000)) // 1小时后过期
            .setId(UUID.randomUUID().toString())
            .claim("roles", roles)
            .signWith(signingKey, SignatureAlgorithm.HS256)
            .compact();
    }
    
    /**
     * 创建过期的JWT令牌
     */
    private String createExpiredToken(String subject, List<String> roles) {
        return Jwts.builder()
            .setSubject(subject)
            .setIssuer("jairouter-test")
            .setIssuedAt(new Date(System.currentTimeMillis() - 7200000)) // 2小时前签发
            .setExpiration(new Date(System.currentTimeMillis() - 3600000)) // 1小时前过期
            .setId(UUID.randomUUID().toString())
            .claim("roles", roles)
            .signWith(signingKey, SignatureAlgorithm.HS256)
            .compact();
    }
}