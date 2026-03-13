package org.unreal.modelrouter.architecture;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 循环依赖检测测试
 * 验证应用启动时是否存在循环依赖问题
 */
@SpringBootTest
@ActiveProfiles("test")
class CircularDependencyTest {

    @Autowired
    private ApplicationContext applicationContext;

    /**
     * 测试应用上下文是否成功加载
     * 如果存在未解决的循环依赖，应用启动将失败
     */
    @Test
    void contextLoads() {
        // 如果应用上下文成功加载，说明没有未解决的循环依赖
        assertNotNull(applicationContext);
        assertTrue(applicationContext.getBeanDefinitionCount() > 0);
    }

    /**
     * 验证关键服务是否正常初始化
     */
    @Test
    void keyServicesAreInitialized() {
        // 验证配置相关服务
        assertTrue(isBeanPresent("versionControlService"));
        assertTrue(isBeanPresent("configPersistenceService"));

        // 验证模型服务注册表
        assertTrue(isBeanPresent("modelServiceRegistry"));

        // 验证存储管理器
        assertTrue(isBeanPresent("reactiveH2StoreManager") ||
                   isBeanPresent("reactiveFileStoreManager") ||
                   isBeanPresent("reactiveMemoryStoreManager"));
    }

    /**
     * 验证没有使用 CircularReferenceConfig
     */
    @Test
    void circularReferenceConfigNotUsed() {
        // CircularReferenceConfig 应该已被删除
        assertFalse(isBeanPresent("circularReferenceConfig"));
    }

    /**
     * 验证 spring.main.allow-circular-references 配置不存在
     */
    @Test
    void allowCircularReferencesNotConfigured() {
        // 获取环境配置
        String allowCircularRefs = applicationContext.getEnvironment()
                .getProperty("spring.main.allow-circular-references");

        // 配置应该不存在或为 false
        assertTrue(allowCircularRefs == null ||
                   allowCircularRefs.equalsIgnoreCase("false"),
                "spring.main.allow-circular-references should not be set to true");
    }

    private boolean isBeanPresent(String beanName) {
        try {
            return applicationContext.containsBean(beanName);
        } catch (Exception e) {
            return false;
        }
    }
}
