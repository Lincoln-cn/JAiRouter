package org.unreal.modelrouter.common.exception;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.unreal.modelrouter.common.constants.ErrorCode;
import org.unreal.modelrouter.common.constants.ErrorCode.RenderPath;

import java.util.EnumMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 错误码注册表的不变量（issue #370 方案 A，阶段 2 起按**渲染路径**分别断言）。
 *
 * <p>核心命题：**引入注册表不改变任何对外行为** —— 状态由规则链决定的码
 * （{@link RenderPath#API_EXCEPTION}），其登记状态必须与旧规则链推导值一致；未登记的码仍走旧规则链
 * （{@link ApiException#legacyResolveStatus}）；状态不由规则链决定的码
 * （{@link RenderPath#AUTHENTICATION} / {@link RenderPath#SANITIZATION} / {@link RenderPath#RESPONSE_BODY}
 * / {@link RenderPath#ERROR_BODY}）则由各自的发出面钉住，且**不得**被 {@code resolveStatus} 查表
 * （否则会悄悄改掉那些码经 {@code ApiException} 发出时的对外状态）。</p>
 */
@DisplayName("ErrorCode 注册表不变量（issue #370）")
class ErrorCodeRegistryTest {

    private static Set<String> codesWithPath(final RenderPath path) {
        return new TreeSet<>(java.util.Arrays.stream(ErrorCode.values())
                .filter(c -> c.renderPath() == path)
                .map(ErrorCode::code)
                .collect(Collectors.toSet()));
    }

    @Test
    @DisplayName("注册表自身形态：码唯一、形如大写下划线、状态/路径/说明齐备")
    void registry_isWellFormed() {
        Set<String> seen = new HashSet<>();
        for (ErrorCode code : ErrorCode.values()) {
            assertTrue(code.code().matches("[A-Z][A-Z0-9_]{2,}"), "码形态不合规: " + code.code());
            assertTrue(seen.add(code.code()), "重复登记: " + code.code());
            assertNotNull(code.renderPath(), "渲染路径不得为空: " + code.code());
            assertFalse(code.description().isBlank(), "说明不得为空: " + code.code());
            if (code.renderPath() == RenderPath.ERROR_BODY) {
                assertNull(code.status(), "状态随上下文的码不得登记固定状态: " + code.code());
            } else {
                assertNotNull(code.status(), "状态不得为空: " + code.code());
            }
        }
        assertFalse(seen.isEmpty(), "注册表不得为空");
        // 反空转：每条渲染路径都必须有登记项，否则下面的分类断言会「空集通过」
        for (RenderPath path : new RenderPath[]{RenderPath.API_EXCEPTION, RenderPath.AUTHENTICATION,
                RenderPath.SANITIZATION, RenderPath.RESPONSE_BODY, RenderPath.ERROR_BODY}) {
            assertFalse(codesWithPath(path).isEmpty(), "该渲染路径没有任何登记项（断言会空转）: " + path);
        }
    }

    @Test
    @DisplayName("规则链路径：登记状态 = resolveStatus 结果 = 规则链推导值（零行为变化）")
    void apiExceptionCodes_resolveToRegisteredStatus() {
        Set<String> checked = codesWithPath(RenderPath.API_EXCEPTION);
        for (String code : checked) {
            ErrorCode entry = ErrorCode.of(code).orElseThrow();
            assertEquals(entry.status(), ApiException.resolveStatus(code),
                    "登记码 " + code + " 的状态必须等于 resolveStatus 的返回值");
            assertEquals(entry.status(), ApiException.legacyResolveStatus(code),
                    "登记状态必须等于规则链对同形命名的推导值（否则引入注册表就改变了对外状态码）: " + code);
        }
        assertTrue(checked.contains("NOT_FOUND"), "反空转：" + checked);
    }

    @Test
    @DisplayName("认证路径：登记状态 = AuthenticationException 携带的状态")
    void authenticationCodes_matchTheStatusCarriedByTheException() {
        Set<String> checked = codesWithPath(RenderPath.AUTHENTICATION);
        for (String code : checked) {
            HttpStatus carried = new AuthenticationException("测试消息", code).getHttpStatus();
            assertEquals(carried, ErrorCode.of(code).orElseThrow().status(),
                    "认证码的登记状态必须等于 AuthenticationException 实际携带的状态: " + code);
        }
        assertTrue(checked.contains("JWT_EXPIRED"), "反空转：" + checked);
    }

    @Test
    @DisplayName("脱敏路径：登记状态 = SanitizationException 携带的状态")
    void sanitizationCodes_matchTheStatusCarriedByTheException() {
        Set<String> checked = codesWithPath(RenderPath.SANITIZATION);
        for (String code : checked) {
            HttpStatus carried = new SanitizationException("测试消息", code).getHttpStatus();
            assertEquals(carried, ErrorCode.of(code).orElseThrow().status(),
                    "脱敏码的登记状态必须等于 SanitizationException 实际携带的状态: " + code);
        }
        assertTrue(checked.contains("SANITIZATION_FAILED"), "反空转：" + checked);
    }

    @Test
    @DisplayName("响应体路径：登记状态 = 发出点实测状态；与规则链不一致的码为空集（issue #405 已对齐）")
    void responseBodyCodes_matchTheStatusObservedAtEmissionSites() {
        // 站点实测证据（stage 2 取证脚本按 `ResponseEntity.*` 归类全部 RouterResponse.error 发出点）：
        //   BUILTIN_READONLY   AdapterConfigController 的 PUT/DELETE 内置分支
        //                      → ResponseEntity.status(HttpStatus.FORBIDDEN) = 403（#405 前为 badRequest = 400）
        //   RESTART_REQUIRED   QuotaConfigController:106 → ResponseEntity.badRequest() = 400
        // issue #405 的混合方案把两条链都对齐了：内置只读 = 授权语义 403，需重启 = 请求校验语义 400
        // ⇒ 响应体路径**不再有**「端点实测状态 ≠ 规则链推导值」的码（其余码本来就是 404/409/400）。
        assertEquals(HttpStatus.FORBIDDEN, ErrorCode.BUILTIN_READONLY.status());
        assertEquals(HttpStatus.BAD_REQUEST, ErrorCode.RESTART_REQUIRED.status());

        Set<String> diverging = new TreeSet<>();
        for (String code : codesWithPath(RenderPath.RESPONSE_BODY)) {
            HttpStatus registered = ErrorCode.of(code).orElseThrow().status();
            if (registered != ApiException.legacyResolveStatus(code)) {
                diverging.add(code);
            }
        }
        assertEquals(Set.<String>of(), diverging,
                "issue #405 之后「端点实测状态 ≠ 规则链推导值」的码应为空集"
                        + "（新增或消除都必须同时改本断言与用例的 @DisplayName）");
    }

    @Test
    @DisplayName("状态随上下文的码：只有明列的那些，且不被 resolveStatus 查表")
    void statusIsNull_onlyForContextDependentCodes() {
        Set<String> dynamic = new TreeSet<>(java.util.Arrays.stream(ErrorCode.values())
                .filter(c -> c.status() == null)
                .map(ErrorCode::code)
                .collect(Collectors.toSet()));
        // DOWNSTREAM_SERVICE_ERROR 由 SecurityExceptionHandler 直写错误体，状态原样取下游返回的状态
        assertEquals(Set.of("DOWNSTREAM_SERVICE_ERROR"), dynamic);
        assertEquals(RenderPath.ERROR_BODY, ErrorCode.DOWNSTREAM_SERVICE_ERROR.renderPath());
    }

    @Test
    @DisplayName("resolveStatus 只对规则链路径查表：其余路径的码仍按历史规则推导")
    void resolveStatus_ignoresCodesWhoseStatusIsNotDerivedFromTheRuleChain() {
        // 响应体路径：issue #405 之后端点状态与规则链推导值一致（403 / 400），但状态仍由发出点的
        // ResponseEntity 显式决定 —— resolveStatus 只是**碰巧**推导出同一个值，依然不查表。
        assertEquals(HttpStatus.FORBIDDEN, ApiException.resolveStatus("BUILTIN_READONLY"));
        assertEquals(HttpStatus.BAD_REQUEST, ApiException.resolveStatus("RESTART_REQUIRED"));
        assertFalse(ErrorCode.isResolvedByRuleChain(ErrorCode.BUILTIN_READONLY.code()));
        assertFalse(ErrorCode.isResolvedByRuleChain(ErrorCode.RESTART_REQUIRED.code()));
        // 认证路径：异常携带 401，规则链推导值是 400（INVALID_ 前缀）/ 500 ⇒ 同样不查表
        assertEquals(HttpStatus.BAD_REQUEST, ApiException.resolveStatus("INVALID_API_KEY"));
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, ApiException.resolveStatus("RATE_LIMIT_EXCEEDED"));
        // 仅规则链路径的码才查表
        assertTrue(ErrorCode.isResolvedByRuleChain("NOT_FOUND"));
        assertFalse(ErrorCode.isResolvedByRuleChain("INVALID_API_KEY"));
        assertFalse(ErrorCode.isResolvedByRuleChain("NOT_A_REGISTERED_CODE"));
        assertFalse(ErrorCode.isResolvedByRuleChain(null));
    }

    @Test
    @DisplayName("路径相关码：同名码在两条真实路径上状态不同，这里把两处都钉住")
    void knownPathDependentCodes_areExplicit() {
        // RATE_LIMIT_EXCEEDED：
        //   ① API Key 校验链（ApiKeyValidator:145 → FullValidationResult.toException()）→ 401（本表登记值）
        //   ② 管理面限流器（AdminApiRateLimiter:113-114 直写 {"code":"RATE_LIMIT_EXCEEDED"}）→ 429
        // 两条路径都真实可达 ⇒ 登记值取异常路径的 401，另一条 429 以 issue #404 跟踪（客户端无法只按状态分支）。
        assertEquals(HttpStatus.UNAUTHORIZED, ErrorCode.RATE_LIMIT_EXCEEDED.status());
        assertEquals(HttpStatus.UNAUTHORIZED,
                new AuthenticationException("测试消息", ErrorCode.RATE_LIMIT_EXCEEDED.code()).getHttpStatus());
        assertEquals(RenderPath.AUTHENTICATION, ErrorCode.RATE_LIMIT_EXCEEDED.renderPath());
    }

    @Test
    @DisplayName("未登记的码仍走历史规则链（且与 legacyResolveStatus 逐字一致）")
    void unregisteredCodes_fallBackToLegacyRules() {
        assertEquals(HttpStatus.NOT_FOUND, ApiException.resolveStatus("SOMETHING_NOT_FOUND"));
        assertEquals(HttpStatus.BAD_REQUEST, ApiException.resolveStatus("INVALID_WHATEVER"));
        assertEquals(HttpStatus.CONFLICT, ApiException.resolveStatus("CONFLICT_WHATEVER"));
        assertEquals(HttpStatus.UNAUTHORIZED, ApiException.resolveStatus("UNAUTHORIZED_THING"));
        assertEquals(HttpStatus.FORBIDDEN, ApiException.resolveStatus("FORBIDDEN_THING"));
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, ApiException.resolveStatus("THING_NOT_AVAILABLE"));
        assertEquals(HttpStatus.TOO_MANY_REQUESTS, ApiException.resolveStatus("RATE_LIMITED_X"));
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, ApiException.resolveStatus("TOTALLY_UNKNOWN"));
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, ApiException.resolveStatus(null));
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, ApiException.resolveStatus("   "));
        assertEquals(HttpStatus.I_AM_A_TEAPOT, ApiException.resolveStatus("418"));

        for (String sample : new String[]{"SOMETHING_NOT_FOUND", "INVALID_WHATEVER", "TOTALLY_UNKNOWN", null}) {
            assertEquals(ApiException.legacyResolveStatus(sample), ApiException.resolveStatus(sample),
                    "未登记码在两条路径上必须一致: " + sample);
        }
    }

    @Test
    @DisplayName("书写约定：类别写在前缀位（曾经的 *_FORBIDDEN 已统一为 FORBIDDEN_*，issue #401）")
    void categoryMustLiveInThePrefixPosition() {
        // 统一后：前缀式码由**既有规则链**直接映射到 403 —— 不需要给规则加例外
        assertEquals(HttpStatus.FORBIDDEN, ApiException.resolveStatus(ErrorCode.FORBIDDEN_TOKEN_USAGE.code()));
        assertEquals(HttpStatus.FORBIDDEN, ApiException.resolveStatus("FORBIDDEN_RESOURCE"));

        // 守卫：任何登记码都不得用「类别后缀」表达类别（全表唯一历史例外 = _NOT_FOUND）
        Set<String> bannedSuffixes = Set.of(
                "_FORBIDDEN", "_UNAUTHORIZED", "_UNAUTHENTICATED", "_UNAVAILABLE", "_RATE_LIMITED");
        for (ErrorCode code : ErrorCode.values()) {
            for (String suffix : bannedSuffixes) {
                assertFalse(code.code().endsWith(suffix),
                        "类别应写在前缀位，不得用后缀表达: " + code.code());
            }
        }
    }

    @Test
    @DisplayName("注册表规模与分路径分布（防止某次改动把整类码悄悄删掉）")
    void registryDistribution_isStable() {
        Map<RenderPath, Integer> counts = new EnumMap<>(RenderPath.class);
        for (ErrorCode code : ErrorCode.values()) {
            counts.merge(code.renderPath(), 1, Integer::sum);
        }
        assertTrue(counts.get(RenderPath.API_EXCEPTION) >= 20, "规则链路径码数异常: " + counts);
        assertTrue(counts.get(RenderPath.AUTHENTICATION) >= 20, "认证路径码数异常: " + counts);
        assertEquals(3, counts.get(RenderPath.SANITIZATION), "脱敏码应为 3 个（多一个都要走同一套取证）: " + counts);
        assertEquals(1, counts.get(RenderPath.ERROR_BODY), "状态随上下文的码今天只有 1 个: " + counts);
    }
}
