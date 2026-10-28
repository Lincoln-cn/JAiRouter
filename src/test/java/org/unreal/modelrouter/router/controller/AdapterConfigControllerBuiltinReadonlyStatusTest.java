package org.unreal.modelrouter.router.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.server.ServerWebInputException;
import org.unreal.modelrouter.common.constants.ErrorCode;
import org.unreal.modelrouter.common.controller.response.RouterResponse;
import org.unreal.modelrouter.router.adapter.AdapterRegistry;
import org.unreal.modelrouter.router.adapter.config.AdapterDefinitionProperties;
import org.unreal.modelrouter.router.adapter.persistence.AdapterDefinitionPersistenceService;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * {@link AdapterConfigController} 内置 adapter 只读分支的状态语义（issue #405 混合方案）。
 *
 * <p><b>判据</b>：内置项只读是**授权**语义 ⇒ PUT / DELETE 内置 adapter 必须返回 <b>403</b>
 * 且 {@code errorCode = BUILTIN_READONLY}；注册表（{@link ErrorCode#BUILTIN_READONLY}）与
 * 规则链（{@code ApiException.legacyResolveStatus}）对同一个码也推导 403 ⇒ 两端一致。</p>
 *
 * <p>纯单元测试：直接调用控制器方法，不启动 Spring 上下文。403 分支在方法最前 return，
 * 只触达 {@link AdapterRegistry#isBuiltinAdapter(String)}；后续为 issue #413 扩充的用例
 * 才注入定义表与持久化服务的 mock（其余构造参数仍为 {@code null} ⇒ 它们不会被用到）。</p>
 *
 * <p>对照组（非内置且未配置 ⇒ 404 + NOT_FOUND）用来证明 403 是**该分支显式写出的**，
 * 而不是「所有请求都变成 403」这类恒真断言。</p>
 *
 * <p><b>issue #413 补充的覆盖面</b>：六个兜底 {@code catch (Exception e)} 分支
 * （{@code /list}、{@code /{name}} GET/PUT/DELETE、POST、{@code /parents}）都必须给出
 * {@code INTERNAL_ERROR}，而不是 {@code null}。</p>
 */
@DisplayName("AdapterConfigController 内置 adapter 只读状态（issue #405）")
class AdapterConfigControllerBuiltinReadonlyStatusTest {

    /** 内置 adapter 名（与 AdapterRegistry 里登记的内置名一致）。 */
    private static final String BUILTIN_ADAPTER = "normal";

    /** 既不是内置、也不在配置里的 adapter 名（对照组的输入）。 */
    private static final String UNKNOWN_ADAPTER = "not-configured-adapter";

    /** 定义表里存在的 adapter 名（用于触达「定义存在」之后的代码路径）。 */
    private static final String CONFIGURED_ADAPTER = "configured-adapter";

    private AdapterConfigController controller;

    /** 定义表（可单独 stub），构造控制器时注入。 */
    private AdapterDefinitionProperties definitionProperties;

    /** 适配器注册表（可单独 stub），构造控制器时注入。 */
    private AdapterRegistry adapterRegistry;

    /** 持久化服务（仅 create 失败路径使用）。 */
    private AdapterDefinitionPersistenceService persistenceService;

    @BeforeEach
    void setUp() {
        adapterRegistry = mock(AdapterRegistry.class);
        when(adapterRegistry.isBuiltinAdapter(BUILTIN_ADAPTER)).thenReturn(true);
        when(adapterRegistry.isBuiltinAdapter(UNKNOWN_ADAPTER)).thenReturn(false);
        definitionProperties = mock(AdapterDefinitionProperties.class);
        persistenceService = mock(AdapterDefinitionPersistenceService.class);
        controller = new AdapterConfigController(adapterRegistry, definitionProperties,
                persistenceService, null, null, null, null, null);
    }

    @Test
    @DisplayName("PUT /api/config/adapter/{name} - 内置 adapter ⇒ 403 + BUILTIN_READONLY")
    void updateBuiltinAdapterReturnsForbidden() {
        // issue #432 之后必须传**非 null** 请求体：方法最前面新增了「请求体缺失 ⇒ 400」的前置校验，
        // 传 null 会先撞上那条（见 updateWithNullRequestBodyIsRejectedAsClientInput）。
        // 本用例要验的是**授权**分支（内置项只读 ⇒ 403），故给一个最小合法请求体把它隔离出来。
        final AdapterConfigController.AdapterDefinitionRequest request =
                new AdapterConfigController.AdapterDefinitionRequest();
        request.setName(BUILTIN_ADAPTER);

        final ResponseEntity<RouterResponse<Map<String, Object>>> response =
                controller.updateAdapter(BUILTIN_ADAPTER, request);

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
    @DisplayName("对照组：非内置且未配置 ⇒ DELETE 404 + NOT_FOUND（证明 403 是该分支显式写出的）")
    void unknownAdapterDeleteStillReturnsNotFound() {
        when(definitionProperties.getAdapterDefinitions()).thenReturn(new HashMap<>());

        final ResponseEntity<RouterResponse<Void>> delete = controller.deleteAdapter(UNKNOWN_ADAPTER);
        assertEquals(HttpStatus.NOT_FOUND, delete.getStatusCode());
        assertNotNull(delete.getBody());
        assertEquals(ErrorCode.NOT_FOUND.code(), delete.getBody().getErrorCode());
    }

    @Test
    @DisplayName("对照组：非内置且未配置 ⇒ PUT 404 + NOT_FOUND（用最小 stub 定义绕开 request 判空）")
    void unknownAdapterUpdateStillReturnsNotFound() {
        // 定义表里塞一个占位定义：让方法走到「name 不在表里」的 404 分支，
        // 而不是先撞上 request.getName() 的 NPE（请求体为 null 的路径见
        // updateWithNullRequestBodyIsRejectedAsClientInput；该方法非空、但 name 不在表里）
        when(definitionProperties.getAdapterDefinitions()).thenReturn(new HashMap<>(Map.of(
                "placeholder-adapter", new AdapterDefinitionProperties.AdapterDefinition())));
        final AdapterConfigController.AdapterDefinitionRequest request =
                new AdapterConfigController.AdapterDefinitionRequest();
        request.setName(UNKNOWN_ADAPTER);

        final ResponseEntity<RouterResponse<Map<String, Object>>> update =
                controller.updateAdapter(UNKNOWN_ADAPTER, request);

        assertEquals(HttpStatus.NOT_FOUND, update.getStatusCode());
        assertNotNull(update.getBody());
        assertEquals(ErrorCode.NOT_FOUND.code(), update.getBody().getErrorCode());
    }

    // ==================== issue #413：兜底 catch 分支的失败响应必须带 errorCode ====================

    @Test
    @DisplayName("GET /list - 注册表抛异常 ⇒ 500 + INTERNAL_ERROR（issue #413）")
    void listFailureCarriesInternalError() {
        when(adapterRegistry.getAllAdapters()).thenThrow(new RuntimeException("boom"));

        final ResponseEntity<RouterResponse<List<Map<String, Object>>>> response = controller.getAllAdapters();

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode(),
                "HTTP 状态码不得因补码而变化");
        assertNotNull(response.getBody());
        assertEquals(ErrorCode.INTERNAL_ERROR.code(), response.getBody().getErrorCode());
    }

    @Test
    @DisplayName("GET /{name} - 读取定义表抛异常 ⇒ 500 + INTERNAL_ERROR（issue #413）")
    void detailFailureCarriesInternalError() {
        when(definitionProperties.getAdapterDefinitions()).thenThrow(new RuntimeException("boom"));

        final ResponseEntity<RouterResponse<Map<String, Object>>> response = controller.getAdapter(UNKNOWN_ADAPTER);

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode(),
                "HTTP 状态码不得因补码而变化");
        assertNotNull(response.getBody());
        assertEquals(ErrorCode.INTERNAL_ERROR.code(), response.getBody().getErrorCode());
    }

    @Test
    @DisplayName("PUT /{name} - request 为 null ⇒ 抛 ServerWebInputException（issue #432 前置校验，客户端得 400）")
    void updateWithNullRequestBodyIsRejectedAsClientInput() {
        when(definitionProperties.getAdapterDefinitions()).thenReturn(new HashMap<>(Map.of(
                CONFIGURED_ADAPTER, new AdapterDefinitionProperties.AdapterDefinition())));

        // issue #432：请求体缺失属客户端可归因的失败 ⇒ 方法开头就抛 ServerWebInputException，
        // 不再走到 request.getType() 的 NPE、被兜底 catch 吞成 500 + INTERNAL_ERROR。
        // 该异常由 ReactiveGlobalExceptionHandler 映射为 400（"请求体无效或缺失"），
        // MVC 层的行为见 AdapterConfigControllerRequestBodyPrevalidationTest（走真实 HTTP 路径）。
        assertThrows(ServerWebInputException.class,
                () -> controller.updateAdapter(CONFIGURED_ADAPTER, null),
                "request 为 null 时必须抛 ServerWebInputException（⇒ 400），而不是返回 500");
    }

    @Test
    @DisplayName("POST / - 持久化抛异常 ⇒ 500 + INTERNAL_ERROR（issue #413）")
    void createFailureCarriesInternalError() {
        when(definitionProperties.getAdapterDefinitions()).thenReturn(new HashMap<>());
        doThrow(new RuntimeException("boom"))
                .when(persistenceService).saveDefinition(anyString(), any());
        final AdapterConfigController.AdapterDefinitionRequest request =
                new AdapterConfigController.AdapterDefinitionRequest();
        request.setName(UNKNOWN_ADAPTER);
        // issue #709 之后，缺 auth/capabilities 会在落库**之前**被校验拦成 400；
        // 本用例要验的是兜底 catch 的 INTERNAL_ERROR，故补齐最小合法字段让它走到持久化。
        request.setAuth(Map.of("headerName", "Authorization"));
        request.setCapabilities(Map.of("chat", true));

        final ResponseEntity<RouterResponse<Map<String, Object>>> response = controller.createAdapter(request);

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode(),
                "HTTP 状态码不得因补码而变化");
        assertNotNull(response.getBody());
        assertEquals(ErrorCode.INTERNAL_ERROR.code(), response.getBody().getErrorCode());
    }

    @Test
    @DisplayName("DELETE /{name} - 删除前读取定义表抛异常 ⇒ 500 + INTERNAL_ERROR（issue #413）")
    void deleteFailureCarriesInternalError() {
        when(definitionProperties.getAdapterDefinitions()).thenThrow(new RuntimeException("boom"));

        final ResponseEntity<RouterResponse<Void>> response = controller.deleteAdapter(UNKNOWN_ADAPTER);

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode(),
                "HTTP 状态码不得因补码而变化");
        assertNotNull(response.getBody());
        assertEquals(ErrorCode.INTERNAL_ERROR.code(), response.getBody().getErrorCode());
    }

    @Test
    @DisplayName("GET /parents - 注册表抛异常 ⇒ 500 + INTERNAL_ERROR（issue #413）")
    void parentListFailureCarriesInternalError() {
        when(adapterRegistry.getAllAdapters()).thenThrow(new RuntimeException("boom"));

        final ResponseEntity<RouterResponse<List<Map<String, Object>>>> response = controller.getParentAdapters();

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode(),
                "HTTP 状态码不得因补码而变化");
        assertNotNull(response.getBody());
        assertEquals(ErrorCode.INTERNAL_ERROR.code(), response.getBody().getErrorCode());
    }
}
