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

import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;

/**
 * 认证主体 → 权限码的轻量判定工具。
 *
 * <p>口径与 {@link PermissionAuthorizationManager} 保持一致：{@code ROLE_ADMIN} 直通，
 * 其余主体要求 authorities 中直接包含该权限码（JWT 把权限码写进 authorities，
 * 见 {@code AccountManager} 与 {@code JwtAuthentication}）。</p>
 *
 * <p>存在理由：URL 规则只能表达「一条规则 → 一个码」，而调用历史正文属于
 * 「同一前缀、按主体裁剪响应」的场景 —— 列表访问由 {@code callhistory:read} 放行，
 * 正文解密由 {@code callhistory:full-content} 在控制器内二次判定。</p>
 */
public final class PermissionChecks {

    private static final String ADMIN_ROLE_AUTHORITY = "ROLE_ADMIN";

    private PermissionChecks() {
    }

    /**
     * 判断当前主体是否持有指定权限码（ADMIN 恒为 true）。
     *
     * @param authentication 当前认证主体，可为 {@code null}
     * @param permissionCode 权限码，{@code null} / 空串一律视为不持有
     * @return 是否持有
     */
    public static boolean hasPermission(final Authentication authentication, final String permissionCode) {
        if (authentication == null || !authentication.isAuthenticated()
                || permissionCode == null || permissionCode.isEmpty()
                || authentication.getAuthorities() == null) {
            return false;
        }
        boolean admin = false;
        boolean matched = false;
        for (GrantedAuthority authority : authentication.getAuthorities()) {
            final String value = authority.getAuthority();
            if (ADMIN_ROLE_AUTHORITY.equals(value)) {
                admin = true;
            } else if (permissionCode.equals(value)) {
                matched = true;
            }
        }
        return admin || matched;
    }

    /**
     * 判断当前主体是否持有其中任意一个权限码（ADMIN 恒为 true）。
     *
     * <p>用于「一条 URL 规则接受多个码」的场景（{@code PermissionRule#getAny}）与数据面范围判定
     * （全局读码 vs 本人读码）。与 {@link #hasPermission} 同一口径：先认 {@code ROLE_ADMIN}，再逐码比对。</p>
     *
     * @param authentication 当前认证主体，可为 {@code null}
     * @param permissionCodes 权限码，空数组表示「不限制」（返回 true）
     * @return 是否持有其中任意一个
     */
    public static boolean hasAny(final Authentication authentication, final String... permissionCodes) {
        if (permissionCodes == null || permissionCodes.length == 0) {
            return true;
        }
        for (String code : permissionCodes) {
            if (hasPermission(authentication, code)) {
                return true;
            }
        }
        return false;
    }
}
