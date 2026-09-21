package org.unreal.modelrouter.auth.sanitization.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.unreal.modelrouter.auth.sanitization.SanitizationService;
import org.unreal.modelrouter.auth.sanitization.impl.DefaultSanitizationService;
import org.unreal.modelrouter.auth.security.config.properties.SanitizationConfig;
import org.unreal.modelrouter.auth.security.config.properties.SecurityProperties;
import org.unreal.modelrouter.auth.security.model.RuleType;
import org.unreal.modelrouter.auth.security.model.SanitizationRule;
import org.unreal.modelrouter.auth.security.model.SanitizationStrategy;
import org.unreal.modelrouter.common.controller.response.RouterResponse;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DisplayName("SanitizationConfigController 测试")
class SanitizationConfigControllerTest {

    private SecurityProperties securityProperties;
    private SanitizationService sanitizationService;
    private DefaultSanitizationService defaultSanitizationService;
    private SanitizationConfigController controller;

    @BeforeEach
    void setUp() {
        securityProperties = new SecurityProperties();
        sanitizationService = mock(SanitizationService.class);
        defaultSanitizationService = mock(DefaultSanitizationService.class);
        when(defaultSanitizationService.rebuildRulesFromProperties()).thenReturn(Mono.empty());
        when(sanitizationService.getAllRules()).thenReturn(Mono.just(List.of(
                SanitizationRule.builder()
                        .ruleId("request-pii-pattern-1")
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
        controller = new SanitizationConfigController(securityProperties, sanitizationService, defaultSanitizationService);
    }

    @Test
    @SuppressWarnings("unchecked")
    void getConfig_shouldReturnSnapshot() {
        ResponseEntity<RouterResponse<Map<String, Object>>> resp = controller.getConfig().block();
        assertEquals(200, resp.getStatusCode().value());
        Map<String, Object> data = resp.getBody().getData();
        assertNotNull(data);
        assertTrue(data.containsKey("request"));
        assertTrue(data.containsKey("response"));
        assertEquals(1, data.get("ruleCount"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void updateConfig_shouldApplyHotFields() {
        SanitizationConfigController.SanitizationUpdateRequest request =
                new SanitizationConfigController.SanitizationUpdateRequest();
        SanitizationConfigController.SanitizationSubUpdate req =
                new SanitizationConfigController.SanitizationSubUpdate();
        req.enabled = true;
        req.piiPatterns = List.of("\\d{11}");
        req.maskingChar = "*";
        request.request = req;

        ResponseEntity<RouterResponse<Map<String, Object>>> resp = controller.updateConfig(request).block();
        assertEquals(200, resp.getStatusCode().value());
        SanitizationConfig.RequestSanitization updated = securityProperties.getSanitization().getRequest();
        assertTrue(updated.isEnabled());
        assertEquals(List.of("\\d{11}"), updated.getPiiPatterns());
    }

    @Test
    void updateConfig_shouldRejectInvalidRegex() {
        SanitizationConfigController.SanitizationUpdateRequest request =
                new SanitizationConfigController.SanitizationUpdateRequest();
        SanitizationConfigController.SanitizationSubUpdate res =
                new SanitizationConfigController.SanitizationSubUpdate();
        res.piiPatterns = List.of("([unclosed");
        request.response = res;

        ResponseEntity<RouterResponse<Map<String, Object>>> resp = controller.updateConfig(request).block();
        assertEquals(400, resp.getStatusCode().value());
    }

    @Test
    @SuppressWarnings("unchecked")
    void updateConfig_shouldRejectRedosAndBlankPatterns() {
        SanitizationConfigController.SanitizationUpdateRequest request =
                new SanitizationConfigController.SanitizationUpdateRequest();
        SanitizationConfigController.SanitizationSubUpdate req =
                new SanitizationConfigController.SanitizationSubUpdate();
        req.piiPatterns = List.of("(a+)+", " ");
        request.request = req;

        ResponseEntity<RouterResponse<Map<String, Object>>> resp = controller.updateConfig(request).block();
        assertEquals(400, resp.getStatusCode().value());
        String message = resp.getBody().getMessage();
        assertTrue(message.contains("request.piiPatterns"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void updateConfig_shouldAcceptDefaultPiiPatterns() {
        SanitizationConfigController.SanitizationUpdateRequest request =
                new SanitizationConfigController.SanitizationUpdateRequest();
        SanitizationConfigController.SanitizationSubUpdate req =
                new SanitizationConfigController.SanitizationSubUpdate();
        req.piiPatterns = List.of(
                "\\d{11}",
                "[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}",
                "\\b\\d{4}[\\s-]?\\d{4}[\\s-]?\\d{4}[\\s-]?\\d{4}\\b"
        );
        request.request = req;

        ResponseEntity<RouterResponse<Map<String, Object>>> resp = controller.updateConfig(request).block();
        assertEquals(200, resp.getStatusCode().value());
        assertEquals(3, securityProperties.getSanitization().getRequest().getPiiPatterns().size());
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
        assertTrue(((List<String>) data.get("matchedRuleIds")).contains("request-pii-pattern-1"));
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
        assertEquals("request-pii-pattern-1", rules.get(0).get("ruleId"));
        assertEquals("PII_PATTERN", rules.get(0).get("type"));
    }
}
