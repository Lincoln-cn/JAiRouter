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

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 其他日志字段DTO测试集合
 */
@DisplayName("日志字段DTO测试集合")
class LogFieldsDtoTest {

    @Nested
    @DisplayName("RequestLogFields测试")
    class RequestLogFieldsTests {

        @Test
        @DisplayName("测试Builder构建")
        void testBuilder() {
            Map<String, String> headers = Map.of("Content-Type", "application/json");
            RequestLogFields fields = RequestLogFields.builder()
                    .method("POST")
                    .path("/api/chat")
                    .url("http://localhost:8080/api/chat?model=gpt4")
                    .clientIp("192.168.1.100")
                    .userAgent("Mozilla/5.0")
                    .requestSize(1024L)
                    .headers(headers)
                    .build();

            assertEquals("POST", fields.getMethod());
            assertEquals("/api/chat", fields.getPath());
            assertEquals("http://localhost:8080/api/chat?model=gpt4", fields.getUrl());
            assertEquals("192.168.1.100", fields.getClientIp());
            assertEquals("Mozilla/5.0", fields.getUserAgent());
            assertEquals(1024L, fields.getRequestSize());
            assertEquals(headers, fields.getHeaders());
        }

        @Test
        @DisplayName("测试getFieldType方法")
        void testGetFieldType() {
            assertEquals("request", new RequestLogFields().getFieldType());
        }

        @Test
        @DisplayName("测试无参和全参构造函数")
        void testConstructors() {
            RequestLogFields empty = new RequestLogFields();
            assertNull(empty.getMethod());

            Map<String, String> headers = Map.of("X-Custom", "value");
            RequestLogFields full = new RequestLogFields(
                    "GET", "/health", "http://localhost/health",
                    "127.0.0.1", "HealthChecker", 0L, headers
            );
            assertEquals("GET", full.getMethod());
            assertEquals("/health", full.getPath());
        }
    }

    @Nested
    @DisplayName("ResponseLogFields测试")
    class ResponseLogFieldsTests {

        @Test
        @DisplayName("测试Builder构建")
        void testBuilder() {
            Map<String, String> headers = Map.of("Content-Type", "application/json");
            ResponseLogFields fields = ResponseLogFields.builder()
                    .statusCode(200)
                    .statusText("OK")
                    .responseSize(2048L)
                    .duration(150L)
                    .headers(headers)
                    .build();

            assertEquals(200, fields.getStatusCode());
            assertEquals("OK", fields.getStatusText());
            assertEquals(2048L, fields.getResponseSize());
            assertEquals(150L, fields.getDuration());
            assertEquals(headers, fields.getHeaders());
        }

        @Test
        @DisplayName("测试getFieldType方法")
        void testGetFieldType() {
            assertEquals("response", new ResponseLogFields().getFieldType());
        }

        @Test
        @DisplayName("测试错误响应")
        void testErrorResponse() {
            ResponseLogFields fields = ResponseLogFields.builder()
                    .statusCode(500)
                    .statusText("Internal Server Error")
                    .duration(50L)
                    .build();

            assertEquals(500, fields.getStatusCode());
            assertEquals("Internal Server Error", fields.getStatusText());
            assertNull(fields.getHeaders());
        }
    }

    @Nested
    @DisplayName("ErrorLogFields测试")
    class ErrorLogFieldsTests {

        @Test
        @DisplayName("测试Builder构建")
        void testBuilder() {
            Map<String, Object> additionalInfo = Map.of("retryCount", 3);
            ErrorLogFields fields = ErrorLogFields.builder()
                    .errorType("NullPointerException")
                    .errorMessage("Object is null")
                    .stackTrace("at com.example.Test.method(Test.java:10)")
                    .additionalInfo(additionalInfo)
                    .build();

            assertEquals("NullPointerException", fields.getErrorType());
            assertEquals("Object is null", fields.getErrorMessage());
            assertEquals("at com.example.Test.method(Test.java:10)", fields.getStackTrace());
            assertEquals(additionalInfo, fields.getAdditionalInfo());
        }

        @Test
        @DisplayName("测试getFieldType方法")
        void testGetFieldType() {
            assertEquals("error", new ErrorLogFields().getFieldType());
        }

        @Test
        @DisplayName("测试create静态方法")
        void testCreate() {
            Exception exception = new RuntimeException("Test error");
            ErrorLogFields fields = ErrorLogFields.create(exception, "stack trace here");

            assertEquals("RuntimeException", fields.getErrorType());
            assertEquals("Test error", fields.getErrorMessage());
            assertEquals("stack trace here", fields.getStackTrace());
        }

        @Test
        @DisplayName("测试withAdditionalInfo静态方法")
        void testWithAdditionalInfo() {
            Exception exception = new IllegalArgumentException("Invalid argument");
            Map<String, Object> info = Map.of("paramName", "userId", "paramValue", "null");
            ErrorLogFields fields = ErrorLogFields.withAdditionalInfo(exception, "trace", info);

            assertEquals("IllegalArgumentException", fields.getErrorType());
            assertEquals(info, fields.getAdditionalInfo());
        }
    }

    @Nested
    @DisplayName("SystemEventFields测试")
    class SystemEventFieldsTests {

        @Test
        @DisplayName("测试Builder构建")
        void testBuilder() {
            Map<String, Object> details = Map.of("version", "2.16.4", "profile", "prod");
            SystemEventFields fields = SystemEventFields.builder()
                    .event("startup")
                    .details(details)
                    .build();

            assertEquals("startup", fields.getEvent());
            assertEquals(details, fields.getDetails());
        }

        @Test
        @DisplayName("测试getFieldType方法")
        void testGetFieldType() {
            assertEquals("system", new SystemEventFields().getFieldType());
        }

        @Test
        @DisplayName("测试create静态方法")
        void testCreate() {
            Map<String, Object> details = Map.of("reason", "config_update");
            SystemEventFields fields = SystemEventFields.create("config_reload", details);

            assertEquals("config_reload", fields.getEvent());
            assertEquals(details, fields.getDetails());
        }

        @Test
        @DisplayName("测试常见系统事件")
        void testCommonSystemEvents() {
            // 启动事件
            SystemEventFields startup = SystemEventFields.builder()
                    .event("startup")
                    .details(Map.of("port", 8080))
                    .build();
            assertEquals("startup", startup.getEvent());

            // 关闭事件
            SystemEventFields shutdown = SystemEventFields.builder()
                    .event("shutdown")
                    .build();
            assertEquals("shutdown", shutdown.getEvent());

            // 健康检查
            SystemEventFields healthCheck = SystemEventFields.builder()
                    .event("health_check")
                    .details(Map.of("status", "healthy"))
                    .build();
            assertEquals("health_check", healthCheck.getEvent());
        }
    }

    @Nested
    @DisplayName("LogFields接口测试")
    class LogFieldsInterfaceTests {

        @Test
        @DisplayName("所有实现类都有正确的getFieldType")
        void testAllFieldTypes() {
            assertEquals("security", new SecurityEventFields().getFieldType());
            assertEquals("performance", new PerformanceFields().getFieldType());
            assertEquals("business_event", new BusinessEventFields().getFieldType());
            assertEquals("request", new RequestLogFields().getFieldType());
            assertEquals("response", new ResponseLogFields().getFieldType());
            assertEquals("backend_call", new BackendCallFields().getFieldType());
            assertEquals("error", new ErrorLogFields().getFieldType());
            assertEquals("system", new SystemEventFields().getFieldType());
        }
    }
}
