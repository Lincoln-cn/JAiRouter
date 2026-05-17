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
            assertEquals(HttpStatus.OK, result.getStatusCode());
            assertFalse(result.getBody().isSuccess());
            assertTrue(result.getBody().getMessage().contains("获取负载均衡器状态失败"));
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
            assertEquals(HttpStatus.OK, result.getStatusCode());
            assertFalse(result.getBody().isSuccess());
            assertTrue(result.getBody().getMessage().contains("无效的服务类型"));
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
            assertEquals(5, result.getBody().getData().size());

            // 验证策略名称
            List<String> strategyNames = result.getBody().getData().stream()
                    .map(s -> s.name)
                    .toList();
            assertTrue(strategyNames.contains("random"));
            assertTrue(strategyNames.contains("round-robin"));
            assertTrue(strategyNames.contains("least-connections"));
            assertTrue(strategyNames.contains("ip-hash"));
            assertTrue(strategyNames.contains("consistent-hash"));
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
            assertEquals(HttpStatus.OK, result.getStatusCode());
            assertFalse(result.getBody().isSuccess());
            assertTrue(result.getBody().getMessage().contains("无效的服务类型"));
        }
    }
}
