package org.unreal.modelrouter.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.stereotype.Controller;

import java.io.IOException;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@code springdoc.packages-to-scan} 必须真的能扫到控制器（issue #457）。
 *
 * <p><b>缺陷史</b>：该键曾经是 {@code org.unreal.modelrouter.controller} —— 这个包在整个代码库里
 * **不存在**（控制器都在 {@code router/}、{@code monitor/}、{@code auth/}、{@code config/}、
 * {@code persistence/} 各自的 {@code controller} 子包下）。springdoc 于是扫了个空包：
 * {@code /v3/api-docs} 返回 {@code {"paths":{},"components":{}}}（实测 180 字节、0 个路径）
 * 却**照样返回 200** ⇒ Swagger UI 打开后没有任何接口，而**没有任何门闩会发现**
 * （它既不报错、也不影响启动、更不影响其它测试）。改成命名空间根包后实测 312 个路径。</p>
 *
 * <p><b>判据</b>：从真实 {@code application-dev.yml} 读该键，然后扫 classpath 上带
 * {@code @RestController}/{@code @Controller} 的类，断言「至少有一个控制器落在该包（或其子包）下」。
 * 纯元数据判定，**不启动 Spring 上下文**，因此毫秒级且不受 profile/DB 影响。</p>
 *
 * <p><b>为什么不是断言 {@code /v3/api-docs} 的 paths 数量</b>：那需要起应用（或起完整上下文）才能验，
 * 而本仓的快闸要求「秒级、纯标准库、不联网」。本类是对「配置指向空包」这个**唯一已知成因**的
 * 低成本代理判据；它能挡住本次的错法，但**不能**证明规范内容完整 —— 这一点必须说清，
 * 否则下一轮会有人把它当成「OpenAPI 正确」的证据。</p>
 *
 * <p>回归守护见 {@link #bogusPackageIsRejected()}：把旧值喂进同一个判定函数必须**被判红**。</p>
 */
@DisplayName("springdoc 扫描范围必须含控制器（issue #457）")
class SpringdocScanScopeGuardTest {

    private static final String KEY = "springdoc.packages-to-scan";

    @Test
    @DisplayName("反空转：classpath 上确实能找到控制器")
    void classpathScanActuallyFindsControllers() {
        Set<String> packages = controllerPackages();
        assertTrue(packages.size() >= 10,
                "应当扫描到 10 个以上的控制器包（实测 15），实际 " + packages.size()
                        + " —— 扫描逻辑失效时本测试必须红");
        assertTrue(packages.stream().anyMatch(p -> p.startsWith("org.unreal.modelrouter.")),
                "控制器包应当位于 org.unreal.modelrouter 之下");
    }

    @Test
    @DisplayName("application-dev.yml 里的 packages-to-scan 必须真的包含控制器")
    void configuredScanScopeContainsControllers() {
        String configured = configuredPackagesToScan();
        assertFalse(configured.isBlank(), "application-dev.yml 应当配置 " + KEY);

        assertTrue(coversAtLeastOneController(configured, controllerPackages()),
                "springdoc.packages-to-scan = `" + configured + "` 扫不到任何控制器 ⇒ "
                        + "/v3/api-docs 会是空文档（paths=0）却仍返回 200，Swagger UI 里什么都没有。"
                        + "控制器实际分布在：" + sample(controllerPackages()));
    }

    @Test
    @DisplayName("反证：把历史错误值 org.unreal.modelrouter.controller 喂进来必须被判红")
    void bogusPackageIsRejected() {
        assertFalse(coversAtLeastOneController("org.unreal.modelrouter.controller", controllerPackages()),
                "旧的错误值必须被判红，否则本类的判据没有检测力（它正是 issue #457 的成因）");
        // 真正的根包必须通过 —— 否则「全判红」也能通过上面那条反证
        assertTrue(coversAtLeastOneController("org.unreal.modelrouter", controllerPackages()),
                "命名空间根包应当通过");
    }

    // ------------------------------------------------------------------ 逻辑

    /** 配置的包是否是某个控制器包的前缀（含相等）。 */
    static boolean coversAtLeastOneController(final String configuredPackage, final Set<String> controllerPackages) {
        String p = configuredPackage.trim();
        if (p.endsWith(".*")) {
            p = p.substring(0, p.length() - 2);
        }
        final String prefix = p;
        return controllerPackages.stream().anyMatch(c -> c.equals(prefix) || c.startsWith(prefix + "."));
    }

    private static String sample(final Set<String> packages) {
        return packages.stream().limit(6).collect(Collectors.joining(", ")) + " …";
    }

    /** 真实 dev profile 里的配置值。 */
    private static String configuredPackagesToScan() {
        StandardEnvironment env = new StandardEnvironment();
        MutablePropertySources sources = env.getPropertySources();
        YamlPropertySourceLoader loader = new YamlPropertySourceLoader();
        try {
            Resource resource = new PathMatchingResourcePatternResolver()
                    .getResource("classpath:application-dev.yml");
            assertTrue(resource.exists(), "application-dev.yml 应当在 classpath 上");
            loader.load(resource.getFilename(), resource).forEach(sources::addFirst);
        } catch (IOException e) {
            throw new IllegalStateException("读取 application-dev.yml 失败", e);
        }
        String value = env.getProperty(KEY);
        return value == null ? "" : value;
    }

    /** classpath 上所有带 @RestController/@Controller 的类的包名（Spring 标准扫描器）。 */
    private static Set<String> controllerPackages() {
        Set<String> packages = new TreeSet<>();
        ClassPathScanningCandidateComponentProvider provider =
                new ClassPathScanningCandidateComponentProvider(false);
        provider.addIncludeFilter(new AnnotationTypeFilter(RestController.class));
        provider.addIncludeFilter(new AnnotationTypeFilter(Controller.class));
        for (BeanDefinition candidate : provider.findCandidateComponents("org.unreal.modelrouter")) {
            String className = candidate.getBeanClassName();
            if (className == null || className.contains("$")) {
                continue;
            }
            int lastDot = className.lastIndexOf('.');
            if (lastDot > 0) {
                packages.add(className.substring(0, lastDot));
            }
        }
        return packages;
    }
}
