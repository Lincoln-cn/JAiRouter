package org.unreal.modelrouter.router.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import org.unreal.modelrouter.common.dto.ChatDTO;
import org.unreal.modelrouter.router.anthropic.AnthropicMessagesRequest;
import org.unreal.modelrouter.router.anthropic.AnthropicMessagesResponse;
import org.unreal.modelrouter.router.anthropic.AnthropicRequestTranslator;
import org.unreal.modelrouter.router.anthropic.AnthropicResponseTranslator;
import org.unreal.modelrouter.router.handler.ServiceEndpoint;
import org.unreal.modelrouter.router.handler.ServiceRequestExecutor;
import org.unreal.modelrouter.router.handler.ServiceRequestHandler;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link AnthropicMessagesController} 单测（v3.1 PR-4b）.
 *
 * <p>控制器使用<b>真实</b>翻译器（仅 {@link ServiceRequestHandler} 为 mock），覆盖：
 * {@code stream=true} 400 拒绝（Anthropic 错误体）、正常非流式请求置原生标记 + 挂 DTO +
 * 下游 JSON 翻译为 Anthropic Message、缺 {@code model} 的 400 前置校验。</p>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("Anthropic Messages 控制器")
class AnthropicMessagesControllerTest {

    private static final String DOWNSTREAM_JSON = """
            {"id":"chatcmpl-abc","object":"chat.completion","created":1735689600,"model":"deepseek-chat",
             "choices":[{"index":0,"message":{"role":"assistant","content":"你好！"},"finish_reason":"stop"}],
             "usage":{"prompt_tokens":12,"completion_tokens":9,"total_tokens":21}}""";

    @Mock
    private ServiceRequestHandler requestHandler;

    private AnthropicMessagesController controller;

    @BeforeEach
    void setUp() {
        controller = new AnthropicMessagesController(
                requestHandler,
                new AnthropicRequestTranslator(),
                new AnthropicResponseTranslator(new ObjectMapper()));
    }

    private MockServerWebExchange exchange() {
        return MockServerWebExchange.from(MockServerHttpRequest.post("/v1/messages")
                .header("anthropic-version", "2023-06-01")
                .header("x-api-key", "jairouter-key")
                .build());
    }

    private AnthropicMessagesRequest request(final String json) throws Exception {
        return new ObjectMapper().readValue(json, AnthropicMessagesRequest.class);
    }

    private void stubHandler(final ResponseEntity<?> response) {
        when(requestHandler.handleRequest(any(ServiceEndpoint.class), any(), any(),
                any(ServerWebExchange.class), any(ServiceRequestExecutor.class)))
                .thenReturn(Mono.just(response));
    }

    @Test
    @DisplayName("stream=true → 400 + Anthropic 错误体，且不触达下游")
    void streamTrueRejectedWith400() throws Exception {
        MockServerWebExchange exchange = exchange();
        AnthropicMessagesRequest request = request("""
                {"model":"deepseek-chat","max_tokens":64,"stream":true,
                 "messages":[{"role":"user","content":"hi"}]}""");

        ResponseEntity<?> response = controller.messages(null, "2023-06-01", null, request, exchange).block();

        assertNotNull(response);
        assertEquals(400, response.getStatusCode().value());
        assertEquals(MediaType.APPLICATION_JSON, response.getHeaders().getContentType());

        Map<?, ?> body = assertInstanceOf(Map.class, response.getBody());
        assertEquals("error", body.get("type"));
        Map<?, ?> error = assertInstanceOf(Map.class, body.get("error"));
        assertEquals("invalid_request_error", error.get("type"));
        assertTrue(String.valueOf(error.get("message")).contains("流式"), "错误信息应说明流式尚未支持");
        verify(requestHandler, never()).handleRequest(any(ServiceEndpoint.class), any(), any(),
                any(ServerWebExchange.class), any(ServiceRequestExecutor.class));
    }

    @Test
    @DisplayName("缺 model → 400 + Anthropic 错误体，且不触达下游")
    void missingModelRejectedWith400() throws Exception {
        AnthropicMessagesRequest request = request("""
                {"max_tokens":64,"messages":[{"role":"user","content":"hi"}]}""");

        ResponseEntity<?> response = controller.messages(null, null, null, request, exchange()).block();

        assertNotNull(response);
        assertEquals(400, response.getStatusCode().value());
        Map<?, ?> body = assertInstanceOf(Map.class, response.getBody());
        assertEquals("error", body.get("type"));
        Map<?, ?> error = assertInstanceOf(Map.class, body.get("error"));
        assertEquals("invalid_request_error", error.get("type"));
        assertTrue(String.valueOf(error.get("message")).contains("model"));
        verify(requestHandler, never()).handleRequest(any(ServiceEndpoint.class), any(), any(),
                any(ServerWebExchange.class), any(ServiceRequestExecutor.class));
    }

    @Test
    @DisplayName("请求体缺失 → 400（不 NPE）")
    void missingBodyRejectedWith400() {
        ResponseEntity<?> response = controller.messages(null, null, null, null, exchange()).block();

        assertNotNull(response);
        assertEquals(400, response.getStatusCode().value());
        assertEquals("error", assertInstanceOf(Map.class, response.getBody()).get("type"));
    }

