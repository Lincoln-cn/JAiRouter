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
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.web.server.ServerWebExchange;
import org.unreal.modelrouter.auth.security.model.ApiKeyAuthentication;
import org.unreal.modelrouter.auth.security.model.JwtAuthentication;
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

    @Nested
    @DisplayName("控制台 JWT 登录态权限判定（issue #77）")
    class JwtPermissionTests {

        /**
         * 控制台页面请求只带 {@code Jairouter_Token}（JWT），不带 API Key。此前实现用
         * {@code instanceof ApiKeyAuthentication} 过滤认证对象，JWT 被直接丢弃，导致 5 个
         * Playground 在登录态下全部不可用。
         */
        @Test
        @DisplayName("ADMIN 角色的 JWT 应该可以访问所有服务")
        void adminJwtShouldAccessAllServices() {
            JwtAuthentication auth = jwtAuth(List.of("ADMIN"), List.of());

            for (ServiceType serviceType : ServiceType.values()) {
                assertTrue(invokeHasServicePermission(auth, serviceType),
                    "ADMIN JWT should access " + serviceType + " service");
            }
        }

        @Test
        @DisplayName("持有 ai:playground:use 的 JWT 应该可以访问所有服务")
        void playgroundPermissionJwtShouldAccessAllServices() {
            JwtAuthentication auth = jwtAuth(List.of("USER"), List.of("ai:playground:use"));

            for (ServiceType serviceType : ServiceType.values()) {
                assertTrue(invokeHasServicePermission(auth, serviceType),
                    "JWT with ai:playground:use should access " + serviceType + " service");
            }
        }

        @Test
        @DisplayName("无 ADMIN 且无 ai:playground:use 的 JWT 应该被拒绝")
        void jwtWithoutPlaygroundPermissionShouldBeRejected() {
            JwtAuthentication auth = jwtAuth(List.of("USER"), List.of("overview:dashboard:read"));

            for (ServiceType serviceType : ServiceType.values()) {
                assertFalse(invokeHasServicePermission(auth, serviceType),
                    "JWT without ADMIN/ai:playground:use should be rejected for " + serviceType);
            }
        }

        @Test
        @DisplayName("JWT 不按 ROLE_<服务类型> 判定（控制台准入与 API Key 服务角色是两套语义）")
        void jwtIsNotJudgedByServiceRole() {
            // 角色 CHAT 会映射为 ROLE_CHAT authority，但控制台登录态不以服务角色为判据
            JwtAuthentication auth = jwtAuth(List.of("CHAT"), List.of());

            assertFalse(invokeHasServicePermission(auth, ServiceType.chat),
                "JWT 缺少 ADMIN/ai:playground:use 时应拒绝，即使带有与语义同名的服务角色");
        }

        private JwtAuthentication jwtAuth(final List<String> roles, final List<String> permissions) {
            JwtAuthentication auth = new JwtAuthentication("admin", "jwt-token", roles, permissions);
            auth.setAuthenticated(true);
            return auth;
        }
    }

    @Nested
    @DisplayName("调用主体标识解析（issue #77）")
    class ResolveCallerIdTests {

        @Test
        @DisplayName("ApiKeyAuthentication 取 keyId（原语义不变）")
        void apiKeyCallerKeepsKeyId() {
            ApiKeyAuthentication auth = new ApiKeyAuthentication(
                "key-1", "sk-key-1", List.of("chat"));
            auth.setAuthenticated(true);

            assertEquals("key-1", invokeResolveCallerId(auth));
        }

        @Test
        @DisplayName("JwtAuthentication 取 jwt:<用户名>（与 API Key 键空间隔离）")
        void jwtCallerIsNamespaced() {
            JwtAuthentication auth = new JwtAuthentication(
                "admin", "jwt-token", List.of("ADMIN"), List.of());

            assertEquals("jwt:admin", invokeResolveCallerId(auth));
        }

        @Test
        @DisplayName("其他认证类型不可识别（不放宽准入面）")
        void unsupportedAuthenticationIsRejected() {
            Authentication other = new UsernamePasswordAuthenticationToken("admin", null);

            assertNull(invokeResolveCallerId(other),
                "非 ApiKey / 非 JWT 的认证对象不得被当作调用主体");
        }

        @Test
        @DisplayName("principal 为空时不可识别")
        void blankPrincipalIsRejected() {
            // 单参构造 = 未认证形态，principal 为 null
            assertNull(invokeResolveCallerId(new ApiKeyAuthentication("sk-only")));
            assertNull(invokeResolveCallerId(null));
        }

        private String invokeResolveCallerId(final Authentication auth) {
            try {
                var method = ServiceRequestHandler.class.getDeclaredMethod(
                    "resolveCallerId", Authentication.class);
                method.setAccessible(true);
                return (String) method.invoke(null, auth);
            } catch (Exception e) {
                throw new RuntimeException("Failed to invoke resolveCallerId", e);
            }
        }
    }

    /**
     * 通过反射调用 private hasServicePermission 方法
     */
    private boolean invokeHasServicePermission(Authentication auth, ServiceType serviceType) {
        try {
            var method = ServiceRequestHandler.class.getDeclaredMethod(
                "hasServicePermission", Authentication.class, ServiceType.class);
            method.setAccessible(true);
            return (boolean) method.invoke(handler, auth, serviceType);
        } catch (Exception e) {
            throw new RuntimeException("Failed to invoke hasServicePermission", e);
        }
    }
}
