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

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.unreal.modelrouter.router.model.ModelServiceRegistry.ServiceType;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ServiceEndpoint 枚举测试.
 *
 * @author JAiRouter Team
 * @since 2.10.0
 */
@DisplayName("ServiceEndpoint 测试")
class ServiceEndpointTest {

    @Nested
    @DisplayName("fromServiceType 测试")
    class FromServiceTypeTests {

        @ParameterizedTest
        @EnumSource(ServiceType.class)
        @DisplayName("所有服务类型都应该有对应的端点")
        void allServiceTypesHaveEndpoint(final ServiceType serviceType) {
            ServiceEndpoint endpoint = ServiceEndpoint.fromServiceType(serviceType);
            assertNotNull(endpoint);
            assertEquals(serviceType, endpoint.getServiceType());
        }

        @Test
        @DisplayName("CHAT 服务类型对应 CHAT 端点")
        void chatServiceTypeMapsToChatEndpoint() {
            ServiceEndpoint endpoint = ServiceEndpoint.fromServiceType(ServiceType.chat);
            assertEquals(ServiceEndpoint.CHAT, endpoint);
            assertEquals("/chat/completions", endpoint.getPath());
            assertEquals("chatCompletions", endpoint.getMethodName());
            assertTrue(endpoint.isRequireValidation());
        }

        @Test
        @DisplayName("EMBEDDING 服务类型对应 EMBEDDING 端点")
        void embeddingServiceTypeMapsToEmbeddingEndpoint() {
            ServiceEndpoint endpoint = ServiceEndpoint.fromServiceType(ServiceType.embedding);
            assertEquals(ServiceEndpoint.EMBEDDING, endpoint);
            assertEquals("/embeddings", endpoint.getPath());
            assertTrue(endpoint.isRequireValidation());
        }

        @Test
        @DisplayName("STT 服务类型不需要验证")
        void sttEndpointDoesNotRequireValidation() {
            ServiceEndpoint endpoint = ServiceEndpoint.fromServiceType(ServiceType.stt);
            assertFalse(endpoint.isRequireValidation());
        }
    }

    @Nested
    @DisplayName("枚举属性测试")
    class EnumPropertyTests {

        @Test
        @DisplayName("所有端点都有路径")
        void allEndpointsHavePath() {
            for (ServiceEndpoint endpoint : ServiceEndpoint.values()) {
                assertNotNull(endpoint.getPath());
                assertTrue(endpoint.getPath().startsWith("/"));
            }
        }

        @Test
        @DisplayName("所有端点都有方法名")
        void allEndpointsHaveMethodName() {
            for (ServiceEndpoint endpoint : ServiceEndpoint.values()) {
                assertNotNull(endpoint.getMethodName());
                assertFalse(endpoint.getMethodName().isEmpty());
            }
        }

        @Test
        @DisplayName("所有端点都有服务类型")
        void allEndpointsHaveServiceType() {
            for (ServiceEndpoint endpoint : ServiceEndpoint.values()) {
                assertNotNull(endpoint.getServiceType());
            }
        }

        @Test
        @DisplayName("端点总数应该为 7")
        void endpointCount() {
            assertEquals(7, ServiceEndpoint.values().length);
        }
    }

    @Nested
    @DisplayName("端点配置测试")
    class EndpointConfigurationTests {

        @Test
        @DisplayName("图像生成端点配置正确")
        void imageGenEndpointConfiguredCorrectly() {
            ServiceEndpoint endpoint = ServiceEndpoint.IMAGE_GEN;
            assertEquals(ServiceType.imgGen, endpoint.getServiceType());
            assertEquals("/images/generations", endpoint.getPath());
            assertEquals("imageGenerate", endpoint.getMethodName());
            assertTrue(endpoint.isRequireValidation());
        }

        @Test
        @DisplayName("图像编辑端点配置正确")
        void imageEditEndpointConfiguredCorrectly() {
            ServiceEndpoint endpoint = ServiceEndpoint.IMAGE_EDIT;
            assertEquals(ServiceType.imgEdit, endpoint.getServiceType());
            assertEquals("/images/edits", endpoint.getPath());
            assertTrue(endpoint.isRequireValidation());
        }

        @Test
        @DisplayName("TTS 端点配置正确")
        void ttsEndpointConfiguredCorrectly() {
            ServiceEndpoint endpoint = ServiceEndpoint.TTS;
            assertEquals(ServiceType.tts, endpoint.getServiceType());
            assertEquals("/audio/speech", endpoint.getPath());
            assertEquals("textToSpeech", endpoint.getMethodName());
            assertTrue(endpoint.isRequireValidation());
        }

        @Test
        @DisplayName("RERANK 端点配置正确")
        void rerankEndpointConfiguredCorrectly() {
            ServiceEndpoint endpoint = ServiceEndpoint.RERANK;
            assertEquals(ServiceType.rerank, endpoint.getServiceType());
            assertEquals("/rerank", endpoint.getPath());
            assertTrue(endpoint.isRequireValidation());
        }
    }
}
