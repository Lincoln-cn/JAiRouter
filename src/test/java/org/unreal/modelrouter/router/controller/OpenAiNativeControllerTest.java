package org.unreal.modelrouter.router.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import org.unreal.modelrouter.common.dto.ChatDTO;
import org.unreal.modelrouter.router.handler.ServiceRequestExecutor;
import org.unreal.modelrouter.router.handler.ServiceRequestHandler;
import org.unreal.modelrouter.router.model.ModelCatalogService;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;

/**
 * {@link OpenAiNativeController} 单测（v3.1 PR-4a）.
 *
 * <p>覆盖：{@code GET /v1/models} 返回 <b>原生</b> OpenAI 列表（非 {@code RouterResponse} 包裹）、
 * 内容类型为 JSON；聊天入口在 exchange 上置原生响应标记并透传 DTO/Authorization。</p>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class OpenAiNativeControllerTest {

    @Mock
    private ServiceRequestHandler requestHandler;

    @Mock
    private ModelCatalogService modelCatalogService;

    private OpenAiNativeController controller;

    @BeforeEach
    void setUp() {
        controller = new OpenAiNativeController(requestHandler, modelCatalogService);
    }

    @Test
    @DisplayName("/v1/models 返回原生 OpenAI 列表（无 RouterResponse 包裹）")
    void models_shouldReturnNativeOpenAiList() {
        when(modelCatalogService.listAllModelsAsOpenAiList()).thenReturn(Map.of(
                "object", "list",
                "data", List.of(Map.of("id", "deepseek-chat", "object", "model"))));

        final ResponseEntity<Map<String, Object>> response = controller.models().block();

        assertNotNull(response);
        assertEquals(200, response.getStatusCode().value());
        assertEquals(MediaType.APPLICATION_JSON, response.getHeaders().getContentType());
        assertNotNull(response.getBody());
        assertFalse(response.getBody().containsKey("success"), "原生面不得返回 RouterResponse 包裹（无 success 字段）");
        assertEquals("list", response.getBody().get("object"));
    }

    @Test
    @DisplayName("chat/completions 置原生标记、透传 DTO 与 Authorization")
    void chatCompletions_shouldMarkNativeAndDelegate() {
        final MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.post("/v1/chat/completions")
                        .header("Authorization", "Bearer downstream-key")
                        .build());
        when(requestHandler.handleRequest(any(), anyString(), anyString(),
                any(ServerWebExchange.class), any(ServiceRequestExecutor.class)))
                .thenReturn(Mono.just(ResponseEntity.ok("ok")));

        final ChatDTO.Request request = new ChatDTO.Request(
                "deepseek-chat", null, null, null, null, null, null, null, null, null, null, null);
        final ResponseEntity<?> response = controller.chatCompletions("Bearer downstream-key", request, exchange).block();

        assertNotNull(response);
        assertEquals(Boolean.TRUE, exchange.getAttribute(ServiceRequestHandler.NATIVE_RESPONSE_ATTRIBUTE),
                "必须置原生响应标记，否则非流式会被包成 RouterResponse");
        assertEquals(request, exchange.getAttribute(ServiceRequestHandler.REQUEST_DTO_ATTRIBUTE),
                "原始 DTO 必须挂到 exchange（响应缓存键依赖）");
    }

    @Test
    @DisplayName("body 缺失时不抛 NPE（model 传 null 交给 handler 处理）")
    void chatCompletions_shouldTolerateMissingBody() {
        final MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.post("/v1/chat/completions").build());
        when(requestHandler.handleRequest(any(), isNull(), any(),
                any(ServerWebExchange.class), any(ServiceRequestExecutor.class)))
                .thenReturn(Mono.just(ResponseEntity.ok("ok")));

        assertNotNull(controller.chatCompletions(null, null, exchange).block());
        assertEquals(Boolean.TRUE, exchange.getAttribute(ServiceRequestHandler.NATIVE_RESPONSE_ATTRIBUTE));
    }
}
