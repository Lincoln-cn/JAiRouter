package org.unreal.modelrouter.auth.security.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.EnumerablePropertySource;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.env.PropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@code jairouter.security.performance} 子树「不得复活」守卫（issue #320）。
 *
 * <p>起因：这棵树在 4 个配置文件里共 21 个叶子键，**全仓没有任何绑定类，也没有
 * {@code @Value} / {@code @ConditionalOnProperty} / {@code @Scheduled} 读它** ——
 * 运维照着 yml 调参不会报错、也不会发生任何事。其中 {@code performance.cache.redis.*} 的误导性最强：
 * 真正生效的 Redis 缓存开关是 {@code jairouter.security.cache.redis.enabled}
 * （{@code CacheConfiguration:32} / {@code RedisApiKeyCache:23} / {@code InMemoryApiKeyCache:25} /
 * 多副本自检 {@code MultiReplicaSharedStateGate:59}），两者只差一段 {@code performance}，
 * 而真开关在**仓库任何 yml 里都没被设置过**。</p>
 *
 * <p>整棵树已按「配置面必须与实现一一对应」删除（PR 见 issue #320）。本测试把
 * 「不许再出现」钉住，并**额外断言扫描器本身有检出能力** —— 否则一旦枚举 yml 的逻辑失效，
 * 「找不到就等于通过」会静默成立（同 {@code AuditConfigBindingGuardTest} 的取向）。</p>
 *
 * <p>若将来真要实现这棵性能调优树，应当**同时**改这个测试与 {@code AuditConfig}/
 * {@code SecurityProperties} 侧的绑定类，而不是只把 yml 加回来。</p>
 */
@DisplayName("jairouter.security.performance 子树已被移除，不得再出现在任何 yml 里（issue #320）")
class SecurityPerformanceTreeGuardTest {

    private static final String REMOVED_PREFIX = "jairouter.security.performance";
    private static final String LIVE_PREFIX = "jairouter.security.audit";

    @Test
    @DisplayName("任何真实 yml 里都不得再有 security.performance 的键")
    void removedTreeDoesNotComeBack() {
        StandardEnvironment environment = loadAllYml();

        Set<String> stale = propertyNamesStartingWith(environment, REMOVED_PREFIX);
        assertTrue(stale.isEmpty(),
                "jairouter.security.performance 树没有任何绑定类（见 issue #320），不应再出现在 yml 里；"
                        + "若确实要重新引入，请先补上绑定类与消费方，并同步修改本测试。实际命中：" + stale);
    }

    @Test
    @DisplayName("扫描器本身有效：同一手法能检出仍然存在的 security.audit 前缀")
    void scannerActuallyDetectsThings() {
        StandardEnvironment environment = loadAllYml();

        Set<String> live = propertyNamesStartingWith(environment, LIVE_PREFIX);
        assertTrue(!live.isEmpty(),
                "扫描器应当能检出 " + LIVE_PREFIX + "（它仍有键）—— 若这里为空，说明枚举 yml 的逻辑坏了，"
                        + "上一条断言会因此静默通过");
    }

    /** 收集 environment 里以给定前缀开头的属性名 */
    private static Set<String> propertyNamesStartingWith(final StandardEnvironment environment,
                                                         final String prefix) {
        Set<String> names = new TreeSet<>();
        for (PropertySource<?> source : environment.getPropertySources()) {
            if (!(source instanceof EnumerablePropertySource)) {
                continue;
            }
            for (String name : ((EnumerablePropertySource<?>) source).getPropertyNames()) {
                if (name.startsWith(prefix)) {
                    names.add(name);
                }
            }
        }
        return names;
    }

    /**
     * 读 classpath 上**真实**的 yml。
     *
     * <p>⚠️ 本地排查提示：Maven 资源插件只拷贝、不删除「源文件已删」的旧副本，
     * 所以刚删掉某个 yml 后 {@code target/classes} 下可能残留它，导致本测试报出已不存在的键；
     * 先 {@code mvn clean} 再看。CI 是干净构建，没有这个问题。</p>
     */
    private static StandardEnvironment loadAllYml() {
        StandardEnvironment environment = new StandardEnvironment();
        MutablePropertySources sources = environment.getPropertySources();
        YamlPropertySourceLoader loader = new YamlPropertySourceLoader();

        List<Resource> resources = new ArrayList<>();
        try {
            PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
            resources.addAll(Arrays.asList(resolver.getResources("classpath*:application*.yml")));
            resources.addAll(Arrays.asList(resolver.getResources("classpath*:config/**/*.yml")));
        } catch (IOException e) {
            throw new IllegalStateException("枚举 classpath 上的 yml 失败", e);
        }

        assertTrue(resources.size() > 5,
                "应当枚举到多个 yml（含 config/ 下的模块配置），实际 " + resources.size() + " 个");

        for (Resource resource : resources) {
            try {
                loader.load(resource.getFilename(), resource).forEach(sources::addFirst);
            } catch (IOException e) {
                throw new IllegalStateException("读取 " + resource.getDescription() + " 失败", e);
            }
        }
        return environment;
    }
}
