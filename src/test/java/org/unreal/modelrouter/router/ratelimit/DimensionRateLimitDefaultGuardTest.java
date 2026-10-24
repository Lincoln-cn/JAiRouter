package org.unreal.modelrouter.router.ratelimit;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 客户端维度限流的默认值守卫（#623）.
 *
 * <p>该维度在 v3.5.2 之前「配置在、文档写、实现完整，但主链路从不调用」；接线后如果仓库自带的
 * yml 仍是 {@code client-ip-enable: true}，就等于一次静默的全局行为变更：集群 + 无反代场景下
 * 所有客户端共用出口 IP，会被合并成同一个桶。所以默认值必须显式为零，并由本测试守住。</p>
 *
 * <p>断言是双向的：既要求「没有任何 true」，也要求「确实扫到了 client-ip-enable 键」——
 * 否则把键删光也会让本测试静默通过。</p>
 */
@DisplayName("客户端维度限流默认关闭守卫（#623）")
class DimensionRateLimitDefaultGuardTest {

    private static final Pattern ENABLED =
            Pattern.compile("client-ip-enable\\s*:\\s*true", Pattern.CASE_INSENSITIVE);

    private static final Pattern ANY_KEY =
            Pattern.compile("client-ip-enable\\s*:", Pattern.CASE_INSENSITIVE);

    @Test
    @DisplayName("仓库自带 yml 不得把 client-ip-enable 设为 true")
    void noBundledYamlEnablesClientDimensionLimit() throws IOException {
        PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
        List<Resource> resources = new ArrayList<>();
        collect(resolver, "classpath*:config/**/*.yml", resources);
        collect(resolver, "classpath*:application*.yml", resources);

        assertTrue(resources.size() > 10,
                "应扫描到 config 下的多份 yml，实际 " + resources.size());

        List<String> offenders = new ArrayList<>();
        int keyOccurrences = 0;
        for (Resource resource : resources) {
            String content = resource.getContentAsString(StandardCharsets.UTF_8);
            Matcher anyMatcher = ANY_KEY.matcher(content);
            while (anyMatcher.find()) {
                keyOccurrences++;
            }
            if (ENABLED.matcher(content).find()) {
                offenders.add(resource.getFilename());
            }
        }

        assertTrue(keyOccurrences > 0,
                "扫描到的 yml 里没有任何 client-ip-enable 键：扫描逻辑可能已失效，守卫会静默通过");
        assertTrue(offenders.isEmpty(),
                "以下 yml 把 client-ip-enable 设成了 true（接线后等于静默改变默认行为）：" + offenders);
    }

    private static void collect(final PathMatchingResourcePatternResolver resolver,
                                final String pattern,
                                final List<Resource> target) throws IOException {
        for (Resource resource : resolver.getResources(pattern)) {
            if (resource.isReadable() && !target.contains(resource)) {
                target.add(resource);
            }
        }
    }
}
