package org.unreal.modelrouter.router.adapter.selector;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.unreal.modelrouter.router.model.ModelRouterProperties;
import org.unreal.modelrouter.router.model.ModelServiceRegistry;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 实例选择器
 *
 * 负责从服务注册表中选择合适的服务实例
 * 支持负载均衡和健康检查
 *
 * @author AI Assistant
 * @since v2.2.4
 */
@Component
public class InstanceSelector {

    private static final Logger logger = LoggerFactory.getLogger(InstanceSelector.class);

    private final ModelServiceRegistry registry;
    private final WebClient webClient;

    // 健康状态缓存：instanceId -> HealthStatus
    private final Map<String, HealthStatus> healthCache = new ConcurrentHashMap<>();

    // 健康检查超时时间（毫秒）
    private static final long HEALTH_CHECK_TIMEOUT_MS = 3000;

    // 健康状态缓存时间（毫秒）
    private static final long HEALTH_CACHE_TTL_MS = 10000;

    public InstanceSelector(final ModelServiceRegistry registry) {
        this.registry = registry;
        this.webClient = WebClient.builder().build();
    }

    /**
     * 健康状态缓存
     */
    private static class HealthStatus {
        final boolean healthy;
        final long timestamp;
        final int consecutiveFailures;

        HealthStatus(final boolean healthy, final int consecutiveFailures) {
            this.healthy = healthy;
            this.timestamp = System.currentTimeMillis();
            this.consecutiveFailures = consecutiveFailures;
        }

        boolean isExpired() {
            return System.currentTimeMillis() - timestamp > HEALTH_CACHE_TTL_MS;
        }
    }

    /**
     * 选择服务实例
     *
     * @param serviceType 服务类型
     * @param modelName 模型名称
     * @param clientIp 客户端 IP
     * @return 选中的实例
     */
    public ModelRouterProperties.ModelInstance selectInstance(
            final ModelServiceRegistry.ServiceType serviceType,
            final String modelName,
            final String clientIp) {
        
        logger.debug("选择实例：serviceType={}, modelName={}, clientIp={}",
                serviceType, modelName, clientIp);
        
        ModelRouterProperties.ModelInstance selected = registry.selectInstance(serviceType, modelName, clientIp);
        
        if (selected != null) {
            logger.debug("已选择实例：instanceId={}, baseUrl={}, weight={}",
                    selected.getInstanceId(), selected.getBaseUrl(), selected.getWeight());
        } else {
            logger.warn("未找到可用实例：serviceType={}, modelName={}", serviceType, modelName);
        }
        
        return selected;
    }

    /**
     * 获取模型路径
     *
     * @param serviceType 服务类型
     * @param modelName 模型名称
     * @return 模型路径
     */
    public String getModelPath(
            final ModelServiceRegistry.ServiceType serviceType,
            final String modelName) {
        
        return registry.getModelPath(serviceType, modelName);
    }

    /**
     * 检查实例健康状态
     *
     * @param serviceType 服务类型
     * @param instance 实例
     * @return true 如果实例健康
     */
    public boolean isInstanceHealthy(
            final ModelServiceRegistry.ServiceType serviceType,
            final ModelRouterProperties.ModelInstance instance) {

        if (instance == null) {
            return false;
        }

        String instanceId = instance.getInstanceId();
        String baseUrl = instance.getBaseUrl();

        // 检查缓存
        HealthStatus cachedStatus = healthCache.get(instanceId);
        if (cachedStatus != null && !cachedStatus.isExpired()) {
            logger.debug("使用缓存的健康状态：instanceId={}, healthy={}", instanceId, cachedStatus.healthy);
            return cachedStatus.healthy;
        }

        // 缓存过期或不存在，执行 HTTP 健康检查
        boolean healthy = performHttpHealthCheck(baseUrl);

        // 更新缓存
        int consecutiveFailures = healthy ? 0 : (cachedStatus != null ? cachedStatus.consecutiveFailures + 1 : 1);
        healthCache.put(instanceId, new HealthStatus(healthy, consecutiveFailures));

        logger.debug("健康检查结果：instanceId={}, baseUrl={}, healthy={}, consecutiveFailures={}",
                instanceId, baseUrl, healthy, consecutiveFailures);

        return healthy;
    }

