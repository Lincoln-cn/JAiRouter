package org.unreal.modelrouter.auth.sanitization.persistence;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.unreal.modelrouter.auth.security.config.properties.SanitizationConfig;
import org.unreal.modelrouter.auth.security.config.properties.SecurityProperties;
import org.unreal.modelrouter.persistence.jpa.entity.SanitizationConfigEntity;
import org.unreal.modelrouter.persistence.jpa.repository.SanitizationConfigRepository;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link SanitizationConfigStore} 单元测试（TA2）。
 *
 * <p>覆盖：DB 优先 / 无行回落 yml / response 专属列恢复 / JSON 损坏时不改内存 / 只写 response 行。
 * 请求侧作用域已移除（issue #601），仓库里只剩 response 一种快照。</p>
 *
 * @author JAiRouter Team
 * @since 3.4.0
 */
@DisplayName("SanitizationConfigStore 单元测试")
class SanitizationConfigStoreTest {

    private SanitizationConfigRepository repository;
    private SanitizationConfigStore store;
    private SecurityProperties securityProperties;

    @BeforeEach
    void setUp() {
        repository = mock(SanitizationConfigRepository.class);
        store = new SanitizationConfigStore(repository, new ObjectMapper());
        securityProperties = new SecurityProperties();
        SanitizationConfig.ResponseSanitization response = securityProperties.getSanitization().getResponse();
        response.setEnabled(true);
        response.setSensitiveWords(new java.util.ArrayList<>(List.of("resp-word")));
        response.setPiiPatterns(new java.util.ArrayList<>(List.of("\\d{11}")));
        response.setMaskingChar("*");
        response.setPreserveJsonStructure(true);
    }

    @Test
    @DisplayName("DB 无行：保留 yml 值，来源=yml")
    void loadInto_withoutRows_keepsYmlConfiguration() {
        when(repository.findById(SanitizationConfigStore.SCOPE_RESPONSE)).thenReturn(Optional.empty());

        SanitizationConfigStore.LoadResult result = store.loadInto(securityProperties);

        assertFalse(result.responseFromDatabase());
        SanitizationConfig.ResponseSanitization response = securityProperties.getSanitization().getResponse();
        assertEquals(List.of("resp-word"), response.getSensitiveWords());
        assertEquals(List.of("\\d{11}"), response.getPiiPatterns());
        assertEquals("*", response.getMaskingChar());
        assertTrue(response.isPreserveJsonStructure());
    }

    @Test
    @DisplayName("DB 有 response 行：开关、列表、掩码与 response 专属列都从 DB 恢复")
    void loadInto_responseRow_restoresResponseSpecificColumns() {
        when(repository.findById(SanitizationConfigStore.SCOPE_RESPONSE))
                .thenReturn(Optional.of(responseRow("[\"resp-db\"]", "[\"[a-z]+\"]", "false", "#",
                        false, false, Boolean.FALSE)));

        SanitizationConfigStore.LoadResult result = store.loadInto(securityProperties);

        assertTrue(result.responseFromDatabase());
        SanitizationConfig.ResponseSanitization response = securityProperties.getSanitization().getResponse();
        assertEquals(List.of("resp-db"), response.getSensitiveWords());
        assertEquals(List.of("[a-z]+"), response.getPiiPatterns());
        assertEquals("#", response.getMaskingChar());
        assertFalse(response.isEnabled());
        assertFalse(response.isLogSanitization());
        assertFalse(response.isFailOnError());
        assertFalse(response.isPreserveJsonStructure());
    }

    @Test
    @DisplayName("DB 行缺 preserveJsonStructure：按历史默认值 true 恢复")
    void loadInto_nullPreserveFlag_defaultsToTrue() {
        when(repository.findById(SanitizationConfigStore.SCOPE_RESPONSE))
                .thenReturn(Optional.of(responseRow("[]", "[]", "true", "*", true, false, null)));

        store.loadInto(securityProperties);

        assertTrue(securityProperties.getSanitization().getResponse().isPreserveJsonStructure());
    }

    @Test
    @DisplayName("DB 行 JSON 损坏：抛异常且内存配置一个字段都不改")
    void loadInto_corruptJson_throwsWithoutPartiallyMutatingMemory() {
        when(repository.findById(SanitizationConfigStore.SCOPE_RESPONSE))
                .thenReturn(Optional.of(responseRow("not-a-json", "[]", "false", "#",
                        false, false, Boolean.FALSE)));

        assertThrows(IllegalStateException.class, () -> store.loadInto(securityProperties));

        SanitizationConfig.ResponseSanitization response = securityProperties.getSanitization().getResponse();
        assertTrue(response.isEnabled());
        assertEquals("*", response.getMaskingChar());
        assertEquals(List.of("resp-word"), response.getSensitiveWords());
        assertEquals(List.of("\\d{11}"), response.getPiiPatterns());
        assertTrue(response.isPreserveJsonStructure());
    }

    @Test
    @DisplayName("保存：只写 response 一行，JSON 列带列表内容、专属列与操作者")
    void persist_writesResponseScopeOnly() {
        securityProperties.getSanitization().getResponse().setSensitiveWords(List.of("a", "b"));

        store.persist(securityProperties, "admin");

        ArgumentCaptor<SanitizationConfigEntity> captor = ArgumentCaptor.forClass(SanitizationConfigEntity.class);
        verify(repository).save(captor.capture());
        SanitizationConfigEntity saved = captor.getValue();
        assertEquals(SanitizationConfigStore.SCOPE_RESPONSE, saved.getScope());
        assertEquals("[\"a\",\"b\"]", saved.getSensitiveWords());
        assertEquals("[\"\\\\d{11}\"]", saved.getPiiPatterns());
        assertEquals("*", saved.getMaskingChar());
        assertEquals(Boolean.TRUE, saved.getPreserveJsonStructure());
        assertEquals("admin", saved.getUpdatedBy());
        assertNotNull(saved.getUpdatedAt());
    }

    @Test
    @DisplayName("保存：空列表仍写成合法的 JSON 数组")
    void persist_nullLists_writtenAsEmptyJsonArrays() {
        SanitizationConfig.ResponseSanitization response = securityProperties.getSanitization().getResponse();
        response.setSensitiveWords(null);
        response.setPiiPatterns(null);

        store.persist(securityProperties, "system");

        ArgumentCaptor<SanitizationConfigEntity> captor = ArgumentCaptor.forClass(SanitizationConfigEntity.class);
        verify(repository).save(captor.capture());
        assertEquals("[]", captor.getValue().getSensitiveWords());
        assertEquals("[]", captor.getValue().getPiiPatterns());
    }

    private static SanitizationConfigEntity responseRow(final String sensitiveWords,
                                                        final String piiPatterns,
                                                        final String enabled,
                                                        final String maskingChar,
                                                        final boolean logSanitization,
                                                        final boolean failOnError,
                                                        final Boolean preserveJsonStructure) {
        return SanitizationConfigEntity.builder()
                .scope(SanitizationConfigStore.SCOPE_RESPONSE)
                .enabled(Boolean.parseBoolean(enabled))
                .sensitiveWords(sensitiveWords)
                .piiPatterns(piiPatterns)
                .maskingChar(maskingChar)
                .preserveJsonStructure(preserveJsonStructure)
                .logSanitization(logSanitization)
                .failOnError(failOnError)
                .updatedAt(java.time.LocalDateTime.now())
                .updatedBy("admin")
                .build();
    }
}
