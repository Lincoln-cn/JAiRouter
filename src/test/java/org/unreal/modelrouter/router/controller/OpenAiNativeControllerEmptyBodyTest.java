package org.unreal.modelrouter.router.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebInputException;
import org.unreal.modelrouter.router.handler.ServiceRequestHandler;
import org.unreal.modelrouter.router.model.ModelCatalogService;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * issue #779：三个 OpenAI 原生入口在<b>空请求体</b>时必须以 400 终止.
 *
 * <p>改动前三处方法签名是 {@code @RequestBody(required = false)}，空体时 {@code request}
 * 为 {@code null} 会被直接透传给适配器 ⇒ adapter 内部 NPE 被中央处理器归为 500，
 * 客户端拿不到「请求体为空」这一可自我修正的信息。现在入口处抛
 * {@link ServerWebInputException}，由 {@code ReactiveGlobalExceptionHandler} 映射为 400
 * （{@code /v1/**} 再渲染成协议形状的 {@code invalid_request_error}）。</p>
 *
 * <p>本类断言三件事：① 状态码是 400；② 异常类型是中央处理器认得的
 * {@link ServerWebInputException}（类型一变 400 分支即不可达）；③ 下游
 * {@link ServiceRequestHandler} 从未被调用（证明不是把 null 放行后才在深处炸）。</p>
 */
@ExtendWith(MockitoExtension.class)
class OpenAiNativeControllerEmptyBodyTest {

    private static final String BODY_REASON_FRAGMENT = "body";

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
    @DisplayName("/v1/chat/completions 空体：400 + ServerWebInputException + 下游零调用")
    void chatCompletionsWithEmptyBody_shouldFailWith400() {
        assertEmptyBodyHandled("/v1/chat/completions",
            exchange -> controller.chatCompletions("Bearer downstream-key", null, exchange));
    }

    @Test
    @DisplayName("/v1/embeddings 空体：400 + ServerWebInputException + 下游零调用")
    void embeddingsWithEmptyBody_shouldFailWith400() {
        assertEmptyBodyHandled("/v1/embeddings",
            exchange -> controller.embeddings("Bearer downstream-key", null, exchange));
    }

    @Test
    @DisplayName("/v1/rerank 空体：400 + ServerWebInputException + 下游零调用")
    void rerankWithEmptyBody_shouldFailWith400() {
        assertEmptyBodyHandled("/v1/rerank",
            exchange -> controller.rerank("Bearer downstream-key", null, exchange));
    }

    private void assertEmptyBodyHandled(final String path, final Invocation invocation) {
        final MockServerWebExchange exchange = MockServerWebExchange.from(
            MockServerHttpRequest.post(path).build());

        final ServerWebInputException ex = assertThrows(ServerWebInputException.class,
            () -> invocation.invoke(exchange), "空体必须抛出 ServerWebInputException（中央处理器据此映射 400）");

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode(), "空体必须是客户端错误 400，而不是 500");
        assertNotNull(ex.getReason(), "400 必须带可读原因");
        assertTrue(ex.getReason().toLowerCase(java.util.Locale.ROOT).contains(BODY_REASON_FRAGMENT),
            "原因应说明请求体问题: " + ex.getReason());
        assertFalse(exchange.getAttributes().containsKey(ServiceRequestHandler.REQUEST_DTO_ATTRIBUTE),
            "空体不得把 null 作为请求 DTO 放进 exchange");
        verifyNoInteractions(requestHandler);
    }

    /**
     * 三个入口的调用形态（都返回 {@code Mono}，空体路径上不应订阅）。
     */
    @FunctionalInterface
    private interface Invocation {

        /**
         * 调用控制器方法.
         *
         * @param exchange 交换对象
         * @return 控制器返回值（空体路径上不会产生）
         */
        Object invoke(MockServerWebExchange exchange);
    }
}
