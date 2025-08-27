package org.unreal.modelrouter.security.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.unreal.modelrouter.security.authentication.JwtTokenValidator;
import org.unreal.modelrouter.security.config.SecurityProperties;
import org.unreal.modelrouter.exception.exception.AuthenticationException;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * JwtTokenRefreshService单元测试
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class JwtTokenRefreshServiceTest {
    
    @Mock
    private JwtTokenValidator jwtTokenValidator;
    
    private SecurityProperties securityProperties;
    private JwtTokenRefreshService tokenRefreshService;
    
    @BeforeEach
    void setUp() {
        // 初始化安全配置
        securityProperties = new SecurityProperties();
        SecurityProperties.JwtConfig jwtConfig = new SecurityProperties.JwtConfig();
        jwtConfig.setEnabled(true);
        jwtConfig.setExpirationMinutes(60);
        jwtConfig.setRefreshExpirationDays(7);
        jwtConfig.setBlacklistEnabled(true);
        securityProperties.setJwt(jwtConfig);
        
        // 创建测试对象
        tokenRefreshService = new JwtTokenRefreshService(jwtTokenValidator, securityProperties);
    }
    
    @Test
    void testRefreshToken_ValidToken_ShouldReturnNewToken() {
        // 准备测试数据
        String currentToken = "current.jwt.token";
        String newToken = "new.jwt.token";
        
        // 模拟JWT验证器返回新令牌
        when(jwtTokenValidator.refreshToken(currentToken)).thenReturn(Mono.just(newToken));
        
        // 执行测试
        StepVerifier.create(tokenRefreshService.refreshToken(currentToken))
            .expectNext(newToken)
            .verifyComplete();
        
        // 验证JWT验证器被调用
        verify(jwtTokenValidator).refreshToken(currentToken);
    }
    
    @Test
    void testRefreshToken_NullToken_ShouldReturnError() {
        // 执行测试
        StepVerifier.create(tokenRefreshService.refreshToken(null))
            .expectErrorMatches(throwable -> 
                throwable instanceof AuthenticationException &&
                ((AuthenticationException) throwable).getErrorCode().equals("TOKEN_REQUIRED"))
            .verify();
        
        // 验证JWT验证器未被调用
        verify(jwtTokenValidator, never()).refreshToken(anyString());
    }
    
    @Test
    void testRefreshToken_EmptyToken_ShouldReturnError() {
        // 执行测试
        StepVerifier.create(tokenRefreshService.refreshToken(""))
            .expectErrorMatches(throwable -> 
                throwable instanceof AuthenticationException &&
                ((AuthenticationException) throwable).getErrorCode().equals("TOKEN_REQUIRED"))
            .verify();
        
        // 验证JWT验证器未被调用
        verify(jwtTokenValidator, never()).refreshToken(anyString());
    }
    
    @Test
    void testRefreshToken_ExpiredToken_ShouldReturnError() {
        // 准备测试数据
        String expiredToken = "expired.jwt.token";
        
        // 模拟JWT验证器返回错误
        AuthenticationException authException = new AuthenticationException("令牌已过期", "JWT_EXPIRED");
        when(jwtTokenValidator.refreshToken(expiredToken)).thenReturn(Mono.error(authException));
        
        // 执行测试
        StepVerifier.create(tokenRefreshService.refreshToken(expiredToken))
            .expectErrorMatches(throwable -> 
                throwable instanceof AuthenticationException &&
                ((AuthenticationException) throwable).getErrorCode().equals("JWT_EXPIRED"))
            .verify();
        
        // 验证JWT验证器被调用
        verify(jwtTokenValidator).refreshToken(expiredToken);
    }
    
    @Test
    void testRevokeToken_ValidToken_ShouldComplete() {
        // 准备测试数据
        String token = "valid.jwt.token";
        
        // 模拟JWT验证器成功将令牌加入黑名单
        when(jwtTokenValidator.blacklistToken(token)).thenReturn(Mono.empty());
        
        // 执行测试
        StepVerifier.create(tokenRefreshService.revokeToken(token))
            .verifyComplete();
        
        // 验证JWT验证器被调用
        verify(jwtTokenValidator).blacklistToken(token);
    }
    
    @Test
    void testRevokeToken_NullToken_ShouldReturnError() {
        // 执行测试
        StepVerifier.create(tokenRefreshService.revokeToken(null))
            .expectErrorMatches(throwable -> 
                throwable instanceof AuthenticationException &&
                ((AuthenticationException) throwable).getErrorCode().equals("TOKEN_REQUIRED"))
            .verify();
        
        // 验证JWT验证器未被调用
        verify(jwtTokenValidator, never()).blacklistToken(anyString());
    }
    
    @Test
    void testRevokeToken_EmptyToken_ShouldReturnError() {
        // 执行测试
        StepVerifier.create(tokenRefreshService.revokeToken("   "))
            .expectErrorMatches(throwable -> 
                throwable instanceof AuthenticationException &&
                ((AuthenticationException) throwable).getErrorCode().equals("TOKEN_REQUIRED"))
            .verify();
        
        // 验证JWT验证器未被调用
        verify(jwtTokenValidator, never()).blacklistToken(anyString());
    }
    
    @Test
    void testRevokeToken_RedisFailure_ShouldStillComplete() {
        // 准备测试数据
        String token = "valid.jwt.token";
        
        // 模拟Redis操作失败
        RuntimeException redisException = new RuntimeException("Redis connection failed");
        when(jwtTokenValidator.blacklistToken(token)).thenReturn(Mono.error(redisException));
        
        // 执行测试（应该仍然完成，因为会加入内存黑名单）
        StepVerifier.create(tokenRefreshService.revokeToken(token))
            .verifyComplete();
        
        // 验证JWT验证器被调用
        verify(jwtTokenValidator).blacklistToken(token);
    }
    
    @Test
    void testIsTokenValid_ValidToken_ShouldReturnTrue() {
        // 准备测试数据
        String token = "valid.jwt.token";
        
        // 模拟JWT验证器返回令牌不在黑名单中
        when(jwtTokenValidator.isTokenBlacklisted(token)).thenReturn(Mono.just(false));
        
        // 执行测试
        StepVerifier.create(tokenRefreshService.isTokenValid(token))
            .expectNext(true)
            .verifyComplete();
        
        // 验证JWT验证器被调用
        verify(jwtTokenValidator).isTokenBlacklisted(token);
    }
    
    @Test
    void testIsTokenValid_BlacklistedToken_ShouldReturnFalse() {
        // 准备测试数据
        String token = "blacklisted.jwt.token";
        
        // 模拟JWT验证器返回令牌在黑名单中
        when(jwtTokenValidator.isTokenBlacklisted(token)).thenReturn(Mono.just(true));
        
        // 执行测试
        StepVerifier.create(tokenRefreshService.isTokenValid(token))
            .expectNext(false)
            .verifyComplete();
        
        // 验证JWT验证器被调用
        verify(jwtTokenValidator).isTokenBlacklisted(token);
    }
    
    @Test
    void testIsTokenValid_NullToken_ShouldReturnFalse() {
        // 执行测试
        StepVerifier.create(tokenRefreshService.isTokenValid(null))
            .expectNext(false)
            .verifyComplete();
        
        // 验证JWT验证器未被调用
        verify(jwtTokenValidator, never()).isTokenBlacklisted(anyString());
    }
    
    @Test
    void testIsTokenValid_EmptyToken_ShouldReturnFalse() {
        // 执行测试
        StepVerifier.create(tokenRefreshService.isTokenValid(""))
            .expectNext(false)
            .verifyComplete();
        
        // 验证JWT验证器未被调用
        verify(jwtTokenValidator, never()).isTokenBlacklisted(anyString());
    }
    
    @Test
    void testIsTokenValid_RedisError_ShouldReturnTrue() {
        // 准备测试数据
        String token = "valid.jwt.token";
        
        // 模拟Redis操作失败
        RuntimeException redisException = new RuntimeException("Redis connection failed");
        when(jwtTokenValidator.isTokenBlacklisted(token)).thenReturn(Mono.error(redisException));
        
        // 执行测试（Redis错误时默认认为有效）
        StepVerifier.create(tokenRefreshService.isTokenValid(token))
            .expectNext(true)
            .verifyComplete();
        
        // 验证JWT验证器被调用
        verify(jwtTokenValidator).isTokenBlacklisted(token);
    }
    
    @Test
    void testRevokeTokens_ValidTokenList_ShouldComplete() {
        // 准备测试数据
        List<String> tokens = Arrays.asList("token1", "token2", "token3");
        
        // 模拟JWT验证器成功将令牌加入黑名单
        when(jwtTokenValidator.blacklistToken(anyString())).thenReturn(Mono.empty());
        
        // 执行测试
        StepVerifier.create(tokenRefreshService.revokeTokens(tokens))
            .verifyComplete();
        
        // 验证JWT验证器被调用了3次
        verify(jwtTokenValidator, times(3)).blacklistToken(anyString());
    }
    
    @Test
    void testRevokeTokens_EmptyList_ShouldComplete() {
        // 准备测试数据
        List<String> tokens = Arrays.asList();
        
        // 执行测试
        StepVerifier.create(tokenRefreshService.revokeTokens(tokens))
            .verifyComplete();
        
        // 验证JWT验证器未被调用
        verify(jwtTokenValidator, never()).blacklistToken(anyString());
    }
    
    @Test
    void testRevokeTokens_NullList_ShouldComplete() {
        // 执行测试
        StepVerifier.create(tokenRefreshService.revokeTokens(null))
            .verifyComplete();
        
        // 验证JWT验证器未被调用
        verify(jwtTokenValidator, never()).blacklistToken(anyString());
    }
    
    @Test
    void testRevokeTokens_ListWithNullTokens_ShouldSkipNullTokens() {
        // 准备测试数据
        List<String> tokens = Arrays.asList("token1", null, "token2", "", "token3");
        
        // 模拟JWT验证器成功将令牌加入黑名单
        when(jwtTokenValidator.blacklistToken(anyString())).thenReturn(Mono.empty());
        
        // 执行测试
        StepVerifier.create(tokenRefreshService.revokeTokens(tokens))
            .verifyComplete();
        
        // 验证JWT验证器被调用了3次（跳过了null和空字符串）
        verify(jwtTokenValidator, times(3)).blacklistToken(anyString());
    }
    
    @Test
    void testGetBlacklistStats_ShouldReturnStats() {
        // 执行测试
        StepVerifier.create(tokenRefreshService.getBlacklistStats())
            .assertNext(stats -> {
                assertThat(stats).isNotNull();
                assertThat(stats).containsKey("memoryBlacklistSize");
                assertThat(stats).containsKey("blacklistEnabled");
                assertThat(stats).containsKey("lastCleanupTime");
                
                assertThat(stats.get("blacklistEnabled")).isEqualTo(true);
                assertThat(stats.get("memoryBlacklistSize")).isInstanceOf(Integer.class);
                assertThat(stats.get("lastCleanupTime")).isInstanceOf(Long.class);
            })
            .verifyComplete();
    }
    
    @Test
    void testCleanupExpiredBlacklistEntries_ShouldNotThrowException() {
        // 执行测试（应该不抛出异常）
        tokenRefreshService.cleanupExpiredBlacklistEntries();
        
        // 验证方法执行完成
        // 这个测试主要是确保方法不会抛出异常
    }
    
    @Test
    void testMemoryBlacklistFunctionality_ShouldWorkCorrectly() {
        // 准备测试数据
        String token = "test.jwt.token";
        
        // 模拟Redis操作失败，应该使用内存黑名单
        RuntimeException redisException = new RuntimeException("Redis connection failed");
        when(jwtTokenValidator.blacklistToken(token)).thenReturn(Mono.error(redisException));
        when(jwtTokenValidator.isTokenBlacklisted(token)).thenReturn(Mono.error(redisException));
        
        // 先撤销令牌（应该加入内存黑名单）
        StepVerifier.create(tokenRefreshService.revokeToken(token))
            .verifyComplete();
        
        // 然后检查令牌有效性（应该从内存黑名单中检查到）
        StepVerifier.create(tokenRefreshService.isTokenValid(token))
            .expectNext(false) // 应该返回false，因为在内存黑名单中
            .verifyComplete();
    }
}