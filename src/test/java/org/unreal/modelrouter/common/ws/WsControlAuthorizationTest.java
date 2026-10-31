package org.unreal.modelrouter.common.ws;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.security.Principal;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

/**
 * #916：监控 WebSocket 控制消息的管理员判定。
 */
class WsControlAuthorizationTest {

    private static Principal principalWith(final String... authorities) {
        return new UsernamePasswordAuthenticationToken(
                "user", "n/a",
                List.of(authorities).stream().map(SimpleGrantedAuthority::new).toList());
    }

    @Test
    @DisplayName("916-001 只有 ROLE_ADMIN 才算管理员，其它角色一律不算")
    void onlyAdminAuthorityCounts() {
        assertTrue(WsControlAuthorization.isPlatformAdmin(principalWith("ROLE_ADMIN")));
        assertFalse(WsControlAuthorization.isPlatformAdmin(principalWith("ROLE_CHAT")));
        assertFalse(WsControlAuthorization.isPlatformAdmin(principalWith("ROLE_USER", "ROLE_CHAT")));
        assertFalse(WsControlAuthorization.isPlatformAdmin(principalWith()));
    }

    @Test
    @DisplayName("916-002 缺失或未认证的主体视为非管理员")
    void missingOrUnauthenticatedPrincipalIsNotAdmin() {
        assertFalse(WsControlAuthorization.isPlatformAdmin(null));

        UsernamePasswordAuthenticationToken unauthenticated = new UsernamePasswordAuthenticationToken(
                "user", "n/a", List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
        unauthenticated.setAuthenticated(false);
        assertFalse(WsControlAuthorization.isPlatformAdmin(unauthenticated));
    }

    @Test
    @DisplayName("916-003 非 Authentication 的裸 Principal 无法读权限集，按非管理员处理")
    void rawPrincipalIsNotAdmin() {
        Principal raw = () -> "anonymous";
        assertFalse(WsControlAuthorization.isPlatformAdmin(raw));
    }

    @Test
    @DisplayName("916-004 控制类消息清单覆盖 pause/resume/updateConfig，其余不算")
    void controlMessageClassification() {
        assertTrue(WsControlAuthorization.isControlMessage("pause"));
        assertTrue(WsControlAuthorization.isControlMessage("resume"));
        assertTrue(WsControlAuthorization.isControlMessage("updateConfig"));
        assertTrue(WsControlAuthorization.isControlMessage(" pause "));
        assertFalse(WsControlAuthorization.isControlMessage("ping"));
        assertFalse(WsControlAuthorization.isControlMessage(null));
    }
}
