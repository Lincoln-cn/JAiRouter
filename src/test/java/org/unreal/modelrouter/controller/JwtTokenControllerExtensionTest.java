package org.unreal.modelrouter.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;
import org.springframework.test.util.ReflectionTestUtils;
import org.unreal.modelrouter.controller.response.RouterResponse;
import org.unreal.modelrouter.dto.JwtTokenInfo;
import org.unreal.modelrouter.dto.PagedResult;
import org.unreal.modelrouter.dto.TokenStatus;
import org.unreal.modelrouter.security.authentication.JwtTokenValidator;
import org.unreal.modelrouter.security.config.properties.SecurityProperties;
import org.unreal.modelrouter.security.service.AccountManager;
import org.unreal.modelrouter.security.service.JwtCleanupService;
import org.unreal.modelrouter.security.service.JwtPersistenceService;
import org.unreal.modelrouter.security.service.JwtTokenRefreshService;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * JWT令牌控制器扩展功能测试
 * 测试新增的令牌管理API端点
 */
@ExtendWith(MockitoExtension.class)
class JwtTokenControllerExtensionTest {

    @Mock
    private JwtTokenRefreshService tokenRefreshService;
    
    @Mock
    private JwtTokenValidator jwtTokenValidator;
    
    @Mock
    private AccountManager accountManager;
    
    @Mock
    private SecurityProperties securityProperties;
    
    @Mock
    private JwtPersistenceService jwtPersistenceService;
    
    @Mock
    private JwtCleanupService jwtCleanupService;
    
    @Mock
    private Authentication authentication;

    private JwtTokenController controller;

    @BeforeEach
    void setUp() {
        controller = new JwtTokenController(tokenRefreshService, jwtTokenValidator, accountManager, securityProperties);
        
        // 使用反射设置可选的服务
        ReflectionTestUtils.setField(controller, "jwtPersistenceService", jwtPersistenceService);
        ReflectionTestUtils.setField(controller, "jwtCleanupService", jwtCleanupService);
        
        when(authentication.getName()).thenReturn("testuser");
    }

    @Test
    void testGetTokens_WithPersistenceService() {
        // 准备测试数据
        JwtTokenInfo token1 = new JwtTokenInfo();
        token1.setId("token1");
        token1.setUserId("user1");
        token1.setStatus(TokenStatus.ACTIVE);
        token1.setIssuedAt(LocalDateTime.now());
        
        JwtTokenInfo token2 = new JwtTokenInfo();
        token2.setId("token2");
        token2.setUserId("user2");
        token2.setStatus(TokenStatus.REVOKED);
        token2.setIssuedAt(LocalDateTime.now());
        
        List<JwtTokenInfo> tokens = Arrays.asList(token1, token2);
        
        when(jwtPersistenceService.findAllTokens(0, 20)).thenReturn(Mono.just(tokens));
        when(jwtPersistenceService.countActiveTokens()).thenReturn(Mono.just(2L));

        // 执行测试
        Mono<RouterResponse<PagedResult<JwtTokenInfo>>> result = controller.getTokens(0, 20, null, null, authentication);

        // 验证结果
        StepVerifier.create(result)
                .assertNext(response -> {
                    assertTrue(response.isSuccess());
                    assertEquals("令牌列表获取成功", response.getMessage());
                    assertNotNull(response.getData());
                    assertEquals(2, response.getData().getContent().size());
                    assertEquals(0, response.getData().getPage());
                    assertEquals(20, response.getData().getSize());
                    assertEquals(2L, response.getData().getTotalElements());
                })
                .verifyComplete();

        verify(jwtPersistenceService).findAllTokens(0, 20);
        verify(jwtPersistenceService).countActiveTokens();
    }

    @Test
    void testGetTokens_WithoutPersistenceService() {
        // 移除持久化服务
        ReflectionTestUtils.setField(controller, "jwtPersistenceService", null);

        // 执行测试
        Mono<RouterResponse<PagedResult<JwtTokenInfo>>> result = controller.getTokens(0, 20, null, null, authentication);

        // 验证结果
        StepVerifier.create(result)
                .assertNext(response -> {
                    assertFalse(response.isSuccess());
                    assertEquals("令牌持久化服务未启用", response.getMessage());
                    assertEquals("SERVICE_NOT_AVAILABLE", response.getErrorCode());
                })
                .verifyComplete();
    }

    @Test
    void testGetTokens_InvalidParameters() {
        // 测试无效页码
        Mono<RouterResponse<PagedResult<JwtTokenInfo>>> result1 = controller.getTokens(-1, 20, null, null, authentication);
        
        StepVerifier.create(result1)
                .assertNext(response -> {
                    assertFalse(response.isSuccess());
                    assertEquals("页码不能小于0", response.getMessage());
                    assertEquals("INVALID_PAGE", response.getErrorCode());
                })
                .verifyComplete();

        // 测试无效页大小
        Mono<RouterResponse<PagedResult<JwtTokenInfo>>> result2 = controller.getTokens(0, 0, null, null, authentication);
        
        StepVerifier.create(result2)
                .assertNext(response -> {
                    assertFalse(response.isSuccess());
                    assertEquals("页大小必须在1-100之间", response.getMessage());
                    assertEquals("INVALID_SIZE", response.getErrorCode());
                })
                .verifyComplete();
    }

