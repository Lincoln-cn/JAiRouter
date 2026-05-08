package org.unreal.modelrouter.router.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.unreal.modelrouter.common.controller.response.RouterResponse;
import org.unreal.modelrouter.config.core.ServiceConfigManager;
import org.unreal.modelrouter.config.core.dto.ServiceConfiguration;
import org.unreal.modelrouter.router.loadbalancer.LoadBalancer;
import org.unreal.modelrouter.router.loadbalancer.LoadBalancerManager;
import org.unreal.modelrouter.router.model.ModelRouterProperties;
import org.unreal.modelrouter.router.model.ModelServiceRegistry;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 负载均衡器管理控制器
 * 提供负载均衡器状态查询和配置管理功能
 *
 * @author JAiRouter Team
 * @since 2.4.3
 */
@Slf4j
@RestController
@RequestMapping("/api/loadbalancer")
@RequiredArgsConstructor
public class LoadBalancerManagementController {

    private final LoadBalancerManager loadBalancerManager;
    private final ModelRouterProperties properties;
    private final ServiceConfigManager serviceConfigManager;  // 替换 ConfigurationService

    /**
     * 获取所有负载均衡器状态
     */
    @GetMapping("/status")
    public ResponseEntity<RouterResponse<List<LoadBalancerStatusResponse>>> getAllStatus() {
        try {
            Map<ModelServiceRegistry.ServiceType, String> status = loadBalancerManager.getLoadBalancerStatus();
            List<LoadBalancerStatusResponse> responseList = status.entrySet().stream()
                    .map(entry -> {
                        ModelServiceRegistry.ServiceType type = entry.getKey();
                        LoadBalancer loadBalancer = loadBalancerManager.getLoadBalancer(type);

                        LoadBalancerStatusResponse response = new LoadBalancerStatusResponse();
                        response.serviceType = type.name().toLowerCase();

                        // 解包追踪包装器获取真实负载均衡器类
                        String realClassName = unwrapLoadBalancer(loadBalancer);
                        response.strategy = parseStrategyName(realClassName);
                        response.loadBalancerClass = realClassName;

                        // 获取配置信息 - 使用 ServiceConfigManager 替代废弃方法
                        try {
                            ServiceConfiguration serviceConfig = serviceConfigManager.getServiceConfiguration(type.name().toLowerCase());
                            if (serviceConfig != null && serviceConfig.loadBalance() != null) {
                                response.configType = serviceConfig.loadBalance().type();
                                response.hashAlgorithm = serviceConfig.loadBalance().hashAlgorithm();
                                // virtualNodes 从全局配置获取
                                ModelRouterProperties.LoadBalanceConfig globalConfig = properties.getLoadBalance();
                                response.virtualNodes = globalConfig != null && globalConfig.getVirtualNodes() != null
                                        ? globalConfig.getVirtualNodes() : 150;
                            }
                        } catch (Exception e) {
                            log.debug("获取服务 {} 配置信息失败: {}", type.name(), e.getMessage());
                        }

                        return response;
                    })
                    .collect(Collectors.toList());

            return ResponseEntity.ok(RouterResponse.success(responseList));
        } catch (Exception e) {
            log.error("获取负载均衡器状态失败: {}", e.getMessage());
            return ResponseEntity.ok(RouterResponse.error("获取负载均衡器状态失败: " + e.getMessage()));
        }
    }

    /**
     * 解包负载均衡器获取真实的类名
     */
    private String unwrapLoadBalancer(final LoadBalancer loadBalancer) {
        if (loadBalancer == null) {
            return "Unknown";
        }
        
        // 检查是否是追踪包装器
        if (loadBalancer.getClass().getSimpleName().contains("TracingWrapper")) {
            try {
                // 使用反射获取 delegate 字段
                java.lang.reflect.Field delegateField = loadBalancer.getClass().getDeclaredField("delegate");
                delegateField.setAccessible(true);
                LoadBalancer delegate = (LoadBalancer) delegateField.get(loadBalancer);
                if (delegate != null) {
                    return delegate.getClass().getSimpleName();
                }
            } catch (Exception e) {
                log.debug("解包负载均衡器失败: {}", e.getMessage());
            }
        }
        
        return loadBalancer.getClass().getSimpleName();
    }

