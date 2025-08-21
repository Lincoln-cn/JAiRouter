package org.unreal.modelrouter.checker;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.unreal.modelrouter.model.ModelRouterProperties;
import org.unreal.modelrouter.model.ModelServiceRegistry;
import org.unreal.modelrouter.monitoring.collector.MetricsCollector;
import org.unreal.modelrouter.tracing.health.HealthCheckTracingEnhancer;
import org.unreal.modelrouter.tracing.TracingContext;
import org.unreal.modelrouter.util.NetUtils;
import org.unreal.modelrouter.util.ApplicationContextProvider;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class ServerChecker {

    private static final Logger log = LoggerFactory.getLogger(ServerChecker.class);

    private final ModelServiceRegistry modelServiceRegistry;
    private final ServiceStateManager serviceStateManager;
    
    @Autowired(required = false)
    private MetricsCollector metricsCollector;
    
    // 缓存实例之前的状态，用于检测状态变化
    private final Map<String, Boolean> previousInstanceStates = new ConcurrentHashMap<>();

    public ServerChecker(ModelServiceRegistry modelServiceRegistry, ServiceStateManager serviceStateManager) {
        this.modelServiceRegistry = modelServiceRegistry;
        this.serviceStateManager = serviceStateManager;
    }

    /**
     * 定时检查所有服务实例的健康状态
     * 每30秒执行一次
     */
    @Scheduled(fixedRate = 30000)
    public void checkAllServices() {
        long batchStartTime = System.currentTimeMillis();
        log.info("开始检查所有服务实例的健康状态");
        
        Map<ModelServiceRegistry.ServiceType, List<ModelRouterProperties.ModelInstance>> instanceRegistry =
                modelServiceRegistry.getAllInstances();
        if (instanceRegistry == null || instanceRegistry.isEmpty()) {
            log.warn("未找到配置的服务实例");
            return;
        }

        List<Runnable> tasks = getRunnable(instanceRegistry);
        
        // 统计信息
        int totalServices = instanceRegistry.size();
        int totalInstances = instanceRegistry.values().stream()
            .mapToInt(List::size)
            .sum();
        int healthyServices = 0;
        int healthyInstances = 0;

        // 执行所有检查任务
        for (Runnable task : tasks) {
            try {
                task.run();
            } catch (Exception e) {
                log.error("执行服务检查任务时发生错误", e);
            }
        }
        
        // 统计健康状态
        for (Map.Entry<ModelServiceRegistry.ServiceType, List<ModelRouterProperties.ModelInstance>> entry : instanceRegistry.entrySet()) {
            String serviceType = entry.getKey().name();
            List<ModelRouterProperties.ModelInstance> instances = entry.getValue();
            
            boolean serviceHasHealthyInstance = false;
            for (ModelRouterProperties.ModelInstance instance : instances) {
                if (serviceStateManager.isInstanceHealthy(serviceType, instance)) {
                    healthyInstances++;
                    serviceHasHealthyInstance = true;
                }
            }
            
            if (serviceHasHealthyInstance) {
                healthyServices++;
            }
        }
        
        long batchDuration = System.currentTimeMillis() - batchStartTime;
        
        // 记录批次完成追踪
        try {
            org.unreal.modelrouter.tracing.health.HealthCheckTracingEnhancer enhancer = 
                org.unreal.modelrouter.util.ApplicationContextProvider.getBean(
                    org.unreal.modelrouter.tracing.health.HealthCheckTracingEnhancer.class);
            enhancer.logHealthCheckBatchComplete(totalServices, healthyServices, 
                totalInstances, healthyInstances, batchDuration);
        } catch (Exception e) {
            // 忽略追踪错误
        }

        log.debug("所有服务实例健康检查完成，健康服务: {}/{}, 健康实例: {}/{}, 耗时: {}ms",
            healthyServices, totalServices, healthyInstances, totalInstances, batchDuration);
    }

    private List<Runnable> getRunnable(Map<ModelServiceRegistry.ServiceType, List<ModelRouterProperties.ModelInstance>> instanceRegistry) {
        List<Runnable> tasks = new ArrayList<>();

        for (Map.Entry<ModelServiceRegistry.ServiceType, List<ModelRouterProperties.ModelInstance>> entry : instanceRegistry.entrySet()) {
            ModelServiceRegistry.ServiceType serviceType = entry.getKey();
            List<ModelRouterProperties.ModelInstance> instances = entry.getValue();

            if (instances != null && !instances.isEmpty()) {
                tasks.add(() -> checkServiceInstances(serviceType.name(), instances));
            }
        }
        return tasks;
    }

    /**
     * 检查特定服务类型的所有实例
     */
    private void checkServiceInstances(String serviceType, List<ModelRouterProperties.ModelInstance> instances) {
        boolean hasHealthyInstance = false;

        for (ModelRouterProperties.ModelInstance instance : instances) {
            // 获取健康检查追踪增强器
            HealthCheckTracingEnhancer tracingEnhancer = null;
            TracingContext tracingContext = null;
            try {
                tracingEnhancer = ApplicationContextProvider.getBean(HealthCheckTracingEnhancer.class);
                tracingContext = tracingEnhancer.createHealthCheckContext(serviceType, instance);
                tracingEnhancer.logHealthCheckStart(serviceType, instance, tracingContext);
            } catch (Exception e) {
                log.error("无法创建健康检查追踪上下文: {}", e.getMessage());
            }
            
            try {
                URI uri = new URI(instance.getBaseUrl());
                String host = uri.getHost();
                int port = uri.getPort();

                // 如果端口未指定，使用协议默认端口
                if (port == -1) {
                    String scheme = uri.getScheme();
                    if ("http".equals(scheme)) {
                        port = 80;
                    } else if ("https".equals(scheme)) {
                        port = 443;
                    }
                }

                // 使用 NetUtils 检查 socket 连接（同步方法）
                long startTime = System.currentTimeMillis();
                NetUtils.NetConnect result = NetUtils.testSocketConnect(host, port);
                long responseTime = System.currentTimeMillis() - startTime;

                // 创建实例的唯一标识符
                String instanceKey = serviceType + ":" + instance.getName() + "@" + instance.getBaseUrl();
                
                // 获取之前的健康状态
                boolean previousHealthStatus = previousInstanceStates.getOrDefault(instanceKey, true);
                boolean currentHealthStatus = result.isConnect();

                if (result.isConnect()) {
                    hasHealthyInstance = true;
                    serviceStateManager.updateInstanceHealthStatus(serviceType, instance, true);
                    log.debug("实例 {} 连接成功: {}", instance.getName(), result.getMsg());
                    recordHealthCheckMetrics(getAdapterType(instance), instance.getName(), true, responseTime);
                    
                    // 记录健康检查完成事件
                    if (tracingEnhancer != null && tracingContext != null) {
                        tracingEnhancer.logHealthCheckComplete(serviceType, instance, true, responseTime, result.getMsg(), tracingContext);
                    }
                } else {
                    serviceStateManager.updateInstanceHealthStatus(serviceType, instance, false);
                    log.warn("实例 {} 连接失败: {}", instance.getName(), result.getMsg());
                    recordHealthCheckMetrics(getAdapterType(instance), instance.getName(), false, responseTime);
                    
                    // 记录健康检查完成事件
                    if (tracingEnhancer != null && tracingContext != null) {
                        tracingEnhancer.logHealthCheckComplete(serviceType, instance, false, responseTime, result.getMsg(), tracingContext);
                    }
                }
                
                // 检查实例状态是否发生变化
                if (previousHealthStatus != currentHealthStatus) {
                    // 记录状态变更事件
                    if (tracingEnhancer != null) {
                        tracingEnhancer.logInstanceStateChange(
                            serviceType, 
                            instance, 
                            previousHealthStatus, 
                            currentHealthStatus, 
                            result.getMsg()
                        );
                    }
                    log.info("实例 {} 状态发生变化: {} -> {}", 
                        instance.getName(), 
                        previousHealthStatus ? "健康" : "不健康", 
                        currentHealthStatus ? "健康" : "不健康");
                }
                
                // 更新缓存的状态
                previousInstanceStates.put(instanceKey, currentHealthStatus);

            } catch (URISyntaxException e) {
                log.error("无效的URL格式: {}", instance.getBaseUrl(), e);
                // 记录健康检查完成事件（失败）
                if (tracingEnhancer != null && tracingContext != null) {
                    tracingEnhancer.logHealthCheckComplete(serviceType, instance, false, 0, "无效的URL格式: " + e.getMessage(), tracingContext);
                }
            } finally {
                // 完成追踪上下文
                if (tracingContext != null) {
                    tracingContext.clear();
                }
            }
        }

        // 更新服务健康状态
        boolean previousServiceState = serviceStateManager.isServiceHealthy(serviceType);
        serviceStateManager.updateServiceHealthStatus(serviceType, hasHealthyInstance);

        // 检查服务状态是否发生变化
        if (previousServiceState != hasHealthyInstance) {
            try {
                HealthCheckTracingEnhancer tracingEnhancer = ApplicationContextProvider.getBean(HealthCheckTracingEnhancer.class);
                int totalInstances = instances.size();
                int healthyInstances = 0;
                for (ModelRouterProperties.ModelInstance instance : instances) {
                    if (serviceStateManager.isInstanceHealthy(serviceType, instance)) {
                        healthyInstances++;
                    }
                }
                tracingEnhancer.logServiceStateChange(serviceType, hasHealthyInstance, totalInstances, healthyInstances);
            } catch (Exception e) {
                log.debug("无法记录服务状态变更事件: {}", e.getMessage());
            }
            
            log.info("服务 {} 状态发生变化: {} -> {}", 
                serviceType, 
                previousServiceState ? "健康" : "不健康", 
                hasHealthyInstance ? "健康" : "不健康");
        }

        if (hasHealthyInstance) {
            log.info("{} 服务至少有一个实例是健康的", serviceType);
        } else {
            log.warn("{} 服务所有实例都不可达", serviceType);
        }
    }

    /**
     * 记录健康检查指标
     */
    private void recordHealthCheckMetrics(String adapter, String instance, boolean healthy, long responseTime) {
        if (metricsCollector != null) {
            try {
                metricsCollector.recordHealthCheck(adapter, instance, healthy, responseTime);
            } catch (Exception e) {
                log.warn("Failed to record health check metrics: {}", e.getMessage());
            }
        }
    }

    /**
     * 根据实例信息推断适配器类型
     */
    private String getAdapterType(ModelRouterProperties.ModelInstance instance) {
        // 根据实例的URL或其他特征推断适配器类型
        String baseUrl = instance.getBaseUrl().toLowerCase();
        if (baseUrl.contains("ollama")) {
            return "ollama";
        } else if (baseUrl.contains("vllm")) {
            return "vllm";
        } else if (baseUrl.contains("gpustack")) {
            return "gpustack";
        } else if (baseUrl.contains("xinference")) {
            return "xinference";
        } else if (baseUrl.contains("localai")) {
            return "localai";
        } else {
            return "normal";
        }
    }
    
    /**
     * 记录服务实例注册事件
     * 
     * @param serviceType 服务类型
     * @param instance 服务实例
     */
    public void logServiceInstanceRegistered(String serviceType, ModelRouterProperties.ModelInstance instance) {
        try {
            HealthCheckTracingEnhancer tracingEnhancer = ApplicationContextProvider.getBean(HealthCheckTracingEnhancer.class);
            tracingEnhancer.logServiceInstanceRegistered(serviceType, instance);
        } catch (Exception e) {
            log.debug("无法记录服务实例注册事件: {}", e.getMessage());
        }
    }
    
    /**
     * 记录服务实例发现事件
     * 
     * @param serviceType 服务类型
     * @param instance 服务实例
     */
    public void logServiceInstanceDiscovered(String serviceType, ModelRouterProperties.ModelInstance instance) {
        try {
            HealthCheckTracingEnhancer tracingEnhancer = ApplicationContextProvider.getBean(HealthCheckTracingEnhancer.class);
            tracingEnhancer.logServiceInstanceDiscovered(serviceType, instance);
        } catch (Exception e) {
            log.debug("无法记录服务实例发现事件: {}", e.getMessage());
        }
    }
}