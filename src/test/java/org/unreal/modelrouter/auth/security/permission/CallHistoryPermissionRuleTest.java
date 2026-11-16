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
import org.springframework.http.HttpMethod;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * `/api/call-history/**` 的权限规则只覆盖 GET（issue #514），且 URL 规则用 {@code callhistory:read}。
 *
 * <p>回归点：该前缀原先是一条 {@code any(...)} 规则（方法集为空 = 所有方法），于是
 * 只读权限码同时放行了 {@code POST /api/call-history/record}（写入）与
 * {@code DELETE /api/call-history/cleanup}（删除）。现在只有 GET 匹配规则；写/删
 * <b>不匹配任何规则</b>，由 {@code RbacUnmatchedPolicy} 裁决（默认 DENY_ALL ⇒ 仅 ROLE_ADMIN 直通）。</p>
 *
 * <p>调用历史拆码后（{@code callhistory:read} + {@code callhistory:full-content}），URL 规则只认前者：
 * 后者是「正文解密」的细粒度闸门，在 controller 里判定，不参与前缀规则 —— 否则没有 full-content 的
 * 运维主体连列表都进不来，拆码就没有意义。</p>
 */
@DisplayName("/api/call-history 权限规则：只读码不再放行写与删")
class CallHistoryPermissionRuleTest {

    private final PermissionRuleRegistry registry = new PermissionRuleRegistry();

    @Test
    @DisplayName("GET 由 callhistory:read 放行（含详情页，它靠 full-content 再细粒度判定）")
    void getIsGuardedByReadCode() {
        for (String path : new String[]{
                "/api/call-history/recent",
                "/api/call-history/count",
                "/api/call-history/errors",
                "/api/call-history/trace/abc-123",
                "/api/call-history/1/detail"}) {
            Optional<PermissionRule> rule = registry.findRule(HttpMethod.GET, path);
            assertTrue(rule.isPresent(), "GET " + path + " 应有规则");
            assertEquals(PermissionCodes.CALLHISTORY_READ, rule.get().permissionCode(),
                    "GET " + path + " 应使用 callhistory:read");
        }
    }

    @Test
    @DisplayName("POST /record 与 DELETE /cleanup 不再匹配 callhistory:read 规则")
    void writesAreNotGuardedByReadCode() {
        for (HttpMethod method : new HttpMethod[]{HttpMethod.POST, HttpMethod.PUT,
                HttpMethod.PATCH, HttpMethod.DELETE}) {
            for (String path : new String[]{"/api/call-history/record", "/api/call-history/cleanup"}) {
                Optional<PermissionRule> rule = registry.findRule(method, path);
                assertTrue(rule.isEmpty() || !PermissionCodes.CALLHISTORY_READ.equals(rule.get().permissionCode()),
                        method + " " + path + " 不应再被 callhistory:read 放行（当前规则="
                                + rule.map(PermissionRule::permissionCode).orElse("<无规则>") + "）");
            }
        }
    }

    @Test
    @DisplayName("反证：同一前缀的 GET 规则确实存在（防止测试因规则表为空而空转）")
    void registryIsNotEmpty() {
        assertFalse(registry.getRules().isEmpty(), "规则表不应为空");
    }
}
