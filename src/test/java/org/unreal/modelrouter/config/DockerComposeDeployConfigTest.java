package org.unreal.modelrouter.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Docker 部署静态配置回归测试。
 *
 * <p>锁定三处历史缺陷（replicas=1 下同样会导致应用不可用 / 数据丢失）：
 * <ol>
 *   <li>prod/dev compose 端口映射必须是 宿主9900→容器8080（不是 9900:9900）；</li>
 *   <li>healthcheck 必须探容器内 8080；</li>
 *   <li>H2 数据卷必须挂到 /app/data（不是把 r2dbc URL 片段当目录名）。</li>
 * </ol>
 */
@DisplayName("Docker 部署配置回归测试")
class DockerComposeDeployConfigTest {

    private static final Path PROD = Path.of("docker-compose.prod.yml");
    private static final Path DEV = Path.of("docker-compose.dev.yml");
    private static final Path BASE = Path.of("docker-compose.yml");

    @SuppressWarnings("unchecked")
    private static Map<String, Object> loadService(Path file, String serviceName) throws Exception {
        assertTrue(Files.exists(file), file + " 应存在");
        try (InputStream in = Files.newInputStream(file)) {
            Map<String, Object> compose = new Yaml().load(in);
            assertNotNull(compose, file + " 应能被 YAML 解析");
            Map<String, Object> services = (Map<String, Object>) compose.get("services");
            assertNotNull(services, file + " 应包含 services");
            Map<String, Object> service = (Map<String, Object>) services.get(serviceName);
            assertNotNull(service, file + " 应包含服务 " + serviceName);
            return service;
        }
    }

    @SuppressWarnings("unchecked")
    private static List<String> stringList(Map<String, Object> service, String key) {
        Object value = service.get(key);
        assertNotNull(value, "服务应包含 " + key);
        assertTrue(value instanceof List, key + " 应为列表");
        return ((List<Object>) value).stream().map(String::valueOf).toList();
    }

    @Test
    @DisplayName("缺陷1: prod 端口映射应为 9900:8080（宿主9900→容器8080）")
    void testProdPortMapping() throws Exception {
        List<String> ports = stringList(loadService(PROD, "jairouter"), "ports");
        assertTrue(ports.contains("9900:8080"),
                "prod 端口映射必须是 9900:8080，当前: " + ports);
        assertFalse(ports.contains("9900:9900"),
                "禁止 9900:9900：容器只监听 8080，该映射导致应用从宿主不可访问");
    }

    @Test
    @DisplayName("缺陷1: dev 端口映射应为 9900:8080")
    void testDevPortMapping() throws Exception {
        List<String> ports = stringList(loadService(DEV, "jairouter-dev"), "ports");
        assertTrue(ports.contains("9900:8080"),
                "dev 端口映射必须是 9900:8080，当前: " + ports);
        assertFalse(ports.contains("9900:9900"),
                "禁止 9900:9900：容器只监听 8080");
    }

    @Test
    @DisplayName("基线 docker-compose.yml 端口映射应为 9900:8080")
    void testBasePortMapping() throws Exception {
        List<String> ports = stringList(loadService(BASE, "jairouter"), "ports");
        assertTrue(ports.contains("9900:8080"), "基线 compose 端口映射应为 9900:8080，当前: " + ports);
    }

    @Test
    @DisplayName("缺陷2: prod healthcheck 应探容器内 8080")
    void testProdHealthcheckPort() throws Exception {
        Map<String, Object> service = loadService(PROD, "jairouter");
        @SuppressWarnings("unchecked")
        Map<String, Object> healthcheck = (Map<String, Object>) service.get("healthcheck");
        assertNotNull(healthcheck, "prod 应配置 healthcheck");
        @SuppressWarnings("unchecked")
        List<String> test = ((List<Object>) healthcheck.get("test")).stream().map(String::valueOf).toList();
        String joined = String.join(" ", test);
        assertTrue(joined.contains("http://localhost:8080/actuator/health"),
                "healthcheck 必须访问容器内 8080，当前: " + joined);
        assertFalse(joined.contains("localhost:9900"),
                "healthcheck 不得访问 9900：那是宿主映射端口，容器内不可达");
    }

