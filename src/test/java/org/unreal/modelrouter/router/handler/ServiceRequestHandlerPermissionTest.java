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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.web.server.ServerWebExchange;
import org.unreal.modelrouter.auth.security.model.ApiKeyAuthentication;
import org.unreal.modelrouter.monitor.monitoring.collector.MetricsCollector;
import org.unreal.modelrouter.monitor.tracing.interceptor.ControllerTracingInterceptor;
import org.unreal.modelrouter.router.adapter.AdapterRegistry;
import org.unreal.modelrouter.router.checker.ServiceStateManager;
import org.unreal.modelrouter.router.model.ModelServiceRegistry;
import org.unreal.modelrouter.router.model.ModelServiceRegistry.ServiceType;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * ServiceRequestHandler 权限检查测试.
 *
 * <p>验证 API Key 服务类型权限检查的大小写一致性问题。
 *
 * @author JAiRouter Team
 * @since 2.10.0
 */
@DisplayName("ServiceRequestHandler 权限检查测试")
class ServiceRequestHandlerPermissionTest {

    private ServiceRequestHandler handler;
    private AdapterRegistry adapterRegistry;
    private ModelServiceRegistry registry;
    private ServiceStateManager serviceStateManager;

    @BeforeEach
    void setUp() {
        adapterRegistry = mock(AdapterRegistry.class);
        registry = mock(ModelServiceRegistry.class);
        serviceStateManager = mock(ServiceStateManager.class);
        handler = new ServiceRequestHandler(
            adapterRegistry,
            registry,
            serviceStateManager,
            null,
            null
        );
    }

    @Nested
    @DisplayName("hasServicePermission 大小写一致性测试")
    class CaseSensitivityTests {

        @Test
        @DisplayName("chat 权限的 API Key 应该可以访问 chat 服务")
        void chatPermissionShouldAccessChatService() {
            ApiKeyAuthentication auth = new ApiKeyAuthentication(
                "test-key-id",
                "sk-test-key-value",
                List.of("chat")
            );
            auth.setAuthenticated(true);

            ServerWebExchange exchange = mock(ServerWebExchange.class);
            when(exchange.getAttribute(anyString())).thenReturn(null);

            // hasServicePermission 是 private 方法，通过反射测试
            boolean result = invokeHasServicePermission(auth, ServiceType.chat);
            assertTrue(result, "API Key with 'chat' permission should have access to chat service");
        }

        @ParameterizedTest
        @EnumSource(ServiceType.class)
        @DisplayName("所有服务类型都应该支持小写权限名称匹配")
        void allServiceTypesSupportLowercasePermissionMatching(final ServiceType serviceType) {
            String permission = serviceType.name();
            ApiKeyAuthentication auth = new ApiKeyAuthentication(
                "test-key-id",
                "sk-test-key-value",
                List.of(permission)
            );
            auth.setAuthenticated(true);

            boolean result = invokeHasServicePermission(auth, serviceType);
            assertTrue(result, "API Key with '" + permission + "' permission should have access to "
                + serviceType + " service");
        }

        @Test
        @DisplayName("ADMIN 权限应该可以访问所有服务")
        void adminPermissionShouldAccessAllServices() {
            ApiKeyAuthentication auth = new ApiKeyAuthentication(
                "test-key-id",
                "sk-test-key-value",
                List.of("admin")
            );
            auth.setAuthenticated(true);

            for (ServiceType serviceType : ServiceType.values()) {
                boolean result = invokeHasServicePermission(auth, serviceType);
                assertTrue(result, "ADMIN permission should access " + serviceType + " service");
            }
        }

        @Test
        @DisplayName("没有权限的 API Key 应该被拒绝")
        void noPermissionShouldBeRejected() {
            ApiKeyAuthentication auth = new ApiKeyAuthentication(
                "test-key-id",
                "sk-test-key-value",
                List.of("embedding")
            );
            auth.setAuthenticated(true);

            boolean result = invokeHasServicePermission(auth, ServiceType.chat);
            assertFalse(result, "API Key without chat permission should be rejected");
        }

        @Test
        @DisplayName("空权限列表的 API Key 应该被拒绝")
        void emptyPermissionListShouldBeRejected() {
            ApiKeyAuthentication auth = new ApiKeyAuthentication(
                "test-key-id",
                "sk-test-key-value",
                List.of()
            );
            auth.setAuthenticated(true);

            boolean result = invokeHasServicePermission(auth, ServiceType.chat);
            assertFalse(result, "API Key with empty permissions should be rejected");
        }
    }

    @Nested
    @DisplayName("handleRequest 权限拒绝测试")
    class HandleRequestPermissionTests {

        @Test
        @DisplayName("没有权限的 API Key 应该收到 403 响应")
        void noPermissionShouldReturn403() {
            ApiKeyAuthentication auth = new ApiKeyAuthentication(
                "test-key-id",
                "sk-test-key-value",
                List.of("embedding")
            );
            auth.setAuthenticated(true);

            ServerWebExchange exchange = mock(ServerWebExchange.class);
            when(exchange.getAttribute(anyString())).thenReturn(null);

            var endpoint = org.unreal.modelrouter.router.handler.ServiceEndpoint.CHAT;
            var executor = mock(ServiceRequestExecutor.class);

            // 注意：由于 handleRequest 需要 ReactiveSecurityContextHolder，这里主要验证 hasServicePermission 的逻辑
            // 完整集成测试需要 Spring Security 上下文
        }
    }

    /**
     * 通过反射调用 private hasServicePermission 方法
     */
    private boolean invokeHasServicePermission(ApiKeyAuthentication auth, ServiceType serviceType) {
        try {
            var method = ServiceRequestHandler.class.getDeclaredMethod(
                "hasServicePermission", ApiKeyAuthentication.class, ServiceType.class);
            method.setAccessible(true);
            return (boolean) method.invoke(handler, auth, serviceType);
        } catch (Exception e) {
            throw new RuntimeException("Failed to invoke hasServicePermission", e);
        }
    }
}
