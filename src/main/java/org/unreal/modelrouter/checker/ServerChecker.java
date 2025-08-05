package org.unreal.modelrouter.checker;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.unreal.modelrouter.config.ModelRouterProperties;
import org.unreal.modelrouter.util.NetUtils;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class ServerChecker {

    private static final Logger log = LoggerFactory.getLogger(ServerChecker.class);

    private final ModelRouterProperties modelRouterProperties;

    // 存储每个服务类型的健康状态
    private final Map<String, Boolean> serviceHealthStatus = new ConcurrentHashMap<>();

    // 存储每个具体实例的健康状态
    private final Map<String, Boolean> instanceHealthStatus = new ConcurrentHashMap<>();

    public ServerChecker(ModelRouterProperties modelRouterProperties) {
        this.modelRouterProperties = modelRouterProperties;
    }

    /**
     * 定时检查所有服务实例的健康状态
     * 每30秒执行一次
     */
    @Scheduled(fixedRate = 30000)
    public void checkAllServices() {
        log.info("开始检查所有服务实例的健康状态");
        Map<String, ModelRouterProperties.ServiceConfig> services = modelRouterProperties.getServices();
        if (services == null || services.isEmpty()) {
            log.warn("未找到配置的服务实例");
            return;
        }

        List<Runnable> tasks = getRunnable(services);

        // 执行所有检查任务
        for (Runnable task : tasks) {
            try {
                task.run();
            } catch (Exception e) {
                log.error("执行服务检查任务时发生错误", e);
            }
        }

        log.info("所有服务实例健康检查完成");
    }

    private List<Runnable> getRunnable(Map<String, ModelRouterProperties.ServiceConfig> services) {
        List<Runnable> tasks = new ArrayList<>();

        for (Map.Entry<String, ModelRouterProperties.ServiceConfig> entry : services.entrySet()) {
            String serviceType = entry.getKey();
            ModelRouterProperties.ServiceConfig serviceConfig = entry.getValue();

            // 跳过负载均衡配置本身
            if ("load-balance".equals(serviceType)) {
                continue;
            }

            if (serviceConfig.getInstances() != null) {
                List<ModelRouterProperties.ModelInstance> instances = serviceConfig.getInstances();
                tasks.add(() -> checkServiceInstances(serviceType, instances));
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
                NetUtils.NetConnect result = NetUtils.testSocketConnect(host, port);

                // 创建实例的唯一标识符
                String instanceKey = serviceType + ":" + instance.getName() + "@" + instance.getBaseUrl();

                if (result.isConnect()) {
                    hasHealthyInstance = true;
                    instanceHealthStatus.put(instanceKey, true);
                    log.debug("实例 {} 连接成功: {}", instance.getName(), result.getMsg());
                } else {
                    instanceHealthStatus.put(instanceKey, false);
                    log.warn("实例 {} 连接失败: {}", instance.getName(), result.getMsg());
                }

            } catch (URISyntaxException e) {
                log.error("无效的URL格式: {}", instance.getBaseUrl(), e);
            }
        }

        // 更新服务健康状态
        serviceHealthStatus.put(serviceType, hasHealthyInstance);

        if (hasHealthyInstance) {
            log.info("{} 服务至少有一个实例是健康的", serviceType);
        } else {
            log.warn("{} 服务所有实例都不可达", serviceType);
        }
    }

    /**
     * 获取特定服务类型的健康状态
     */
    public boolean isServiceHealthy(String serviceType) {
        return serviceHealthStatus.getOrDefault(serviceType, true); // 默认认为是健康的
    }

    /**
     * 获取特定实例的健康状态
     * @param serviceType 服务类型
     * @param instance 模型实例
     * @return 实例是否健康
     */
    public boolean isInstanceHealthy(String serviceType, ModelRouterProperties.ModelInstance instance) {
        String instanceKey = serviceType + ":" + instance.getName() + "@" + instance.getBaseUrl();
        return instanceHealthStatus.getOrDefault(instanceKey, true); // 默认认为是健康的
    }

    /**
     * 获取所有服务的健康状态
     */
    public Map<String, Boolean> getAllServiceHealthStatus() {
        return new ConcurrentHashMap<>(serviceHealthStatus);
    }

    /**
     * 获取所有实例的健康状态
     */
    public Map<String, Boolean> getAllInstanceHealthStatus() {
        return new ConcurrentHashMap<>(instanceHealthStatus);
    }
}
