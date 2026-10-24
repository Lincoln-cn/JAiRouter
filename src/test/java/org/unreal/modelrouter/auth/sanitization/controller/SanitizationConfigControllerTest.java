package org.unreal.modelrouter.auth.sanitization.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.unreal.modelrouter.auth.sanitization.SanitizationService;
import org.unreal.modelrouter.auth.sanitization.impl.DefaultSanitizationService;
import org.unreal.modelrouter.auth.sanitization.persistence.SanitizationConfigStore;
import org.unreal.modelrouter.auth.security.config.properties.SanitizationConfig;
import org.unreal.modelrouter.auth.security.config.properties.SecurityProperties;
import org.unreal.modelrouter.auth.security.model.RuleType;
import org.unreal.modelrouter.auth.security.model.SanitizationRule;
import org.unreal.modelrouter.auth.security.model.SanitizationStrategy;
import org.unreal.modelrouter.common.controller.response.RouterResponse;
import org.unreal.modelrouter.common.exception.ApiException;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link SanitizationConfigController} 测试。
 *
 * <p>请求侧配置面已在 issue #601 中整体移除：控制器只读写 response，请求体只接受
 * {@code {"response": {...}}}，多传 {@code request} 会被 Jackson 忽略。</p>
 */
@DisplayName("SanitizationConfigController 测试")
class SanitizationConfigControllerTest {

    private SecurityProperties securityProperties;
    private SanitizationService sanitizationService;
    private DefaultSanitizationService defaultSanitizationService;
    private SanitizationConfigStore configStore;
    private SanitizationConfigController controller;

    @BeforeEach
    void setUp() {
        securityProperties = new SecurityProperties();
        sanitizationService = mock(SanitizationService.class);
        defaultSanitizationService = mock(DefaultSanitizationService.class);
        configStore = mock(SanitizationConfigStore.class);
        when(defaultSanitizationService.rebuildRulesFromProperties()).thenReturn(Mono.empty());
        when(sanitizationService.getAllRules()).thenReturn(Mono.just(List.of(
                SanitizationRule.builder()
                        .ruleId("response-pii-pattern-1")
                        .name("phone")
                        .type(RuleType.PII_PATTERN)
                        .pattern("\\d{11}")
                        .strategy(SanitizationStrategy.MASK)
                        .enabled(true)
                        .replacementChar("*")
                        .build()
        )));
        when(sanitizationService.sanitizeForStorage(anyString(), anyString()))
                .thenAnswer(inv -> Mono.just(inv.getArgument(0, String.class).replaceAll("\\d{11}", "***********")));
        controller = new SanitizationConfigController(
                securityProperties, sanitizationService, defaultSanitizationService, configStore);
    }

