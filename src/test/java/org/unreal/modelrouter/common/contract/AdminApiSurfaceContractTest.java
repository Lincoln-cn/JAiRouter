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

package org.unreal.modelrouter.common.contract;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.unreal.modelrouter.auth.controller.ApiKeyManagementController;
import org.unreal.modelrouter.auth.controller.JwtAuthController;
import org.unreal.modelrouter.auth.security.dto.ApiKeyListVO;
import org.unreal.modelrouter.common.controller.response.RouterResponse;
import org.unreal.modelrouter.common.dto.LoginResponse;
import org.unreal.modelrouter.common.dto.ServiceInstanceDTO;
import org.unreal.modelrouter.config.controller.ServiceTypeInstanceController;
import org.unreal.modelrouter.monitor.controller.ModelStatsController;
import org.unreal.modelrouter.router.model.ModelServiceRegistry;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 管理端 API 契约快照（质量门禁：控制台与外部自动化工具的接口面）.
 *
 * <p>控制台之外还有一类调用方：运维脚本、监控探针、CI 巡检。它们不读 Java 源码，
 * 只按 JSON 的键名解析响应。这类调用方一旦被破坏，症状是「脚本报服务异常」，
 * 而不是编译失败 —— 排查成本远高于单元测试红一次。</p>
 *
 * <p>本类把这类调用方依赖的接口面逐项钉死，任何一项变化都会红：</p>
 * <ol>
 *   <li>路由路径（类上的 {@code @RequestMapping}）</li>
 *   <li>响应里的关键字段名（{@code data.token}、{@code data.totalInstances} …）</li>
 *   <li>指标端点所依赖的注册表是否还在 classpath 上</li>
 * </ol>
 *
 * <p>为什么不用 {@code @SpringBootTest}：这里守的是「形状」，不是「行为」。启动完整上下文
 * 会引入数据库与缓存依赖，让一条形状断言变得又慢又不稳定；而这些形状在编译期就能精确取到。</p>
 *
 * <p>为什么全部平铺、不用 {@code @Nested}：实测 {@code -Dtest=AdminApiSurfaceContractTest}
 * 对 {@code @Nested} 内的用例匹配不到，会报 {@code Tests run: 0} 并「通过」——
 * 这正是本类要防的那类静默失效。平铺之后单跑该类能精确命中每一条。</p>
 *
 * <p>与 {@link org.unreal.modelrouter.common.exceptionhandler.ErrorBodyContractSnapshotTest}
 * 的分工：那个守错误体，这个守成功响应。两者都是「客户端按什么键解析」的问题，属于同一类风险。</p>
 *
 * @author JAiRouter Team
 * @since v3.7.4
 */
@DisplayName("管理端 API 契约：路由与响应键名逐项钉死")
class AdminApiSurfaceContractTest {

    private final ObjectMapper objectMapper = JsonMapper.builder().build();

    /**
     * 取类级 {@code @RequestMapping} 的路径.
     */
    private static String classMapping(Class<?> type) {
        RequestMapping mapping = type.getAnnotation(RequestMapping.class);
        assertNotNull(mapping, type.getSimpleName() + " 缺少类级 @RequestMapping");
        assertTrue(mapping.value().length > 0, type.getSimpleName() + " 的 @RequestMapping 未声明路径");
        return mapping.value()[0];
    }

    /**
     * 断言给定类型的实例字段恰好包含期望的全部字段名.
     */
    private static void assertDeclaresFields(Class<?> type, String... expected) {
        Set<String> actual = Arrays.stream(type.getDeclaredFields())
                .filter(f -> !Modifier.isStatic(f.getModifiers()))
                .map(Field::getName)
                .collect(Collectors.toSet());
        for (String field : expected) {
            assertTrue(actual.contains(field),
                    type.getSimpleName() + " 不再声明字段 '" + field + "'，依赖该字段的客户端会静默失败。实际字段: " + actual);
        }
    }

    @Test
    @DisplayName("登录：POST /api/auth/jwt/login 返回 data.token")
    void loginExposesTokenAtDataToken() {
        assertEquals("/api/auth/jwt", classMapping(JwtAuthController.class));
        assertTrue(Arrays.stream(JwtAuthController.class.getDeclaredMethods())
                        .anyMatch(m -> "login".equals(m.getName())),
                "JwtAuthController 不再声明 login 方法");
        assertDeclaresFields(LoginResponse.class, "token");
    }

    @Test
    @DisplayName("实例配置：GET /api/config/instance/{type} 的元素含 name/baseUrl/adapter/status")
    void instanceListExposesFields() {
        assertEquals("/api/config/instance", classMapping(ServiceTypeInstanceController.class));
        assertTrue(Arrays.stream(ServiceTypeInstanceController.class.getDeclaredMethods())
                        .anyMatch(m -> "getInstancesByServiceType".equals(m.getName())),
                "ServiceTypeInstanceController 不再声明 getInstancesByServiceType");
        assertDeclaresFields(ServiceInstanceDTO.class, "name", "baseUrl", "adapter", "status");
    }

    @Test
    @DisplayName("配置统计：GET /api/models/stats 的 data 含 totalServices/totalInstances/totalModels")
    void statsExposeTotals() throws Exception {
        assertEquals("/api/models/stats", classMapping(ModelStatsController.class));

        ModelServiceRegistry registry = Mockito.mock(ModelServiceRegistry.class);
        Mockito.when(registry.getAllServiceTypes()).thenReturn(Set.of());
        Mockito.when(registry.getAllInstances()).thenReturn(Map.of());
        Mockito.when(registry.getAvailableModels(Mockito.any())).thenReturn(Set.of());

        ModelStatsController controller = new ModelStatsController(registry);
        ResponseEntity<RouterResponse<Object>> response = controller.getConfigurationStats().block();
        assertNotNull(response, "getConfigurationStats 返回了空 Mono");
        assertNotNull(response.getBody(), "响应体为空");

        JsonNode data = objectMapper.valueToTree(response.getBody()).path("data");
        for (String key : new String[] {"totalServices", "totalInstances", "totalModels"}) {
            assertTrue(data.has(key),
                    "data 中缺少 '" + key + "'，按该键读取的统计脚本会拿到 undefined。实际: " + data);
        }
    }

    @Test
    @DisplayName("API Key 列表：GET /api/auth/api-keys 返回 data.items")
    void apiKeysExposeItems() {
        assertEquals("api/auth/api-keys", classMapping(ApiKeyManagementController.class));
        assertDeclaresFields(ApiKeyListVO.class, "items", "total");
    }

    @Test
    @DisplayName("指标端点：PrometheusMeterRegistry 在 classpath 上")
    void prometheusRegistryPresent() {
        // 是否真的对外暴露由 monitoring-base.yml 的 exposure.include 控制；
        // 这里只守住「依赖还在」—— 否则 /actuator/prometheus 会直接 404，
        // 而所有按该端点抓指标的巡检脚本会静默失效。
        try {
            Class.forName("io.micrometer.prometheusmetrics.PrometheusMeterRegistry");
        } catch (ClassNotFoundException e) {
            throw new AssertionError(
                    "PrometheusMeterRegistry 不在 classpath 上：/actuator/prometheus 将不可用，"
                            + "依赖该端点的巡检与报表全部失效", e);
        }
    }
}
