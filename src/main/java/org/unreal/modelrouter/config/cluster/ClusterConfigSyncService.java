package org.unreal.modelrouter.config.cluster;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.unreal.modelrouter.auth.security.service.ApiKeyService;
import org.unreal.modelrouter.common.cluster.ClusterEventBus;
import org.unreal.modelrouter.persistence.store.StoreManager;
import org.unreal.modelrouter.router.model.ModelServiceRegistry;
import reactor.core.Disposable;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 跨副本配置同步（#1132）。
 *
 * <p><b>要解决的问题</b>：配置写入共享库后，只有写入的那个副本刷新自己的运行时状态，
 * 其它副本继续按内存里的旧路由表工作。管理面（读库）显示新配置，流量（读内存）走旧配置，
 * 表现为「改了不生效，重启才生效」。</p>
 *
 * <p><b>怎么解决</b>：每个副本记录「本副本已应用的版本号」，定时把库里的最大版本号比一次——
 * 变大了就刷新本副本。写入方还会在提交后广播一条集群事件，订阅方收到后立即触发同一次检查，
 * 把生效延迟从「一个轮询周期」压到「一次网络往返」。事件丢失只退化为轮询，不会漏更新。</p>
 *
 * <p><b>为什么用版本号而不是对比配置内容</b>：版本号是数据库里已有的单调递增整数
 * （{@code config_data.version}），比较是一次 {@code MAX()} 查询，不需要把整份配置拉回来
 * 做 diff；而且「内容相同但版本不同」也应当刷新（例如回滚到旧版本号）。</p>
 *
 * <p><b>为什么刷新用全量重建而不是按服务增量</b>：版本号只说明「这份配置变了」，不说明
 * 「哪个服务变了」——要从版本内容里反推变化面需要保留上一版快照，多一份状态也多一处
 * 不一致风险。配置写入是低频操作，全量重建（{@link ModelServiceRegistry#refreshFromMergedConfig()}）
 * 的代价可以接受。</p>
 *
 * <p><b>为什么调度是「固定 1 秒 tick + 内部按配置间隔判断」而不是把间隔写进
 * {@code @Scheduled}</b>：注解里的间隔在启动时解析并固定，页面改了间隔就不生效；把间隔
 * 放进每 tick 的判断里，{@code poll-interval-ms} 与 {@code enabled} 都能热改，不必重启。</p>
 *
 * @author JAiRouter Team
 * @since 3.8.7
 */
@Slf4j
@Component
public class ClusterConfigSyncService {

    /** 主配置在存储里的 key（与 {@code ConfigVersionManager} 内部常量同值）。 */
    static final String CONFIG_STORE_KEY = "model-router-config";

    /** API Key 配置在存储里的 key（与 {@code ApiKeyConfigManager} 内部常量同值）。 */
    static final String API_KEY_STORE_KEY = "security.api-keys";

    /** 配置变更事件的逻辑通道名。 */
    public static final String CONFIG_CHANGED_CHANNEL = "config.changed";

    /** 「尚未建立基线」的哨兵值。 */
    private static final int UNINITIALIZED = -1;

    /** 调度 tick 间隔（毫秒）：固定值，实际同步频率由配置的轮询间隔决定。 */
    private static final long TICK_INTERVAL_MS = 1_000L;

    private final StoreManager storeManager;
    private final ObjectProvider<ModelServiceRegistry> registryProvider;
    private final ObjectProvider<ApiKeyService> apiKeyServiceProvider;
    private final ClusterEventBus clusterEventBus;
    private final ClusterConfigSyncProperties properties;

    /** 本副本已应用的主配置版本号；{@link #UNINITIALIZED} 表示尚未建立基线。 */
    private final AtomicInteger appliedConfigVersion = new AtomicInteger(UNINITIALIZED);

    /** 本副本已应用的 API Key 配置版本号。 */
    private final AtomicInteger appliedApiKeyVersion = new AtomicInteger(UNINITIALIZED);

    private volatile Disposable eventSubscription;

    /** 上一次执行同步检查的时间戳（毫秒），用于按配置间隔节流。 */
    private volatile long lastSyncAt = 0L;

    /**
     * 构造同步服务。
     *
     * @param storeManager         存储访问（读版本号用）
     * @param registryProvider     路由注册表（可选：装配顺序未就绪时为空）
     * @param apiKeyServiceProvider API Key 服务（可选：装配顺序未就绪时为空）
     * @param clusterEventBus      集群事件总线（单副本时是空实现）
     * @param properties           本层配置
     */
    public ClusterConfigSyncService(final StoreManager storeManager,
                                    final ObjectProvider<ModelServiceRegistry> registryProvider,
                                    final ObjectProvider<ApiKeyService> apiKeyServiceProvider,
                                    final ClusterEventBus clusterEventBus,
                                    final ClusterConfigSyncProperties properties) {
        this.storeManager = storeManager;
        this.registryProvider = registryProvider;
        this.apiKeyServiceProvider = apiKeyServiceProvider;
        this.clusterEventBus = clusterEventBus;
        this.properties = properties;
    }

    /**
     * 订阅集群配置变更事件（事件总线未启用时订阅得到空流，不产生副作用）.
     *
     * <p>订阅始终建立，是否处理由配置开关在回调里判断——这样页面把事件加速关掉再打开时
     * 不需要重启。</p>
     */
    @PostConstruct
    public void subscribeClusterEvents() {
        eventSubscription = clusterEventBus.subscribe(CONFIG_CHANGED_CHANNEL)
                .subscribe(event -> {
                    if (!properties.isEnabled() || !properties.isEventBusEnabled()) {
                        return;
                    }
                    log.info("收到其它副本的配置变更事件（来源 {}），立即检查版本", event.sourceInstance());
                    forceSyncNow();
                }, error -> log.warn("集群配置事件订阅异常，退化为轮询：{}", error.getMessage()));
        log.info("集群配置同步已启用：轮询间隔 {} ms，事件加速={}，事件总线跨副本={}",
                properties.effectivePollIntervalMs(), properties.isEventBusEnabled(), clusterEventBus.isClusterWide());
    }

    /**
     * 取消事件订阅（容器关闭时）.
     */
    @PreDestroy
    public void unsubscribeClusterEvents() {
        final Disposable subscription = eventSubscription;
        if (subscription != null && !subscription.isDisposed()) {
            subscription.dispose();
        }
    }

    /**
     * 固定 tick：按配置的轮询间隔节流执行一次版本检查.
     *
     * <p>1 秒一次的空转只做两次基本类型比较，成本可以忽略；它换来的是
     * {@code enabled} 与 {@code poll-interval-ms} 的热改能力。</p>
     */
    @Scheduled(fixedDelay = TICK_INTERVAL_MS, initialDelayString = "${jairouter.cluster.config-sync.initial-delay-ms:30000}")
    public void tick() {
        if (!properties.isEnabled()) {
            return;
        }
        final long now = System.currentTimeMillis();
        if (now - lastSyncAt < properties.effectivePollIntervalMs()) {
            return;
        }
        lastSyncAt = now;
        forceSyncNow();
    }

    /**
     * 立即执行一次版本检查（不受轮询间隔节流），有变化就刷新本副本.
     *
     * <p>公开是为了让事件回调与测试都能直接触发，不必等调度器。</p>
     */
    public void forceSyncNow() {
        syncConfig();
        syncApiKeys();
    }

    /**
     * 检查主配置版本.
     */
    private void syncConfig() {
        final int latest = latestVersion(CONFIG_STORE_KEY);
        if (latest < 0) {
            return;
        }
        final int applied = appliedConfigVersion.get();
        if (applied == UNINITIALIZED) {
            appliedConfigVersion.compareAndSet(UNINITIALIZED, latest);
            log.debug("集群配置同步：建立主配置基线 version={}", latest);
            return;
        }
        if (latest <= applied) {
            return;
        }
        final ModelServiceRegistry registry = registryProvider.getIfAvailable();
        if (registry == null) {
            log.debug("集群配置同步：版本 {} → {}，但路由注册表尚未就绪，下个周期重试", applied, latest);
            return;
        }
        try {
            registry.refreshFromMergedConfig();
            appliedConfigVersion.set(latest);
            log.info("检测到其它副本修改了配置，本副本已刷新：version {} → {}", applied, latest);
        } catch (RuntimeException e) {
            log.warn("按新版本刷新本副本配置失败（version {} → {}），下个周期重试：{}",
                    applied, latest, e.getMessage());
        }
    }

    /**
     * 检查 API Key 配置版本.
     *
     * <p>API Key 有自己的 store key 与版本序列，与主配置分开判断——把两者绑在一起会让
     * 「只改了 API Key」也触发整份路由表重建。</p>
     */
    private void syncApiKeys() {
        final int latest = latestVersion(API_KEY_STORE_KEY);
        if (latest < 0) {
            return;
        }
        final int applied = appliedApiKeyVersion.get();
        if (applied == UNINITIALIZED) {
            appliedApiKeyVersion.compareAndSet(UNINITIALIZED, latest);
            log.debug("集群配置同步：建立 API Key 基线 version={}", latest);
            return;
        }
        if (latest <= applied) {
            return;
        }
        final ApiKeyService apiKeyService = apiKeyServiceProvider.getIfAvailable();
        if (apiKeyService == null) {
            log.debug("集群配置同步：API Key 版本 {} → {}，但服务尚未就绪，下个周期重试", applied, latest);
            return;
        }
        try {
            apiKeyService.refreshCacheFromSharedStore();
            appliedApiKeyVersion.set(latest);
            log.info("检测到其它副本修改了 API Key 配置，本副本缓存已刷新：version {} → {}", applied, latest);
        } catch (RuntimeException e) {
            log.warn("按新版本刷新 API Key 缓存失败（version {} → {}），下个周期重试：{}",
                    applied, latest, e.getMessage());
        }
    }

    /**
     * 读取某个 store key 的当前最大版本号.
     *
     * @param key 存储 key
     * @return 最大版本号；没有任何版本时返回 0；读取失败返回 -1（本周期跳过，不误判为变更）
     */
    private int latestVersion(final String key) {
        try {
            final List<Integer> versions = storeManager.getConfigVersions(key);
            return versions == null || versions.isEmpty()
                    ? 0
                    : versions.stream().mapToInt(Integer::intValue).max().orElse(0);
        } catch (RuntimeException e) {
            log.debug("读取配置版本失败（key={}），本周期跳过：{}", key, e.getMessage());
            return -1;
        }
    }

    /**
     * 当前已应用的主配置版本号（供测试与运维诊断）.
     *
     * @return 版本号；尚未建立基线时为 -1
     */
    public int getAppliedConfigVersion() {
        return appliedConfigVersion.get();
    }

    /**
     * 当前已应用的 API Key 配置版本号（供测试与运维诊断）.
     *
     * @return 版本号；尚未建立基线时为 -1
     */
    public int getAppliedApiKeyVersion() {
        return appliedApiKeyVersion.get();
    }

    /**
     * 强制下一轮重新建立基线（供配置回滚等场景调用）.
     */
    public void resetBaseline() {
        appliedConfigVersion.set(UNINITIALIZED);
        appliedApiKeyVersion.set(UNINITIALIZED);
    }
}
