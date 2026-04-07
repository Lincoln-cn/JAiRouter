package org.unreal.modelrouter.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.unreal.modelrouter.store.StoreManager;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
public class VersionGenerationTest {

    @Autowired
    private ConfigurationService configurationService;

    @Autowired
    private StoreManager storeManager;

    @Test
    public void testCreateServiceGeneratesVersion() {
        // 获取初始版本数量
        List<Integer> initialVersions = configurationService.getAllVersions();
        int initialCount = initialVersions.size();
        System.out.println("初始版本数量: " + initialCount);

        // 创建测试服务配置
        Map<String, Object> serviceConfig = new HashMap<>();
        serviceConfig.put("adapter", "normal");

        Map<String, Object> loadBalance = new HashMap<>();
        loadBalance.put("type", "round-robin");
        loadBalance.put("hashAlgorithm", "murmur3");
        serviceConfig.put("loadBalance", loadBalance);

        Map<String, Object> rateLimit = new HashMap<>();
        rateLimit.put("algorithm", "token-bucket");
        rateLimit.put("capacity", 1000);
        rateLimit.put("rate", 100);
        rateLimit.put("scope", "service");
        rateLimit.put("clientIpEnable", true);
        rateLimit.put("enabled", true);
        serviceConfig.put("rateLimit", rateLimit);

        Map<String, Object> circuitBreaker = new HashMap<>();
        circuitBreaker.put("failureThreshold", 5);
        circuitBreaker.put("timeout", 60000);
        circuitBreaker.put("successThreshold", 2);
        circuitBreaker.put("enabled", true);
        serviceConfig.put("circuitBreaker", circuitBreaker);

        Map<String, Object> fallback = new HashMap<>();
        fallback.put("enabled", false);
        fallback.put("maxRetries", 3);
        fallback.put("retryInterval", 1000);
        fallback.put("returnDefaultResponse", true);
        serviceConfig.put("fallback", fallback);

        // 创建实例列表
        Map<String, Object> instance = new HashMap<>();
        instance.put("name", "test-instance");
        instance.put("baseUrl", "http://localhost:8081");
        instance.put("path", "/v1/images/generations");
        instance.put("weight", 1);
        instance.put("status", "active");

        serviceConfig.put("instances", List.of(instance));

        // 创建服务
        String serviceType = "imggen";
        try {
            configurationService.createService(serviceType, serviceConfig);
            System.out.println("服务创建成功");
        } catch (Exception e) {
            System.out.println("服务创建失败: " + e.getMessage());
            e.printStackTrace();
        }

        // 获取创建后的版本数量
        List<Integer> afterCreateVersions = configurationService.getAllVersions();
        int afterCreateCount = afterCreateVersions.size();
        System.out.println("创建服务后版本数量: " + afterCreateCount);

        // 验证版本是否生成
        assertTrue(afterCreateCount > initialCount, "创建服务应该生成新版本");

        // 获取最新版本号
        int latestVersion = afterCreateVersions.get(afterCreateVersions.size() - 1);
        System.out.println("最新版本号: " + latestVersion);

        // 获取版本配置
        Map<String, Object> versionConfig = configurationService.getVersionConfig(latestVersion);
        assertNotNull(versionConfig, "版本配置不应为空");
        assertTrue(versionConfig.containsKey("services"), "版本配置应包含 services 字段");

        System.out.println("版本配置: " + versionConfig);
    }
}
