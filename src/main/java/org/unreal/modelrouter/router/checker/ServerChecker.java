package org.unreal.modelrouter.router.checker;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.unreal.modelrouter.persistence.jpa.entity.ServiceInstanceEntity;
import org.unreal.modelrouter.persistence.jpa.repository.ServiceInstanceRepository;
import org.unreal.modelrouter.router.model.InstanceKeys;
import org.unreal.modelrouter.router.model.ModelRouterProperties;
import org.unreal.modelrouter.router.model.ModelServiceRegistry;
import org.unreal.modelrouter.monitor.monitoring.collector.MetricsCollector;
import org.unreal.modelrouter.monitor.tracing.TracingContext;
import org.unreal.modelrouter.monitor.tracing.health.HealthCheckTracingEnhancer;
import org.unreal.modelrouter.common.util.ApplicationContextProvider;
import org.unreal.modelrouter.common.util.NetUtils;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

import jakarta.annotation.PreDestroy;

@Component
public class ServerChecker {

    private static final Logger log = LoggerFactory.getLogger(ServerChecker.class);

    private final ModelServiceRegistry modelServiceRegistry;
    private final ServiceStateManager serviceStateManager;
    private final HealthCheckProperties healthCheckProperties;
    /** 懒创建：只有 mode: http 真正用到时才建 HttpClient（构建它要走系统代理探测，成本不低）。 */
    private volatile HttpHealthProbe httpHealthProbe;

    /** 批次协调者：只负责「跑完一轮」，与调度线程解耦（v3.5.4，#651）。 */
    private final ExecutorService batchCoordinator;
    /** 服务级检查的工作线程池；并发度见 {@code jairouter.health-check.parallelism}。 */
    private final ExecutorService healthCheckExecutor;
    /** 上一轮还没结束就跳过本轮触发，避免批次堆积。 */
    private final AtomicBoolean batchRunning = new AtomicBoolean(false);

    @Autowired(required = false)
    private MetricsCollector metricsCollector;

    @Autowired(required = false)
    private ServiceInstanceRepository serviceInstanceRepository;

    // 缓存实例之前的状态，用于检测状态变化
    private final Map<String, Boolean> previousInstanceStates = new ConcurrentHashMap<>();

    public ServerChecker(final ModelServiceRegistry modelServiceRegistry,
                         final ServiceStateManager serviceStateManager) {
        this(modelServiceRegistry, serviceStateManager, new HealthCheckProperties());
    }

