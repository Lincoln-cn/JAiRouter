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
 * <p>覆盖：DB 优先 / 无行回落 yml / 两个 scope 独立判定 / JSON 损坏时不改内存 / 只写显式 scope。</p>
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
        SanitizationConfig sanitization = securityProperties.getSanitization();
        SanitizationConfig.RequestSanitization request = sanitization.getRequest();
        request.setEnabled(true);
        request.setSensitiveWords(new java.util.ArrayList<>(List.of("yml-word")));
        request.setPiiPatterns(new java.util.ArrayList<>(List.of("\\d{11}")));
        request.setMaskingChar("*");
        SanitizationConfig.ResponseSanitization response = sanitization.getResponse();
        response.setEnabled(true);
        response.setSensitiveWords(new java.util.ArrayList<>(List.of("resp-word")));
        response.setPreserveJsonStructure(true);
    }

    @Test
    @DisplayName("DB 无行：保留 yml 值，来源=yml")
    void loadInto_withoutRows_keepsYmlConfiguration() {
        when(repository.findById(SanitizationConfigStore.SCOPE_REQUEST)).thenReturn(Optional.empty());
        when(repository.findById(SanitizationConfigStore.SCOPE_RESPONSE)).thenReturn(Optional.empty());

        SanitizationConfigStore.LoadResult result = store.loadInto(securityProperties);

        assertFalse(result.requestFromDatabase());
        assertFalse(result.responseFromDatabase());
        SanitizationConfig.RequestSanitization request = securityProperties.getSanitization().getRequest();
        assertEquals(List.of("yml-word"), request.getSensitiveWords());
        assertEquals(List.of("\\d{11}"), request.getPiiPatterns());
        assertEquals("*", request.getMaskingChar());
        assertEquals(List.of("resp-word"), securityProperties.getSanitization().getResponse().getSensitiveWords());
    }

    @Test
    @DisplayName("DB 有 request 行、无 response 行：request 取 DB，response 仍取 yml")
    void loadInto_overridesOnlyScopesPresentInDatabase() {
        when(repository.findById(SanitizationConfigStore.SCOPE_REQUEST))
                .thenReturn(Optional.of(requestRow(
                        "[\"db-word\"]", "[\"[a-z]+\"]", "false", "#", "[\"alice\"]", false)));
        when(repository.findById(SanitizationConfigStore.SCOPE_RESPONSE)).thenReturn(Optional.empty());

        SanitizationConfigStore.LoadResult result = store.loadInto(securityProperties);

        assertTrue(result.requestFromDatabase());
        assertFalse(result.responseFromDatabase());
        SanitizationConfig.RequestSanitization request = securityProperties.getSanitization().getRequest();
        assertFalse(request.isEnabled());
        assertEquals(List.of("db-word"), request.getSensitiveWords());
        assertEquals(List.of("[a-z]+"), request.getPiiPatterns());
        assertEquals("#", request.getMaskingChar());
        assertEquals(List.of("alice"), request.getWhitelistUsers());
        assertFalse(request.isLogSanitization());
        assertFalse(request.isFailOnError());
        // response 未被 request 行波及
        assertEquals(List.of("resp-word"), securityProperties.getSanitization().getResponse().getSensitiveWords());
        assertTrue(securityProperties.getSanitization().getResponse().isPreserveJsonStructure());
    }

    @Test
    @DisplayName("DB 有 response 行：preserveJsonStructure 与开关从 DB 恢复")
    void loadInto_responseRow_restoresResponseSpecificColumns() {
        when(repository.findById(SanitizationConfigStore.SCOPE_REQUEST)).thenReturn(Optional.empty());
        when(repository.findById(SanitizationConfigStore.SCOPE_RESPONSE))
                .thenReturn(Optional.of(responseRow("[\"resp-db\"]", "false", Boolean.FALSE)));

        SanitizationConfigStore.LoadResult result = store.loadInto(securityProperties);

        assertTrue(result.responseFromDatabase());
        SanitizationConfig.ResponseSanitization response = securityProperties.getSanitization().getResponse();
        assertEquals(List.of("resp-db"), response.getSensitiveWords());
        assertFalse(response.isEnabled());
        assertFalse(response.isPreserveJsonStructure());
    }

    @Test
    @DisplayName("DB 行 JSON 损坏：抛异常且内存配置一个字段都不改")
    void loadInto_corruptJson_throwsWithoutPartiallyMutatingMemory() {
        when(repository.findById(SanitizationConfigStore.SCOPE_REQUEST))
                .thenReturn(Optional.of(requestRow("not-a-json", "[]", "false", "#", null, false)));
        when(repository.findById(SanitizationConfigStore.SCOPE_RESPONSE)).thenReturn(Optional.empty());

        assertThrows(IllegalStateException.class, () -> store.loadInto(securityProperties));

        SanitizationConfig.RequestSanitization request = securityProperties.getSanitization().getRequest();
        assertTrue(request.isEnabled());
        assertEquals("*", request.getMaskingChar());
        assertEquals(List.of("yml-word"), request.getSensitiveWords());
        assertEquals(List.of("\\d{11}"), request.getPiiPatterns());
    }

    @Test
    @DisplayName("保存：只写显式 scope，JSON 列带列表内容与操作者")
    void persist_writesOnlyRequestScope() {
        securityProperties.getSanitization().getRequest().setSensitiveWords(List.of("a", "b"));

        store.persist(securityProperties, true, false, "admin");

        ArgumentCaptor<SanitizationConfigEntity> captor = ArgumentCaptor.forClass(SanitizationConfigEntity.class);
        verify(repository).save(captor.capture());
        SanitizationConfigEntity saved = captor.getValue();
        assertEquals(SanitizationConfigStore.SCOPE_REQUEST, saved.getScope());
        assertEquals("[\"a\",\"b\"]", saved.getSensitiveWords());
        assertEquals("[\"\\\\d{11}\"]", saved.getPiiPatterns());
        assertEquals("admin", saved.getUpdatedBy());
        assertNotNull(saved.getUpdatedAt());
    }

    @Test
    @DisplayName("保存：两个 scope 一起保存时写两行，response 行带 preserveJsonStructure")
    void persist_writesBothScopes() {
        store.persist(securityProperties, true, true, "system");

        ArgumentCaptor<SanitizationConfigEntity> captor = ArgumentCaptor.forClass(SanitizationConfigEntity.class);
        verify(repository, org.mockito.Mockito.times(2)).save(captor.capture());
        List<SanitizationConfigEntity> saved = captor.getAllValues();
        assertEquals(SanitizationConfigStore.SCOPE_REQUEST, saved.get(0).getScope());
        assertEquals(SanitizationConfigStore.SCOPE_RESPONSE, saved.get(1).getScope());
        assertEquals(Boolean.TRUE, saved.get(1).getPreserveJsonStructure());
        assertNull(saved.get(0).getPreserveJsonStructure());
    }

    private static SanitizationConfigEntity requestRow(final String sensitiveWords,
                                                       final String piiPatterns,
                                                       final String enabled,
                                                       final String maskingChar,
                                                       final String whitelistUsers,
                                                       final boolean logSanitization) {
        return SanitizationConfigEntity.builder()
                .scope(SanitizationConfigStore.SCOPE_REQUEST)
                .enabled(Boolean.parseBoolean(enabled))
                .sensitiveWords(sensitiveWords)
                .piiPatterns(piiPatterns)
                .maskingChar(maskingChar)
                .whitelistUsers(whitelistUsers)
                .logSanitization(logSanitization)
                .failOnError(false)
                .updatedAt(java.time.LocalDateTime.now())
                .updatedBy("admin")
                .build();
    }

    private static SanitizationConfigEntity responseRow(final String sensitiveWords,
                                                        final String enabled,
                                                        final Boolean preserveJsonStructure) {
        return SanitizationConfigEntity.builder()
                .scope(SanitizationConfigStore.SCOPE_RESPONSE)
                .enabled(Boolean.parseBoolean(enabled))
                .sensitiveWords(sensitiveWords)
                .piiPatterns("[]")
                .maskingChar("*")
                .preserveJsonStructure(preserveJsonStructure)
                .logSanitization(true)
                .failOnError(false)
                .updatedAt(java.time.LocalDateTime.now())
                .updatedBy("admin")
                .build();
    }
}
