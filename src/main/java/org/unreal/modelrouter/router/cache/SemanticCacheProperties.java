package org.unreal.modelrouter.router.cache;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * 语义缓存的配置面（{@code jairouter.semantic-cache.*}）.
 *
 * <p>语义缓存分三层，隔离强度递减：</p>
 * <ul>
 *   <li><b>L1 精确缓存</b>（{@link ResponseCacheService}）—— 按「租户 + 请求体」精确键命中；</li>
 *   <li><b>L2 语义租户内</b>（本配置的 {@code tenant-scope-enabled}）—— 同一租户内的相似请求复用，
 *       键为 {@code t|<apiKeyId>|<user>|<model>}；</li>
 *   <li><b>L3 语义公共</b>（{@code public-scope-enabled}）—— 跨租户复用，键为 {@code p|<model>}。
 *       <b>默认关闭</b>，开启需要额外满足七条准入规则与租户白名单。</li>
 * </ul>
 *
 * <p><b>为什么 L2 默认开、L3 默认关</b>：L2 的读者就是写入者本人，复用不跨越信任边界；
 * L3 的读者是任意租户，一个措辞相似但语义不同的问题会拿到别人的答案，且答案里可能有对方的数据。
 * 这条边界必须由使用者显式打开，而不是靠阈值暗示。</p>
 *
 * <p><b>与会话身份的关系</b>：{@code user} 字段由客户端自报，<b>不构成安全边界</b>，只用于把
 * L2 的范围收得更窄（同一 API Key 下不同终端用户互不可见）。缺省时退化为「该 API Key 的全部请求
 * 共享一个 L2 桶」。</p>
 *
 * @author JAiRouter Team
 * @since 3.7.1
 */
@Component
@ConfigurationProperties(prefix = "jairouter.semantic-cache")
public class SemanticCacheProperties {

    /**
     * 是否启用语义缓存（L2）。
     *
     * <p>默认开启 —— 但只在请求满足准入条件且不含敏感内容时才会真正命中/写入。</p>
     */
    private boolean enabled = true;

    /** 是否启用 L2（语义租户内）。关闭后只剩 L1 精确缓存。 */
    private boolean tenantScopeEnabled = true;

    /** 是否启用 L3（语义公共）。<b>默认关闭</b>，见类注释。 */
    private boolean publicScopeEnabled = false;

    /**
     * 允许进入 L3 的租户（API Key id）。
     *
     * <p>为空表示「谁都不允许」—— 即便 {@code public-scope-enabled=true}，也必须在此显式列出。</p>
     */
    private List<String> publicAllowedTenants = new ArrayList<>();

    /**
     * 允许进入 L3 的请求文本最大长度（归一化后的字符数）。
     *
     * <p>短问题的语义边界清晰（「北京天气」就是「北京天气」），长问题的相似不等于等价。</p>
     */
    private int maxPublicChars = 128;

    /** 每个 scope 保留的最大条目数（超出后淘汰最旧的）。 */
    private int maxEntriesPerModel = 512;

    /** 全部 scope 的条目总数上限（防止高基数租户把内存撑爆）。 */
    private int globalMaxEntries = 5000;

    /** 条目存活时长。 */
    private Duration ttl = Duration.ofMinutes(10);

    /**
     * SimHash 的最大汉明距离：距离越小越严格。
     *
     * <p>64 位指纹上，距离 3 约等于「极相似」、距离 8 已经会误命中。默认取 3。</p>
     */
    private int maxHammingDistance = 3;

    /** 参与相似度计算的文本最短长度；过短的请求（如「你好」）不做语义命中。 */
    private int minTextLength = 8;

    /**
     * 是否缓存带 {@code temperature>0} 的请求（默认否）.
     *
     * <p>非确定性响应的语义复用在语义上就不成立：同一问题本就该得到不同答案。</p>
     */
    private boolean allowNonDeterministic = false;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(final boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isTenantScopeEnabled() {
        return tenantScopeEnabled;
    }

    public void setTenantScopeEnabled(final boolean tenantScopeEnabled) {
        this.tenantScopeEnabled = tenantScopeEnabled;
    }

    public boolean isPublicScopeEnabled() {
        return publicScopeEnabled;
    }

    public void setPublicScopeEnabled(final boolean publicScopeEnabled) {
        this.publicScopeEnabled = publicScopeEnabled;
    }

    public List<String> getPublicAllowedTenants() {
        return publicAllowedTenants;
    }

    public void setPublicAllowedTenants(final List<String> publicAllowedTenants) {
        this.publicAllowedTenants = publicAllowedTenants == null ? new ArrayList<>() : publicAllowedTenants;
    }

    public int getMaxPublicChars() {
        return maxPublicChars;
    }

    public void setMaxPublicChars(final int maxPublicChars) {
        this.maxPublicChars = maxPublicChars;
    }

    public int getMaxEntriesPerModel() {
        return maxEntriesPerModel;
    }

    public void setMaxEntriesPerModel(final int maxEntriesPerModel) {
        this.maxEntriesPerModel = maxEntriesPerModel;
    }

    public int getGlobalMaxEntries() {
        return globalMaxEntries;
    }

    public void setGlobalMaxEntries(final int globalMaxEntries) {
        this.globalMaxEntries = globalMaxEntries;
    }

    public Duration getTtl() {
        return ttl;
    }

    public void setTtl(final Duration ttl) {
        this.ttl = ttl;
    }

    public int getMaxHammingDistance() {
        return maxHammingDistance;
    }

    public void setMaxHammingDistance(final int maxHammingDistance) {
        this.maxHammingDistance = maxHammingDistance;
    }

    public int getMinTextLength() {
        return minTextLength;
    }

    public void setMinTextLength(final int minTextLength) {
        this.minTextLength = minTextLength;
    }

    public boolean isAllowNonDeterministic() {
        return allowNonDeterministic;
    }

    public void setAllowNonDeterministic(final boolean allowNonDeterministic) {
        this.allowNonDeterministic = allowNonDeterministic;
    }

    /**
     * Redis 共享后端配置（issue #1002）。
     *
     * <p>开启后 L2/L3 的候选集存入 Redis（多副本共享，命中率不再按副本数摊薄）；
     * 关闭（默认）保持进程内内存后端。该开关已登记进
     * {@code MultiReplicaSharedStateGate}：多副本部署且未开启时会给出告警。</p>
     */
    private final Redis redis = new Redis();

    public Redis getRedis() {
        return redis;
    }

    /**
     * Redis 后端子配置（{@code jairouter.semantic-cache.redis.*}）。
     *
     * @since v3.8.0
     */
    public static class Redis {

        /** 是否启用 Redis 共享后端（多副本部署时建议开启）。 */
        private boolean enabled = false;

        /** 单次 Redis 操作的等待上限（同步桥接 block 的超时）。 */
        private java.time.Duration timeout = java.time.Duration.ofMillis(200);

        /** 按租户清除时 SCAN 的每批数量（禁用 KEYS，走 SCAN）。 */
        private int scanCount = 100;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(final boolean enabled) {
            this.enabled = enabled;
        }

        public java.time.Duration getTimeout() {
            return timeout;
        }

        public void setTimeout(final java.time.Duration timeout) {
            this.timeout = timeout;
        }

        public int getScanCount() {
            return scanCount;
        }

        public void setScanCount(final int scanCount) {
            this.scanCount = scanCount;
        }
    }
}
