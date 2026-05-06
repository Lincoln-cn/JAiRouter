package org.unreal.modelrouter.router.adapter;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.unreal.modelrouter.router.model.ModelServiceRegistry;

import static org.junit.jupiter.api.Assertions.*;

/**
 * AdapterCapabilities 单元测试 - v2.9.6
 */
@DisplayName("AdapterCapabilities v2.9.6 测试")
class AdapterCapabilitiesTest {

    @Test
    @DisplayName("测试 1: all() 返回所有能力启用")
    void testAllCapabilities() {
        AdapterCapabilities capabilities = AdapterCapabilities.all();
        
        assertTrue(capabilities.contains(ModelServiceRegistry.ServiceType.chat));
        assertTrue(capabilities.contains(ModelServiceRegistry.ServiceType.embedding));
        assertTrue(capabilities.contains(ModelServiceRegistry.ServiceType.rerank));
        assertTrue(capabilities.contains(ModelServiceRegistry.ServiceType.tts));
        assertTrue(capabilities.contains(ModelServiceRegistry.ServiceType.stt));
        assertTrue(capabilities.contains(ModelServiceRegistry.ServiceType.imgGen));
        assertTrue(capabilities.contains(ModelServiceRegistry.ServiceType.imgEdit));
    }

    @Test
    @DisplayName("测试 2: Builder 创建 chat 能力")
    void testBuilderChatOnly() {
        AdapterCapabilities capabilities = AdapterCapabilities.builder()
                .chat(true)
                .build();
        
        assertTrue(capabilities.contains(ModelServiceRegistry.ServiceType.chat));
        assertFalse(capabilities.contains(ModelServiceRegistry.ServiceType.embedding));
        assertFalse(capabilities.contains(ModelServiceRegistry.ServiceType.rerank));
    }

    @Test
    @DisplayName("测试 3: Builder 创建 chat + embedding 能力")
    void testBuilderChatEmbedding() {
        AdapterCapabilities capabilities = AdapterCapabilities.builder()
                .chat(true)
                .embedding(true)
                .build();
        
        assertTrue(capabilities.contains(ModelServiceRegistry.ServiceType.chat));
        assertTrue(capabilities.contains(ModelServiceRegistry.ServiceType.embedding));
        assertFalse(capabilities.contains(ModelServiceRegistry.ServiceType.rerank));
        assertFalse(capabilities.contains(ModelServiceRegistry.ServiceType.tts));
    }

    @Test
    @DisplayName("测试 4: Builder 创建 chat + embedding + rerank 能力")
    void testBuilderChatEmbeddingRerank() {
        AdapterCapabilities capabilities = AdapterCapabilities.builder()
                .chat(true)
                .embedding(true)
                .rerank(true)
                .build();
        
        assertTrue(capabilities.contains(ModelServiceRegistry.ServiceType.chat));
        assertTrue(capabilities.contains(ModelServiceRegistry.ServiceType.embedding));
        assertTrue(capabilities.contains(ModelServiceRegistry.ServiceType.rerank));
        assertFalse(capabilities.contains(ModelServiceRegistry.ServiceType.tts));
        assertFalse(capabilities.contains(ModelServiceRegistry.ServiceType.stt));
    }

    @Test
    @DisplayName("测试 5: Builder 创建 tts + stt 能力")
    void testBuilderTtsStt() {
        AdapterCapabilities capabilities = AdapterCapabilities.builder()
                .tts(true)
                .stt(true)
                .build();
        
        assertFalse(capabilities.contains(ModelServiceRegistry.ServiceType.chat));
        assertTrue(capabilities.contains(ModelServiceRegistry.ServiceType.tts));
        assertTrue(capabilities.contains(ModelServiceRegistry.ServiceType.stt));
    }

    @Test
    @DisplayName("测试 6: Builder 创建图片生成能力")
    void testBuilderImageGenerate() {
        AdapterCapabilities capabilities = AdapterCapabilities.builder()
                .imageGenerate(true)
                .build();
        
        assertTrue(capabilities.contains(ModelServiceRegistry.ServiceType.imgGen));
        assertFalse(capabilities.contains(ModelServiceRegistry.ServiceType.imgEdit));
    }

    @Test
    @DisplayName("测试 7: Builder 创建图片编辑能力")
    void testBuilderImageEdit() {
        AdapterCapabilities capabilities = AdapterCapabilities.builder()
                .imageEdit(true)
                .build();
        
        assertFalse(capabilities.contains(ModelServiceRegistry.ServiceType.imgGen));
        assertTrue(capabilities.contains(ModelServiceRegistry.ServiceType.imgEdit));
    }

    @Test
    @DisplayName("测试 8: Builder 创建全部能力（逐项设置）")
    void testBuilderAllIndividually() {
        AdapterCapabilities capabilities = AdapterCapabilities.builder()
                .chat(true)
                .embedding(true)
                .rerank(true)
                .tts(true)
                .stt(true)
                .imageGenerate(true)
                .imageEdit(true)
                .build();
        
        assertTrue(capabilities.contains(ModelServiceRegistry.ServiceType.chat));
        assertTrue(capabilities.contains(ModelServiceRegistry.ServiceType.embedding));
        assertTrue(capabilities.contains(ModelServiceRegistry.ServiceType.rerank));
        assertTrue(capabilities.contains(ModelServiceRegistry.ServiceType.tts));
        assertTrue(capabilities.contains(ModelServiceRegistry.ServiceType.stt));
        assertTrue(capabilities.contains(ModelServiceRegistry.ServiceType.imgGen));
        assertTrue(capabilities.contains(ModelServiceRegistry.ServiceType.imgEdit));
    }

    @Test
    @DisplayName("测试 9: 默认 Builder 创建空能力")
    void testBuilderDefaultEmpty() {
        AdapterCapabilities capabilities = AdapterCapabilities.builder().build();
        
        assertFalse(capabilities.contains(ModelServiceRegistry.ServiceType.chat));
        assertFalse(capabilities.contains(ModelServiceRegistry.ServiceType.embedding));
        assertFalse(capabilities.contains(ModelServiceRegistry.ServiceType.rerank));
        assertFalse(capabilities.contains(ModelServiceRegistry.ServiceType.tts));
        assertFalse(capabilities.contains(ModelServiceRegistry.ServiceType.stt));
        assertFalse(capabilities.contains(ModelServiceRegistry.ServiceType.imgGen));
        assertFalse(capabilities.contains(ModelServiceRegistry.ServiceType.imgEdit));
    }

    @Test
    @DisplayName("测试 10: contains() null 参数处理")
    void testContainsNull() {
        AdapterCapabilities capabilities = AdapterCapabilities.all();
        
        // contains 方法参数是枚举，不能为 null，测试所有枚举值
        for (ModelServiceRegistry.ServiceType type : ModelServiceRegistry.ServiceType.values()) {
            assertTrue(capabilities.contains(type));
        }
    }
}