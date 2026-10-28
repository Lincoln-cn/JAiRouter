package org.unreal.modelrouter.router.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.unreal.modelrouter.common.constants.ErrorCode;
import org.unreal.modelrouter.common.controller.response.RouterResponse;
import org.unreal.modelrouter.router.adapter.AdapterRegistry;
import org.unreal.modelrouter.router.adapter.config.AdapterDefinitionProperties;
import org.unreal.modelrouter.router.adapter.support.AdapterContext;
import org.unreal.modelrouter.router.adapter.persistence.AdapterDefinitionPersistenceService;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link AdapterConfigController} 创建/更新 adapter 的必填字段校验（issue #709）。
 *
 * <p><b>修的病</b>：缺 {@code auth} 或 {@code capabilities} 时，控制器先把定义
 * {@code put} 进 {@code AdapterDefinitionProperties} 并 {@code saveDefinition} 落库，
 * 之后 {@code createAdapterByType} 才去解引用 {@code definition.getAuth().getHeaderName()} /
 * {@code definition.getCapabilities().isChat()} ⇒ NPE 被兜底 {@code catch (Exception e)}
 * 包成 <b>500</b>，而<b>不完整的定义已经落库</b>；客户端重试只会撞上「adapter名称已存在」的 409，
 * 永远拿不到 201。</p>
 *
 * <p><b>判据</b>：缺字段必须是 <b>400 + INVALID_PARAM</b>，且此刻
 * ① 持久化层零调用、② 内存定义表零新增（更新场景：原定义对象未被替换）。
 * 对照组用「字段齐全 ⇒ 201/200 且确实落库」证明 400 是这几个字段的显式判断，
 * 而不是「所有创建都被拒」。</p>
 *
 * <p>纯单元测试：直接调用控制器方法，不启动 Spring 上下文。{@code AdapterContext} 传一个只 stub 了
 * {@code getObjectMapper()} 的 mock —— 对照组「字段齐全 ⇒ 201」会真的构造 {@code ConfigurableAdapter}，
 * 而 {@code BaseAdapter} 的构造函数需要它；其余三个依赖（请求处理 / 弹性 / transformer）仍传 {@code null}，
 * 它们在构造期只被赋值、不被解引用。</p>
 */
@DisplayName("AdapterConfigController 必填字段校验（issue #709）")
class AdapterConfigControllerCreateValidationTest {

    /** 被测 adapter 名（非内置、未被占用）。 */
    private static final String ADAPTER = "custom-openai";

    /** 父 adapter 名（只有 extend 场景使用）。 */
    private static final String PARENT = "normal";

    /** 适配器注册表（mock）。 */
    private AdapterRegistry adapterRegistry;

    /** 内存定义表（mock 返回真实 map，便于断言「有没有多出一条脏记录」）。 */
    private AdapterDefinitionProperties definitionProperties;

    /** 持久化服务（mock，用于断言「校验失败时零落库」）。 */
    private AdapterDefinitionPersistenceService persistenceService;

    /** 定义表本体。 */
    private Map<String, AdapterDefinitionProperties.AdapterDefinition> definitions;

    /** 被测控制器。 */
    private AdapterConfigController controller;

    @BeforeEach
    void setUp() {
        adapterRegistry = mock(AdapterRegistry.class);
        definitionProperties = mock(AdapterDefinitionProperties.class);
        persistenceService = mock(AdapterDefinitionPersistenceService.class);
        definitions = new HashMap<>();
        when(definitionProperties.getAdapterDefinitions()).thenReturn(definitions);
        // 对照组会真的构造 ConfigurableAdapter，其父类构造期解引用 context.getObjectMapper()
        final AdapterContext adapterContext = mock(AdapterContext.class);
        when(adapterContext.getObjectMapper()).thenReturn(new ObjectMapper());
        controller = new AdapterConfigController(adapterRegistry, definitionProperties,
                persistenceService, adapterContext, null, null, null, null);
    }

    // ==================== 创建：缺字段 ⇒ 400 且仓库无新增记录 ====================

