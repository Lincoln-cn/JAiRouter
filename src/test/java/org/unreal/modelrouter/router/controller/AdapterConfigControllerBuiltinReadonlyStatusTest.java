package org.unreal.modelrouter.router.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.unreal.modelrouter.common.constants.ErrorCode;
import org.unreal.modelrouter.common.controller.response.RouterResponse;
import org.unreal.modelrouter.router.adapter.AdapterRegistry;
import org.unreal.modelrouter.router.adapter.config.AdapterDefinitionProperties;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * {@link AdapterConfigController} 内置 adapter 只读分支的状态语义（issue #405 混合方案）。
 *
 * <p><b>判据</b>：内置项只读是**授权**语义 ⇒ PUT / DELETE 内置 adapter 必须返回 <b>403</b>
 * 且 {@code errorCode = BUILTIN_READONLY}；注册表（{@link ErrorCode#BUILTIN_READONLY}）与
 * 规则链（{@code ApiException.legacyResolveStatus}）对同一个码也推导 403 ⇒ 两端一致。</p>
 *
 * <p>纯单元测试：直接调用控制器方法，不启动 Spring 上下文。该分支在方法最前 return，
 * 只有 {@link AdapterRegistry#isBuiltinAdapter(String)} 会被触达 ⇒ 其余构造参数显式传
 * {@code null}，这本身就是「它们不会被用到」的证明；一旦分支顺序被改动，测试会以 NPE 立刻暴露。</p>
 *
 * <p>对照组（非内置且未配置 ⇒ 404 + NOT_FOUND）用来证明 403 是**该分支显式写出的**，
 * 而不是「所有请求都变成 403」这类恒真断言。</p>
 */
@DisplayName("AdapterConfigController 内置 adapter 只读状态（issue #405）")
class AdapterConfigControllerBuiltinReadonlyStatusTest {

    /** 内置 adapter 名（与 AdapterRegistry 里登记的内置名一致）。 */
    private static final String BUILTIN_ADAPTER = "normal";

    /** 既不是内置、也不在配置里的 adapter 名（对照组的输入）。 */
    private static final String UNKNOWN_ADAPTER = "not-configured-adapter";

    private AdapterConfigController controller;

    @BeforeEach
    void setUp() {
        final AdapterRegistry adapterRegistry = mock(AdapterRegistry.class);
        when(adapterRegistry.isBuiltinAdapter(BUILTIN_ADAPTER)).thenReturn(true);
        when(adapterRegistry.isBuiltinAdapter(UNKNOWN_ADAPTER)).thenReturn(false);
        // 空的 adapter 定义表：对照组要落到「定义了里没有这个名字」的 404 分支
        controller = new AdapterConfigController(adapterRegistry, new AdapterDefinitionProperties(),
                null, null, null, null, null, null);
    }

    @Test
    @DisplayName("PUT /api/config/adapter/{name} - 内置 adapter ⇒ 403 + BUILTIN_READONLY")
    void updateBuiltinAdapterReturnsForbidden() {
        final ResponseEntity<RouterResponse<Map<String, Object>>> response =
                controller.updateAdapter(BUILTIN_ADAPTER, null);

        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode(),
                "内置项只读是授权语义，端点必须与规则链推导值一致（403）");
        final RouterResponse<Map<String, Object>> body = response.getBody();
        assertNotNull(body);
        assertFalse(body.isSuccess());
        assertEquals(ErrorCode.BUILTIN_READONLY.code(), body.getErrorCode());
        assertTrue(body.getMessage().contains(BUILTIN_ADAPTER), "消息里应指出被拒的 adapter 名");
    }

    @Test
    @DisplayName("DELETE /api/config/adapter/{name} - 内置 adapter ⇒ 403 + BUILTIN_READONLY")
    void deleteBuiltinAdapterReturnsForbidden() {
        final ResponseEntity<RouterResponse<Void>> response = controller.deleteAdapter(BUILTIN_ADAPTER);

        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode(),
                "内置项只读是授权语义，端点必须与规则链推导值一致（403）");
        final RouterResponse<Void> body = response.getBody();
        assertNotNull(body);
        assertFalse(body.isSuccess());
        assertEquals(ErrorCode.BUILTIN_READONLY.code(), body.getErrorCode());
        assertTrue(body.getMessage().contains(BUILTIN_ADAPTER), "消息里应指出被拒的 adapter 名");
    }

    @Test
    @DisplayName("对照组：非内置且未配置 ⇒ 404 + NOT_FOUND（证明 403 是该分支显式写出的）")
    void unknownAdapterStillReturnsNotFound() {
        final ResponseEntity<RouterResponse<Map<String, Object>>> update =
                controller.updateAdapter(UNKNOWN_ADAPTER, null);
        assertEquals(HttpStatus.NOT_FOUND, update.getStatusCode());
        assertNotNull(update.getBody());
        assertEquals(ErrorCode.NOT_FOUND.code(), update.getBody().getErrorCode());

        final ResponseEntity<RouterResponse<Void>> delete = controller.deleteAdapter(UNKNOWN_ADAPTER);
        assertEquals(HttpStatus.NOT_FOUND, delete.getStatusCode());
        assertNotNull(delete.getBody());
        assertEquals(ErrorCode.NOT_FOUND.code(), delete.getBody().getErrorCode());
    }
}
