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
 * `#454` 的干净处置：监控指标面的读/写分码 + 调用记录摄取面显式豁免。
 *
 * <p>两件事在同一个测试里，因为它们是同一条结论的两半：
 * <b>"每个端点要么有一条显式规则，要么有一条显式豁免"</b>，不能靠
 * {@code RbacUnmatchedPolicy} 的默认 {@code DENY_ALL} 碰巧兜住。</p>
 */
@DisplayName("#454：metrics 读写分码 + call-history/record 摄取面豁免")
class MetricsWriteAndIngestExemptionTest {

    private final PermissionRuleRegistry registry = new PermissionRuleRegistry();

    @Test
    @DisplayName("GET /api/monitoring/metrics/** 仍由 monitoring:metrics:read 保护")
    void readsKeepReadCode() {
        for (String path : new String[]{
                "/api/monitoring/metrics/list",
                "/api/monitoring/metrics/statistics",
                "/api/monitoring/metrics/metadata",
                "/api/monitoring/metrics/search"}) {
            Optional<PermissionRule> rule = registry.findRule(HttpMethod.GET, path);
            assertTrue(rule.isPresent(), "GET " + path + " 应有规则");
            assertEquals(PermissionCodes.MONITORING_METRICS_READ, rule.get().permissionCode(),
                    "GET " + path);
        }
    }

    @Test
    @DisplayName("写操作（POST/PUT/DELETE）改为 monitoring:metrics:write")
    void writesUseWriteCode() {
        for (HttpMethod method : new HttpMethod[]{HttpMethod.POST, HttpMethod.PUT, HttpMethod.DELETE}) {
            for (String path : new String[]{
                    "/api/monitoring/metrics/register",
                    "/api/monitoring/metrics/register/batch",
                    "/api/monitoring/metrics/cleanup",
                    "/api/monitoring/metrics/validate"}) {
                Optional<PermissionRule> rule = registry.findRule(method, path);
                assertTrue(rule.isPresent(),
                        method + " " + path + " 应有显式规则（此前只靠默认 DENY_ALL 兜住）");
                assertEquals(PermissionCodes.MONITORING_METRICS_WRITE, rule.get().permissionCode(),
                        method + " " + path);
            }
        }
    }

    @Test
    @DisplayName("写码在权限全集里（否则 OPERATOR 的 :write 过滤拿不到它）")
    void writeCodeIsInAllPermissionCodes() {
        assertTrue(PermissionCodes.ALL_PERMISSION_CODES.contains(PermissionCodes.MONITORING_METRICS_WRITE),
                "新权限码必须进 ALL_PERMISSION_CODES，否则角色模板按后缀过滤时看不到它");
    }

    @Test
    @DisplayName("POST /api/call-history/record 是显式豁免（摄取面），不是无规则")
    void recordEndpointIsExplicitlyExempt() {
        assertTrue(RbacExemptEndpoints.isExempt(HttpMethod.POST, "/api/call-history/record"),
                "调用记录摄取面应显式豁免；否则它只是被默认 DENY_ALL 碰巧挡住");
        // 反证：同前缀的写端点不该被连带豁免
        assertFalse(RbacExemptEndpoints.isExempt(HttpMethod.DELETE, "/api/call-history/cleanup"),
                "cleanup 是删除操作，不应继承 record 的摄取面豁免");
        assertFalse(RbacExemptEndpoints.isExempt(HttpMethod.GET, "/api/call-history/recent"),
                "读取仍应由 callhistory:view 保护，不该被豁免");
    }

    @Test
    @DisplayName("每条豁免都写了理由（登记可追溯）")
    void everyExemptionHasReason() {
        assertFalse(RbacExemptEndpoints.exemptions().isEmpty());
        RbacExemptEndpoints.exemptions().forEach(e ->
                assertFalse(e.reason() == null || e.reason().isBlank(),
                        "豁免缺少理由：" + e.pathPattern()));
    }
}
