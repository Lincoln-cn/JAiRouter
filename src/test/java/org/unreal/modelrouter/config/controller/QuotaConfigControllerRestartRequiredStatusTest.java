package org.unreal.modelrouter.config.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.unreal.modelrouter.auth.security.quota.QuotaProperties;
import org.unreal.modelrouter.common.constants.ErrorCode;
import org.unreal.modelrouter.common.controller.response.RouterResponse;
import org.unreal.modelrouter.common.exception.ApiException;
import org.unreal.modelrouter.common.util.JacksonHelper;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link QuotaConfigController} 「需重启」字段的状态语义（issue #405 混合方案）。
 *
 * <p><b>判据</b>：需重启 = 请求校验语义 ⇒ PUT 携带 {@code flushIntervalSeconds} /
 * {@code distributed.*} / {@code retention.*} 时必须返回 <b>400</b> 且
 * {@code errorCode = RESTART_REQUIRED}；注册表（{@link ErrorCode#RESTART_REQUIRED}）与
 * 规则链（{@code ApiException.legacyResolveStatus}，issue #405 起从 403 对齐为 400）
 * 对同一个码也推导 400 ⇒ 端点实测值与规则链推导值一致。</p>
 *
 * <p>纯单元测试：直接调用控制器方法，不启动 Spring 上下文。被拒分支在方法最前 return，
 * 因此账本服务传 {@code null} 即可（一旦分支顺序被改动，测试会以 NPE 立刻暴露）。</p>
 */
@DisplayName("QuotaConfigController 需重启字段的状态语义（issue #405）")
class QuotaConfigControllerRestartRequiredStatusTest {

    private static final ObjectMapper MAPPER = JacksonHelper.getObjectMapper();

    private QuotaConfigController controller;

    @BeforeEach
    void setUp() {
        controller = new QuotaConfigController(new QuotaProperties(), null);
    }

    @Test
    @DisplayName("PUT /api/config/quota - 提交需重启字段 ⇒ 400 + RESTART_REQUIRED")
    void restartRequiredFieldReturnsBadRequest() throws Exception {
        final QuotaConfigController.QuotaConfigUpdateRequest request =
                MAPPER.readValue("{\"flushIntervalSeconds\": 120}",
                        QuotaConfigController.QuotaConfigUpdateRequest.class);

        final ResponseEntity<RouterResponse<Map<String, Object>>> response =
                controller.updateConfig(request);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode(),
                "需重启是请求校验语义，端点必须与规则链推导值一致（400）");
        final RouterResponse<Map<String, Object>> body = response.getBody();
        assertNotNull(body);
        assertFalse(body.isSuccess());
        assertEquals(ErrorCode.RESTART_REQUIRED.code(), body.getErrorCode());
        assertTrue(body.getMessage().contains("flushIntervalSeconds"), "消息里应列出被拒字段名");
    }

    @Test
    @DisplayName("两端一致：注册表状态 = 规则链推导值 = 400")
    void registryAndRuleChainAgreeOnBadRequest() {
        assertEquals(HttpStatus.BAD_REQUEST, ErrorCode.RESTART_REQUIRED.status());
        assertEquals(HttpStatus.BAD_REQUEST,
                ApiException.resolveStatus(ErrorCode.RESTART_REQUIRED.code()));
        assertEquals(ErrorCode.RESTART_REQUIRED.status(),
                ApiException.resolveStatus(ErrorCode.RESTART_REQUIRED.code()));
    }
}
