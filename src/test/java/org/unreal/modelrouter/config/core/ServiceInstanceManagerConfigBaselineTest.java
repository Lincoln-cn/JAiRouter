package org.unreal.modelrouter.config.core;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.unreal.modelrouter.persistence.jpa.repository.ServiceConfigRepository;
import org.unreal.modelrouter.persistence.jpa.repository.ServiceInstanceRepository;
import org.unreal.modelrouter.persistence.jpa.repository.InstanceRateLimitRepository;
import org.unreal.modelrouter.persistence.jpa.repository.InstanceCircuitBreakerRepository;
import org.unreal.modelrouter.persistence.jpa.entity.ServiceConfigEntity;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

/**
 * issue #1102 的守卫测试：实例变更保存版本时，必须以「现有服务配置」为基线。
 *
 * <p>缺陷形态：{@code buildFullConfigFromDatabase()} 原先只写 adapter / loadBalance / instances，
 * 其余服务级字段（sticky、服务级 rateLimit、服务级 circuitBreaker、fallback）会被整段丢弃。
 * 由于 {@code saveVersion()} 会把这份 map 存成新版本并立即 {@code refreshFromMergedConfig()}，
 * 结果是「关闭会话粘性 → 新增实例」会把粘性悄悄恢复成默认开启，而读面显示 enabled=true。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("实例变更保存版本时保留服务级配置")
class ServiceInstanceManagerConfigBaselineTest {

    @Mock
    private ConfigurationService configurationService;

    @Mock
    private ServiceConfigRepository serviceConfigRepository;

    @Mock
    private ServiceInstanceRepository serviceInstanceRepository;

    @Mock
    private InstanceRateLimitRepository rateLimitRepository;

    @Mock
    private InstanceCircuitBreakerRepository circuitBreakerRepository;

    @InjectMocks
    private ServiceInstanceManager serviceInstanceManager;

    private Map<String, Object> buildFullConfig() throws Exception {
        final Method method = ServiceInstanceManager.class.getDeclaredMethod("buildFullConfigFromDatabase");
        method.setAccessible(true);
        @SuppressWarnings("unchecked")
        final Map<String, Object> result = (Map<String, Object>) method.invoke(serviceInstanceManager);
        return result;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> chatService(final Map<String, Object> config) {
        final Map<String, Object> services = (Map<String, Object>) config.get("services");
        assertNotNull(services, "必须输出 services 段");
        final Map<String, Object> chat = (Map<String, Object>) services.get("chat");
        assertNotNull(chat, "必须输出 chat 服务配置");
        return chat;
    }

    @SuppressWarnings("unchecked")
    private void stubExistingChatConfig() {
        when(configurationService.getAllConfigurations()).thenReturn(Map.of(
                "services", Map.of("chat", Map.of(
                        "adapter", "gpustack",
                        "loadBalance", Map.of("type", "least-connections", "hashAlgorithm", "sha256"),
                        "sticky", Map.of("enabled", false, "affinityKeyScope", "tenant_model"),
                        "rateLimit", Map.of("capacity", 100, "rate", 10, "algorithm", "token-bucket"),
                        "fallback", Map.of("strategy", "default")
                ))));
        when(serviceConfigRepository.findAllByIsLatestTrue()).thenReturn(List.of(
                ServiceConfigEntity.builder()
                        .id(1L)
                        .serviceType("chat")
                        .adapter("gpustack")
                        .loadBalanceType("least-connections")
                        .build()));
        when(serviceInstanceRepository.findByServiceConfigId(1L)).thenReturn(List.of());
    }

    @Test
    @DisplayName("关闭的会话粘性必须原样保留（否则增删实例会把粘性打开）")
    @SuppressWarnings("unchecked")
    void stickyDisabled_isPreserved() throws Exception {
        stubExistingChatConfig();

        final Map<String, Object> chat = chatService(buildFullConfig());
        final Map<String, Object> sticky = (Map<String, Object>) chat.get("sticky");

        assertNotNull(sticky, "sticky 块必须保留，否则读面回落到默认开启");
        assertFalse((Boolean) sticky.get("enabled"),
                "关闭的粘性不能被实例变更重置为开启");
        assertEquals("tenant_model", sticky.get("affinityKeyScope"));
    }

    @Test
    @DisplayName("服务级 rateLimit 与 fallback 必须保留（原先整段丢失）")
    @SuppressWarnings("unchecked")
    void serviceLevelBlocks_arePreserved() throws Exception {
        stubExistingChatConfig();

        final Map<String, Object> chat = chatService(buildFullConfig());
        assertNotNull(chat.get("rateLimit"), "服务级 rateLimit 不能丢");
        assertNotNull(chat.get("fallback"), "fallback 不能丢");
        assertEquals(100, ((Map<String, Object>) chat.get("rateLimit")).get("capacity"));
    }

    @Test
    @DisplayName("hashAlgorithm 不能被实例变更改回 md5")
    @SuppressWarnings("unchecked")
    void hashAlgorithm_isNotOverwritten() throws Exception {
        stubExistingChatConfig();

        final Map<String, Object> chat = chatService(buildFullConfig());
        final Map<String, Object> lb = (Map<String, Object>) chat.get("loadBalance");

        assertEquals("least-connections", lb.get("type"), "type 以实体为准");
        assertEquals("sha256", lb.get("hashAlgorithm"), "hashAlgorithm 不能硬编码成 md5");
    }

    @Test
    @DisplayName("没有既有服务配置时仍能构建（首次初始化路径）")
    void withoutExistingConfig_stillBuilds() throws Exception {
        when(configurationService.getAllConfigurations()).thenReturn(Map.of());
        when(serviceConfigRepository.findAllByIsLatestTrue()).thenReturn(List.of(
                ServiceConfigEntity.builder()
                        .id(2L)
                        .serviceType("embedding")
                        .adapter("gpustack")
                        .loadBalanceType("round-robin")
                        .build()));
        when(serviceInstanceRepository.findByServiceConfigId(2L)).thenReturn(List.of());

        @SuppressWarnings("unchecked")
        final Map<String, Object> services =
                (Map<String, Object>) buildFullConfig().get("services");

        assertNotNull(services);
        assertTrue(services.containsKey("embedding"));
    }
}
