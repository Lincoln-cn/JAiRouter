package org.unreal.modelrouter.router.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.unreal.modelrouter.common.constants.ErrorCode;
import org.unreal.modelrouter.common.controller.response.RouterResponse;
import org.unreal.modelrouter.config.core.ServiceConfigManager;
import org.unreal.modelrouter.config.core.dto.LoadBalanceConfiguration;
import org.unreal.modelrouter.config.core.dto.ServiceConfiguration;
import org.unreal.modelrouter.router.loadbalancer.LoadBalancer;
import org.unreal.modelrouter.router.loadbalancer.LoadBalancerManager;
import org.unreal.modelrouter.router.model.ModelRouterProperties;
import org.unreal.modelrouter.router.model.ModelServiceRegistry;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * LoadBalancerManagementController RESTful 接口测试
 *
 * 测试范围：
 * - GET /api/loadbalancer/status - 获取所有负载均衡器状态
 * - GET /api/loadbalancer/status/{serviceType} - 获取指定服务类型状态
 * - GET /api/loadbalancer/config/global - 获取全局配置
 * - GET /api/loadbalancer/config/{serviceType} - 获取服务配置
 * - PUT /api/loadbalancer/config/{serviceType} - 更新服务配置
 * - GET /api/loadbalancer/strategies - 获取支持策略列表
 * - GET /api/loadbalancer/stats - 获取统计信息
 *
 * v2.7.6: 使用 Mockito 单元测试方式
 */
@DisplayName("LoadBalancerManagementController RESTful 接口测试")
@ExtendWith(MockitoExtension.class)
class LoadBalancerManagementControllerTest {

    @Mock
    private LoadBalancerManager loadBalancerManager;

    @Mock
    private ModelRouterProperties properties;

    @Mock
    private ServiceConfigManager serviceConfigManager;

    @InjectMocks
    private LoadBalancerManagementController controller;

    @BeforeEach
    void setUp() {
        // 配置全局负载均衡属性
        ModelRouterProperties.LoadBalanceConfig globalConfig = new ModelRouterProperties.LoadBalanceConfig();
        globalConfig.setType("random");
        globalConfig.setHashAlgorithm("md5");
        globalConfig.setVirtualNodes(150);
        lenient().when(properties.getLoadBalance()).thenReturn(globalConfig);
    }

    // ==================== 获取所有负载均衡器状态测试 ====================

    @Nested
    @DisplayName("GET /api/loadbalancer/status - 获取所有状态测试")
    class GetAllStatusTests {

        @Test
        @DisplayName("LB-001: 成功获取所有负载均衡器状态")
        void testGetAllStatus_success() {
            // Given
            Map<ModelServiceRegistry.ServiceType, String> statusMap = new HashMap<>();
            statusMap.put(ModelServiceRegistry.ServiceType.chat, "random");
            statusMap.put(ModelServiceRegistry.ServiceType.embedding, "round-robin");

            LoadBalancer mockLoadBalancer = mock(LoadBalancer.class);
            when(loadBalancerManager.getLoadBalancerStatus()).thenReturn(statusMap);
            when(loadBalancerManager.getLoadBalancer(any())).thenReturn(mockLoadBalancer);

            // When
            ResponseEntity<RouterResponse<List<LoadBalancerManagementController.LoadBalancerStatusResponse>>> result =
                    controller.getAllStatus();

            // Then
            assertEquals(HttpStatus.OK, result.getStatusCode());
            assertTrue(result.getBody().isSuccess());
            assertNotNull(result.getBody().getData());
        }

        @Test
        @DisplayName("LB-002: 空状态列表")
        void testGetAllStatus_empty() {
            // Given
            when(loadBalancerManager.getLoadBalancerStatus()).thenReturn(new HashMap<>());

            // When
            ResponseEntity<RouterResponse<List<LoadBalancerManagementController.LoadBalancerStatusResponse>>> result =
                    controller.getAllStatus();

            // Then
            assertEquals(HttpStatus.OK, result.getStatusCode());
            assertTrue(result.getBody().isSuccess());
            assertEquals(0, result.getBody().getData().size());
        }