    @Test
    void testGetTokenDetails_Success() {
        // 准备测试数据
        JwtTokenInfo tokenInfo = new JwtTokenInfo();
        tokenInfo.setId("token123");
        tokenInfo.setUserId("user1");
        tokenInfo.setStatus(TokenStatus.ACTIVE);
        
        when(jwtPersistenceService.findByTokenId("token123")).thenReturn(Mono.just(tokenInfo));

        // 执行测试
        Mono<RouterResponse<JwtTokenInfo>> result = controller.getTokenDetails("token123", authentication);

        // 验证结果
        StepVerifier.create(result)
                .assertNext(response -> {
                    assertTrue(response.isSuccess());
                    assertEquals("令牌详情获取成功", response.getMessage());
                    assertNotNull(response.getData());
                    assertEquals("token123", response.getData().getId());
                    assertEquals("user1", response.getData().getUserId());
                })
                .verifyComplete();

        verify(jwtPersistenceService).findByTokenId("token123");
    }

    @Test
    void testGetTokenDetails_NotFound() {
        when(jwtPersistenceService.findByTokenId("nonexistent")).thenReturn(Mono.empty());

        // 执行测试
        Mono<RouterResponse<JwtTokenInfo>> result = controller.getTokenDetails("nonexistent", authentication);

        // 验证结果
        StepVerifier.create(result)
                .assertNext(response -> {
                    assertFalse(response.isSuccess());
                    assertEquals("令牌不存在", response.getMessage());
                    assertEquals("TOKEN_NOT_FOUND", response.getErrorCode());
                })
                .verifyComplete();
    }

    @Test
    void testCleanupExpiredTokens_Success() {
        // 准备测试数据
        JwtCleanupService.CleanupResult cleanupResult = new JwtCleanupService.CleanupResult();
        cleanupResult.setRemovedTokens(5);
        cleanupResult.setRemovedBlacklistEntries(3);
        cleanupResult.setSuccess(true);
        
        when(jwtCleanupService.performFullCleanup()).thenReturn(Mono.just(cleanupResult));

        // 执行测试
        Mono<RouterResponse<JwtCleanupService.CleanupResult>> result = controller.cleanupExpiredTokens(authentication);

        // 验证结果
        StepVerifier.create(result)
                .assertNext(response -> {
                    assertTrue(response.isSuccess());
                    assertTrue(response.getMessage().contains("清理完成"));
                    assertTrue(response.getMessage().contains("5个过期令牌"));
                    assertTrue(response.getMessage().contains("3个过期黑名单条目"));
                    assertNotNull(response.getData());
                    assertEquals(5, response.getData().getRemovedTokens());
                    assertEquals(3, response.getData().getRemovedBlacklistEntries());
                })
                .verifyComplete();

        verify(jwtCleanupService).performFullCleanup();
    }

    @Test
    void testCleanupExpiredTokens_WithoutCleanupService() {
        // 移除清理服务
        ReflectionTestUtils.setField(controller, "jwtCleanupService", null);

        // 执行测试
        Mono<RouterResponse<JwtCleanupService.CleanupResult>> result = controller.cleanupExpiredTokens(authentication);

        // 验证结果
        StepVerifier.create(result)
                .assertNext(response -> {
                    assertFalse(response.isSuccess());
                    assertEquals("令牌清理服务未启用", response.getMessage());
                    assertEquals("SERVICE_NOT_AVAILABLE", response.getErrorCode());
                })
                .verifyComplete();
    }

    @Test
    void testGetCleanupStats_Success() {
        // 准备测试数据
        JwtCleanupService.CleanupStats stats = new JwtCleanupService.CleanupStats();
        stats.setTotalCleanupsPerformed(10);
        stats.setTotalTokensRemoved(50);
        stats.setCleanupEnabled(true);
        
        when(jwtCleanupService.getCleanupStats()).thenReturn(Mono.just(stats));

        // 执行测试
        Mono<RouterResponse<JwtCleanupService.CleanupStats>> result = controller.getCleanupStats(authentication);

        // 验证结果
        StepVerifier.create(result)
                .assertNext(response -> {
                    assertTrue(response.isSuccess());
                    assertEquals("清理统计信息获取成功", response.getMessage());
                    assertNotNull(response.getData());
                    assertEquals(10, response.getData().getTotalCleanupsPerformed());
                    assertEquals(50, response.getData().getTotalTokensRemoved());
                    assertTrue(response.getData().isCleanupEnabled());
                })
                .verifyComplete();

        verify(jwtCleanupService).getCleanupStats();
    }
}