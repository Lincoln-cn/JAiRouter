package org.unreal.modelrouter.common.exception;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.unreal.modelrouter.auth.security.service.ApiKeyValidator.FullValidationResult;
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
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
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
 *
 * <p>issue #404（2026-10-15 裁决「429 + 拆新码」）：限流不再「同码两状态」——
 * 管理面限流器的 {@code RATE_LIMIT_EXCEEDED} 归 {@link RenderPath#ERROR_BODY} + 429，
 * API Key 校验链改用 {@code RATE_LIMITED_API_KEY}（{@link RenderPath#AUTHENTICATION} + 429）。
 * 因此本类的两条断言被改写：{@code registry_isWellFormed} 不再断言「ERROR_BODY ⇒ 状态为 null」，
 * {@code authenticationCodes_matchTheStatusCarriedByTheException} 改为「2 参构造器对 401 码成立、
 * 非 401 码由校验链显式携带并明列」。</p>
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
            if (code.renderPath() != RenderPath.ERROR_BODY) {
                // 只有「错误体直写」路径允许状态随上下文（见 statusIsNull_onlyForDownstreamServiceError）
                assertNotNull(code.status(), "状态不得为空: " + code.code());
            }
        }
        assertFalse(seen.isEmpty(), "注册表不得为空");
        // 反空转：每条渲染路径都必须有登记项，否则下面的分类断言会「空集通过」
        for (RenderPath path : new RenderPath[]{RenderPath.API_EXCEPTION, RenderPath.AUTHENTICATION,
                RenderPath.SANITIZATION, RenderPath.RESPONSE_BODY, RenderPath.ERROR_BODY}) {
            assertFalse(codesWithPath(path).isEmpty(), "该渲染路径没有任何登记项（断言会空转）: " + path);
        }
        // 注：原先此处还有一条「ERROR_BODY ⇒ status() == null」的分支式断言。#404 拆码把
        // RATE_LIMIT_EXCEEDED 登记成 ERROR_BODY + 429 后该断言不再成立，也不该成立：
        // 「状态不由规则链决定」不等于「状态为空」。这条不变量的**准确形态**由下面两个用例承担 ——
        // statusIsNull_onlyForDownstreamServiceError（为 null 的码恰好一个）与
        // apiExceptionCodes_resolveToRegisteredStatus（规则链码的登记状态 = 推导值）。
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
    @DisplayName("认证路径：2 参构造器恒 401；登记状态 ≠ 401 的认证码必须由校验链显式携带（明列清单）")
    void authenticationCodes_matchTheStatusCarriedByTheException() {
        Set<String> checked = codesWithPath(RenderPath.AUTHENTICATION);
        Set<String> nonDefault = new TreeSet<>();
        for (String code : checked) {
            HttpStatus registered = ErrorCode.of(code).orElseThrow().status();
            if (registered == HttpStatus.UNAUTHORIZED) {
                // 绝大多数认证码：登记 401 且 2 参构造器也是 401 ⇒ 两条链一致（零行为变化）
                assertEquals(HttpStatus.UNAUTHORIZED,
                        new AuthenticationException("测试消息", code).getHttpStatus(),
                        "登记 401 的认证码必须与 2 参构造器携带的状态一致: " + code);
            } else {
                // #404 起认证链上出现了非 401 的码：它不再走 2 参构造器，而由校验链
                // FullValidationResult.toException() 显式携带注册表状态 ⇒ 用真实发出点断言。
                nonDefault.add(code);
                assertEquals(registered,
                        FullValidationResult.validationError("key-001", "测试消息", code)
                                .toException().getHttpStatus(),
                        "登记状态 ≠ 401 的认证码必须由校验链显式携带该状态: " + code);
            }
        }
        assertEquals(Set.of("DAILY_QUOTA_EXCEEDED", "RATE_LIMITED_API_KEY", "TOKEN_QUOTA_EXCEEDED"), nonDefault,
                "凡登记状态 ≠ 401 的认证码都必须在此清单里明列（新增或消除都要改这张清单）");
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
    @DisplayName("状态为 null 的码：只有 DOWNSTREAM_SERVICE_ERROR（错误体直写路径不再等于「状态为空」）")
    void statusIsNull_onlyForDownstreamServiceError() {
        Set<String> dynamic = new TreeSet<>(java.util.Arrays.stream(ErrorCode.values())
                .filter(c -> c.status() == null)
                .map(ErrorCode::code)
                .collect(Collectors.toSet()));
        // DOWNSTREAM_SERVICE_ERROR 由 SecurityExceptionHandler 直写错误体，状态原样取下游返回的状态；
        // 同为 ERROR_BODY 路径的 RATE_LIMIT_EXCEEDED 由管理面限流器固定写 429 ⇒ 它**有**登记状态。
        assertEquals(Set.of("DOWNSTREAM_SERVICE_ERROR"), dynamic);
        assertEquals(RenderPath.ERROR_BODY, ErrorCode.DOWNSTREAM_SERVICE_ERROR.renderPath());
        assertEquals(HttpStatus.TOO_MANY_REQUESTS, ErrorCode.RATE_LIMIT_EXCEEDED.status());
        assertEquals(RenderPath.ERROR_BODY, ErrorCode.RATE_LIMIT_EXCEEDED.renderPath());
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
        // #404 新增的认证码：规则链推导值（RATE_LIMITED_ 前缀 ⇒ 429）恰好与登记值自洽，
        // 但它**依然不查表** —— 429 是校验链显式携带的，不是规则链给的。
        assertEquals(HttpStatus.TOO_MANY_REQUESTS, ApiException.resolveStatus("RATE_LIMITED_API_KEY"));
        assertFalse(ErrorCode.isResolvedByRuleChain("RATE_LIMITED_API_KEY"));
        // 仅规则链路径的码才查表
        assertTrue(ErrorCode.isResolvedByRuleChain("NOT_FOUND"));
        assertFalse(ErrorCode.isResolvedByRuleChain("INVALID_API_KEY"));
        assertFalse(ErrorCode.isResolvedByRuleChain("NOT_A_REGISTERED_CODE"));
        assertFalse(ErrorCode.isResolvedByRuleChain(null));
    }

    @Test
    @DisplayName("限流两码分立（issue #404 已裁决拆码）：渲染路径与状态逐个钉住，校验链不再产出 401 限流")
    void rateLimitCodes_areSplitByRenderPath() {
        // ① 管理面限流器（AdminApiRateLimiter:113-119 直写 {"code":"RATE_LIMIT_EXCEEDED","message":...}）→ 429 扁平体
        assertEquals(HttpStatus.TOO_MANY_REQUESTS, ErrorCode.RATE_LIMIT_EXCEEDED.status());
        assertEquals(RenderPath.ERROR_BODY, ErrorCode.RATE_LIMIT_EXCEEDED.renderPath());
        // ② API Key 校验链（ApiKeyValidator.validateRateLimit → FullValidationResult.toException()）→ 429，但码不同
        assertEquals(HttpStatus.TOO_MANY_REQUESTS, ErrorCode.RATE_LIMITED_API_KEY.status());
        assertEquals(RenderPath.AUTHENTICATION, ErrorCode.RATE_LIMITED_API_KEY.renderPath());
        AuthenticationException fromChain = FullValidationResult
                .validationError("key-001", "请求频率超过限制，请稍后再试",
                        ErrorCode.RATE_LIMITED_API_KEY.code())
                .toException();
        assertEquals(HttpStatus.TOO_MANY_REQUESTS, fromChain.getHttpStatus(),
                "校验链的限流异常必须携带 429（拆码前它恒为 401）");
        assertEquals(ErrorCode.RATE_LIMITED_API_KEY.code(), fromChain.getErrorCode());
        assertNotEquals(ErrorCode.RATE_LIMIT_EXCEEDED.code(), fromChain.getErrorCode(),
                "两条路径不得再共用同一个码");
        // 拆码前「同码两状态」的两个取值今天分属两个码：客户端只按状态码即可写分支
        assertEquals(HttpStatus.TOO_MANY_REQUESTS, ErrorCode.RATE_LIMIT_EXCEEDED.status());
        assertEquals(HttpStatus.TOO_MANY_REQUESTS, ErrorCode.RATE_LIMITED_API_KEY.status());
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
        assertEquals(2, counts.get(RenderPath.ERROR_BODY),
                "错误体直写路径今天有 2 个：DOWNSTREAM_SERVICE_ERROR（随上下文）与 "
                        + "RATE_LIMIT_EXCEEDED（管理面固定 429，issue #404）: " + counts);
    }
}