        @Test
        @DisplayName("LB-003: 异常处理")
        void testGetAllStatus_exception() {
            // Given
            when(loadBalancerManager.getLoadBalancerStatus()).thenThrow(new RuntimeException("Connection error"));

            // When
            ResponseEntity<RouterResponse<List<LoadBalancerManagementController.LoadBalancerStatusResponse>>> result =
                    controller.getAllStatus();

            // Then
            // issue #94: 内部异常返回 500 而非 200 + success=false
            assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, result.getStatusCode());
            assertFalse(result.getBody().isSuccess());
            assertTrue(result.getBody().getMessage().contains("获取负载均衡器状态失败"));
            // issue #413：端点已按 INTERNAL_ERROR 解析状态，响应体必须带同一个码（此前为 null）
            assertEquals(ErrorCode.INTERNAL_ERROR.code(), result.getBody().getErrorCode());
        }
    }

    // ==================== 获取指定服务类型状态测试 ====================

    @Nested
    @DisplayName("GET /api/loadbalancer/status/{serviceType} - 获取指定服务状态测试")
    class GetStatusByServiceTypeTests {

        @Test
        @DisplayName("LB-004: 成功获取指定服务类型状态")
        void testGetStatusByServiceType_success() {
            // Given
            String serviceType = "chat";
            LoadBalancer mockLoadBalancer = mock(LoadBalancer.class);
            when(loadBalancerManager.getLoadBalancer(ModelServiceRegistry.ServiceType.chat)).thenReturn(mockLoadBalancer);

            LoadBalanceConfiguration lbConfig = mock(LoadBalanceConfiguration.class);
            when(lbConfig.type()).thenReturn("random");
            when(lbConfig.hashAlgorithm()).thenReturn("md5");

            ServiceConfiguration serviceConfig = mock(ServiceConfiguration.class);
            when(serviceConfig.loadBalance()).thenReturn(lbConfig);
            when(serviceConfigManager.getServiceConfiguration(serviceType)).thenReturn(serviceConfig);

            // When
            ResponseEntity<RouterResponse<LoadBalancerManagementController.LoadBalancerStatusResponse>> result =
                    controller.getStatusByServiceType(serviceType);

            // Then
            assertEquals(HttpStatus.OK, result.getStatusCode());
            assertTrue(result.getBody().isSuccess());
            assertNotNull(result.getBody().getData());
            assertEquals("chat", result.getBody().getData().serviceType);
        }

        @Test
        @DisplayName("LB-005: 无效服务类型")
        void testGetStatusByServiceType_invalidType() {
            // Given
            String serviceType = "invalid-type";

            // When
            ResponseEntity<RouterResponse<LoadBalancerManagementController.LoadBalancerStatusResponse>> result =
                    controller.getStatusByServiceType(serviceType);

            // Then
            // issue #94: 非法服务类型返回 400 而非 200 + success=false
            assertEquals(HttpStatus.BAD_REQUEST, result.getStatusCode());
            assertFalse(result.getBody().isSuccess());
            assertTrue(result.getBody().getMessage().contains("无效的服务类型"));
            // issue #413：端点已按 INVALID_REQUEST 解析状态，响应体必须带同一个码（此前为 null）
            assertEquals(ErrorCode.INVALID_REQUEST.code(), result.getBody().getErrorCode());
        }
    }

    // ==================== 获取全局配置测试 ====================

    @Nested
    @DisplayName("GET /api/loadbalancer/config/global - 获取全局配置测试")
    class GetGlobalConfigTests {

        @Test
        @DisplayName("LB-006: 成功获取全局配置")
        void testGetGlobalConfig_success() {
            // When
            ResponseEntity<RouterResponse<LoadBalancerManagementController.LoadBalanceConfigResponse>> result =
                    controller.getGlobalConfig();

            // Then
            assertEquals(HttpStatus.OK, result.getStatusCode());
            assertTrue(result.getBody().isSuccess());
            assertNotNull(result.getBody().getData());
            assertEquals("random", result.getBody().getData().type);
            assertEquals("md5", result.getBody().getData().hashAlgorithm);
            assertEquals(150, result.getBody().getData().virtualNodes);
        }

        @Test
        @DisplayName("LB-007: 全局配置为空时使用默认值")
        void testGetGlobalConfig_nullConfig() {
            // Given
            when(properties.getLoadBalance()).thenReturn(null);

            // When
            ResponseEntity<RouterResponse<LoadBalancerManagementController.LoadBalanceConfigResponse>> result =
                    controller.getGlobalConfig();

            // Then
            assertEquals(HttpStatus.OK, result.getStatusCode());
            assertTrue(result.getBody().isSuccess());
            assertEquals("random", result.getBody().getData().type);
        }
    }

    // ==================== 获取支持策略列表测试 ====================

    @Nested
    @DisplayName("GET /api/loadbalancer/strategies - 获取策略列表测试")
    class GetSupportedStrategiesTests {

        @Test
        @DisplayName("LB-008: 成功获取支持策略列表")
        void testGetSupportedStrategies_success() {
            // When
            ResponseEntity<RouterResponse<List<LoadBalancerManagementController.StrategyInfo>>> result =
                    controller.getSupportedStrategies();

            // Then
            assertEquals(HttpStatus.OK, result.getStatusCode());
            assertTrue(result.getBody().isSuccess());
            assertNotNull(result.getBody().getData());
            assertEquals(6, result.getBody().getData().size());

            // 验证策略名称
            List<String> strategyNames = result.getBody().getData().stream()
                    .map(s -> s.name)
                    .toList();
            assertTrue(strategyNames.contains("random"));
            assertTrue(strategyNames.contains("round-robin"));
            assertTrue(strategyNames.contains("least-connections"));
            assertTrue(strategyNames.contains("ip-hash"));
            assertTrue(strategyNames.contains("consistent-hash"));
            assertTrue(strategyNames.contains("latency"));
        }
    }

    // ==================== 获取统计信息测试 ====================

    @Nested
    @DisplayName("GET /api/loadbalancer/stats - 获取统计信息测试")
    class GetStatsTests {

        @Test
        @DisplayName("LB-009: 成功获取统计信息")
        void testGetStats_success() {
            // Given
            when(loadBalancerManager.getLoadBalancerCount()).thenReturn(6);
            when(loadBalancerManager.validateConfiguration()).thenReturn(true);
            when(loadBalancerManager.getLoadBalancer(any())).thenReturn(mock(LoadBalancer.class));

            // When
            ResponseEntity<RouterResponse<LoadBalancerManagementController.LoadBalancerStatsResponse>> result =
                    controller.getStats();

            // Then
            assertEquals(HttpStatus.OK, result.getStatusCode());
            assertTrue(result.getBody().isSuccess());
            assertNotNull(result.getBody().getData());
            assertEquals(6, result.getBody().getData().totalLoadBalancers);
            assertTrue(result.getBody().getData().validationStatus);
        }
    }

    // ==================== 更新服务配置测试 ====================

    @Nested
    @DisplayName("PUT /api/loadbalancer/config/{serviceType} - 更新服务配置测试")
    class UpdateServiceConfigTests {

        @Test
        @DisplayName("LB-010: 成功更新服务配置")
        void testUpdateServiceConfig_success() {
            // Given
            String serviceType = "chat";
            LoadBalancerManagementController.LoadBalanceConfigRequest request =
                    new LoadBalancerManagementController.LoadBalanceConfigRequest();
            request.type = "round-robin";
            request.hashAlgorithm = "sha256";
            request.virtualNodes = 200;

            doNothing().when(loadBalancerManager).reinitializeLoadBalancer(any(), any());

            // When
            ResponseEntity<RouterResponse<String>> result = controller.updateServiceConfig(serviceType, request);

            // Then
            assertEquals(HttpStatus.OK, result.getStatusCode());
            assertTrue(result.getBody().isSuccess());
        }

        @Test
        @DisplayName("LB-011: 无效服务类型更新")
        void testUpdateServiceConfig_invalidType() {
            // Given
            String serviceType = "invalid-type";
            LoadBalancerManagementController.LoadBalanceConfigRequest request =
                    new LoadBalancerManagementController.LoadBalanceConfigRequest();
            request.type = "random";

            // When
            ResponseEntity<RouterResponse<String>> result = controller.updateServiceConfig(serviceType, request);

            // Then
            // issue #94: 非法服务类型返回 400 而非 200 + success=false
            assertEquals(HttpStatus.BAD_REQUEST, result.getStatusCode());
            assertFalse(result.getBody().isSuccess());
            assertTrue(result.getBody().getMessage().contains("无效的服务类型"));
            // issue #413：端点已按 INVALID_REQUEST 解析状态，响应体必须带同一个码（此前为 null）
            assertEquals(ErrorCode.INVALID_REQUEST.code(), result.getBody().getErrorCode());
        }
    }

    // ==================== issue #413：其余兜底分支的失败响应必须带 errorCode ====================

    @Nested
    @DisplayName("issue #413 - 兜底 catch 分支的 errorCode")
    class J413ErrorCodeTests {

        @Test
        @DisplayName("LB-413-001: GET /status/{serviceType} 非枚举异常 ⇒ 500 + INTERNAL_ERROR")
        void testGetStatusByServiceType_internalError() {
            when(loadBalancerManager.getLoadBalancer(ModelServiceRegistry.ServiceType.chat))
                    .thenThrow(new RuntimeException("boom"));

            ResponseEntity<RouterResponse<LoadBalancerManagementController.LoadBalancerStatusResponse>> result =
                    controller.getStatusByServiceType("chat");

            assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, result.getStatusCode());
            assertFalse(result.getBody().isSuccess());
            assertEquals(ErrorCode.INTERNAL_ERROR.code(), result.getBody().getErrorCode());
        }

        @Test
        @DisplayName("LB-413-002: GET /config/global 异常 ⇒ 500 + INTERNAL_ERROR")
        void testGetGlobalConfig_internalError() {
            when(properties.getLoadBalance()).thenThrow(new RuntimeException("boom"));

            ResponseEntity<RouterResponse<LoadBalancerManagementController.LoadBalanceConfigResponse>> result =
                    controller.getGlobalConfig();

            assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, result.getStatusCode());
            assertFalse(result.getBody().isSuccess());
            assertEquals(ErrorCode.INTERNAL_ERROR.code(), result.getBody().getErrorCode());
        }

        @Test
        @DisplayName("LB-413-003: GET /config/{serviceType} 异常 ⇒ 500 + INTERNAL_ERROR")
        void testGetServiceConfig_internalError() {
            when(serviceConfigManager.getServiceConfiguration("chat"))
                    .thenThrow(new RuntimeException("boom"));

            ResponseEntity<RouterResponse<LoadBalancerManagementController.LoadBalanceConfigResponse>> result =
                    controller.getServiceConfig("chat");

            assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, result.getStatusCode());
            assertFalse(result.getBody().isSuccess());
            assertEquals(ErrorCode.INTERNAL_ERROR.code(), result.getBody().getErrorCode());
        }

        @Test
        @DisplayName("LB-413-004: PUT /config/{serviceType} 非枚举异常 ⇒ 500 + INTERNAL_ERROR")
        void testUpdateServiceConfig_internalError() {
            LoadBalancerManagementController.LoadBalanceConfigRequest request =
                    new LoadBalancerManagementController.LoadBalanceConfigRequest();
            request.type = "random";
            doThrow(new RuntimeException("boom"))
                    .when(loadBalancerManager).reinitializeLoadBalancer(
                            eq(ModelServiceRegistry.ServiceType.chat), any());

            ResponseEntity<RouterResponse<String>> result =
                    controller.updateServiceConfig("chat", request);

            assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, result.getStatusCode());
            assertFalse(result.getBody().isSuccess());
            assertEquals(ErrorCode.INTERNAL_ERROR.code(), result.getBody().getErrorCode());
        }

        @Test
        @DisplayName("LB-413-005: GET /stats 异常 ⇒ 500 + INTERNAL_ERROR")
        void testGetStats_internalError() {
            when(loadBalancerManager.getLoadBalancerCount()).thenThrow(new RuntimeException("boom"));

            ResponseEntity<RouterResponse<LoadBalancerManagementController.LoadBalancerStatsResponse>> result =
                    controller.getStats();

            assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, result.getStatusCode());
            assertFalse(result.getBody().isSuccess());
            assertEquals(ErrorCode.INTERNAL_ERROR.code(), result.getBody().getErrorCode());
        }
    }

    // ==================== issue #575：服务级配置缺失要回落到全局默认，不能被兜底吞成 500 ====================

    @Nested
    @DisplayName("issue #575 - 服务级配置缺失时回落到全局默认")
    class J575ServiceConfigNotFoundTests {

        @Test
        @DisplayName("LB-575-001: 服务级 LB 配置不存在 ⇒ 200 + 全局默认 + isGlobal=true")
        void testGetServiceConfig_notFound() {
            // Given: imggen 这类服务没有服务级 LB 配置，ServiceConfigManager 抛 NotFoundException
            when(serviceConfigManager.getServiceConfiguration("imggen"))
                    .thenThrow(new ServiceConfigManager.NotFoundException("服务配置不存在：imggen"));

            // When
            ResponseEntity<RouterResponse<LoadBalancerManagementController.LoadBalanceConfigResponse>> result =
                    controller.getServiceConfig("imggen");

            // Then: 缺失配置是正常情形（生效的就是全局默认），既不能报 500，
            // 也不该用 404 让前端策略表把整行丢掉（issue #575）
            assertEquals(HttpStatus.OK, result.getStatusCode());
            assertNotNull(result.getBody());
            assertTrue(result.getBody().isSuccess());
            assertEquals("imggen", result.getBody().getData().serviceType);
            assertEquals("random", result.getBody().getData().type);
            assertEquals("md5", result.getBody().getData().hashAlgorithm);
            assertTrue(result.getBody().getData().isGlobal);
        }

        @Test
        @DisplayName("LB-575-002: 有服务级配置 ⇒ 200 + isGlobal=false，采用服务级策略")
        void testGetServiceConfig_serviceLevelConfig() {
            // Given
            LoadBalanceConfiguration lbConfig = mock(LoadBalanceConfiguration.class);
            when(lbConfig.type()).thenReturn("round-robin");
            when(lbConfig.hashAlgorithm()).thenReturn("sha256");

            ServiceConfiguration serviceConfig = mock(ServiceConfiguration.class);
            when(serviceConfig.loadBalance()).thenReturn(lbConfig);
            when(serviceConfigManager.getServiceConfiguration("chat")).thenReturn(serviceConfig);

            // When
            ResponseEntity<RouterResponse<LoadBalancerManagementController.LoadBalanceConfigResponse>> result =
                    controller.getServiceConfig("chat");

            // Then
            assertEquals(HttpStatus.OK, result.getStatusCode());
            assertTrue(result.getBody().isSuccess());
            assertEquals("chat", result.getBody().getData().serviceType);
            assertEquals("round-robin", result.getBody().getData().type);
            assertEquals("sha256", result.getBody().getData().hashAlgorithm);
            assertFalse(result.getBody().getData().isGlobal);
        }

        @Test
        @DisplayName("LB-575-003: serviceConfig 为 null ⇒ 200 + 全局默认，isGlobal=true（既有行为）")
        void testGetServiceConfig_fallbackToGlobalConfig() {
            // Given: 不抛 NotFoundException，只是返回 null（既有兜底分支）
            when(serviceConfigManager.getServiceConfiguration("imggen")).thenReturn(null);

            // When
            ResponseEntity<RouterResponse<LoadBalancerManagementController.LoadBalanceConfigResponse>> result =
                    controller.getServiceConfig("imggen");

            // Then
            assertEquals(HttpStatus.OK, result.getStatusCode());
            assertTrue(result.getBody().isSuccess());
            assertEquals("imggen", result.getBody().getData().serviceType);
            assertEquals("random", result.getBody().getData().type);
            assertEquals("md5", result.getBody().getData().hashAlgorithm);
            assertTrue(result.getBody().getData().isGlobal);
        }
    }
}
