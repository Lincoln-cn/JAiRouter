package org.unreal.modelrouter.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * v2.8.x 配置文件结构验证测试
 * 验证模块化配置文件目录结构正确
 */
@DisplayName("v2.8.x 配置文件结构验证测试")
class ConfigFileStructureTest {

    private static final String CONFIG_BASE_PATH = "src/main/resources/config";

    @Test
    @DisplayName("验证配置目录结构存在")
    void testConfigDirectoriesExist() {
        // 验证各模块目录存在
        assertTrue(new File(CONFIG_BASE_PATH + "/auth").exists(), "auth 目录应存在");
        assertTrue(new File(CONFIG_BASE_PATH + "/common").exists(), "common 目录应存在");
        assertTrue(new File(CONFIG_BASE_PATH + "/router").exists(), "router 目录应存在");
        assertTrue(new File(CONFIG_BASE_PATH + "/monitor").exists(), "monitor 目录应存在");
        assertTrue(new File(CONFIG_BASE_PATH + "/persistence").exists(), "persistence 目录应存在");
        assertTrue(new File(CONFIG_BASE_PATH + "/config-service").exists(), "config-service 目录应存在");
        assertTrue(new File(CONFIG_BASE_PATH + "/tracing").exists(), "tracing 目录应存在");
    }

    @Test
    @DisplayName("验证 auth 模块配置文件存在")
    void testAuthModuleConfigFiles() {
        File authDir = new File(CONFIG_BASE_PATH + "/auth");
        assertTrue(authDir.exists() && authDir.isDirectory(), "auth 目录应存在且是目录");

        // 验证 auth 模块配置文件
        assertTrue(new File(authDir, "jwt.yml").exists(), "jwt.yml 应存在");
        assertTrue(new File(authDir, "api-key.yml").exists(), "api-key.yml 应存在");
        assertTrue(new File(authDir, "audit.yml").exists(), "audit.yml 应存在");
        assertTrue(new File(authDir, "sanitization.yml").exists(), "sanitization.yml 应存在");

        // 验证文件数量
        File[] ymlFiles = authDir.listFiles((dir, name) -> name.endsWith(".yml"));
        assertNotNull(ymlFiles, "应能列出配置文件");
        assertTrue(ymlFiles.length >= 4, "auth 目录应有至少 4 个配置文件");
    }

    @Test
    @DisplayName("验证 router 模块配置文件存在")
    void testRouterModuleConfigFiles() {
        File routerDir = new File(CONFIG_BASE_PATH + "/router");
        assertTrue(routerDir.exists() && routerDir.isDirectory(), "router 目录应存在且是目录");

        // 验证 router 模块配置文件
        assertTrue(new File(routerDir, "adapter.yml").exists(), "adapter.yml 应存在");
        assertTrue(new File(routerDir, "loadbalancer.yml").exists(), "loadbalancer.yml 应存在");
        assertTrue(new File(routerDir, "ratelimit.yml").exists(), "ratelimit.yml 应存在");
        assertTrue(new File(routerDir, "circuitbreaker.yml").exists(), "circuitbreaker.yml 应存在");
        assertTrue(new File(routerDir, "fallback.yml").exists(), "fallback.yml 应存在");
        assertTrue(new File(routerDir, "services.yml").exists(), "services.yml 应存在");

        // 验证文件数量
        File[] ymlFiles = routerDir.listFiles((dir, name) -> name.endsWith(".yml"));
        assertNotNull(ymlFiles, "应能列出配置文件");
        assertTrue(ymlFiles.length >= 6, "router 目录应有至少 6 个配置文件");
    }

    @Test
    @DisplayName("验证 common 模块配置文件存在")
    void testCommonModuleConfigFiles() {
        File commonDir = new File(CONFIG_BASE_PATH + "/common");
        assertTrue(commonDir.exists() && commonDir.isDirectory(), "common 目录应存在且是目录");

        // 验证 common 模块配置文件
        assertTrue(new File(commonDir, "server.yml").exists(), "server.yml 应存在");
        assertTrue(new File(commonDir, "webclient.yml").exists(), "webclient.yml 应存在");
        assertTrue(new File(commonDir, "logging.yml").exists(), "logging.yml 应存在");
    }

