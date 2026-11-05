package org.unreal.modelrouter.router.cache;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * 语义缓存的配置面（{@code jairouter.semantic-cache.*}）.
 *
 * <p><b>默认关闭</b>（{@code enabled=false}）。它是在响应缓存的<b>精确键</b>之外额外提供的一层
 * 「近似请求」复用：同一个模型下，若两个请求的消息文本足够相似，直接回放前一次的响应。</p>
 *
 * <p><b>为什么默认关闭</b>：语义命中意味着「不同但相似的提问」拿到同一个答案，这在客服问答类
 * 场景是收益、在需要逐字准确的场景是缺陷。是否开启、阈值多宽松必须由使用者按业务决定。</p>
 *
 * <p><b>与响应缓存的关系</b>：两者互不替代。响应缓存命中是「同一个请求」，语义缓存命中是
 * 「相似但不相同的请求」；语义缓存<b>不</b>写入响应缓存的存储，也不改变
 * {@link CacheStore} 的契约。</p>
 *
 * @author JAiRouter Team
 * @since 3.7.1
 */
@Component
@ConfigurationProperties(prefix = "jairouter.semantic-cache")
public class SemanticCacheProperties {

    /** 是否启用语义缓存（默认关闭）。 */
    private boolean enabled = false;

    /** 每个模型保留的最大条目数（超出后淘汰最旧的）。 */
    private int maxEntriesPerModel = 512;

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

    public int getMaxEntriesPerModel() {
        return maxEntriesPerModel;
    }

    public void setMaxEntriesPerModel(final int maxEntriesPerModel) {
        this.maxEntriesPerModel = maxEntriesPerModel;
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
}
