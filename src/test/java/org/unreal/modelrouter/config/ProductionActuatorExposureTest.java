package org.unreal.modelrouter.config;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

/**
 * 生产环境 Actuator 暴露面的守卫测试.
 *
 * <p>暴露面是「排障能力」与「攻击面」的取舍，改一次就影响所有生产部署，因此钉成断言：
 * <b>必须</b>包含 {@code threaddump}（进程卡死、死锁、线程泄漏的唯一现场取证手段，issue #1081），
 * <b>不得</b>包含 {@code heapdump}（单次产出可达数百 MB，容易写满磁盘）。</p>
 *
 * <p>注意这三个端点仍受安全链约束：{@code SecurityConfiguration} 对 {@code /actuator/**}
 * 要求 {@code ROLE_ADMIN}，未认证请求在过滤链就被拒绝 —— 暴露不等于公开。</p>
 */
class ProductionActuatorExposureTest {

    private static final Path PROD_YML = Path.of("src/main/resources/application-prod.yml");

    @SuppressWarnings("unchecked")
    private static String prodExposureIncludes() throws Exception {
        try (InputStream in = Files.newInputStream(PROD_YML)) {
            final Map<String, Object> root = new Yaml().load(in);
            final Map<String, Object> management = (Map<String, Object>) root.get("management");
            final Map<String, Object> endpoints = (Map<String, Object>) management.get("endpoints");
            final Map<String, Object> web = (Map<String, Object>) endpoints.get("web");
            final Map<String, Object> exposure = (Map<String, Object>) web.get("exposure");
            return String.valueOf(exposure.get("include"));
        }
    }

    @Test
    @DisplayName("ACT-001: 生产环境暴露 threaddump，否则卡死与死锁无法取证")
    void threaddumpIsExposedInProduction() throws Exception {
        final String include = prodExposureIncludes();
        assertTrue(include.contains("threaddump"),
                "prod 必须暴露 threaddump（进程卡死的唯一现场取证手段），当前: " + include);
        assertTrue(include.contains("health") && include.contains("prometheus"),
                "health 与 prometheus 不能因为加 threaddump 而丢失，当前: " + include);
    }

    @Test
    @DisplayName("ACT-002: 生产环境不暴露 heapdump，避免一次请求写满磁盘")
    void heapdumpIsNotExposedInProduction() throws Exception {
        final String include = prodExposureIncludes();
        assertFalse(include.contains("heapdump"),
                "heapdump 产出可达数百 MB，不应默认暴露，当前: " + include);
    }
}