    @Autowired
    public ServerChecker(final ModelServiceRegistry modelServiceRegistry,
                         final ServiceStateManager serviceStateManager,
                         final HealthCheckProperties healthCheckProperties) {
        this.modelServiceRegistry = modelServiceRegistry;
        this.serviceStateManager = serviceStateManager;
        this.healthCheckProperties = healthCheckProperties != null
                ? healthCheckProperties : new HealthCheckProperties();
        this.batchCoordinator = Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "health-check-batch");
            thread.setDaemon(true);
            return thread;
        });
        this.healthCheckExecutor = Executors.newFixedThreadPool(
                this.healthCheckProperties.effectiveParallelism(), runnable -> {
                    Thread thread = new Thread(runnable, "health-check-worker");
                    thread.setDaemon(true);
                    return thread;
                });
    }

    /**
     * 关闭健康检查专用线程（v3.5.4，#651：不再占用 Spring 默认的单线程 scheduler）。
     */
    @PreDestroy
    public void shutdownExecutors() {
        batchCoordinator.shutdownNow();
        healthCheckExecutor.shutdownNow();
    }

    /**
     * 当配置更新时，清理过期的实例状态缓存
     */
    public void clearExpiredInstanceStates() {
        log.info("清理过期的实例状态缓存，清理前缓存大小: {}", previousInstanceStates.size());
        previousInstanceStates.clear();
        log.info("实例状态缓存清理完成");
    }

    /**
     * 定时检查所有服务实例的健康状态
     * 每30秒执行一次
     */
    @Scheduled(fixedDelayString = "${jairouter.health-check.interval-ms:30000}")
    public void checkAllServices() {
        if (!batchRunning.compareAndSet(false, true)) {
            log.debug("上一轮健康检查尚未结束，跳过本轮触发");
            return;
        }
        try {
            batchCoordinator.execute(this::runHealthCheckBatch);
        } catch (RuntimeException e) {
            batchRunning.set(false);
            log.error("提交健康检查批次失败", e);
        }
    }

    /**
     * 跑完一轮健康检查（v3.5.4：由调度线程提交到 coordinator 执行；测试可直接调用）。
     */
    void runHealthCheckBatch() {
        try {
            long batchStartTime = System.currentTimeMillis();
        log.info("开始检查所有服务实例的健康状态");
        
        Map<ModelServiceRegistry.ServiceType, List<ModelRouterProperties.ModelInstance>> instanceRegistry =
                modelServiceRegistry.getAllInstances();
        
        // 增强日志输出，帮助诊断问题
        if (instanceRegistry == null) {
            log.warn("服务实例注册表为null");
            return;
        }
        
        if (instanceRegistry.isEmpty()) {
            log.warn("未找到配置的服务实例");
            // 添加更多调试信息
            log.debug("检查配置是否正确加载...");
            try {
                // 尝试获取服务类型列表
                Set<ModelServiceRegistry.ServiceType> availableTypes = modelServiceRegistry.getAvailableServiceTypes();
                log.debug("可用服务类型: {}", availableTypes != null ? availableTypes.size() : 0);
                
                // 检查每个服务类型的实例
                for (ModelServiceRegistry.ServiceType type : ModelServiceRegistry.ServiceType.values()) {
                    Set<String> models = modelServiceRegistry.getAvailableModels(type);
                    log.debug("服务类型 {} 包含 {} 个模型", type, models != null ? models.size() : 0);
                }
            } catch (Exception e) {
                log.error("检查配置时发生错误", e);
            }
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

        // v3.5.4（#651）：服务级检查并发执行，单个服务异常不影响其它服务
        List<CompletableFuture<Void>> futures = new ArrayList<>();
        for (Runnable task : tasks) {
            futures.add(CompletableFuture.runAsync(task, healthCheckExecutor)
                    .exceptionally(ex -> {
                        log.error("执行服务检查任务时发生错误", ex);
                        return null;
                    }));
        }
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
        
        // 统计健康状态
        for (Map.Entry<ModelServiceRegistry.ServiceType,
                List<ModelRouterProperties.ModelInstance>> entry : instanceRegistry.entrySet()) {
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
            org.unreal.modelrouter.monitor.tracing.health.HealthCheckTracingEnhancer enhancer = 
                org.unreal.modelrouter.common.util.ApplicationContextProvider.getBean(
                    org.unreal.modelrouter.monitor.tracing.health.HealthCheckTracingEnhancer.class);
            enhancer.logHealthCheckBatchComplete(totalServices, healthyServices, 
                totalInstances, healthyInstances, batchDuration);
        } catch (Exception e) {
            // 忽略追踪错误
        }

        log.debug("所有服务实例健康检查完成，健康服务: {}/{}, 健康实例: {}/{}, 耗时: {}ms",
            healthyServices, totalServices, healthyInstances, totalInstances, batchDuration);
        } finally {
            batchRunning.set(false);
        }
    }

    private List<Runnable> getRunnable(
            final Map<ModelServiceRegistry.ServiceType,
                    List<ModelRouterProperties.ModelInstance>> instanceRegistry) {
        List<Runnable> tasks = new ArrayList<>();

        for (Map.Entry<ModelServiceRegistry.ServiceType,
                List<ModelRouterProperties.ModelInstance>> entry : instanceRegistry.entrySet()) {
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
    private void checkServiceInstances(
            final String serviceType,
            final List<ModelRouterProperties.ModelInstance> instances) {
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
            
            // v3.5.4（#650）：无效 / 缺失的 base-url 一律判不健康，且单个实例的异常不再中断同服务其它实例的检查
            ProbeOutcome probe;
            try {
                probe = probeInstance(instance);
            } catch (RuntimeException e) {
                probe = new ProbeOutcome(false, 0L, "健康检查异常: "
                        + e.getClass().getSimpleName()
                        + (e.getMessage() != null ? ": " + e.getMessage() : ""));
            }

            // 创建实例的唯一标识符（#636：instanceId 缺失时回退 baseUrl，避免所有实例共用 chat:null）
            String instanceKey = InstanceKeys.healthKey(serviceType, instance);

            // 获取之前的健康状态
            boolean previousHealthStatus = previousInstanceStates.getOrDefault(instanceKey, true);
            boolean currentHealthStatus = probe.healthy();

            try {
                if (currentHealthStatus) {
                    hasHealthyInstance = true;
                    serviceStateManager.updateInstanceHealthStatus(serviceType, instance, true);
                    updateDatabaseHealthStatus(instance.getName(), instance.getInstanceId(),
                            instance.getBaseUrl(), "HEALTHY", null);
                    log.debug("实例 {} 探活成功: {}", instance.getName(), probe.detail());
                } else {
                    serviceStateManager.updateInstanceHealthStatus(serviceType, instance, false);
                    updateDatabaseHealthStatus(instance.getName(), instance.getInstanceId(),
                            instance.getBaseUrl(), "UNHEALTHY", probe.detail());
                    log.warn("实例 {} 探活失败: {}（base-url={}）",
                            instance.getName(), probe.detail(), instance.getBaseUrl());
                }

                recordHealthCheckMetrics(getAdapterType(instance), instance.getName(),
                        currentHealthStatus, probe.responseTimeMs());

                // 记录健康检查完成事件
                if (tracingEnhancer != null && tracingContext != null) {
                    tracingEnhancer.logHealthCheckComplete(
                            serviceType, instance, currentHealthStatus, probe.responseTimeMs(),
                            probe.detail(), tracingContext);
                }

                // 检查实例状态是否发生变化
                if (previousHealthStatus != currentHealthStatus) {
                    if (tracingEnhancer != null) {
                        tracingEnhancer.logInstanceStateChange(
                            serviceType,
                            instance,
                            previousHealthStatus,
                            currentHealthStatus,
                            probe.detail()
                        );
                    }
                    log.info("实例 {} 状态发生变化: {} -> {}",
                        instance.getName(),
                        previousHealthStatus ? "健康" : "不健康",
                        currentHealthStatus ? "健康" : "不健康");
                }

                // 更新缓存的状态
                previousInstanceStates.put(instanceKey, currentHealthStatus);
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
                HealthCheckTracingEnhancer tracingEnhancer =
                        ApplicationContextProvider.getBean(HealthCheckTracingEnhancer.class);
                int totalInstances = instances.size();
                int healthyInstances = 0;
                for (ModelRouterProperties.ModelInstance instance : instances) {
                    if (serviceStateManager.isInstanceHealthy(serviceType, instance)) {
                        healthyInstances++;
                    }
                }
                tracingEnhancer.logServiceStateChange(
                        serviceType, hasHealthyInstance, totalInstances, healthyInstances);
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
     * 探活单个实例（v3.5.4，#650/#651）。
     *
     * <p>解析失败、URL 指向本机但端口缺失等情况一律返回「不健康」，而不是沿用上一次的状态；
     * 探活方式由 {@code jairouter.health-check.mode} 决定：{@code TCP} 只看端口连通，
     * {@code HTTP} 还会校验状态码（&lt;500 视为可用）。</p>
     */
    private HttpHealthProbe httpProbe() {
        HttpHealthProbe probe = this.httpHealthProbe;
        if (probe == null) {
            synchronized (this) {
                probe = this.httpHealthProbe;
                if (probe == null) {
                    probe = new HttpHealthProbe(healthCheckProperties.effectiveTimeoutMs());
                    this.httpHealthProbe = probe;
                }
            }
        }
        return probe;
    }

    private ProbeOutcome probeInstance(final ModelRouterProperties.ModelInstance instance) {
        final String baseUrl = instance.getBaseUrl();
        final int timeoutMs = healthCheckProperties.effectiveTimeoutMs();

        if (baseUrl == null || baseUrl.isBlank()) {
            return new ProbeOutcome(false, 0L, "base-url 为空");
        }

        if (healthCheckProperties.getMode() == HealthCheckProperties.Mode.HTTP) {
            final String configured = healthCheckProperties.getHttpPath();
            final String path = configured == null || configured.isBlank()
                    ? instance.getPath() : configured;
            final long start = System.currentTimeMillis();
            final HttpHealthProbe.Result result = httpProbe().probe(baseUrl, path, timeoutMs);
            return new ProbeOutcome(result.healthy(),
                    System.currentTimeMillis() - start, result.detail());
        }

        final URI uri;
        try {
            uri = new URI(baseUrl);
        } catch (URISyntaxException e) {
            return new ProbeOutcome(false, 0L, "无效的 base-url: " + e.getMessage());
        }

        final String host = uri.getHost();
        if (host == null || host.isBlank()) {
            return new ProbeOutcome(false, 0L, "base-url 缺少主机名");
        }

        int port = uri.getPort();
        if (port == -1) {
            final String scheme = uri.getScheme();
            if ("http".equalsIgnoreCase(scheme)) {
                port = 80;
            } else if ("https".equalsIgnoreCase(scheme)) {
                port = 443;
            }
        }
        if (port <= 0 || port > 65535) {
            return new ProbeOutcome(false, 0L, "base-url 端口无效: " + uri.getPort());
        }

        final long start = System.currentTimeMillis();
        final NetUtils.NetConnect result = NetUtils.testSocketConnect(host, port, timeoutMs);
        return new ProbeOutcome(result.isConnect(), System.currentTimeMillis() - start, result.getMsg());
    }

    /**
     * 单个实例一次探活的结果。
     */
    record ProbeOutcome(boolean healthy, long responseTimeMs, String detail) {
    }

    /**
     * 记录健康检查指标
     */
    private void recordHealthCheckMetrics(
            final String adapter, final String instance,
            final boolean healthy, final long responseTime) {
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
    private String getAdapterType(final ModelRouterProperties.ModelInstance instance) {
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
    public void logServiceInstanceRegistered(
            final String serviceType,
            final ModelRouterProperties.ModelInstance instance) {
        try {
            HealthCheckTracingEnhancer tracingEnhancer =
                    ApplicationContextProvider.getBean(HealthCheckTracingEnhancer.class);
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
    public void logServiceInstanceDiscovered(
            final String serviceType,
            final ModelRouterProperties.ModelInstance instance) {
        try {
            HealthCheckTracingEnhancer tracingEnhancer =
                    ApplicationContextProvider.getBean(HealthCheckTracingEnhancer.class);
            tracingEnhancer.logServiceInstanceDiscovered(serviceType, instance);
        } catch (Exception e) {
            log.debug("无法记录服务实例发现事件: {}", e.getMessage());
        }
    }
    /**
     * 更新数据库中实例的健康状态
     *
     * @param instanceName 实例名称
     * @param instanceId 实例唯一ID (UUID)
     * @param healthStatus 健康状态 (HEALTHY, UNHEALTHY, UNKNOWN)
     * @param errorMessage 错误信息
     */
    private void updateDatabaseHealthStatus(
            final String instanceName, final String instanceId, final String baseUrl,
            final String healthStatus, final String errorMessage) {
        if (serviceInstanceRepository == null) {
            return;
        }
        try {
            // v2.x: 优先使用 instanceId 查找，避免同名实例导致的重复记录错误
            if (instanceId != null && !instanceId.isEmpty()) {
                serviceInstanceRepository.findByInstanceId(instanceId).ifPresent(entity ->
                        persistIfChanged(entity, instanceName, healthStatus, errorMessage));
                return;
            }

            // v3.5.4（#652）：instanceId 为空（内存对象从 YAML 加载）时不能「按名字批量更新」，
            // 否则同名副本会互相覆盖状态；改为在同名记录里按 base-url 精确定位。
            List<ServiceInstanceEntity> entities =
                    serviceInstanceRepository.findAllByInstanceName(instanceName);
            if (entities.isEmpty()) {
                log.debug("未找到实例 {} 的数据库记录", instanceName);
                return;
            }
            if (entities.size() == 1) {
                persistIfChanged(entities.get(0), instanceName, healthStatus, errorMessage);
                return;
            }

            final String normalizedBaseUrl = normalizeBaseUrl(baseUrl);
            for (ServiceInstanceEntity entity : entities) {
                if (normalizedBaseUrl != null
                        && normalizedBaseUrl.equals(normalizeBaseUrl(entity.getBaseUrl()))) {
                    persistIfChanged(entity, instanceName, healthStatus, errorMessage);
                    return;
                }
            }
            log.warn("实例 {} 在库中有 {} 条同名记录，但没有 base-url={} 的匹配行，跳过健康状态入库",
                    instanceName, entities.size(), baseUrl);
        } catch (Exception e) {
            log.error("更新数据库实例健康状态失败: {}", e.getMessage());
        }
    }

    /**
     * 状态与错误信息都没变时不写库（v3.5.4，#652：30 秒一轮的空写会持续占用连接池）。
     */
    private void persistIfChanged(final ServiceInstanceEntity entity, final String instanceName,
                                  final String healthStatus, final String errorMessage) {
        final boolean sameStatus = healthStatus.equals(entity.getHealthStatus());
        final boolean sameMessage = errorMessage == null
                ? entity.getErrorMessage() == null
                : errorMessage.equals(entity.getErrorMessage());
        if (sameStatus && sameMessage) {
            return;
        }
        entity.setHealthStatus(healthStatus);
        entity.setErrorMessage(errorMessage);
        serviceInstanceRepository.save(entity);
        log.debug("更新数据库实例 {} (instanceId={}) 健康状态: {}",
                instanceName, entity.getInstanceId(), healthStatus);
    }

    /**
     * 归一化 base-url：去掉尾部斜杠并转小写，便于同名实例之间比对。
     */
    private static String normalizeBaseUrl(final String baseUrl) {
        if (baseUrl == null || baseUrl.isBlank()) {
            return null;
        }
        String normalized = baseUrl.trim();
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized.toLowerCase(java.util.Locale.ROOT);
    }
}
