/*
 * Copyright 2024 JAiRouter Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.unreal.modelrouter.router.handler;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.unreal.modelrouter.router.model.ModelServiceRegistry.ServiceType;

/**
 * 服务端点配置枚举.
 *
 * <p>定义了所有支持的服务端点及其配置信息，
 * 用于统一管理和路由各种AI模型服务请求。
 *
 * @author JAiRouter Team
 * @since 2.10.0
 */
@Getter
@AllArgsConstructor
public enum ServiceEndpoint {

    /**
     * 聊天补全服务端点.
     */
    CHAT(
        ServiceType.chat,
        "/chat/completions",
        true,
        "chatCompletions"
    ),

    /**
     * 向量嵌入服务端点.
     */
    EMBEDDING(
        ServiceType.embedding,
        "/embeddings",
        true,
        "embeddings"
    ),

    /**
     * 重排序服务端点.
     */
    RERANK(
        ServiceType.rerank,
        "/rerank",
        true,
        "rerank"
    ),

    /**
     * 文本转语音服务端点.
     */
    TTS(
        ServiceType.tts,
        "/audio/speech",
        true,
        "textToSpeech"
    ),

    /**
     * 语音转文本服务端点.
     */
    STT(
        ServiceType.stt,
        "/audio/transcriptions",
        false,
        "speechToText"
    ),

    /**
     * 图像生成服务端点.
     */
    IMAGE_GEN(
        ServiceType.imgGen,
        "/images/generations",
        true,
        "imageGenerate"
    ),

    /**
     * 图像编辑服务端点.
     */
    IMAGE_EDIT(
        ServiceType.imgEdit,
        "/images/edits",
        true,
        "imageEdits"
    );

    private final ServiceType serviceType;
    private final String path;
    private final boolean requireValidation;
    private final String methodName;

    /**
     * 根据服务类型获取对应的端点配置.
     *
     * @param serviceType 服务类型
     * @return 对应的服务端点配置，如果不存在则抛出异常
     * @throws IllegalArgumentException 如果服务类型不存在对应的端点
     */
    public static ServiceEndpoint fromServiceType(final ServiceType serviceType) {
        for (ServiceEndpoint endpoint : values()) {
            if (endpoint.getServiceType() == serviceType) {
                return endpoint;
            }
        }
        throw new IllegalArgumentException(
            "No endpoint configured for service type: " + serviceType
        );
    }
}
