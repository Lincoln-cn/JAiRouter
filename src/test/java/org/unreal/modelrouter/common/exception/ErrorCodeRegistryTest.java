package org.unreal.modelrouter.common.exception;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.unreal.modelrouter.common.constants.ErrorCode;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 错误码注册表的不变量（issue #370 方案 A）。
 *
 * <p>核心命题：**引入注册表不改变任何对外行为** —— 登记在 {@link ErrorCode} 里的码，其状态码必须与它们
 * 在旧规则链下解析出来的完全一致；未登记的码仍走旧规则链（{@link ApiException#legacyResolveStatus}）。</p>
 */
@DisplayName("ErrorCode 注册表不变量（issue #370）")
class ErrorCodeRegistryTest {

    @Test
    @DisplayName("每个登记码都解析成注册表里登记的状态码（零行为变化）")
    void everyRegisteredCode_resolvesToRegisteredStatus() {
        for (ErrorCode code : ErrorCode.values()) {
            assertEquals(code.status(), ApiException.resolveStatus(code.code()),
                    "登记码 " + code.code() + " 的状态必须等于注册表登记值（不得因引入注册表而改变对外状态码）");
            assertEquals(code, ErrorCode.of(code.code()).orElseThrow(),
                    "按码值应能反查回同一枚举项: " + code.code());
            assertTrue(ErrorCode.isRegistered(code.code()));
        }
    }

    @Test
    @DisplayName("注册表自身形态：码唯一、形如大写下划线、状态与说明非空")
    void registry_isWellFormed() {
        Set<String> seen = new HashSet<>();
        for (ErrorCode code : ErrorCode.values()) {
            assertTrue(code.code().matches("[A-Z][A-Z0-9_]{2,}"), "码形态不合规: " + code.code());
            assertTrue(seen.add(code.code()), "重复登记: " + code.code());
            assertNotNull(code.status(), "状态不得为空: " + code.code());
            assertFalse(code.description().isBlank(), "说明不得为空: " + code.code());
        }
        assertFalse(seen.isEmpty(), "注册表不得为空");
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
        assertEquals(HttpStatus.FORBIDDEN, ErrorCode.FORBIDDEN_TOKEN_USAGE.status());
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
    @DisplayName("约定自洽：每个登记状态都能由规则链对同形命名推出（防注册表与规则分叉）")
    void everyRegisteredStatus_isDerivableFromTheConvention() {
        for (ErrorCode code : ErrorCode.values()) {
            assertEquals(ApiException.legacyResolveStatus(code.code()), code.status(),
                    "登记状态必须等于规则链对同形命名的推导值: " + code.code());
        }
    }
}
