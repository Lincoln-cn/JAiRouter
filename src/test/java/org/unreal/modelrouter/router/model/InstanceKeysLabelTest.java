package org.unreal.modelrouter.router.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link InstanceKeys#label} 的行为契约。
 *
 * <p>同名实例是「同一模型的多个副本」的正常形态（请求体的 {@code model} 就按 name 匹配），
 * 单看 name 无法区分副本 —— 这些用例守的就是「标签能区分副本」这件事。
 *
 * @since 3.6.1
 */
@DisplayName("InstanceKeys.label：指标与日志的实例标识")
class InstanceKeysLabelTest {

    private static ModelRouterProperties.ModelInstance instance(
            final String name, final String baseUrl) {
        final ModelRouterProperties.ModelInstance instance = new ModelRouterProperties.ModelInstance();
        instance.setName(name);
        instance.setBaseUrl(baseUrl);
        return instance;
    }

    @Test
    @DisplayName("同名不同主机必须得到不同标签")
    void sameNameDifferentHostDiffers() {
        final String a = InstanceKeys.label(instance("qwen3:4b", "http://10.0.0.1:8000"));
        final String b = InstanceKeys.label(instance("qwen3:4b", "http://10.0.0.2:8000"));

        assertThat(a).isEqualTo("qwen3:4b@10.0.0.1:8000");
        assertThat(b).isEqualTo("qwen3:4b@10.0.0.2:8000");
        assertThat(a).as("这正是本改动的目的：两副本在面板上可区分").isNotEqualTo(b);
    }

    @Test
    @DisplayName("只有实例名时退化为实例名")
    void nameOnly() {
        assertThat(InstanceKeys.label(instance("qwen3:4b", null))).isEqualTo("qwen3:4b");
        assertThat(InstanceKeys.label(instance("qwen3:4b", "  "))).isEqualTo("qwen3:4b");
    }

    @Test
    @DisplayName("只有 base-url 时退化为 host:port")
    void hostOnly() {
        assertThat(InstanceKeys.label(instance(null, "https://api.example.com:8443/v1")))
                .isEqualTo("api.example.com:8443");
    }

    @Test
    @DisplayName("都缺失时给出占位符而不是 null 或空串")
    void unknownWhenNothingUsable() {
        assertThat(InstanceKeys.label(null)).isEqualTo(InstanceKeys.UNKNOWN_IDENTIFIER);
        assertThat(InstanceKeys.label(instance("  ", "   "))).isEqualTo(InstanceKeys.UNKNOWN_IDENTIFIER);
    }

    @Test
    @DisplayName("解析不了的 base-url 原样保留，不抛异常")
    void unparsableBaseUrlIsKeptVerbatim() {
        assertThat(InstanceKeys.label(instance("m", "not a url"))).isEqualTo("m@not a url");
    }

    @Test
    @DisplayName("带路径与查询串的 base-url 只取 authority")
    void pathAndQueryDropped() {
        assertThat(InstanceKeys.label(instance("m", "http://host:8080/proxy/v1?x=1")))
                .isEqualTo("m@host:8080");
    }
}
