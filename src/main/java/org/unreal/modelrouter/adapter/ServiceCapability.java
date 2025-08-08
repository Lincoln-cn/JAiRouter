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
     *
     * @param request       聊天请求
     * @param authorization 授权头
     * @param httpRequest   HTTP请求对象
     * @return 响应结果
     */
    default Mono<ResponseEntity<?>> chat(ChatDTO.Request request, String authorization, ServerHttpRequest httpRequest){
        throw new UnsupportedOperationException("does not support chat service");
    }

    /**
     * 文本嵌入服务
     *
     * @param request       嵌入请求
     * @param authorization 授权头
     * @param httpRequest   HTTP请求对象
     * @return 响应结果
     */
    default Mono<ResponseEntity<?>> embedding(EmbeddingDTO.Request request, String authorization, ServerHttpRequest httpRequest){
        throw new UnsupportedOperationException("does not support embedding service");
    }

    /**
     * 重排序服务
     *
     * @param request       重排序请求
     * @param authorization 授权头
     * @param httpRequest   HTTP请求对象
     * @return 响应结果
     */
    default Mono<ResponseEntity<?>> rerank(RerankDTO.Request request, String authorization, ServerHttpRequest httpRequest){
        throw new UnsupportedOperationException("does not support rerank service");
    }

    /**
     * 文本转语音服务
     *
     * @param request       TTS请求
     * @param authorization 授权头
     * @param httpRequest   HTTP请求对象
     * @return 响应结果
     */
    default Mono<ResponseEntity<?>> tts(TtsDTO.Request request, String authorization, ServerHttpRequest httpRequest){
        throw new UnsupportedOperationException("does not support tts service");
    }

    /**
     * 语音转文本服务
     *
     * @param request       STT请求
     * @param authorization 授权头
     * @param httpRequest   HTTP请求对象
     * @return 响应结果
     */
    default Mono<ResponseEntity<?>> stt(SttDTO.Request request, String authorization, ServerHttpRequest httpRequest){
        throw new UnsupportedOperationException("does not support stt service");
    }

    /**
     * 图像生成服务
     *
     * @param request       图像生成请求
     * @param authorization 授权头
     * @param httpRequest   HTTP请求对象
     * @return 响应结果
     */
    default Mono<ResponseEntity<?>> imageGenerate(ImageGenerateDTO.Request request, String authorization, ServerHttpRequest httpRequest){
        throw new UnsupportedOperationException("does not support image generate service");
    }

    /**
     * 图像编辑服务
     * @param request 图像编辑请求
     * @param authorization 授权头
     * @param httpRequest HTTP请求对象
     * @return 响应结果
     */
    default Mono<? extends ResponseEntity<?>> imageEdit(ImageEditDTO.Request request, String authorization, ServerHttpRequest httpRequest){
        throw new UnsupportedOperationException("does not support image edit service");
    }
}