    /**
     * 获取指定服务类型的负载均衡器状态
     */
    @GetMapping("/status/{serviceType}")
    public ResponseEntity<RouterResponse<LoadBalancerStatusResponse>> getStatusByServiceType(
            @PathVariable final String serviceType) {
        try {
            ModelServiceRegistry.ServiceType type = ModelServiceRegistry.ServiceType.valueOf(serviceType.toLowerCase());
            LoadBalancer loadBalancer = loadBalancerManager.getLoadBalancer(type);

            LoadBalancerStatusResponse response = new LoadBalancerStatusResponse();
            response.serviceType = serviceType.toLowerCase();
            response.strategy = parseStrategyName(loadBalancer.getClass().getSimpleName());
            response.loadBalancerClass = loadBalancer.getClass().getSimpleName();

            // 获取该服务类型的实例信息 - 使用 ServiceConfigManager 替代废弃方法
            ServiceConfiguration serviceConfig = serviceConfigManager.getServiceConfiguration(serviceType);
            if (serviceConfig != null && serviceConfig.loadBalance() != null) {
                response.configType = serviceConfig.loadBalance().type();
                response.hashAlgorithm = serviceConfig.loadBalance().hashAlgorithm();
                // virtualNodes 从全局配置获取
                ModelRouterProperties.LoadBalanceConfig globalConfig = properties.getLoadBalance();
                response.virtualNodes = globalConfig != null && globalConfig.getVirtualNodes() != null
                        ? globalConfig.getVirtualNodes() : 150;
            }

            return ResponseEntity.ok(RouterResponse.success(response));
        } catch (IllegalArgumentException e) {
            log.warn("无效的服务类型: {}", serviceType);
            return ResponseEntity.ok(RouterResponse.error("无效的服务类型: " + serviceType));
        } catch (Exception e) {
            log.error("获取负载均衡器状态失败: {}", e.getMessage());
            return ResponseEntity.ok(RouterResponse.error("获取负载均衡器状态失败: " + e.getMessage()));
        }
    }

    /**
     * 获取全局负载均衡配置
     */
    @GetMapping("/config/global")
    public ResponseEntity<RouterResponse<LoadBalanceConfigResponse>> getGlobalConfig() {
        try {
            ModelRouterProperties.LoadBalanceConfig config = properties.getLoadBalance();

            LoadBalanceConfigResponse response = new LoadBalanceConfigResponse();
            response.type = config != null ? config.getType() : "random";
            response.hashAlgorithm = config != null ? config.getHashAlgorithm() : "md5";
            response.virtualNodes = config != null && config.getVirtualNodes() != null
                    ? config.getVirtualNodes() : 150;

            return ResponseEntity.ok(RouterResponse.success(response));
        } catch (Exception e) {
            log.error("获取全局负载均衡配置失败: {}", e.getMessage());
            return ResponseEntity.ok(RouterResponse.error("获取全局负载均衡配置失败: " + e.getMessage()));
        }
    }

    /**
     * 获取服务级别负载均衡配置
     */
    @GetMapping("/config/{serviceType}")
    public ResponseEntity<RouterResponse<LoadBalanceConfigResponse>> getServiceConfig(
            @PathVariable final String serviceType) {
        try {
            // 使用 ServiceConfigManager 替代废弃方法
            ServiceConfiguration serviceConfig = serviceConfigManager.getServiceConfiguration(serviceType);

            LoadBalanceConfigResponse response = new LoadBalanceConfigResponse();
            response.serviceType = serviceType;

            if (serviceConfig != null && serviceConfig.loadBalance() != null) {
                response.type = serviceConfig.loadBalance().type();
                response.hashAlgorithm = serviceConfig.loadBalance().hashAlgorithm();
                // virtualNodes 从全局配置获取
                ModelRouterProperties.LoadBalanceConfig globalConfig = properties.getLoadBalance();
                response.virtualNodes = globalConfig != null && globalConfig.getVirtualNodes() != null
                        ? globalConfig.getVirtualNodes() : 150;
            } else {
                // 使用全局配置
                ModelRouterProperties.LoadBalanceConfig globalConfig = properties.getLoadBalance();
                response.type = globalConfig != null ? globalConfig.getType() : "random";
                response.hashAlgorithm = globalConfig != null ? globalConfig.getHashAlgorithm() : "md5";
                response.virtualNodes = globalConfig != null && globalConfig.getVirtualNodes() != null
                        ? globalConfig.getVirtualNodes() : 150;
                response.isGlobal = true;
            }

            return ResponseEntity.ok(RouterResponse.success(response));
        } catch (Exception e) {
            log.error("获取服务负载均衡配置失败: {}", e.getMessage());
            return ResponseEntity.ok(RouterResponse.error("获取服务负载均衡配置失败: " + e.getMessage()));
        }
    }

    /**
     * 更新服务级别负载均衡配置
     */
    @PutMapping("/config/{serviceType}")
    public ResponseEntity<RouterResponse<String>> updateServiceConfig(
            @PathVariable final String serviceType,
            @RequestBody final LoadBalanceConfigRequest request) {
        try {
            ModelServiceRegistry.ServiceType type = ModelServiceRegistry.ServiceType.valueOf(serviceType.toLowerCase());

            ModelRouterProperties.LoadBalanceConfig config = new ModelRouterProperties.LoadBalanceConfig();
            config.setType(request.type);
            config.setHashAlgorithm(request.hashAlgorithm != null ? request.hashAlgorithm : "md5");
            config.setVirtualNodes(request.virtualNodes != null ? request.virtualNodes : 150);

            loadBalancerManager.reinitializeLoadBalancer(type, config);

            log.info("服务 {} 负载均衡配置已更新: type={}, hashAlgorithm={}, virtualNodes={}",
                    serviceType, config.getType(), config.getHashAlgorithm(), config.getVirtualNodes());

            return ResponseEntity.ok(RouterResponse.success("负载均衡配置已更新"));
        } catch (IllegalArgumentException e) {
            log.warn("无效的服务类型: {}", serviceType);
            return ResponseEntity.ok(RouterResponse.error("无效的服务类型: " + serviceType));
        } catch (Exception e) {
            log.error("更新负载均衡配置失败: {}", e.getMessage());
            return ResponseEntity.ok(RouterResponse.error("更新负载均衡配置失败: " + e.getMessage()));
        }
    }

