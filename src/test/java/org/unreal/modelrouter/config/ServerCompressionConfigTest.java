package org.unreal.modelrouter.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 响应压缩的配置守卫（#1039）。
 *
 * <p>控制台产物包含三个超过 1MB 的 chunk，未压缩时冷启动刷新要全量传输约 3.3MB。
 * Spring Boot 4 的 {@code reactor-netty} {@code CompressionCustomizer} 对本项目的
 * WebFlux 栈是生效的（实测：带 {@code Accept-Encoding: gzip} 的 {@code .js} 响应返回
 * {@code Content-Encoding: gzip}；SSE 走 chunked + {@code text/event-stream}，不受
 * {@code min-response-size} 影响，实测无 {@code Content-Encoding}）。</p>
 *
 * <p>这里守的是「这个开关被人悄无声息地删掉」：一旦配置缺失，压缩会静默关闭，
 * 而任何功能测试都不会因此变红 —— 所以用守卫断言把它钉住。</p>
 */
class ServerCompressionConfigTest {

    @Test
    @DisplayName("COMP-001 通用服务配置开启了响应压缩")
    void compressionIsEnabledInCommonServerConfig() throws Exception {
        final List<PropertySource<?>> sources = new YamlPropertySourceLoader()
                .load("server", new ClassPathResource("config/common/server.yml"));

        assertThat(sources).as("config/common/server.yml 应能被解析").isNotEmpty();
        final PropertySource<?> source = sources.get(0);

        assertThat(source.getProperty("server.compression.enabled"))
                .as("server.compression.enabled 必须为 true，否则控制台静态资源不压缩")
                .isEqualTo(Boolean.TRUE);
        assertThat(source.getProperty("server.compression.min-response-size"))
                .as("min-response-size 应保持 1024，过大则小资源不再压缩")
                .isEqualTo(1024);
    }
}
