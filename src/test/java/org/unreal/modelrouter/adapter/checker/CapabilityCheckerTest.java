package org.unreal.modelrouter.router.adapter.checker;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.unreal.modelrouter.router.adapter.AdapterCapabilities;
import org.unreal.modelrouter.model.ModelServiceRegistry;
import reactor.core.publisher.Mono;

import static org.junit.jupiter.api.Assertions.*;

/**
 * CapabilityChecker 单元测试
 *
 * @author JAiRouter Team
 * @since v2.2.7
 */
class CapabilityCheckerTest {

    private CapabilityChecker capabilityChecker;
    private AdapterCapabilities allCapabilities;
    private AdapterCapabilities chatOnlyCapabilities;
    private AdapterCapabilities emptyCapabilities;

    @BeforeEach
    void setUp() {
        capabilityChecker = new CapabilityChecker();

        // 支持所有能力
        allCapabilities = AdapterCapabilities.all();

        // 仅支持聊天
        chatOnlyCapabilities = AdapterCapabilities.builder()
                .chat(true)
                .build();

        // 不支持任何能力
        emptyCapabilities = AdapterCapabilities.builder()
                .chat(false)
                .embedding(false)
                .rerank(false)
                .tts(false)
                .stt(false)
                .imageGenerate(false)
                .imageEdit(false)
                .streaming(false)
                .build();
    }

    @Test
    void testCheckCapability_Success() {
        // Given
        ModelServiceRegistry.ServiceType serviceType = ModelServiceRegistry.ServiceType.chat;

        // When
        Mono<ResponseEntity<String>> result = capabilityChecker.checkCapability(allCapabilities, serviceType);

        // Then
        assertNull(result, "应该返回 null 表示能力检查通过");
    }

    @Test
    void testCheckCapability_Failure() {
        // Given
        ModelServiceRegistry.ServiceType serviceType = ModelServiceRegistry.ServiceType.chat;

        // When
        Mono<ResponseEntity<String>> result = capabilityChecker.checkCapability(emptyCapabilities, serviceType);

        // Then
        assertNotNull(result, "应该返回错误响应");
        ResponseEntity<String> response = result.block();
        assertNotNull(response);
        assertEquals(HttpStatus.NOT_IMPLEMENTED, response.getStatusCode());
        assertTrue(response.getBody().contains("does not support"));
        assertTrue(response.getBody().contains("chat"));
    }

    @Test
    void testCheckCapability_NullCapabilities() {
        // Given
        ModelServiceRegistry.ServiceType serviceType = ModelServiceRegistry.ServiceType.chat;

        // When
        Mono<ResponseEntity<String>> result = capabilityChecker.checkCapability(null, serviceType);

        // Then
        assertNotNull(result, "应该返回错误响应");
        ResponseEntity<String> response = result.block();
        assertNotNull(response);
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        assertTrue(response.getBody().contains("not configured"));
    }

    @Test
    void testSupportsChat() {
        assertTrue(capabilityChecker.supportsChat(allCapabilities));
        assertTrue(capabilityChecker.supportsChat(chatOnlyCapabilities));
        assertFalse(capabilityChecker.supportsChat(emptyCapabilities));
        assertFalse(capabilityChecker.supportsChat(null));
    }

    @Test
    void testSupportsEmbedding() {
        assertTrue(capabilityChecker.supportsEmbedding(allCapabilities));
        assertFalse(capabilityChecker.supportsEmbedding(chatOnlyCapabilities));
        assertFalse(capabilityChecker.supportsEmbedding(emptyCapabilities));
        assertFalse(capabilityChecker.supportsEmbedding(null));
    }

    @Test
    void testSupportsRerank() {
        assertTrue(capabilityChecker.supportsRerank(allCapabilities));
        assertFalse(capabilityChecker.supportsRerank(chatOnlyCapabilities));
        assertFalse(capabilityChecker.supportsRerank(emptyCapabilities));
        assertFalse(capabilityChecker.supportsRerank(null));
    }

    @Test
    void testSupportsTts() {
        assertTrue(capabilityChecker.supportsTts(allCapabilities));
        assertFalse(capabilityChecker.supportsTts(chatOnlyCapabilities));
        assertFalse(capabilityChecker.supportsTts(emptyCapabilities));
        assertFalse(capabilityChecker.supportsTts(null));
    }

    @Test
    void testSupportsStt() {
        assertTrue(capabilityChecker.supportsStt(allCapabilities));
        assertFalse(capabilityChecker.supportsStt(chatOnlyCapabilities));
        assertFalse(capabilityChecker.supportsStt(emptyCapabilities));
        assertFalse(capabilityChecker.supportsStt(null));
    }

    @Test
    void testSupportsImageGenerate() {
        assertTrue(capabilityChecker.supportsImageGenerate(allCapabilities));
        assertFalse(capabilityChecker.supportsImageGenerate(chatOnlyCapabilities));
        assertFalse(capabilityChecker.supportsImageGenerate(emptyCapabilities));
        assertFalse(capabilityChecker.supportsImageGenerate(null));
    }

    @Test
    void testSupportsImageEdit() {
        assertTrue(capabilityChecker.supportsImageEdit(allCapabilities));
        assertFalse(capabilityChecker.supportsImageEdit(chatOnlyCapabilities));
        assertFalse(capabilityChecker.supportsImageEdit(emptyCapabilities));
        assertFalse(capabilityChecker.supportsImageEdit(null));
    }

    @Test
    void testSupportsStreaming() {
        assertTrue(capabilityChecker.supportsStreaming(allCapabilities));
        assertFalse(capabilityChecker.supportsStreaming(chatOnlyCapabilities));
        assertFalse(capabilityChecker.supportsStreaming(emptyCapabilities));
        assertFalse(capabilityChecker.supportsStreaming(null));
    }

    @Test
    void testGetSupportedServices_AllCapabilities() {
        // When
        String[] services = capabilityChecker.getSupportedServices(allCapabilities);

        // Then
        assertNotNull(services);
        assertEquals(7, services.length);
        assertArrayEquals(new String[]{
                "chat", "embedding", "rerank", "tts", "stt", "imgGen", "imgEdit"
        }, services);
    }

    @Test
    void testGetSupportedServices_ChatOnly() {
        // When
        String[] services = capabilityChecker.getSupportedServices(chatOnlyCapabilities);

        // Then
        assertNotNull(services);
        assertEquals(1, services.length);
        assertArrayEquals(new String[]{"chat"}, services);
    }

    @Test
    void testGetSupportedServices_EmptyCapabilities() {
        // When
        String[] services = capabilityChecker.getSupportedServices(emptyCapabilities);

        // Then
        assertNotNull(services);
        assertEquals(0, services.length);
    }

    @Test
    void testGetSupportedServices_NullCapabilities() {
        // When
        String[] services = capabilityChecker.getSupportedServices(null);

        // Then
        assertNotNull(services);
        assertEquals(0, services.length);
    }

    @Test
    void testCheckCapability_AllServiceTypes() {
        // 测试所有服务类型
        ModelServiceRegistry.ServiceType[] serviceTypes = ModelServiceRegistry.ServiceType.values();

        for (ModelServiceRegistry.ServiceType serviceType : serviceTypes) {
            // Given
            Mono<ResponseEntity<String>> result = capabilityChecker.checkCapability(allCapabilities, serviceType);

            // Then
            assertNull(result, "所有能力应该支持：" + serviceType);
        }
    }
}