    /**
     * 获取支持的负载均衡策略列表
     */
    @GetMapping("/strategies")
    public ResponseEntity<RouterResponse<List<StrategyInfo>>> getSupportedStrategies() {
        List<StrategyInfo> strategies = Arrays.asList(
                new StrategyInfo("random", "随机策略", "按权重随机选择实例，适合无状态的请求场景"),
                new StrategyInfo("round-robin", "轮询策略", "按权重轮询选择实例，适合均匀分布的场景"),
                new StrategyInfo("least-connections", "最少连接策略", "选择当前连接数最少的实例，适合长连接场景"),
                new StrategyInfo("ip-hash", "IP Hash策略", "基于客户端IP哈希选择实例，适合会话保持场景"),
                new StrategyInfo("consistent-hash", "一致性哈希策略", "使用一致性哈希环选择实例，适合分布式缓存场景")
        );

        return ResponseEntity.ok(RouterResponse.success(strategies));
    }

    /**
     * 获取负载均衡器统计信息
     */
    @GetMapping("/stats")
    public ResponseEntity<RouterResponse<LoadBalancerStatsResponse>> getStats() {
        try {
            LoadBalancerStatsResponse response = new LoadBalancerStatsResponse();
            response.totalLoadBalancers = loadBalancerManager.getLoadBalancerCount();
            response.validationStatus = loadBalancerManager.validateConfiguration();

            // 获取所有服务类型的负载均衡器，解包后统计策略分布
            response.strategyDistribution = new HashMap<>();
            for (ModelServiceRegistry.ServiceType type : ModelServiceRegistry.ServiceType.values()) {
                LoadBalancer loadBalancer = loadBalancerManager.getLoadBalancer(type);
                String realClassName = unwrapLoadBalancer(loadBalancer);
                String strategy = parseStrategyName(realClassName);
                response.strategyDistribution.merge(strategy, 1, Integer::sum);
            }

            return ResponseEntity.ok(RouterResponse.success(response));
        } catch (Exception e) {
            log.error("获取负载均衡器统计信息失败: {}", e.getMessage());
            return ResponseEntity.ok(RouterResponse.error("获取负载均衡器统计信息失败: " + e.getMessage()));
        }
    }

    /**
     * 解析策略名称
     */
    private String parseStrategyName(final String className) {
        if (className == null) {
            return "unknown";
        }
        // 移除 LoadBalancer 后缀和包装器后缀
        String name = className.replace("LoadBalancer", "").replace("TracingWrapper", "");
        // 转换为小写并处理驼峰命名
        if (name.isEmpty()) {
            return "unknown";
        }
        // 将驼峰转换为短横线格式
        StringBuilder result = new StringBuilder();
        result.append(Character.toLowerCase(name.charAt(0)));
        for (int i = 1; i < name.length(); i++) {
            if (Character.isUpperCase(name.charAt(i))) {
                result.append('-');
                result.append(Character.toLowerCase(name.charAt(i)));
            } else {
                result.append(name.charAt(i));
            }
        }
        return result.toString();
    }

    /**
     * 负载均衡器状态响应
     */
    public static class LoadBalancerStatusResponse {
        public String serviceType;
        public String strategy;
        public String loadBalancerClass;
        public String configType;
        public String hashAlgorithm;
        public Integer virtualNodes;
    }

    /**
     * 负载均衡配置响应
     */
    public static class LoadBalanceConfigResponse {
        public String serviceType;
        public String type;
        public String hashAlgorithm;
        public Integer virtualNodes;
        public boolean isGlobal = false;
    }

    /**
     * 负载均衡配置请求
     */
    public static class LoadBalanceConfigRequest {
        public String type;
        public String hashAlgorithm;
        public Integer virtualNodes;
    }

    /**
     * 负载均衡器统计响应
     */
    public static class LoadBalancerStatsResponse {
        public int totalLoadBalancers;
        public boolean validationStatus;
        public Map<String, Integer> strategyDistribution;
    }

    /**
     * 策略信息
     */
    public static class StrategyInfo {
        public String name;
        public String displayName;
        public String description;

        public StrategyInfo(final String name, final String displayName, final String description) {
            this.name = name;
            this.displayName = displayName;
            this.description = description;
        }
    }
}