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
import org.unreal.modelrouter.config.core.helper.ServiceTypeResolver;
import org.unreal.modelrouter.config.core.helper.ConfigValidatorHelper;
import org.unreal.modelrouter.config.core.helper.ConfigConverterHelper;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * ConfigurationHelper 单元测试 - v2.13.4
 * v2.13.4重构：添加helper类mock注入
 */
@DisplayName("ConfigurationHelper v2.13.4 测试")
@ExtendWith(MockitoExtension.class)
class ConfigurationHelperTest {

    @Mock
    private ConfigurationValidator configurationValidator;

    @Mock
    private ModelRouterProperties modelRouterProperties;

    @Mock
    private ServiceTypeResolver serviceTypeResolver;

    @Mock
    private ConfigValidatorHelper configValidatorHelper;

    @Mock
    private ConfigConverterHelper configConverterHelper;

    @InjectMocks
    private ConfigurationHelper configurationHelper;

    @BeforeEach
    void setUp() {
        configurationHelper.setConfigurationValidator(configurationValidator);
        configurationHelper.setServiceTypeResolver(serviceTypeResolver);
        configurationHelper.setConfigValidatorHelper(configValidatorHelper);
        configurationHelper.setConfigConverterHelper(configConverterHelper);
    }

    @Test
    @DisplayName("测试 1: 解析服务类型 - chat")
    void testParseServiceTypeChat() {
        when(serviceTypeResolver.parseServiceType("chat")).thenReturn(ModelServiceRegistry.ServiceType.chat);
        ModelServiceRegistry.ServiceType result = configurationHelper.parseServiceType("chat");
        assertEquals(ModelServiceRegistry.ServiceType.chat, result);
        verify(serviceTypeResolver).parseServiceType("chat");
    }

    @Test
    @DisplayName("测试 2: 解析服务类型 - embedding")
    void testParseServiceTypeEmbedding() {
        when(serviceTypeResolver.parseServiceType("embedding")).thenReturn(ModelServiceRegistry.ServiceType.embedding);
        ModelServiceRegistry.ServiceType result = configurationHelper.parseServiceType("embedding");
        assertEquals(ModelServiceRegistry.ServiceType.embedding, result);
        verify(serviceTypeResolver).parseServiceType("embedding");
    }

    @Test
    @DisplayName("测试 3: 解析服务类型 - 大小写兼容")
    void testParseServiceTypeUpperCase() {
        when(serviceTypeResolver.parseServiceType("CHAT")).thenReturn(ModelServiceRegistry.ServiceType.chat);
        ModelServiceRegistry.ServiceType result = configurationHelper.parseServiceType("CHAT");
        assertEquals(ModelServiceRegistry.ServiceType.chat, result);
        verify(serviceTypeResolver).parseServiceType("CHAT");
    }

    @Test
    @DisplayName("测试 4: 解析服务类型 - 无效类型返回null")
    void testParseServiceTypeInvalid() {
        when(serviceTypeResolver.parseServiceType("invalid_type")).thenReturn(null);
        ModelServiceRegistry.ServiceType result = configurationHelper.parseServiceType("invalid_type");
        assertNull(result);
        verify(serviceTypeResolver).parseServiceType("invalid_type");
    }

    @Test
    @DisplayName("测试 5: 验证服务类型是否有效 - chat")
    void testIsValidServiceTypeChat() {
        when(serviceTypeResolver.isValidServiceType("chat")).thenReturn(true);
        boolean result = configurationHelper.isValidServiceType("chat");
        assertTrue(result);
        verify(serviceTypeResolver).isValidServiceType("chat");
    }

    @Test
    @DisplayName("测试 6: 验证服务类型是否有效 - embedding")
    void testIsValidServiceTypeEmbedding() {
        when(serviceTypeResolver.isValidServiceType("embedding")).thenReturn(true);
        boolean result = configurationHelper.isValidServiceType("embedding");
        assertTrue(result);
        verify(serviceTypeResolver).isValidServiceType("embedding");
    }

    @Test
    @DisplayName("测试 7: 验证服务类型是否有效 - rerank")
    void testIsValidServiceTypeRerank() {
        when(serviceTypeResolver.isValidServiceType("rerank")).thenReturn(true);
        boolean result = configurationHelper.isValidServiceType("rerank");
        assertTrue(result);
        verify(serviceTypeResolver).isValidServiceType("rerank");
    }