    @Test
    @DisplayName("正常请求：置原生标记 + 挂翻译后 DTO + 下游 JSON 翻译为 Anthropic Message")
    void normalRequestTranslatesBothWays() throws Exception {
        MockServerWebExchange exchange = exchange();
        stubHandler(ResponseEntity.ok(DOWNSTREAM_JSON));
        AnthropicMessagesRequest request = request("""
                {"model":"deepseek-chat","max_tokens":128,"temperature":0.2,"top_p":0.9,"stream":false,
                 "system":"你是助手","stop_sequences":["END"],
                 "messages":[{"role":"user","content":[{"type":"text","text":"你好"}]}]}""");

        ResponseEntity<?> response = controller.messages("Bearer downstream-token", "2023-06-01",
                "prompt-caching-2024-07-31", request, exchange).block();

        // 1. exchange 标记与 DTO
        assertEquals(Boolean.TRUE, exchange.getAttribute(ServiceRequestHandler.NATIVE_RESPONSE_ATTRIBUTE),
                "必须置原生响应标记，否则非流式会被包成 RouterResponse");
        ChatDTO.Request dto = assertInstanceOf(ChatDTO.Request.class,
                exchange.getAttribute(ServiceRequestHandler.REQUEST_DTO_ATTRIBUTE));
        assertEquals("deepseek-chat", dto.model());
        assertEquals(128, dto.maxTokens());
        assertEquals(0.2, dto.temperature());
        assertEquals(0.9, dto.topP());
        assertEquals(List.of("END"), dto.stop());
        assertEquals(Boolean.FALSE, dto.stream());
        assertEquals(2, dto.messages().size());
        assertEquals("system", dto.messages().get(0).role());
        assertEquals("你是助手", dto.messages().get(0).content());
        assertEquals("user", dto.messages().get(1).role());
        assertEquals("你好", dto.messages().get(1).content());

        // 2. 委派参数
        ArgumentCaptor<String> modelCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> authCaptor = ArgumentCaptor.forClass(String.class);
        verify(requestHandler).handleRequest(eq(ServiceEndpoint.CHAT), modelCaptor.capture(),
                authCaptor.capture(), eq(exchange), any(ServiceRequestExecutor.class));
        assertEquals("deepseek-chat", modelCaptor.getValue());
        assertEquals("Bearer downstream-token", authCaptor.getValue());

        // 3. 响应翻译
        assertNotNull(response);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(MediaType.APPLICATION_JSON, response.getHeaders().getContentType());
        AnthropicMessagesResponse message = assertInstanceOf(AnthropicMessagesResponse.class, response.getBody());
        assertEquals("message", message.type());
        assertEquals("assistant", message.role());
        assertEquals("chatcmpl-abc", message.id());
        assertEquals("deepseek-chat", message.model());
        assertEquals("你好！", message.content().get(0).text());
        assertEquals("end_turn", message.stopReason());
        assertEquals(12, message.usage().inputTokens());
        assertEquals(9, message.usage().outputTokens());
    }

    @Test
    @DisplayName("无 Authorization 头时透传 null（实例级 headers 优先）")
    void nullAuthorizationPassedThrough() throws Exception {
        MockServerWebExchange exchange = exchange();
        stubHandler(ResponseEntity.ok(DOWNSTREAM_JSON));
        AnthropicMessagesRequest request = request("""
                {"model":"deepseek-chat","messages":[{"role":"user","content":"hi"}]}""");

        controller.messages(null, "2023-06-01", null, request, exchange).block();

        verify(requestHandler).handleRequest(eq(ServiceEndpoint.CHAT), eq("deepseek-chat"),
                isNull(), eq(exchange), any(ServiceRequestExecutor.class));
    }

    @Test
    @DisplayName("下游非 2xx 原样透传（不二次包装）")
    void nonSuccessfulResponsePassedThrough() throws Exception {
        stubHandler(ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .contentType(MediaType.APPLICATION_JSON)
                .body("{\"error\":\"rate limited\"}"));
        AnthropicMessagesRequest request = request("""
                {"model":"deepseek-chat","messages":[{"role":"user","content":"hi"}]}""");

        ResponseEntity<?> response = controller.messages(null, null, null, request, exchange()).block();

        assertNotNull(response);
        assertEquals(429, response.getStatusCode().value());
        assertEquals("{\"error\":\"rate limited\"}", response.getBody());
    }

    @Test
    @DisplayName("原生标记下响应体非 JSON 文本 → 502 + Anthropic 错误体（防御分支）")
    void unexpectedBodyShapeRejectedWith502() throws Exception {
        stubHandler(ResponseEntity.ok(Map.of("object", "chat.completion")));
        AnthropicMessagesRequest request = request("""
                {"model":"deepseek-chat","messages":[{"role":"user","content":"hi"}]}""");

        ResponseEntity<?> response = controller.messages(null, null, null, request, exchange()).block();

        assertNotNull(response);
        assertEquals(502, response.getStatusCode().value());
        assertEquals("error", assertInstanceOf(Map.class, response.getBody()).get("type"));
    }

    @Test
    @DisplayName("anthropic-version / anthropic-beta 头被忽略（不影响正常请求）")
    void anthropicHeadersIgnored() throws Exception {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.post("/v1/messages")
                        .header("anthropic-version", "9999-01-01")
                        .header("anthropic-beta", "some-beta-feature")
                        .header("x-api-key", "jairouter-key")
                        .build());
        stubHandler(ResponseEntity.ok(DOWNSTREAM_JSON));
        AnthropicMessagesRequest request = request("""
                {"model":"deepseek-chat","messages":[{"role":"user","content":"hi"}]}""");

        ResponseEntity<?> response = controller.messages(null, "9999-01-01", "some-beta-feature",
                request, exchange).block();

        assertNotNull(response);
        assertEquals(200, response.getStatusCode().value());
        verify(requestHandler).handleRequest(any(ServiceEndpoint.class), anyString(), any(),
                eq(exchange), any(ServiceRequestExecutor.class));
    }
}