    @Test
    @SuppressWarnings("unchecked")
    void getConfig_shouldReturnResponseOnlySnapshot() {
        ResponseEntity<RouterResponse<Map<String, Object>>> resp = controller.getConfig().block();
        assertEquals(200, resp.getStatusCode().value());
        Map<String, Object> data = resp.getBody().getData();
        assertNotNull(data);
        assertTrue(data.containsKey("response"));
        assertFalse(data.containsKey("request"), "请求侧配置面已移除");
        assertEquals(1, data.get("ruleCount"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void updateConfig_shouldApplyHotFields() {
        SanitizationConfigController.SanitizationUpdateRequest request =
                new SanitizationConfigController.SanitizationUpdateRequest();
        SanitizationConfigController.SanitizationSubUpdate res =
                new SanitizationConfigController.SanitizationSubUpdate();
        res.enabled = false;
        res.piiPatterns = List.of("\\d{11}");
        res.maskingChar = "#";
        request.response = res;

        ResponseEntity<RouterResponse<Map<String, Object>>> resp = controller.updateConfig(request).block();
        assertEquals(200, resp.getStatusCode().value());
        SanitizationConfig.ResponseSanitization updated = securityProperties.getSanitization().getResponse();
        assertFalse(updated.isEnabled());
        assertEquals(List.of("\\d{11}"), updated.getPiiPatterns());
        assertEquals("#", updated.getMaskingChar());
    }

    @Test
    void updateConfig_shouldRequireResponseSubConfig() {
        SanitizationConfigController.SanitizationUpdateRequest request =
                new SanitizationConfigController.SanitizationUpdateRequest();

        ApiException ex = assertThrows(ApiException.class, () -> controller.updateConfig(request).block());
        assertEquals("INVALID_REQUEST", ex.getErrorCode());
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
        assertTrue(ex.getMessage().contains("response"));
    }

    @Test
    void updateConfig_shouldRejectInvalidRegex() {
        SanitizationConfigController.SanitizationUpdateRequest request =
                new SanitizationConfigController.SanitizationUpdateRequest();
        SanitizationConfigController.SanitizationSubUpdate res =
                new SanitizationConfigController.SanitizationSubUpdate();
        res.piiPatterns = List.of("([unclosed");
        request.response = res;

        // issue #94：参数校验失败以 ApiException 形式抛出（400），而不是 200 + success=false。
        ApiException ex = assertThrows(ApiException.class, () -> controller.updateConfig(request).block());
        assertEquals("INVALID_REQUEST", ex.getErrorCode());
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
    }

    @Test
    void updateConfig_shouldRejectRedosAndBlankPatterns() {
        SanitizationConfigController.SanitizationUpdateRequest request =
                new SanitizationConfigController.SanitizationUpdateRequest();
        SanitizationConfigController.SanitizationSubUpdate res =
                new SanitizationConfigController.SanitizationSubUpdate();
        res.piiPatterns = List.of("(a+)+", " ");
        request.response = res;

        ApiException ex = assertThrows(ApiException.class, () -> controller.updateConfig(request).block());
        assertEquals("INVALID_REQUEST", ex.getErrorCode());
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
        assertTrue(ex.getMessage().contains("response.piiPatterns"));
    }

    @Test
    void updateConfig_shouldAcceptDefaultPiiPatterns() {
        SanitizationConfigController.SanitizationUpdateRequest request =
                new SanitizationConfigController.SanitizationUpdateRequest();
        SanitizationConfigController.SanitizationSubUpdate res =
                new SanitizationConfigController.SanitizationSubUpdate();
        res.piiPatterns = List.of(
                "\\d{11}",
                "[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}",
                "\\b\\d{4}[\\s-]?\\d{4}[\\s-]?\\d{4}[\\s-]?\\d{4}\\b"
        );
        request.response = res;

        ResponseEntity<RouterResponse<Map<String, Object>>> resp = controller.updateConfig(request).block();
        assertEquals(200, resp.getStatusCode().value());
        assertEquals(3, securityProperties.getSanitization().getResponse().getPiiPatterns().size());
    }

    @Test
    @SuppressWarnings("unchecked")
    void updateConfig_shouldPreserveFieldsNotExposedByUpdateApi() {
        SanitizationConfig.ResponseSanitization response = securityProperties.getSanitization().getResponse();
        response.getScope().setMode("include");
        response.getScope().setPatterns(List.of("/v1/**"));
        response.setMaxBufferedBytes(2048L);
        response.setSanitizeSse(false);

        SanitizationConfigController.SanitizationUpdateRequest request =
                new SanitizationConfigController.SanitizationUpdateRequest();
        SanitizationConfigController.SanitizationSubUpdate res =
                new SanitizationConfigController.SanitizationSubUpdate();
        res.enabled = false;
        request.response = res;

        controller.updateConfig(request).block();

        SanitizationConfig.ResponseSanitization updated = securityProperties.getSanitization().getResponse();
        assertEquals("include", updated.getScope().getMode(), "热更新不得重置 #560 的生效范围");
        assertEquals(List.of("/v1/**"), updated.getScope().getPatterns());
        assertEquals(2048L, updated.getMaxBufferedBytes(), "热更新不得重置 #594 的缓冲上限");
        assertFalse(updated.isSanitizeSse());
    }

    @Test
    @SuppressWarnings("unchecked")
    void testSanitization_shouldMaskPhone() {
        SanitizationConfigController.SanitizationTestRequest testRequest =
                new SanitizationConfigController.SanitizationTestRequest();
        testRequest.sample = "phone 13800138000";
        ResponseEntity<RouterResponse<Map<String, Object>>> resp = controller.testSanitization(testRequest).block();
        assertEquals(200, resp.getStatusCode().value());
        Map<String, Object> data = resp.getBody().getData();
        assertEquals("phone ***********", data.get("after"));
        assertTrue(((List<String>) data.get("matchedRuleIds")).contains("response-pii-pattern-1"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void testSanitization_nonJsonSample_declaredAsJson_shouldMaskAsText() {
        // 控制台试脱敏常见：样例是聊天原文，contentType 误传/默认 application/json
        when(sanitizationService.sanitizeForStorage(anyString(), eq("text/plain")))
                .thenAnswer(inv -> Mono.just(inv.getArgument(0, String.class).replaceAll("\\d{11}", "***********")));
        when(sanitizationService.sanitizeForStorage(anyString(), eq("application/json")))
                .thenAnswer(inv -> Mono.just(inv.getArgument(0, String.class)));

        SanitizationConfigController.SanitizationTestRequest req =
                new SanitizationConfigController.SanitizationTestRequest();
        req.sample = "phone 13800138000";
        req.contentType = "application/json";

        Map<String, Object> data = controller.testSanitization(req).block().getBody().getData();
        assertEquals("phone ***********", data.get("after"));
        assertEquals("text/plain", data.get("contentType"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void testSanitization_realJsonSample_shouldKeepJsonContentType() {
        when(sanitizationService.sanitizeForStorage(anyString(), eq("application/json")))
                .thenAnswer(inv -> Mono.just("{\"phone\":\"138********\"}"));

        SanitizationConfigController.SanitizationTestRequest req =
                new SanitizationConfigController.SanitizationTestRequest();
        req.sample = "{\"phone\":\"13800138000\"}";
        req.contentType = "application/json";

        Map<String, Object> data = controller.testSanitization(req).block().getBody().getData();
        assertEquals("application/json", data.get("contentType"));
        assertEquals("{\"phone\":\"138********\"}", data.get("after"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void getRules_shouldReturnMappedFields() {
        ResponseEntity<RouterResponse<List<Map<String, Object>>>> resp = controller.getRules().block();
        assertEquals(200, resp.getStatusCode().value());
        List<Map<String, Object>> rules = resp.getBody().getData();
        assertEquals(1, rules.size());
        assertEquals("response-pii-pattern-1", rules.get(0).get("ruleId"));
        assertEquals("PII_PATTERN", rules.get(0).get("type"));
    }

    // ==================== TA2：脱敏配置持久化 ====================

    @Test
    @SuppressWarnings("unchecked")
    void updateConfig_shouldPersistBeforeRebuild() {
        SanitizationConfigController.SanitizationUpdateRequest request =
                new SanitizationConfigController.SanitizationUpdateRequest();
        SanitizationConfigController.SanitizationSubUpdate res =
                new SanitizationConfigController.SanitizationSubUpdate();
        res.enabled = false;
        res.sensitiveWords = List.of("secret-word");
        request.response = res;

        ResponseEntity<RouterResponse<Map<String, Object>>> resp = controller.updateConfig(request).block();
        assertEquals(200, resp.getStatusCode().value());
        assertEquals("脱敏配置已更新", resp.getBody().getMessage());

        InOrder order = inOrder(configStore, defaultSanitizationService);
        order.verify(configStore).persist(securityProperties, "system");
        order.verify(defaultSanitizationService).rebuildRulesFromProperties();
        assertFalse(securityProperties.getSanitization().getResponse().isEnabled());
    }

    @Test
    void updateConfig_shouldRejectInvalidRegexWithoutTouchingMemoryOrDatabase() {
        SanitizationConfigController.SanitizationUpdateRequest request =
                new SanitizationConfigController.SanitizationUpdateRequest();
        SanitizationConfigController.SanitizationSubUpdate res =
                new SanitizationConfigController.SanitizationSubUpdate();
        res.enabled = false;
        res.maskingChar = "#";
        res.piiPatterns = List.of("([unclosed");
        request.response = res;

        ApiException ex = assertThrows(ApiException.class, () -> controller.updateConfig(request).block());
        assertEquals("INVALID_REQUEST", ex.getErrorCode());

        // 旧实现先写 enabled/maskingChar 再校验 piiPatterns，校验失败会留下半更新；现在一个字段都不改。
        SanitizationConfig.ResponseSanitization current = securityProperties.getSanitization().getResponse();
        assertTrue(current.isEnabled());
        assertEquals("*", current.getMaskingChar());
        assertEquals(List.of(), current.getPiiPatterns());
        verify(configStore, never()).persist(any(SecurityProperties.class), anyString());
        verify(defaultSanitizationService, never()).rebuildRulesFromProperties();
    }

    @Test
    void updateConfig_shouldRollbackMemoryWhenPersistFails() {
        SanitizationConfig.ResponseSanitization response = securityProperties.getSanitization().getResponse();
        response.setEnabled(true);
        response.setSensitiveWords(List.of("old-word"));
        response.setPiiPatterns(List.of("\\d{11}"));
        response.setMaskingChar("*");

        doThrow(new IllegalStateException("database down"))
                .when(configStore).persist(any(SecurityProperties.class), anyString());

        SanitizationConfigController.SanitizationUpdateRequest request =
                new SanitizationConfigController.SanitizationUpdateRequest();
        SanitizationConfigController.SanitizationSubUpdate res =
                new SanitizationConfigController.SanitizationSubUpdate();
        res.enabled = false;
        res.sensitiveWords = List.of("new-word");
        res.piiPatterns = List.of("[a-z]+");
        res.maskingChar = "#";
        request.response = res;

        ApiException ex = assertThrows(ApiException.class, () -> controller.updateConfig(request).block());
        assertEquals("INTERNAL_ERROR", ex.getErrorCode());
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, ex.getStatus());

        // 落库失败 ⇒ 内存回到更新前，且不重建规则（否则运行态会按未持久化的配置生效）
        SanitizationConfig.ResponseSanitization after = securityProperties.getSanitization().getResponse();
        assertTrue(after.isEnabled());
        assertEquals(List.of("old-word"), after.getSensitiveWords());
        assertEquals(List.of("\\d{11}"), after.getPiiPatterns());
        assertEquals("*", after.getMaskingChar());
        verify(defaultSanitizationService, never()).rebuildRulesFromProperties();
    }

    @Test
    @SuppressWarnings("unchecked")
    void getConfig_shouldExposeEffectiveScope() {
        // 默认（未配置 scope.patterns）⇒ 走内置排除集合，回显必须标明来源，避免运维误以为「已自定义」
        ResponseEntity<RouterResponse<Map<String, Object>>> resp = controller.getConfig().block();
        Map<String, Object> response = (Map<String, Object>) resp.getBody().getData().get("response");
        Map<String, Object> scope = (Map<String, Object>) response.get("scope");
        assertEquals("exclude", scope.get("mode"));
        assertEquals(true, scope.get("usingBuiltInDefaults"));
        assertEquals("builtin-defaults", response.get("scopeSource"));

        // 显式配置后来源翻转为 configuration
        securityProperties.getSanitization().getResponse().getScope().setMode("include");
        securityProperties.getSanitization().getResponse().getScope().setPatterns(List.of("/v1/**"));
        resp = controller.getConfig().block();
        response = (Map<String, Object>) resp.getBody().getData().get("response");
        scope = (Map<String, Object>) response.get("scope");
        assertEquals("include", scope.get("mode"));
        assertEquals(false, scope.get("usingBuiltInDefaults"));
        assertEquals(List.of("/v1/**"), scope.get("patterns"));
        assertEquals("configuration", response.get("scopeSource"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void previewScope_shouldRespectIncludeMode() {
        securityProperties.getSanitization().getResponse().getScope().setMode("include");
        securityProperties.getSanitization().getResponse().getScope().setPatterns(List.of("/v1/**"));

        SanitizationConfigController.ScopePreviewRequest inScope =
                new SanitizationConfigController.ScopePreviewRequest();
        inScope.path = "/v1/chat/completions";
        Map<String, Object> hit = (Map<String, Object>) controller.previewScope(inScope).block().getBody().getData();
        assertEquals(true, hit.get("shouldSanitize"));
        assertEquals("/v1/**", hit.get("matchedPattern"));

        SanitizationConfigController.ScopePreviewRequest outOfScope =
                new SanitizationConfigController.ScopePreviewRequest();
        outOfScope.path = "/api/security/sanitization/config";
        Map<String, Object> miss = (Map<String, Object>) controller.previewScope(outOfScope).block().getBody().getData();
        assertEquals(false, miss.get("shouldSanitize"));
        assertNull(miss.get("matchedPattern"));
    }

    @Test
    void previewScope_shouldRejectBlankPath() {
        SanitizationConfigController.ScopePreviewRequest request =
                new SanitizationConfigController.ScopePreviewRequest();
        request.path = "  ";
        ApiException ex = assertThrows(ApiException.class, () -> controller.previewScope(request).block());
        assertEquals("INVALID_REQUEST", ex.getErrorCode());
    }
}