    @Test
    @DisplayName("测试 8: 验证服务类型是否有效 - tts")
    void testIsValidServiceTypeTts() {
        when(serviceTypeResolver.isValidServiceType("tts")).thenReturn(true);
        boolean result = configurationHelper.isValidServiceType("tts");
        assertTrue(result);
        verify(serviceTypeResolver).isValidServiceType("tts");
    }

    @Test
    @DisplayName("测试 9: 验证服务类型是否有效 - 别名 chat-completion")
    void testIsValidServiceTypeAlias() {
        when(serviceTypeResolver.isValidServiceType("chat-completion")).thenReturn(true);
        boolean result = configurationHelper.isValidServiceType("chat-completion");
        assertTrue(result);
        verify(serviceTypeResolver).isValidServiceType("chat-completion");
    }

    @Test
    @DisplayName("测试 10: 验证服务类型是否有效 - 无效类型")
    void testIsValidServiceTypeInvalid() {
        when(serviceTypeResolver.isValidServiceType("invalid_service")).thenReturn(false);
        boolean result = configurationHelper.isValidServiceType("invalid_service");
        assertFalse(result);
        verify(serviceTypeResolver).isValidServiceType("invalid_service");
    }

    @Test
    @DisplayName("测试 11: 获取服务配置键 - chat")
    void testGetServiceConfigKeyChat() {
        when(serviceTypeResolver.getServiceConfigKey(ModelServiceRegistry.ServiceType.chat)).thenReturn("chat");
        String result = configurationHelper.getServiceConfigKey(ModelServiceRegistry.ServiceType.chat);
        assertEquals("chat", result);
        verify(serviceTypeResolver).getServiceConfigKey(ModelServiceRegistry.ServiceType.chat);
    }

    @Test
    @DisplayName("测试 12: 获取服务配置键 - embedding")
    void testGetServiceConfigKeyEmbedding() {
        when(serviceTypeResolver.getServiceConfigKey(ModelServiceRegistry.ServiceType.embedding)).thenReturn("embedding");
        String result = configurationHelper.getServiceConfigKey(ModelServiceRegistry.ServiceType.embedding);
        assertEquals("embedding", result);
        verify(serviceTypeResolver).getServiceConfigKey(ModelServiceRegistry.ServiceType.embedding);
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
        when(configValidatorHelper.validateServiceAddress("")).thenReturn(false);
        boolean result = configurationHelper.validateServiceAddress("");
        assertFalse(result);
        verify(configValidatorHelper).validateServiceAddress("");
    }

    @Test
    @DisplayName("测试 15: 验证服务地址 - null")
    void testValidateServiceAddressNull() {
        when(configValidatorHelper.validateServiceAddress(null)).thenReturn(false);
        boolean result = configurationHelper.validateServiceAddress(null);
        assertFalse(result);
        verify(configValidatorHelper).validateServiceAddress(null);
    }

    @Test
    @DisplayName("测试 16: 验证服务地址 - localhost带端口")
    void testValidateServiceAddressLocalhost() {
        when(configValidatorHelper.validateServiceAddress("http://127.0.0.1:8080")).thenReturn(true);
        boolean result = configurationHelper.validateServiceAddress("http://127.0.0.1:8080");
        assertTrue(result);
        verify(configValidatorHelper).validateServiceAddress("http://127.0.0.1:8080");
    }

    @Test
    @DisplayName("测试 17: 验证负载均衡配置 - 有效配置")
    void testValidateLoadBalanceConfigValid() {
        ModelRouterProperties.LoadBalanceConfig config = new ModelRouterProperties.LoadBalanceConfig();
        config.setType("random");
        config.setHashAlgorithm("md5");

        when(configValidatorHelper.validateLoadBalanceConfig(config)).thenReturn(true);
        boolean result = configurationHelper.validateLoadBalanceConfig(config);
        assertTrue(result);
        verify(configValidatorHelper).validateLoadBalanceConfig(config);
    }

    @Test
    @DisplayName("测试 18: 验证负载均衡配置 - round-robin类型")
    void testValidateLoadBalanceConfigRoundRobin() {
        ModelRouterProperties.LoadBalanceConfig config = new ModelRouterProperties.LoadBalanceConfig();
        config.setType("round-robin");
        config.setHashAlgorithm("md5");

        when(configValidatorHelper.validateLoadBalanceConfig(config)).thenReturn(true);
        boolean result = configurationHelper.validateLoadBalanceConfig(config);
        assertTrue(result);
        verify(configValidatorHelper).validateLoadBalanceConfig(config);
    }
}