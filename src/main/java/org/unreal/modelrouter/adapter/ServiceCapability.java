package org.unreal.modelrouter.adapter;

import org.springframework.http.ResponseEntity;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.unreal.modelrouter.dto.*;
import reactor.core.publisher.Mono;

/**
 * 服务能力接口 - 定义各种AI服务的标准接口
 */
public interface ServiceCapability {

    /**
     * 聊天完成服务
     * @param request 聊天请求
     * @param authorization 授权头
     * @param httpRequest HTTP请求对象
     * @return 响应结果
     */
    Mono<? extends ResponseEntity<?>> chat(ChatDTO.Request request, String authorization, ServerHttpRequest httpRequest);

    /**
     * 文本嵌入服务
     * @param request 嵌入请求
     * @param authorization 授权头
     * @param httpRequest HTTP请求对象
     * @return 响应结果
     */
    Mono<? extends ResponseEntity<?>> embedding(EmbeddingDTO.Request request, String authorization, ServerHttpRequest httpRequest);

    /**
     * 重排序服务
     * @param request 重排序请求
     * @param authorization 授权头
     * @param httpRequest HTTP请求对象
     * @return 响应结果
     */
    Mono<? extends ResponseEntity<?>> rerank(RerankDTO.Request request, String authorization, ServerHttpRequest httpRequest);

    /**
     * 文本转语音服务
     * @param request TTS请求
     * @param authorization 授权头
     * @param httpRequest HTTP请求对象
     * @return 响应结果
     */
    Mono<? extends ResponseEntity<?>> tts(TtsDTO.Request request, String authorization, ServerHttpRequest httpRequest);

    /**
     * 语音转文本服务
     * @param request STT请求
     * @param authorization 授权头
     * @param httpRequest HTTP请求对象
     * @return 响应结果
     */
    Mono<? extends ResponseEntity<?>> stt(SttDTO.Request request, String authorization, ServerHttpRequest httpRequest);
}