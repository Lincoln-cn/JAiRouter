package org.unreal.modelrouter.config.core;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.unreal.modelrouter.router.model.ModelServiceRegistry;
import org.unreal.modelrouter.router.model.ModelRouterProperties;
import org.unreal.modelrouter.config.core.ConfigurationValidator;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * ConfigurationHelper 单元测试 - v2.9.6
 */
@DisplayName("ConfigurationHelper v2.9.6 测试")
@ExtendWith(MockitoExtension.class)
class ConfigurationHelperTest {

    @Mock
    private ConfigurationValidator configurationValidator;

    @Mock
    private ModelRouterProperties modelRouterProperties;

    @InjectMocks
    private ConfigurationHelper configurationHelper;

    @BeforeEach
    void setUp() {
        configurationHelper.setConfigurationValidator(configurationValidator);
    }

    @Test
    @DisplayName("测试 1: 解析服务类型 - chat")
    void testParseServiceTypeChat() {
        ModelServiceRegistry.ServiceType result = configurationHelper.parseServiceType("chat");
        assertEquals(ModelServiceRegistry.ServiceType.chat, result);
    }

    @Test
    @DisplayName("测试 2: 解析服务类型 - embedding")
    void testParseServiceTypeEmbedding() {
        ModelServiceRegistry.ServiceType result = configurationHelper.parseServiceType("embedding");
        assertEquals(ModelServiceRegistry.ServiceType.embedding, result);
    }

    @Test
    @DisplayName("测试 3: 解析服务类型 - 大小写兼容")
    void testParseServiceTypeUpperCase() {
        ModelServiceRegistry.ServiceType result = configurationHelper.parseServiceType("CHAT");
        assertEquals(ModelServiceRegistry.ServiceType.chat, result);
    }

    @Test
    @DisplayName("测试 4: 解析服务类型 - 无效类型返回null")
    void testParseServiceTypeInvalid() {
        ModelServiceRegistry.ServiceType result = configurationHelper.parseServiceType("invalid_type");
        assertNull(result);
    }

    @Test
    @DisplayName("测试 5: 验证服务类型是否有效 - chat")
    void testIsValidServiceTypeChat() {
        boolean result = configurationHelper.isValidServiceType("chat");
        assertTrue(result);
    }

    @Test
    @DisplayName("测试 6: 验证服务类型是否有效 - embedding")
    void testIsValidServiceTypeEmbedding() {
        boolean result = configurationHelper.isValidServiceType("embedding");
        assertTrue(result);
    }

    @Test
    @DisplayName("测试 7: 验证服务类型是否有效 - rerank")
    void testIsValidServiceTypeRerank() {
        boolean result = configurationHelper.isValidServiceType("rerank");
        assertTrue(result);
    }

    @Test
    @DisplayName("测试 8: 验证服务类型是否有效 - tts")
    void testIsValidServiceTypeTts() {
        boolean result = configurationHelper.isValidServiceType("tts");
        assertTrue(result);
    }

    @Test
    @DisplayName("测试 9: 验证服务类型是否有效 - 别名 chat-completion")
    void testIsValidServiceTypeAlias() {
        boolean result = configurationHelper.isValidServiceType("chat-completion");
        assertTrue(result);
    }

    @Test
    @DisplayName("测试 10: 验证服务类型是否有效 - 无效类型")
    void testIsValidServiceTypeInvalid() {
        boolean result = configurationHelper.isValidServiceType("invalid_service");
        assertFalse(result);
    }

    @Test
    @DisplayName("测试 11: 获取服务配置键 - chat")
    void testGetServiceConfigKeyChat() {
        String result = configurationHelper.getServiceConfigKey(ModelServiceRegistry.ServiceType.chat);
        assertEquals("chat", result);
    }

    @Test
    @DisplayName("测试 12: 获取服务配置键 - embedding")
    void testGetServiceConfigKeyEmbedding() {
        String result = configurationHelper.getServiceConfigKey(ModelServiceRegistry.ServiceType.embedding);
        assertEquals("embedding", result);
    }

    @Test
    @DisplayName("测试 13: 创建默认负载均衡配置")
    void testCreateDefaultLoadBalanceConfig() {
        ModelRouterProperties.LoadBalanceConfig result = configurationHelper.createDefaultLoadBalanceConfig();
        assertNotNull(result);
        assertEquals("random", result.getType());
        assertEquals("md5", result.getHashAlgorithm());
    }

    @Test
    @DisplayName("测试 14: 验证服务地址 - 空值")
    void testValidateServiceAddressEmpty() {
        boolean result = configurationHelper.validateServiceAddress("");
        assertFalse(result);
    }

    @Test
    @DisplayName("测试 15: 验证服务地址 - null")
    void testValidateServiceAddressNull() {
        boolean result = configurationHelper.validateServiceAddress(null);
        assertFalse(result);
    }

    @Test
    @DisplayName("测试 16: 验证服务地址 - localhost带端口")
    void testValidateServiceAddressLocalhost() {
        boolean result = configurationHelper.validateServiceAddress("http://127.0.0.1:8080");
        assertTrue(result);
    }

    @Test
    @DisplayName("测试 17: 验证负载均衡配置 - 有效配置")
    void testValidateLoadBalanceConfigValid() {
        ModelRouterProperties.LoadBalanceConfig config = new ModelRouterProperties.LoadBalanceConfig();
        config.setType("random");
        config.setHashAlgorithm("md5");
        
        boolean result = configurationHelper.validateLoadBalanceConfig(config);
        assertTrue(result);
    }

    @Test
    @DisplayName("测试 18: 验证负载均衡配置 - round-robin类型")
    void testValidateLoadBalanceConfigRoundRobin() {
        ModelRouterProperties.LoadBalanceConfig config = new ModelRouterProperties.LoadBalanceConfig();
        config.setType("round-robin");
        config.setHashAlgorithm("md5");
        
        boolean result = configurationHelper.validateLoadBalanceConfig(config);
        assertTrue(result);
    }
}