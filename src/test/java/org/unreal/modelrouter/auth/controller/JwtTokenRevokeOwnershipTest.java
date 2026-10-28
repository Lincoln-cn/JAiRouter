package org.unreal.modelrouter.auth.controller;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.util.ReflectionTestUtils;
import org.unreal.modelrouter.auth.security.authentication.JwtTokenValidator;
import org.unreal.modelrouter.auth.security.service.JwtPersistenceService;
import org.unreal.modelrouter.auth.security.service.JwtTokenRefreshService;
import org.unreal.modelrouter.common.dto.JwtTokenInfo;
import org.unreal.modelrouter.common.dto.TokenRevokeRequest;
import org.unreal.modelrouter.common.exception.ApiException;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * {@code POST /api/auth/jwt/revoke} 的属主校验（issue #696）。
 *
 * <p>旧实现的 {@code @PreAuthorize} 只比对请求体自报的 {@code userId}，与真正被撤销的
 * {@code token} 无关 ⇒ 任意已登录用户把 {@code userId} 填成自己，就能拉黑他人令牌。
 * 本测试钉住新语义：<b>以令牌自身的属主为准</b>。</p>
 *
 * @author JAiRouter Team
 * @since 3.5.4
 */
@DisplayName("JwtTokenController 撤销属主校验（#696）")
@ExtendWith(MockitoExtension.class)
class JwtTokenRevokeOwnershipTest {

    private static final String OTHER_USERS_FULL_TOKEN = "eyJhbGciOiJIUzI1NiJ9.bob-payload.bob-signature";
    private static final String SELF_FULL_TOKEN = "eyJhbGciOiJIUzI1NiJ9.alice-payload.alice-signature";

    @Mock
    private JwtTokenRefreshService tokenRefreshService;

    @Mock
    private JwtTokenValidator jwtTokenValidator;

    @Mock
    private JwtPersistenceService jwtPersistenceService;

    @InjectMocks
    private JwtTokenController controller;

    private static Authentication user(final String name) {
        return new UsernamePasswordAuthenticationToken(name, "n/a",
                List.of(new SimpleGrantedAuthority("ROLE_USER")));
    }

    private static Authentication admin() {
        return new UsernamePasswordAuthenticationToken("root", "n/a",
                List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
    }

    private static TokenRevokeRequest request(final String token, final String selfReportedUserId) {
        TokenRevokeRequest request = new TokenRevokeRequest();
        request.setToken(token);
        // 关键：自报的 userId 与目标令牌无关 —— 旧实现就是被这一行骗过的
        request.setUserId(selfReportedUserId);
        return request;
    }

    private static void assertForbidden(final Throwable error) {
        ApiException api = assertInstanceOf(ApiException.class, error,
                "越权撤销必须以 ApiException(FORBIDDEN) 终止，实际为: " + error);
        assertEquals("FORBIDDEN", api.getErrorCode());
        assertEquals(HttpStatus.FORBIDDEN, api.getStatus());
    }

    @Test
    @DisplayName("他人完整令牌 + 自报 userId=自己 ⇒ 403，且不触发任何撤销动作")
    void otherUsersTokenIsRejected() {
        lenient().when(jwtTokenValidator.extractUserId(OTHER_USERS_FULL_TOKEN)).thenReturn(Mono.just("bob"));
        // 旧实现根本不看属主：撤销会真的走完，测试仍必须在「期望 403」处失败
        lenient().when(tokenRefreshService.revokeToken(OTHER_USERS_FULL_TOKEN)).thenReturn(Mono.empty());

        StepVerifier.create(controller.revokeToken(
                        request(OTHER_USERS_FULL_TOKEN, "alice"), user("alice")))
                .expectErrorSatisfies(JwtTokenRevokeOwnershipTest::assertForbidden)
                .verify();

        verify(tokenRefreshService, never()).revokeToken(anyString());
    }

    @Test
    @DisplayName("他人 tokenHash ⇒ 403（属主从持久化记录回查）")
    void otherUsersTokenHashIsRejected() {
        // @InjectMocks 只按构造器注入 final 字段，@Autowired(required = false) 的字段要显式塞进去
        ReflectionTestUtils.setField(controller, "jwtPersistenceService", jwtPersistenceService);
        JwtTokenInfo info = new JwtTokenInfo();
        info.setUserId("bob");
        lenient().when(jwtPersistenceService.findByTokenHash("hash-of-bobs-token")).thenReturn(Mono.just(info));

        StepVerifier.create(controller.revokeToken(
                        request("hash-of-bobs-token", "alice"), user("alice")))
                .expectErrorSatisfies(JwtTokenRevokeOwnershipTest::assertForbidden)
                .verify();

        verify(jwtPersistenceService).findByTokenHash("hash-of-bobs-token");
        verify(tokenRefreshService, never()).revokeToken(anyString());
    }

    @Test
    @DisplayName("本人完整令牌 ⇒ 放行并撤销成功")
    void ownTokenIsAllowed() {
        when(jwtTokenValidator.extractUserId(SELF_FULL_TOKEN)).thenReturn(Mono.just("alice"));
        when(tokenRefreshService.revokeToken(SELF_FULL_TOKEN)).thenReturn(Mono.empty());

        StepVerifier.create(controller.revokeToken(request(SELF_FULL_TOKEN, "alice"), user("alice")))
                .expectNextMatches(response -> response.isSuccess())
                .verifyComplete();

        verify(tokenRefreshService).revokeToken(SELF_FULL_TOKEN);
    }

    @Test
    @DisplayName("ADMIN 可撤销他人令牌")
    void adminMayRevokeOtherUsersToken() {
        when(tokenRefreshService.revokeToken(OTHER_USERS_FULL_TOKEN)).thenReturn(Mono.empty());

        StepVerifier.create(controller.revokeToken(request(OTHER_USERS_FULL_TOKEN, "root"), admin()))
                .expectNextMatches(response -> response.isSuccess())
                .verifyComplete();

        verify(tokenRefreshService).revokeToken(OTHER_USERS_FULL_TOKEN);
    }

    @Test
    @DisplayName("属主解析不出（令牌畸形/已失效）⇒ 非 ADMIN 一律拒绝")
    void unresolvableOwnerIsRejected() {
        lenient().when(jwtTokenValidator.extractUserId(OTHER_USERS_FULL_TOKEN)).thenReturn(Mono.empty());
        lenient().when(tokenRefreshService.revokeToken(OTHER_USERS_FULL_TOKEN)).thenReturn(Mono.empty());

        StepVerifier.create(controller.revokeToken(
                        request(OTHER_USERS_FULL_TOKEN, "alice"), user("alice")))
                .expectErrorSatisfies(JwtTokenRevokeOwnershipTest::assertForbidden)
                .verify();

        verify(tokenRefreshService, never()).revokeToken(anyString());
    }

    @Test
    @DisplayName("属主解析抛异常（校验器报错）⇒ 拒绝，不 500、不调用撤销")
    void ownerResolutionFailureIsRejected() {
        lenient().when(jwtTokenValidator.extractUserId(OTHER_USERS_FULL_TOKEN))
                .thenReturn(Mono.error(new IllegalStateException("malformed token")));
        lenient().when(tokenRefreshService.revokeToken(OTHER_USERS_FULL_TOKEN)).thenReturn(Mono.empty());

        StepVerifier.create(controller.revokeToken(
                        request(OTHER_USERS_FULL_TOKEN, "alice"), user("alice")))
                .expectErrorSatisfies(JwtTokenRevokeOwnershipTest::assertForbidden)
                .verify();

        verifyNoInteractions(tokenRefreshService);
    }
}