    @Test
    @DisplayName("缺陷2: dev healthcheck 应探容器内 8080")
    void testDevHealthcheckPort() throws Exception {
        Map<String, Object> service = loadService(DEV, "jairouter-dev");
        @SuppressWarnings("unchecked")
        Map<String, Object> healthcheck = (Map<String, Object>) service.get("healthcheck");
        assertNotNull(healthcheck, "dev 应配置 healthcheck");
        @SuppressWarnings("unchecked")
        List<String> test = ((List<Object>) healthcheck.get("test")).stream().map(String::valueOf).toList();
        String joined = String.join(" ", test);
        assertTrue(joined.contains("http://localhost:8080/actuator/health"),
                "healthcheck 必须访问容器内 8080，当前: " + joined);
        assertFalse(joined.contains("localhost:9900"),
                "healthcheck 不得访问 9900");
    }

    @Test
    @DisplayName("缺陷3: prod 数据卷应挂载 ./data:/app/data（H2 落盘路径）")
    void testProdDataVolume() throws Exception {
        List<String> volumes = stringList(loadService(PROD, "jairouter"), "volumes");
        assertTrue(volumes.contains("./data:/app/data"),
                "H2 数据卷必须是 ./data:/app/data，当前: " + volumes);
        assertFalse(volumes.stream().anyMatch(v -> v.contains("r2dbc:h2:file")),
                "禁止把 r2dbc URL 片段当容器内目录名，数据库会写进容器可写层导致重建丢数据");
    }

    @Test
    @DisplayName("缺陷3: dev 数据卷应挂载 ./data:/app/data")
    void testDevDataVolume() throws Exception {
        List<String> volumes = stringList(loadService(DEV, "jairouter-dev"), "volumes");
        assertTrue(volumes.contains("./data:/app/data"),
                "H2 数据卷必须是 ./data:/app/data，当前: " + volumes);
        assertFalse(volumes.stream().anyMatch(v -> v.contains("r2dbc:h2:file")),
                "禁止把 r2dbc URL 片段当容器内目录名");
    }

    @Test
    @DisplayName("基线 docker-compose.yml 数据卷应为 ./data:/app/data")
    void testBaseDataVolume() throws Exception {
        List<String> volumes = stringList(loadService(BASE, "jairouter"), "volumes");
        assertTrue(volumes.contains("./data:/app/data"), "基线 compose 数据卷应为 ./data:/app/data，当前: " + volumes);
    }

    @Test
    @DisplayName("优雅停机: stop_grace_period 必须大于 shutdown 阶段超时，否则 Docker 先 SIGKILL")
    void testStopGracePeriodCoversShutdownTimeout() throws Exception {
        for (Map.Entry<Path, String> entry : Map.of(PROD, "jairouter", DEV, "jairouter-dev").entrySet()) {
            Map<String, Object> service = loadService(entry.getKey(), entry.getValue());
            Object grace = service.get("stop_grace_period");
            assertNotNull(grace, entry.getKey() + " 应配置 stop_grace_period，否则默认 10s 会截断 30s 优雅停机");
            String graceStr = String.valueOf(grace);
            // 约定 35s > spring.lifecycle.timeout-per-shutdown-phase: 30s
            assertTrue(graceStr.startsWith("35s") || graceStr.startsWith("40s") || graceStr.startsWith("60s"),
                    entry.getKey() + " stop_grace_period 应 >= 35s，当前: " + graceStr);
        }
    }

    @Test
    @DisplayName("同族缺陷: Dockerfile 不得把 r2dbc URL 片段当 mkdir 目录")
    void testDockerfilesDoNotCreateR2dbcUrlDirectory() throws Exception {
        for (String dockerfile : List.of("Dockerfile", "Dockerfile.dev", "Dockerfile.china",
                "Dockerfile.optimized", "Dockerfile.jlink", "Dockerfile.distroless")) {
            Path path = Path.of(dockerfile);
            if (!Files.exists(path)) {
                continue;
            }
            String content = Files.readString(path);
            assertFalse(content.contains("/app/r2dbc:h2:file"),
                    dockerfile + " 不得创建 /app/r2dbc:h2:file 目录（URL 片段不是路径）");
        }
    }

    @Test
    @DisplayName("同族缺陷: Makefile 不得使用 r2dbc URL 作为卷挂载目标")
    void testMakefileVolumeMounts() throws Exception {
        Path makefile = Path.of("Makefile");
        assertTrue(Files.exists(makefile), "Makefile 应存在");
        String content = Files.readString(makefile);
        assertFalse(content.contains("/app/r2dbc:h2:file"),
                "Makefile 卷挂载不得使用 r2dbc URL 片段作为容器内路径");
        assertTrue(content.contains("/app/data"),
                "Makefile 应把宿主 data 目录挂到 /app/data");
    }
}
