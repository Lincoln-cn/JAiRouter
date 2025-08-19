package org.unreal.modelrouter.security.integration;

import org.junit.platform.suite.api.SelectClasses;
import org.junit.platform.suite.api.Suite;
import org.junit.platform.suite.api.SuiteDisplayName;

/**
 * 安全功能集成测试套件
 * 
 * 该测试套件包含所有安全功能的集成测试，用于验证：
 * - API Key认证功能的端到端测试
 * - JWT认证流程的集成测试
 * - 数据脱敏功能的集成测试
 * - 安全配置管理的集成测试
 * - 完整的安全功能端到端测试
 * 
 * 运行方式：
 * 1. 通过IDE运行整个测试套件
 * 2. 通过Maven命令：mvn test -Dtest=SecurityIntegrationTestSuite
 * 3. 通过Gradle命令：./gradlew test --tests SecurityIntegrationTestSuite
 * 
 * 需求覆盖：
 * - 认证功能：1.1, 1.2, 1.3, 1.4, 1.5, 1.6, 1.7, 2.1, 2.2, 2.3, 2.4, 2.5, 2.6, 3.1-3.6
 * - 数据脱敏：4.1, 4.2, 4.3, 4.4, 4.5, 4.6, 4.7, 5.1, 5.2, 5.3, 5.4, 5.5
 * - 配置管理：6.1, 6.2, 6.3, 6.4, 6.5, 6.6
 * - 审计监控：7.1, 7.2, 7.3, 7.4, 7.5, 7.6
 */
@Suite
@SuiteDisplayName("Security Integration Test Suite")
@SelectClasses({
    // 认证功能集成测试
    AuthenticationIntegrationTest.class,
    JwtAuthenticationIntegrationTest.class,
    AuthenticationErrorHandlingIntegrationTest.class,
    
    // 数据脱敏集成测试
    SanitizationIntegrationTest.class,
    SanitizationRuleIntegrationTest.class,
    
    // 安全配置管理集成测试
    SecurityConfigurationIntegrationTest.class,
    
    // 端到端安全功能测试
    SecurityEndToEndIntegrationTest.class
})
public class SecurityIntegrationTestSuite {
    
    /**
     * 测试套件说明
     * 
     * 本测试套件按照以下顺序执行测试：
     * 
     * 1. 认证功能测试
     *    - AuthenticationIntegrationTest: API Key认证的基础功能测试
     *    - JwtAuthenticationIntegrationTest: JWT令牌认证的功能测试
     *    - AuthenticationErrorHandlingIntegrationTest: 认证错误处理测试
     * 
     * 2. 数据脱敏功能测试
     *    - SanitizationIntegrationTest: 基础数据脱敏功能测试
     *    - SanitizationRuleIntegrationTest: 脱敏规则和策略测试
     * 
     * 3. 配置管理测试
     *    - SecurityConfigurationIntegrationTest: 安全配置管理功能测试
     * 
     * 4. 端到端测试
     *    - SecurityEndToEndIntegrationTest: 完整的安全功能端到端测试
     * 
     * 测试环境要求：
     * - Spring Boot Test环境
     * - 内存数据库（H2）
     * - 模拟的外部服务
     * - 测试配置文件（application-test.yml）
     * 
     * 性能要求：
     * - 单个测试方法执行时间不超过30秒
     * - 整个测试套件执行时间不超过10分钟
     * - 内存使用不超过512MB
     * 
     * 覆盖率要求：
     * - 代码行覆盖率 >= 80%
     * - 分支覆盖率 >= 70%
     * - 方法覆盖率 >= 90%
     */
    
    // 测试套件不需要实现任何方法，所有测试逻辑都在被选择的测试类中
}