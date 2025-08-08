package org.unreal.modelrouter.checker;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.unreal.modelrouter.model.ModelRouterProperties;
import org.unreal.modelrouter.model.ModelServiceRegistry;
import org.unreal.modelrouter.util.NetUtils;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
public class ServerChecker {

    private static final Logger log = LoggerFactory.getLogger(ServerChecker.class);

    private final ModelServiceRegistry modelServiceRegistry;
    private final ServiceStateManager serviceStateManager;

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
        log.info("开始检查所有服务实例的健康状态");
        Map<ModelServiceRegistry.ServiceType, List<ModelRouterProperties.ModelInstance>> instanceRegistry =
                modelServiceRegistry.getAllInstances();
        if (instanceRegistry == null || instanceRegistry.isEmpty()) {
            log.warn("未找到配置的服务实例");
            return;
        }

        List<Runnable> tasks = getRunnable(instanceRegistry);

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
                    serviceStateManager.updateInstanceHealthStatus(serviceType, instance, true);
                    log.debug("实例 {} 连接成功: {}", instance.getName(), result.getMsg());
                } else {
                    serviceStateManager.updateInstanceHealthStatus(serviceType, instance, false);
                    log.warn("实例 {} 连接失败: {}", instance.getName(), result.getMsg());
                }

            } catch (URISyntaxException e) {
                log.error("无效的URL格式: {}", instance.getBaseUrl(), e);
            }
        }

        // 更新服务健康状态
        serviceStateManager.updateServiceHealthStatus(serviceType, hasHealthyInstance);

        if (hasHealthyInstance) {
            log.info("{} 服务至少有一个实例是健康的", serviceType);
        } else {
            log.warn("{} 服务所有实例都不可达", serviceType);
        }
    }
}