    @Test
    @DisplayName("验证 monitor 模块配置文件存在")
    void testMonitorModuleConfigFiles() {
        File monitorDir = new File(CONFIG_BASE_PATH + "/monitor");
        assertTrue(monitorDir.exists() && monitorDir.isDirectory(), "monitor 目录应存在且是目录");

        // 验证 monitor 模块配置文件（从 monitoring 迁移）
        assertTrue(new File(monitorDir, "slow-query-alerts.yml").exists(), "slow-query-alerts.yml 应存在");
        assertTrue(new File(monitorDir, "error-tracking.yml").exists(), "error-tracking.yml 应存在");
    }

    @Test
    @DisplayName("验证主配置文件导入链正确")
    void testApplicationYmlImportChain() throws Exception {
        Path applicationYml = Path.of("src/main/resources/application.yml");
        assertTrue(Files.exists(applicationYml), "application.yml 应存在");

        List<String> lines = Files.readAllLines(applicationYml);

        // 验证导入了各模块配置
        assertTrue(lines.stream().anyMatch(l -> l.contains("config/auth/jwt.yml")),
                "应导入 auth/jwt.yml");
        assertTrue(lines.stream().anyMatch(l -> l.contains("config/router/loadbalancer.yml")),
                "应导入 router/loadbalancer.yml");
        assertTrue(lines.stream().anyMatch(l -> l.contains("config/common/server.yml")),
                "应导入 common/server.yml");
        assertTrue(lines.stream().anyMatch(l -> l.contains("config/config-service/core.yml")),
                "应导入 config-service/core.yml");
        assertTrue(lines.stream().anyMatch(l -> l.contains("config/monitor/slow-query-alerts.yml")),
                "应导入 monitor/slow-query-alerts.yml");
    }

    @Test
    @DisplayName("验证环境配置文件存在")
    void testEnvironmentConfigFiles() {
        assertTrue(new File("src/main/resources/application-dev.yml").exists(), "application-dev.yml 应存在");
        assertTrue(new File("src/main/resources/application-staging.yml").exists(), "application-staging.yml 应存在");
        assertTrue(new File("src/main/resources/application-prod.yml").exists(), "application-prod.yml 应存在");
    }

    @Test
    @DisplayName("验证环境配置文件已精简")
    void testEnvironmentConfigFilesSimplified() throws Exception {
        // 环境配置应该比原来精简（从 763 行减少到 271 行）
        Path devYml = Path.of("src/main/resources/application-dev.yml");
        Path stagingYml = Path.of("src/main/resources/application-staging.yml");
        Path prodYml = Path.of("src/main/resources/application-prod.yml");

        int devLines = Files.readAllLines(devYml).size();
        int stagingLines = Files.readAllLines(stagingYml).size();
        int prodLines = Files.readAllLines(prodYml).size();

        // 精简后应该小于 150 行
        assertTrue(devLines < 150, "dev 配置应精简（当前: " + devLines + " 行）");
        assertTrue(stagingLines < 100, "staging 配置应精简（当前: " + stagingLines + " 行）");
        assertTrue(prodLines < 150, "prod 配置应精简（当前: " + prodLines + " 行）");
    }

    @Test
    @DisplayName("验证旧的 monitoring 目录已迁移")
    void testOldMonitoringDirectoryMigrated() {
        // monitoring 目录应该不存在（已迁移到 monitor）
        File oldMonitoringDir = new File(CONFIG_BASE_PATH + "/monitoring");
        assertFalse(oldMonitoringDir.exists(), "旧的 monitoring 目录应已迁移到 monitor");
    }

    @Test
    @DisplayName("统计配置文件总数")
    void testTotalConfigFileCount() {
        File configDir = new File(CONFIG_BASE_PATH);
        assertTrue(configDir.exists(), "config 目录应存在");

        // 递归统计 yml 文件数量
        int totalYmlFiles = countYmlFiles(configDir);
        System.out.println("配置文件总数: " + totalYmlFiles);

        // v2.8.x 重构后应有至少 20 个配置文件
        assertTrue(totalYmlFiles >= 20, "配置文件总数应 >= 20（当前: " + totalYmlFiles + "）");
    }

    private int countYmlFiles(File dir) {
        int count = 0;
        File[] files = dir.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.isDirectory()) {
                    count += countYmlFiles(file);
                } else if (file.getName().endsWith(".yml")) {
                    count++;
                }
            }
        }
        return count;
    }
}