    @Test
    @DisplayName("POST 缺 auth ⇒ 400 + INVALID_PARAM 且零落库、定义表无新增")
    void createWithoutAuthIsRejectedBeforePersisting() {
        final AdapterConfigController.AdapterDefinitionRequest request = request(ADAPTER, "openai-compatible");
        request.setCapabilities(Map.of("chat", true));

        final ResponseEntity<RouterResponse<Map<String, Object>>> response = controller.createAdapter(request);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode(),
                "缺 auth 属客户端可归因的请求不合法 ⇒ 400，而不是构造期 NPE 被包成 500");
        final RouterResponse<Map<String, Object>> body = response.getBody();
        assertNotNull(body);
        assertFalse(body.isSuccess());
        assertEquals(ErrorCode.INVALID_PARAM.code(), body.getErrorCode());
        assertTrue(body.getMessage().contains("auth 必填"),
                "消息应点名缺失的字段，实际：" + body.getMessage());
        verify(persistenceService, never()).saveDefinition(anyString(), any());
        assertFalse(definitions.containsKey(ADAPTER),
                "校验失败时绝不能把不完整定义写进内存定义表（修复前这里会多出一条脏记录）");
        verify(adapterRegistry, never()).registerAdapter(anyString(), any());
    }

    @Test
    @DisplayName("POST capabilities 里显式 null ⇒ 400，而不是构造期拆箱 NPE 的 500")
    void createWithNullCapabilityValueIsRejected() {
        // 复现路径：request.getCapabilities().getOrDefault("chat", false) 对「键存在、值为 null」
        // 仍返回 null，赋给 boolean 参数时拆箱抛 NPE —— 修复前被兜底 catch 包成 500。
        final AdapterConfigController.AdapterDefinitionRequest request =
                new AdapterConfigController.AdapterDefinitionRequest();
        request.setName(ADAPTER);
        request.setAuth(Map.of("headerName", "Authorization"));
        final Map<String, Boolean> capabilities = new HashMap<>();
        capabilities.put("chat", null);
        request.setCapabilities(capabilities);

        final ResponseEntity<RouterResponse<Map<String, Object>>> response =
                controller.createAdapter(request);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertTrue(String.valueOf(response.getBody().getMessage()).contains("不能为 null"),
                "400 消息应说明哪个字段的 null 不合法");
        verify(persistenceService, never()).saveDefinition(anyString(), any());
        assertFalse(definitions.containsKey(ADAPTER), "校验失败时不能让定义进入内存表");
        verify(adapterRegistry, never()).registerAdapter(anyString(), any());
    }

    @Test
    @DisplayName("POST auth 里显式 null 头名 ⇒ 400，而不是把 null 落库")
    void createWithNullAuthValueIsRejected() {
        final AdapterConfigController.AdapterDefinitionRequest request =
                new AdapterConfigController.AdapterDefinitionRequest();
        request.setName(ADAPTER);
        final Map<String, String> auth = new HashMap<>();
        auth.put("headerName", null);
        request.setAuth(auth);
        request.setCapabilities(Map.of("chat", true));

        final ResponseEntity<RouterResponse<Map<String, Object>>> response =
                controller.createAdapter(request);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        verify(persistenceService, never()).saveDefinition(anyString(), any());
        assertFalse(definitions.containsKey(ADAPTER), "校验失败时不能让定义进入内存表");
        verify(adapterRegistry, never()).registerAdapter(anyString(), any());
    }

    @Test
    @DisplayName("POST 缺 capabilities ⇒ 400 + INVALID_PARAM 且零落库、定义表无新增")
    void createWithoutCapabilitiesIsRejectedBeforePersisting() {
        final AdapterConfigController.AdapterDefinitionRequest request = request(ADAPTER, "openai-compatible");
        request.setAuth(Map.of("headerName", "Authorization"));

        final ResponseEntity<RouterResponse<Map<String, Object>>> response = controller.createAdapter(request);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode(),
                "缺 capabilities 时 createConfigurableAdapter 里 definition.getCapabilities().isChat() 是 NPE 源");
        final RouterResponse<Map<String, Object>> body = response.getBody();
        assertNotNull(body);
        assertEquals(ErrorCode.INVALID_PARAM.code(), body.getErrorCode());
        assertTrue(body.getMessage().contains("capabilities 必填"),
                "消息应点名缺失的字段，实际：" + body.getMessage());
        verify(persistenceService, never()).saveDefinition(anyString(), any());
        assertFalse(definitions.containsKey(ADAPTER));
    }

    @Test
    @DisplayName("POST capabilities 为空对象 ⇒ 也按缺字段处理（400）")
    void createWithEmptyCapabilitiesIsRejected() {
        final AdapterConfigController.AdapterDefinitionRequest request = request(ADAPTER, "openai-compatible");
        request.setAuth(Map.of("headerName", "Authorization"));
        request.setCapabilities(Map.of());

        final ResponseEntity<RouterResponse<Map<String, Object>>> response = controller.createAdapter(request);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(ErrorCode.INVALID_PARAM.code(), response.getBody().getErrorCode());
        verify(persistenceService, never()).saveDefinition(anyString(), any());
    }

    @Test
    @DisplayName("POST type=extend 缺 parent ⇒ 400 且零落库（修复前是 500）")
    void createExtendWithoutParentIsRejected() {
        final AdapterConfigController.AdapterDefinitionRequest request = request(ADAPTER, "extend");
        request.setAuth(Map.of("headerName", "Authorization"));

        final ResponseEntity<RouterResponse<Map<String, Object>>> response = controller.createAdapter(request);

        // 先断言副作用、再断言状态：旧顺序是「先落库后构造」，构造失败时这里会先红，
        // 把「不完整的定义已经落库 / 已经进内存表」直接暴露出来（A/B 反证依赖这个顺序）。
        verify(persistenceService, never()).saveDefinition(anyString(), any());
        assertFalse(definitions.containsKey(ADAPTER), "构造失败时不完整定义不能已进内存表");
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode(),
                "createExtendedAdapter 里 parent 为空只抛 IllegalArgumentException ⇒ 修复前被包成 500");
        assertNotNull(response.getBody());
        assertEquals(ErrorCode.INVALID_PARAM.code(), response.getBody().getErrorCode());
        assertTrue(response.getBody().getMessage().contains("parent 必填"),
                "消息应点名 parent，实际：" + response.getBody().getMessage());
    }

    @Test
    @DisplayName("POST type=extend 的 parent 未注册 ⇒ 400 且零落库")
    void createExtendWithUnknownParentIsRejected() {
        final AdapterConfigController.AdapterDefinitionRequest request = request(ADAPTER, "extend");
        request.setAuth(Map.of("headerName", "Authorization"));
        request.setParent("no-such-parent");
        when(adapterRegistry.getAdapterByName("no-such-parent")).thenReturn(null);

        final ResponseEntity<RouterResponse<Map<String, Object>>> response = controller.createAdapter(request);

        verify(persistenceService, never()).saveDefinition(anyString(), any());
        assertFalse(definitions.containsKey(ADAPTER), "构造失败时不完整定义不能已进内存表");
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(ErrorCode.INVALID_PARAM.code(), response.getBody().getErrorCode());
        assertTrue(response.getBody().getMessage().contains("parent adapter 不存在"),
                "消息应点名那个不存在的父 adapter，实际：" + response.getBody().getMessage());
    }

    @Test
    @DisplayName("对照组：字段齐全 ⇒ 201 且确实落库（证明 400 是字段判断，不是一律拒绝）")
    void createWithRequiredFieldsSucceeds() {
        final AdapterConfigController.AdapterDefinitionRequest request = request(ADAPTER, "openai-compatible");
        request.setAuth(Map.of("headerName", "X-Api-Key", "headerPrefix", ""));
        request.setCapabilities(Map.of("chat", true));

        final ResponseEntity<RouterResponse<Map<String, Object>>> response = controller.createAdapter(request);

        assertEquals(HttpStatus.CREATED, response.getStatusCode(),
                "字段齐全时必须照旧创建成功，实际：" + response.getBody().getMessage());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().isSuccess());
        verify(persistenceService).saveDefinition(eq(ADAPTER), any());
        assertTrue(definitions.containsKey(ADAPTER), "创建成功后定义表必须有这条记录");
        assertEquals("X-Api-Key", definitions.get(ADAPTER).getAuth().getHeaderName());
        verify(adapterRegistry).registerAdapter(eq(ADAPTER), any());
    }

    @Test
    @DisplayName("落库失败 ⇒ 500 且内存定义表与注册表都没被改（顺序修复的直接证据）")
    void persistFailureLeavesMemoryUntouched() {
        final AdapterConfigController.AdapterDefinitionRequest request = request(ADAPTER, "openai-compatible");
        request.setAuth(Map.of("headerName", "Authorization"));
        request.setCapabilities(Map.of("chat", true));
        doThrow(new RuntimeException("store down")).when(persistenceService).saveDefinition(anyString(), any());

        final ResponseEntity<RouterResponse<Map<String, Object>>> response = controller.createAdapter(request);

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode(),
                "存储故障仍是服务端失败 ⇒ 500 语义不变");
        assertNotNull(response.getBody());
        assertEquals(ErrorCode.INTERNAL_ERROR.code(), response.getBody().getErrorCode());
        assertFalse(definitions.containsKey(ADAPTER),
                "落库失败时内存定义表必须保持原样（修复前是先 put 再落库 ⇒ 会留下一条重启后消失的幽灵定义）");
        verify(adapterRegistry, never()).registerAdapter(anyString(), any());
        assertEquals(0, definitions.size());
    }

    // ==================== 更新：缺字段 ⇒ 400 且原定义不被覆盖 ====================

    @Test
    @DisplayName("PUT 缺 auth ⇒ 400 且原定义对象未被替换（修复前会覆盖成不完整定义）")
    void updateWithoutAuthKeepsPreviousDefinition() {
        final AdapterDefinitionProperties.AdapterDefinition previous = definition("openai-compatible");
        previous.setAuth(new AdapterDefinitionProperties.AuthConfig());
        definitions.put(ADAPTER, previous);
        final AdapterConfigController.AdapterDefinitionRequest request = request(ADAPTER, "openai-compatible");
        request.setCapabilities(Map.of("chat", true));

        final ResponseEntity<RouterResponse<Map<String, Object>>> response =
                controller.updateAdapter(ADAPTER, request);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(ErrorCode.INVALID_PARAM.code(), response.getBody().getErrorCode());
        assertSame(previous, definitions.get(ADAPTER),
                "校验失败时原定义必须原样保留（修复前 definitions.put 已把它换成不完整的定义）");
        verify(persistenceService, never()).saveDefinition(anyString(), any());
    }

    @Test
    @DisplayName("PUT 缺 capabilities ⇒ 400 且原定义对象未被替换")
    void updateWithoutCapabilitiesKeepsPreviousDefinition() {
        final AdapterDefinitionProperties.AdapterDefinition previous = definition("openai-compatible");
        definitions.put(ADAPTER, previous);
        final AdapterConfigController.AdapterDefinitionRequest request = request(ADAPTER, "openai-compatible");
        request.setAuth(Map.of("headerName", "Authorization"));

        final ResponseEntity<RouterResponse<Map<String, Object>>> response =
                controller.updateAdapter(ADAPTER, request);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertSame(previous, definitions.get(ADAPTER));
        verify(persistenceService, never()).saveDefinition(anyString(), any());
    }

    @Test
    @DisplayName("对照组：PUT 字段齐全 ⇒ 200 且定义被替换并落库")
    void updateWithRequiredFieldsSucceeds() {
        final AdapterDefinitionProperties.AdapterDefinition previous = definition("openai-compatible");
        definitions.put(ADAPTER, previous);
        final AdapterConfigController.AdapterDefinitionRequest request = request(ADAPTER, "openai-compatible");
        request.setAuth(Map.of("headerName", "X-Api-Key"));
        request.setCapabilities(Map.of("embedding", true));

        final ResponseEntity<RouterResponse<Map<String, Object>>> response =
                controller.updateAdapter(ADAPTER, request);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().isSuccess());
        assertNotSame(previous, definitions.get(ADAPTER), "更新成功后定义表应换成新对象");
        assertEquals("X-Api-Key", definitions.get(ADAPTER).getAuth().getHeaderName());
        verify(persistenceService).saveDefinition(eq(ADAPTER), any());
    }

    // ==================== 夹具 ====================

    /**
     * 构造一个「只填 name/type」的请求体 —— 其余字段按用例补。
     *
     * @param name adapter 名
     * @param type adapter 类型
     * @return 请求体
     */
    private static AdapterConfigController.AdapterDefinitionRequest request(final String name, final String type) {
        final AdapterConfigController.AdapterDefinitionRequest request =
                new AdapterConfigController.AdapterDefinitionRequest();
        request.setName(name);
        request.setType(type);
        return request;
    }

    /**
     * 构造一个已存在的定义（更新场景的「原定义」）。
     *
     * @param type adapter 类型
     * @return 定义
     */
    private static AdapterDefinitionProperties.AdapterDefinition definition(final String type) {
        final AdapterDefinitionProperties.AdapterDefinition definition =
                new AdapterDefinitionProperties.AdapterDefinition();
        definition.setType(type);
        return definition;
    }
}