    /**
     * 执行 HTTP 健康检查
     *
     * @param baseUrl 实例基础 URL
     * @return true 如果 HTTP 连通性正常
     */
    private boolean performHttpHealthCheck(final String baseUrl) {
        try {
            // 尝试访问健康检查端点或基础 URL
            String healthUrl = baseUrl.endsWith("/") ? baseUrl + "health" : baseUrl + "/health";

            HttpStatusCode statusCode = webClient.get()
                    .uri(healthUrl)
                    .retrieve()
                    .toBodilessEntity()
                    .block(Duration.ofMillis(HEALTH_CHECK_TIMEOUT_MS))
                    .getStatusCode();

            // 2xx 状态码表示健康
            boolean healthy = statusCode != null && statusCode.is2xxSuccessful();

            if (!healthy) {
                logger.warn("健康检查失败：baseUrl={}, statusCode={}", baseUrl, statusCode);
            }

            return healthy;

        } catch (Exception e) {
            // HTTP 请求失败，尝试备用检查方式
            logger.debug("健康检查端点不可用，尝试备用检查：baseUrl={}, error={}", baseUrl, e.getMessage());
            return performFallbackHealthCheck(baseUrl);
        }
    }

    /**
     * 备用健康检查（当健康检查端点不可用时）
     *
     * @param baseUrl 实例基础 URL
     * @return true 如果 TCP 连通性正常
     */
    private boolean performFallbackHealthCheck(final String baseUrl) {
        try {
            // 解析 URL
            java.net.URI uri = java.net.URI.create(baseUrl);
            String host = uri.getHost();
            int port = uri.getPort() != -1 ? uri.getPort()
                    : ("https".equals(uri.getScheme()) ? 443 : 80);

            // 尝试建立 TCP 连接（仅用于健康检查，不传输敏感数据）
            javax.net.SocketFactory factory = "https".equals(uri.getScheme())
                    ? javax.net.ssl.SSLSocketFactory.getDefault()
                    : javax.net.SocketFactory.getDefault();
            java.net.Socket socket = factory.createSocket();
            socket.connect(new java.net.InetSocketAddress(host, port), (int) HEALTH_CHECK_TIMEOUT_MS);
            socket.close();

            logger.debug("TCP 连通性检查成功：host={}, port={}", host, port);
            return true;

        } catch (Exception e) {
            logger.warn("TCP 连通性检查失败：baseUrl={}, error={}", baseUrl, e.getMessage());
            return false;
        }
    }

    /**
     * 清除实例健康缓存
     *
     * @param instanceId 实例 ID
     */
    public void clearHealthCache(final String instanceId) {
        healthCache.remove(instanceId);
        logger.debug("清除健康缓存：instanceId={}", instanceId);
    }

    /**
     * 清除所有健康缓存
     */
    public void clearAllHealthCache() {
        healthCache.clear();
        logger.debug("清除所有健康缓存");
    }

    /**
     * 记录实例调用完成
     *
     * @param serviceType 服务类型
     * @param instance 实例
     */
    public void recordInstanceCallComplete(
            final ModelServiceRegistry.ServiceType serviceType,
            final ModelRouterProperties.ModelInstance instance) {
        
        registry.recordCallComplete(serviceType, instance);
    }

    /**
     * 记录实例调用失败
     *
     * @param serviceType 服务类型
     * @param instance 实例
     */
    public void recordInstanceCallFailure(
            final ModelServiceRegistry.ServiceType serviceType,
            final ModelRouterProperties.ModelInstance instance) {
        
        registry.recordCallFailure(serviceType, instance);
    }
}
