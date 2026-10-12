package org.unreal.modelrouter.router.adapter.processor;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import org.unreal.modelrouter.common.exceptionhandler.V1ErrorBodyMapper;
import reactor.core.publisher.Flux;

import java.util.Map;
import java.util.concurrent.TimeoutException;

/**
 * 流式失败的终止语义（issue #257）。
 *
 * <p><b>协议背景</b>：OpenAI 的流式面上没有标准错误事件语汇，唯一的终止语汇
 * {@code data: [DONE]} 表示<b>正常结束</b>。因此这里<b>绝不</b>在错误路径补 {@code [DONE]} ——
 * 那会让只认 {@code [DONE]} 或只取 {@code choices[].delta} 的客户端把截断读成成功
 * （本仓两个客户端正是如此：{@code frontend/src/api/universal.ts} 与
 * {@code frontend/src/views/playground/composables/useStreaming.ts} 见到 {@code [DONE]} 即
 * {@code onComplete()}）。</p>
 *
 * <p><b>做法</b>：先发一个与 {@link V1ErrorBodyMapper} 同口径的 error 帧（客户端因此拿到可解析的
 * 错误类型与原因），随后<b>仍以原异常终止</b>（保留连接级失败信号，客户端据此判定失败）。
 * 实测依据：error 帧在连接中止前可完整送达，见 {@code StreamingPreCommitErrorProbeTest} 情形 C。</p>
 *
 * <p><b>适用范围</b>：仅 {@code /v1/**} 的 OpenAI 面。Anthropic 面（{@code /v1/messages}）由
 * {@code AnthropicStreamingTranslator} 发出协议定义的 {@code event: error}；legacy {@code /api/**}
 * 面逐字节保持既有语义 —— 两者都原样终止、不补帧。</p>
 */
final class StreamingErrorTermination {

    private static final Logger logger = LoggerFactory.getLogger(StreamingErrorTermination.class);

    /** 读不到下游状态码（连接级失败）时用于选错误类型的状态码：5xx → {@code api_error}。 */
    private static final int CONNECTION_FAILURE_STATUS = 502;

    private StreamingErrorTermination() {
    }

    /**
     * 依失败语义终止流：可补帧时「先发 error 帧、再以原异常终止」，否则原样终止。
     *
     * @param throwable    流式失败原因
     * @param httpRequest  客户端请求（用于判定面与构造协议形状错误体；可为 {@code null}）
     * @param objectMapper 序列化错误体
     * @return 终止用的流：至多一个 error 帧，然后必定以 {@code throwable} 终止
     */
    static Flux<ServerSentEvent<String>> terminate(final Throwable throwable,
                                                   final ServerHttpRequest httpRequest,
                                                   final ObjectMapper objectMapper) {
        final String clientPath = clientPathOf(httpRequest);
        final ServerSentEvent<String> errorFrame = errorFrameOf(throwable, clientPath, objectMapper);
        if (errorFrame == null) {
            return Flux.error(throwable);
        }
        logger.warn("流式中断：向 {} 补发 error 帧后以异常终止（不补 [DONE]）: {}",
                clientPath, messageOf(throwable));
        return Flux.concat(Flux.just(errorFrame), Flux.error(throwable));
    }

    private static ServerSentEvent<String> errorFrameOf(final Throwable throwable, final String clientPath,
                                                        final ObjectMapper objectMapper) {
        if (!isOpenAiFace(clientPath)) {
            return null;
        }
        final Map<String, Object> body = V1ErrorBodyMapper.toErrorBody(
                clientPath, statusOf(throwable), messageOf(throwable), null);
        if (body == null) {
            return null;
        }
        try {
            return ServerSentEvent.<String>builder()
                    .data(objectMapper.writeValueAsString(body))
                    .build();
        } catch (JsonProcessingException e) {
            logger.warn("构造 /v1 流式错误帧失败，改以纯异常终止: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 是否属于需要「按下游状态保真」的客户端面（{@code /v1/**}，含 Anthropic Messages 面）。
     *
     * <p>#257 A：这些面上网关应在订阅响应体之前先看下游状态，把 4xx/5xx 原样结束掉
     * （错误体由既有 {@link V1ErrorBodyMapper} 机制产出）；其余路径（legacy {@code /api/**}）
     * 保持「200 + 流内终止」的既有语义不变。</p>
     */
    static boolean isV1Face(final ServerHttpRequest httpRequest) {
        return V1ErrorBodyMapper.isV1Path(clientPathOf(httpRequest));
    }

    /** OpenAI 面 = {@code /v1/**} 且非 Anthropic Messages 面 */
    private static boolean isOpenAiFace(final String clientPath) {
        return V1ErrorBodyMapper.isV1Path(clientPath) && !V1ErrorBodyMapper.isAnthropicPath(clientPath);
    }

    private static String clientPathOf(final ServerHttpRequest httpRequest) {
        if (httpRequest == null || httpRequest.getPath() == null) {
            return null;
        }
        return httpRequest.getPath().value();
    }

    /**
     * 尽量保真下游状态码（429 → {@code rate_limit_error} 等），读不到时按连接级失败处理。
     */
    static int statusOf(final Throwable throwable) {
        Throwable cause = throwable;
        while (cause != null) {
            if (cause instanceof WebClientResponseException responseException) {
                return responseException.getStatusCode().value();
            }
            if (cause instanceof TimeoutException) {
                return 504;
            }
            cause = cause.getCause();
        }
        return CONNECTION_FAILURE_STATUS;
    }

    private static String messageOf(final Throwable throwable) {
        if (throwable == null) {
            return "流式响应异常";
        }
        final String message = throwable.getMessage();
        return message == null || message.isBlank() ? throwable.getClass().getSimpleName() : message;
    }
}
