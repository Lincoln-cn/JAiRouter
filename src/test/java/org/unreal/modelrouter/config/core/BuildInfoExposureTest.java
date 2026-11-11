package org.unreal.modelrouter.config.core;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.info.BuildProperties;

/**
 * 守卫 `/actuator/info` 的 build 信息（issue #1077）。
 *
 * <p>为什么需要这个测试：SLA 工具链（{@code sla/}）要按契约里的 {@code targetRange} 判定
 * 「工具与当前网关是否兼容」。它跑在客户机器上，拿不到 jar 与镜像标签，唯一能问的接口就是
 * {@code /actuator/info} 的 {@code build.version}。而这份信息由 {@code pom.xml} 里
 * {@code spring-boot-maven-plugin} 的 {@code build-info} goal 生成
 * {@code META-INF/build-info.properties}。一旦有人删掉那个 goal，工具就会退化成
 * 「版本未知 → 拒绝给出判据」，而这种失效是**静默**的：应用照常启动、接口照常返回，
 * 只有 SLA 自检会红，而且报的是「无法确认版本」，看不出是构建配置被人拿掉了。</p>
 *
 * <p>本测试直接读 classpath 上的 properties 文件，因此「删掉 build-info goal」会让它立刻变红。</p>
 */
@DisplayName("构建信息暴露契约（/actuator/info 的 build.version）")
class BuildInfoExposureTest {

    private static final String BUILD_INFO = "META-INF/build-info.properties";

    private Properties loadBuildInfo() throws IOException {
        try (InputStream in = getClass().getClassLoader().getResourceAsStream(BUILD_INFO)) {
            assertThat(in)
                .as("classpath 上缺少 %s：pom.xml 的 spring-boot-maven-plugin 必须保留 "
                    + "build-info goal，否则 SLA 工具链无法确认网关版本", BUILD_INFO)
                .isNotNull();
            Properties properties = new Properties();
            properties.load(in);
            return properties;
        }
    }

    @Test
    @DisplayName("build-info.properties 在 classpath 上且带版本号")
    void buildInfoIsPresentWithVersion() throws IOException {
        Properties properties = loadBuildInfo();

        assertThat(properties.getProperty("build.version"))
            .as("build.version 必须是 x.y.z 形式，SLA 的版本范围判定依赖它")
            .isNotBlank()
            .matches("\\d+\\.\\d+\\.\\d+.*");
    }

    @Test
    @DisplayName("BuildProperties 能解析出版本（/actuator/info 的 build 段取数路径）")
    void buildPropertiesExposeVersion() throws IOException {
        Properties properties = loadBuildInfo();

        // BuildProperties 绑定的是去掉 build. 前缀之后的键（ConfigurationProperties("build")），
        // 所以这里先按 Boot 的方式剥掉前缀，再确认真正的取数路径能读到版本。
        Properties stripped = new Properties();
        for (String key : properties.stringPropertyNames()) {
            stripped.setProperty(key.replaceFirst("^build\\.", ""), properties.getProperty(key));
        }

        BuildProperties buildProperties = new BuildProperties(stripped);

        assertThat(buildProperties.getVersion())
            .as("actuator 的 build 段就来自这里；为空意味着 /actuator/info 不会暴露版本")
            .isEqualTo(properties.getProperty("build.version"));
    }
}
