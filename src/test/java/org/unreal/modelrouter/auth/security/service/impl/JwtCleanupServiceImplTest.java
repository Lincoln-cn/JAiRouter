package org.unreal.modelrouter.auth.security.service.impl;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.unreal.modelrouter.auth.security.service.JwtBlacklistService;
import org.unreal.modelrouter.auth.security.service.JwtCleanupService;
import org.unreal.modelrouter.auth.security.service.JwtPersistenceService;
import org.unreal.modelrouter.persistence.store.StoreManager;
import reactor.core.publisher.Mono;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 清理失败结果的对外文案。
 *
 * <p>覆盖 issue #1197 的 P3 分支：{@code CleanupResult} 会经
 * {@code GET /api/auth/jwt/tokens/cleanup/stats} 之类的接口返回给调用方，
 * 因此 {@code errorMessage} 与 {@code details.error} 都不能带异常原文
 * （可能含连接串 / 表名 / 驱动类名）。原文只进日志。</p>
 *
 * <p>未覆盖的一条：{@code handleFullCleanupError}。该分支只在
 * {@code cleanupExpiredTokens()/cleanupExpiredBlacklistEntries()} 的 {@code block()} 超时，
 * 或合并结果时抛异常才可达；构造该场景要等满 {@code BLOCK_TIMEOUT}，成本远高于收益，
 * 已在 PR 正文如实申报，不假装有覆盖。</p>
 */
@DisplayName("JWT 清理服务的失败文案不外显异常原文")
class JwtCleanupServiceImplTest {

    /** 一眼能认出的「内部细节」样本：连接串 + 口令 + 驱动类名。 */
    private static final String LEAKY =
            "java.sql.SQLException: 查询失败 jdbc:postgresql://db.internal:5432/prod?password=s3cret";

    private JwtPersistenceService persistenceService;
    private JwtBlacklistService blacklistService;
    private JwtCleanupService service;

    @BeforeEach
    void setUp() {
        persistenceService = mock(JwtPersistenceService.class);
        blacklistService = mock(JwtBlacklistService.class);
        // storeManager.getConfig(...) 返回 null ⇒ loadStats() 走「从零开始」分支，不碰真实存储
        final JwtCleanupServiceImpl impl = new JwtCleanupServiceImpl(
                persistenceService, blacklistService, mock(StoreManager.class), new SimpleMeterRegistry());
        // 没有 Spring 上下文就不会触发 @PostConstruct，必须手动调一次，否则 metrics / statsManager 为 null
        impl.init();
        service = impl;
    }

    @Test
    @DisplayName("令牌清理失败：结果是固定文案，不含连接串")
    void tokenCleanupFailureDoesNotEchoExceptionDetail() {
        when(persistenceService.countActiveTokens()).thenReturn(Mono.error(new RuntimeException(LEAKY)));

        final JwtCleanupService.CleanupResult result = service.cleanupExpiredTokens().block();

        assertNotNull(result, "清理失败也应返回结果对象，而不是抛出");
        assertFalse(result.isSuccess(), "失败必须是 success=false");
        assertEquals("清理令牌失败", result.getErrorMessage(),
                "对外文案固定；原文只应出现在日志里");
        final Map<String, Object> details = result.getDetails();
        assertEquals("清理令牌失败", details.get("error"),
                "details.error 会随响应体返回，同样不能带原文");
        assertEquals("RuntimeException", details.get("errorClass"),
                "类名是有限枚举，不是异常原文，保留以便排障");
        assertNoLeak(result.getErrorMessage());
        assertNoLeak(String.valueOf(details.get("error")));
    }

    @Test
    @DisplayName("黑名单清理失败：结果是固定文案，不含连接串")
    void blacklistCleanupFailureDoesNotEchoExceptionDetail() {
        when(blacklistService.cleanupExpiredEntriesWithCount()).thenReturn(Mono.error(new RuntimeException(LEAKY)));

        final JwtCleanupService.CleanupResult result = service.cleanupExpiredBlacklistEntries().block();

        assertNotNull(result, "清理失败也应返回结果对象，而不是抛出");
        assertFalse(result.isSuccess(), "失败必须是 success=false");
        assertEquals("清理黑名单失败", result.getErrorMessage(),
                "对外文案固定；原文只应出现在日志里");
        assertEquals("清理黑名单失败", result.getDetails().get("error"));
        assertNoLeak(result.getErrorMessage());
    }

    private void assertNoLeak(final String text) {
        assertFalse(text.contains("jdbc:postgresql"), "不得回显连接串：" + text);
        assertFalse(text.contains("s3cret"), "不得回显口令：" + text);
        assertFalse(text.contains("SQLException"), "不得回显异常类名：" + text);
    }
}
