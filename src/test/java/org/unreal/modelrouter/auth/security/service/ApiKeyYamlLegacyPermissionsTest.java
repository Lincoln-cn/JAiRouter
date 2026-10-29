package org.unreal.modelrouter.auth.security.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.unreal.modelrouter.auth.security.config.properties.ApiKey;
import org.unreal.modelrouter.auth.security.config.properties.SecurityProperties;
import org.unreal.modelrouter.common.constants.ServiceTypeConstants;
import org.unreal.modelrouter.persistence.store.StoreManager;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * #813：YAML 里配置的 API Key 必须与控制台创建的 Key 走同一套权限归一化。
 *
 * <p>历史上 {@code loadApiKeysFromConfig()} 只调 {@code initializeApiKeyFields}，不调
 * {@code validatePermissions}。于是 {@code application-dev.yml} 里内置 Key 的
 * {@code permissions: read,write} 被原样搬进 {@code ApiKey}，{@code ApiKeyAuthentication}
 * 把它们转成 {@code ROLE_READ}/{@code ROLE_WRITE}，而 {@code hasServicePermission} 只认
 * {@code ROLE_<SERVICETYPE>}（如 ROLE_CHAT）⇒ dev profile 下「开箱即用」的内置 Key
 * 调任何模型都会 403，用户会误判为权限配置错误。</p>
 *
 * @author JAiRouter Team
 * @since 3.5.6
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("#813 YAML 内置 Key 的旧权限归一化")
class ApiKeyYamlLegacyPermissionsTest {

    @Mock
    private StoreManager storeManager;

    @Mock
    private ObjectMapper objectMapper;

    @Mock
    private ApiKeyValidator apiKeyValidator;

    @Mock
    private ApiKeyBatchService apiKeyBatchService;

    @Mock
    private ApiKeyPersistenceService apiKeyPersistenceService;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @Test
    @DisplayName("813-001 旧权限 read,write 归一化为全部服务类型")
    @SuppressWarnings("unchecked")
    void legacyPermissionsAreNormalizedWhenLoadedFromYaml() throws Exception {
        final SecurityProperties properties = new SecurityProperties();
        final ApiKey builtin = ApiKey.builder()
                .keyId("builtin-dev-key")
                .keyHash("hash")
                .keyPrefix("sk-")
                .permissions(new ArrayList<>(List.of("read", "write")))
                .enabled(true)
                .build();
        properties.getApiKey().setKeys(new ArrayList<>(List.of(builtin)));

        final ApiKeyService service = new ApiKeyService(storeManager, objectMapper, properties);
        setField(service, "apiKeyValidator", apiKeyValidator);
        setField(service, "apiKeyBatchService", apiKeyBatchService);
        setField(service, "apiKeyPersistenceService", apiKeyPersistenceService);
        setField(service, "eventPublisher", eventPublisher);

        final Method load = ApiKeyService.class.getDeclaredMethod("loadApiKeysFromConfig");
        load.setAccessible(true);
        final List<ApiKey> loaded = (List<ApiKey>) load.invoke(service);

        assertEquals(1, loaded.size(), "YAML 里的一个 Key 应原样加载出来");
        final List<String> permissions = loaded.get(0).getPermissions();
        assertFalse(permissions.contains("read"), "旧权限 read 不得残留");
        assertFalse(permissions.contains("write"), "旧权限 write 不得残留");
        assertEquals(new HashSet<>(ServiceTypeConstants.getServiceTypeList()), new HashSet<>(permissions),
                "旧权限必须被转换成全部服务类型，否则内置 Key 调服务会 403");
    }

    @Test
    @DisplayName("813-002 已经是服务类型的权限保持不变")
    void serviceTypePermissionsAreKept() throws Exception {
        final SecurityProperties properties = new SecurityProperties();
        final ApiKey key = ApiKey.builder()
                .keyId("service-key")
                .keyHash("hash")
                .keyPrefix("sk-")
                .permissions(new ArrayList<>(List.of("chat")))
                .enabled(true)
                .build();
        properties.getApiKey().setKeys(new ArrayList<>(List.of(key)));

        final ApiKeyService service = new ApiKeyService(storeManager, objectMapper, properties);
        setField(service, "apiKeyPersistenceService", apiKeyPersistenceService);

        final Method load = ApiKeyService.class.getDeclaredMethod("loadApiKeysFromConfig");
        load.setAccessible(true);
        @SuppressWarnings("unchecked")
        final List<ApiKey> loaded = (List<ApiKey>) load.invoke(service);

        assertTrue(loaded.get(0).getPermissions().contains("chat"));
        assertEquals(1, loaded.get(0).getPermissions().size(), "只配 chat 的 Key 不应被扩权成全部服务");
    }

    private static void setField(final ApiKeyService target, final String name, final Object value) throws Exception {
        final Field field = ApiKeyService.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }
}
