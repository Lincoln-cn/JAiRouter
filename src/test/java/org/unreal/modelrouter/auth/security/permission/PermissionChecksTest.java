/*
 * Copyright (c) 2025 JAiRouter Authors. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.unreal.modelrouter.auth.security.permission;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link PermissionChecks} 的判定语义测试。
 *
 * <p>为什么单独守它：调用历史的正文可见性完全依赖这个判定 ——
 * 判定一旦从「精确码」漂成「前缀/角色名」，要么把正文漏给只读运维，
 * 要么让 ADMIN 也解不了密。这里逐条钉住边界：null、未认证、空码、
 * 非 ADMIN 只认精确码、ADMIN 直通。</p>
 */
@DisplayName("PermissionChecks：认证主体 → 权限码判定")
class PermissionChecksTest {

    private static Authentication authenticated(final String... authorities) {
        return new UsernamePasswordAuthenticationToken(
                "user", null,
                List.of(authorities).stream().map(SimpleGrantedAuthority::new).toList());
    }

    @Test
    @DisplayName("未认证主体（null）一律不持有")
    void nullAuthentication() {
        assertFalse(PermissionChecks.hasPermission(null, PermissionCodes.CALLHISTORY_READ));
        assertFalse(PermissionChecks.hasPermission(null, PermissionCodes.CALLHISTORY_FULL_CONTENT));
    }

    @Test
    @DisplayName("空码 / null 码一律视为不持有")
    void blankPermissionCode() {
        Authentication auth = authenticated(PermissionCodes.CALLHISTORY_READ);
        assertFalse(PermissionChecks.hasPermission(auth, null));
        assertFalse(PermissionChecks.hasPermission(auth, ""));
    }

    @Test
    @DisplayName("两参构造的 token（isAuthenticated=false）不持有任何码")
    void unauthenticatedToken() {
        // 两参构造（principal, credentials）在 Spring Security 里是未认证 token；
        // 三参构造才会把 authenticated 置 true，因此这里不能用带 authorities 的构造器。
        Authentication auth = new UsernamePasswordAuthenticationToken("user", "credentials");
        assertFalse(auth.isAuthenticated(), "前置：两参构造的 token 是未认证状态");
        assertFalse(PermissionChecks.hasPermission(auth, PermissionCodes.CALLHISTORY_READ));
        assertFalse(PermissionChecks.hasPermission(auth, PermissionCodes.CALLHISTORY_FULL_CONTENT));
    }

    @Test
    @DisplayName("非 ADMIN 只认精确码，前缀相近不算")
    void exactCodeOnly() {
        Authentication auth = authenticated(PermissionCodes.CALLHISTORY_READ);

        assertTrue(PermissionChecks.hasPermission(auth, PermissionCodes.CALLHISTORY_READ));
        assertFalse(PermissionChecks.hasPermission(auth, PermissionCodes.CALLHISTORY_FULL_CONTENT),
                "callhistory:read 不得顺带解开正文");
        assertFalse(PermissionChecks.hasPermission(auth, "callhistory"),
                "前缀不是权限码");
    }

    @Test
    @DisplayName("ROLE_ADMIN 直通（不需要显式授予 full-content）")
    void adminBypassesEverything() {
        Authentication auth = authenticated("ROLE_ADMIN");

        assertTrue(PermissionChecks.hasPermission(auth, PermissionCodes.CALLHISTORY_READ));
        assertTrue(PermissionChecks.hasPermission(auth, PermissionCodes.CALLHISTORY_FULL_CONTENT));
        assertTrue(PermissionChecks.hasPermission(auth, "any:unknown:code"));
    }

    @Test
    @DisplayName("普通角色名不放行（ROLE_OPERATOR 不等于持码）")
    void roleAuthorityIsNotAPermissionCode() {
        Authentication auth = authenticated("ROLE_OPERATOR", "ROLE_USER");

        assertFalse(PermissionChecks.hasPermission(auth, PermissionCodes.CALLHISTORY_READ),
                "角色名不是权限码，判定必须看权限码本身");
    }
}
