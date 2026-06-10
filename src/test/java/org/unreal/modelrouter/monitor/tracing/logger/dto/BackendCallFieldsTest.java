/*
 * Copyright (c) 2025 JAiRouter Authors. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.unreal.modelrouter.monitor.tracing.logger.dto;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * BackendCallFields测试
 */
@DisplayName("BackendCallFields测试")
class BackendCallFieldsTest {

    @Test
    @DisplayName("测试Builder构建")
    void testBuilder() {
        BackendCallFields fields = BackendCallFields.builder()
                .adapter("GPUStack")
                .instance("gpu-instance-1")
                .duration(150L)
                .success(true)
                .statusCode(200)
                .url("http://localhost:8080/api/chat")
                .method("POST")
                .build();

        assertEquals("GPUStack", fields.getAdapter());
        assertEquals("gpu-instance-1", fields.getInstance());
        assertEquals(150L, fields.getDuration());
        assertTrue(fields.getSuccess());
        assertEquals(200, fields.getStatusCode());
        assertEquals("http://localhost:8080/api/chat", fields.getUrl());
        assertEquals("POST", fields.getMethod());
    }

    @Test
    @DisplayName("测试无参构造函数")
    void testNoArgsConstructor() {
        BackendCallFields fields = new BackendCallFields();
        assertNull(fields.getAdapter());
        assertNull(fields.getInstance());
        assertNull(fields.getDuration());
    }

    @Test
    @DisplayName("测试全参构造函数")
    void testAllArgsConstructor() {
        BackendCallFields fields = new BackendCallFields(
                "Ollama", "localhost:11434", 50L, true, 200,
                "http://localhost:11434/api/generate", "GET"
        );

        assertEquals("Ollama", fields.getAdapter());
        assertEquals("localhost:11434", fields.getInstance());
        assertEquals(50L, fields.getDuration());
        assertTrue(fields.getSuccess());
        assertEquals(200, fields.getStatusCode());
    }

    @Test
    @DisplayName("测试getFieldType方法")
    void testGetFieldType() {
        assertEquals("backend_call", new BackendCallFields().getFieldType());
    }

    @Nested
    @DisplayName("simple静态工厂方法测试")
    class SimpleTests {

        @Test
        @DisplayName("成功的简化调用")
        void testSimpleSuccess() {
            BackendCallFields fields = BackendCallFields.simple(
                    "vLLM", "vllm-instance", 100, true
            );

            assertEquals("vLLM", fields.getAdapter());
            assertEquals("vllm-instance", fields.getInstance());
            assertEquals(100L, fields.getDuration());
            assertTrue(fields.getSuccess());
            assertEquals(200, fields.getStatusCode());
            assertNull(fields.getUrl());
            assertNull(fields.getMethod());
        }

        @Test
        @DisplayName("失败的简化调用")
        void testSimpleFailure() {
            BackendCallFields fields = BackendCallFields.simple(
                    "LocalAI", "local-1", 200, false
            );

            assertFalse(fields.getSuccess());
            assertEquals(500, fields.getStatusCode());
        }
    }

    @Nested
    @DisplayName("detailed静态工厂方法测试")
    class DetailedTests {

        @Test
        @DisplayName("详细的调用记录")
        void testDetailed() {
            BackendCallFields fields = BackendCallFields.detailed(
                    "Xinference", "xinference-server",
                    "http://localhost:9997/v1/chat/completions",
                    "POST", 250, 201, true
            );

            assertEquals("Xinference", fields.getAdapter());
            assertEquals("xinference-server", fields.getInstance());
            assertEquals("http://localhost:9997/v1/chat/completions", fields.getUrl());
            assertEquals("POST", fields.getMethod());
            assertEquals(250L, fields.getDuration());
            assertEquals(201, fields.getStatusCode());
            assertTrue(fields.getSuccess());
        }

        @Test
        @DisplayName("失败的详细调用")
        void testDetailedFailure() {
            BackendCallFields fields = BackendCallFields.detailed(
                    "GPUStack", "gpu-1",
                    "http://gpu.local:80/api/models",
                    "GET", 5000, 503, false
            );

            assertEquals(503, fields.getStatusCode());
            assertFalse(fields.getSuccess());
        }
    }

    @Test
    @DisplayName("测试Setter方法")
    void testSetters() {
        BackendCallFields fields = new BackendCallFields();

        fields.setAdapter("NewAdapter");
        fields.setInstance("new-instance");
        fields.setDuration(300L);
        fields.setSuccess(false);
        fields.setStatusCode(404);
        fields.setUrl("http://test.com/api");
        fields.setMethod("DELETE");

        assertEquals("NewAdapter", fields.getAdapter());
        assertEquals("new-instance", fields.getInstance());
        assertEquals(300L, fields.getDuration());
        assertFalse(fields.getSuccess());
        assertEquals(404, fields.getStatusCode());
        assertEquals("http://test.com/api", fields.getUrl());
        assertEquals("DELETE", fields.getMethod());
    }
}
