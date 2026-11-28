package org.unreal.modelrouter.auth.security.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.context.ApplicationEventPublisher;
import org.unreal.modelrouter.auth.security.config.properties.ApiKey;
import org.unreal.modelrouter.auth.security.config.properties.SecurityProperties;
import org.unreal.modelrouter.auth.security.dto.ApiKeyCreateRequest;
import org.unreal.modelrouter.auth.security.dto.ApiKeyUpdateRequest;
import org.unreal.modelrouter.auth.security.model.UsageStatistics;
import org.unreal.modelrouter.auth.security.util.ApiKeyHashUtil;
import org.unreal.modelrouter.persistence.store.StoreManager;
import reactor.test.StepVerifier;
import tools.jackson.databind.ObjectMapper;

import java.lang.reflect.Field;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

/**
 * API Key 写路径在持久化失败时的行为（issue #1245）.
 *
 * <p>缺陷史：{@code ApiKeyPersistenceService.saveApiKeysToStore} 把存储异常
 * {@code catch} 掉只记 error，而调用方是「先改内存、再落存储」—— 于是删除 / 禁用
 * 接口返回成功，其它副本永远读不到、重启后已删的 Key 复活。</p>
 *
 * <p>本类断言两件事：① 失败要变成可见的错误（Reactive 流以 {@link IllegalStateException}
 * 结束，接口就是 5xx）；② 内存改动要回滚，别让本副本带着一个没落库的状态继续跑。</p>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("API Key 持久化失败时回滚")
class ApiKeyServicePersistenceFailureTest {

    @Mock private StoreManager storeManager;
    @Mock private ObjectMapper objectMapper;
    @Mock private SecurityProperties securityProperties;
    @Mock private ApiKeyValidator apiKeyValidator;
    @Mock private ApiKeyBatchService apiKeyBatchService;
    @Mock private ApiKeyPersistenceService apiKeyPersistenceService;
    @Mock private ApplicationEventPublisher eventPublisher;

    private ApiKeyService service;
    private Map<String, ApiKey> cache;
    private Map<String, String> index;
    private ApiKey apiKey;

    private static final String KID = "test-key-id";
    private static final String KEY_VALUE = "sk-test-key-persist-1245";

    @BeforeEach
    void setUp() throws Exception {
        service = new ApiKeyService(storeManager, objectMapper, securityProperties);
        inject("apiKeyValidator", apiKeyValidator);
        inject("apiKeyBatchService", apiKeyBatchService);
        inject("apiKeyPersistenceService", apiKeyPersistenceService);
        inject("eventPublisher", eventPublisher);

        Field cacheField = ApiKeyService.class.getDeclaredField("apiKeyCache");
        cacheField.setAccessible(true);
        cache = (Map<String, ApiKey>) cacheField.get(service);
        Field indexField = ApiKeyService.class.getDeclaredField("keyIdIndex");
        indexField.setAccessible(true);
        index = (Map<String, String>) indexField.get(service);

        apiKey = ApiKey.builder()
                .keyId(KID)
                .keyHash(ApiKeyHashUtil.hashApiKey(KEY_VALUE))
                .keyPrefix("sk-")
                .description("persist test")
                .permissions(List.of("chat"))
                .enabled(true)
                .createdAt(LocalDateTime.now())
                .createdBy("system")
                .usage(UsageStatistics.builder()
                        .totalRequests(0L).successfulRequests(0L).failedRequests(0L)
                        .dailyUsage(new HashMap<>()).dailyTokenUsage(new HashMap<>())
                        .build())
                .build();
        cache.put(apiKey.getKeyHash(), apiKey);
        index.put(KID, apiKey.getKeyHash());
    }

    private void inject(final String name, final Object value) throws Exception {
        Field field = ApiKeyService.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(service, value);
    }

    private void failPersistence() {
        doThrow(new IllegalStateException("保存 API Key 到存储失败：store down"))
                .when(apiKeyPersistenceService).saveApiKeysToStoreOrThrow(any());
    }

    @Test
    @DisplayName("删除落库失败：报错且缓存恢复，Key 不会被「本副本已删」")
    void deleteRollsBackWhenPersistenceFails() {
        failPersistence();

        StepVerifier.create(service.deleteApiKey(KID))
                .expectError(IllegalStateException.class)
                .verify();

        assertTrue(cache.containsKey(apiKey.getKeyHash()), "删除没落库，缓存里必须还有它");
        assertEquals(apiKey.getKeyHash(), index.get(KID));
    }

    @Test
    @DisplayName("禁用落库失败：报错且 enabled 仍是 true")
    void disableKeepsEnabledFlagWhenPersistenceFails() {
        failPersistence();

        StepVerifier.create(service.disableApiKey(KID))
                .expectError(IllegalStateException.class)
                .verify();

        assertTrue(apiKey.isEnabled(), "没落库就不该在本副本里变成禁用");
    }

    @Test
    @DisplayName("启用落库失败：报错且 enabled 仍回到原值")
    void enableKeepsDisabledFlagWhenPersistenceFails() {
        apiKey.setEnabled(false);
        failPersistence();

        StepVerifier.create(service.enableApiKey(KID))
                .expectError(IllegalStateException.class)
                .verify();

        assertFalse(apiKey.isEnabled(), "没落库就不该在本副本里变成启用");
    }

    @Test
    @DisplayName("更新落库失败：报错（不再静默返回成功）")
    void updateFailsVisiblyWhenPersistenceFails() {
        failPersistence();
        ApiKeyUpdateRequest req = new ApiKeyUpdateRequest();
        req.setDescription("changed");

        StepVerifier.create(service.updateApiKey(KID, req))
                .expectError(IllegalStateException.class)
                .verify();
    }

    @Test
    @DisplayName("创建落库失败：报错且缓存里不留这个 Key")
    void createRemovesCacheEntryWhenPersistenceFails() {
        failPersistence();
        ApiKeyCreateRequest req = new ApiKeyCreateRequest();
        req.setDescription("new key");
        req.setPermissions(List.of("chat"));

        final int before = cache.size();
        StepVerifier.create(service.createApiKey(req, "tester", "127.0.0.1"))
                .expectError(IllegalStateException.class)
                .verify();

        assertEquals(before, cache.size(), "没落库就创建，缓存里不能多出这个 Key");
    }

    @Test
    @DisplayName("落库成功时删除照常生效（回归）")
    void deleteStillWorksWhenPersistenceSucceeds() {
        StepVerifier.create(service.deleteApiKey(KID)).verifyComplete();

        assertNull(cache.get(apiKey.getKeyHash()));
        assertNull(index.get(KID));
        verify(apiKeyPersistenceService).saveApiKeysToStoreOrThrow(any());
    }
}
