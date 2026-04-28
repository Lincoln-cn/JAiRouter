package org.unreal.modelrouter.router.adapter.builder;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.client.reactive.ClientHttpRequest;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.reactive.function.BodyInserter;
import org.unreal.modelrouter.common.dto.ImageEditDTO;
import org.unreal.modelrouter.common.dto.SttDTO;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * RequestBuilder 单元测试
 * 
 * @author AI Assistant
 * @since v2.2.3
 */
@ExtendWith(MockitoExtension.class)
class RequestBuilderTest {

    private RequestBuilder requestBuilder;

    @Mock
    private org.springframework.http.codec.multipart.FilePart mockFilePart;

    @BeforeEach
    void setUp() {
        requestBuilder = new RequestBuilder();
    }

    @Test
    void testCreateRequestBody_WithMultiValueMap() {
        // 准备测试数据
        MultiValueMap<String, String> formData = new LinkedMultiValueMap<>();
        formData.add("key1", "value1");
        formData.add("key2", "value2");

        // 执行测试
        BodyInserter<?, ? super ClientHttpRequest> result = requestBuilder.createRequestBody(formData);

        // 验证结果
        assertNotNull(result);
    }

    @Test
    void testCreateRequestBody_WithSttRequest() {
        // 准备测试数据
        SttDTO.Request sttRequest = new SttDTO.Request(
                "whisper-1",
                null, // file
                "en",
                null, // prompt
                "json",
                0.5
        );

        // 执行测试
        BodyInserter<?, ? super ClientHttpRequest> result = requestBuilder.createRequestBody(sttRequest);

        // 验证结果
        assertNotNull(result);
    }

    @Test
    void testCreateRequestBody_WithImageEditRequest() {
        // 准备测试数据
        ImageEditDTO.Request imageEditRequest = new ImageEditDTO.Request(
                null, // image
                "edit this image", // prompt
                null, // background
                null, // input_fidelity
                null, // mask
                "dall-e-3", // model
                1, // n
                null, // output_compression
                "url", // output_format
                null, // partial_images
                "standard", // quality
                "url", // response_format
                "1024x1024", // size
                null, // stream
                null // user
        );

        // 执行测试
        BodyInserter<?, ? super ClientHttpRequest> result = requestBuilder.createRequestBody(imageEditRequest);

        // 验证结果
        assertNotNull(result);
    }

    @Test
    void testCreateRequestBody_WithPlainObject() {
        // 准备测试数据
        TestRequest request = new TestRequest("test", 123);

        // 执行测试
        BodyInserter<?, ? super ClientHttpRequest> result = requestBuilder.createRequestBody(request);

        // 验证结果
        assertNotNull(result);
    }

    @Test
    void testCreateSttMultipartBody() {
        // 准备测试数据
        SttDTO.Request sttRequest = new SttDTO.Request(
                "whisper-1",
                null, // file
                "zh",
                "test prompt",
                "verbose_json",
                0.8
        );

        // 执行测试
        BodyInserter<?, ? super ClientHttpRequest> result = requestBuilder.createSttMultipartBody(sttRequest);

        // 验证结果
        assertNotNull(result);
    }

    @Test
    void testCreateImageEditMultipartBody() {
        // 准备测试数据
        ImageEditDTO.Request imageEditRequest = new ImageEditDTO.Request(
                List.of(), // empty image list
                "edit this image", // prompt
                "original", // background
                "high", // input_fidelity
                null, // mask
                "dall-e-3", // model
                1, // n
                null, // output_compression
                "png", // output_format
                null, // partial_images
                "standard", // quality
                "url", // response_format
                "1024x1024", // size
                null, // stream
                "test-user" // user
        );

        // 执行测试
        BodyInserter<?, ? super ClientHttpRequest> result = requestBuilder.createImageEditMultipartBody(imageEditRequest);

        // 验证结果
        assertNotNull(result);
    }

    @Test
    void testCreateJsonBody() {
        // 准备测试数据
        TestRequest request = new TestRequest("json-test", 456);

        // 执行测试
        BodyInserter<?, ? super ClientHttpRequest> result = requestBuilder.createJsonBody(request);

        // 验证结果
        assertNotNull(result);
    }

    @Test
    void testCreateFormData() {
        // 执行测试
        MultiValueMap<String, Object> result = requestBuilder.createFormData("key", "value");

        // 验证结果
        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals("value", result.getFirst("key"));
    }

    @Test
    void testAddFormField_WithStringValue() {
        // 准备测试数据
        MultiValueMap<String, Object> parts = new LinkedMultiValueMap<>();

        // 执行测试
        requestBuilder.addFormField(parts, "name", "test");

        // 验证结果
        assertEquals(1, parts.size());
        assertEquals("test", parts.getFirst("name"));
    }

    @Test
    void testAddFormField_WithNumberValue() {
        // 准备测试数据
        MultiValueMap<String, Object> parts = new LinkedMultiValueMap<>();

        // 执行测试
        requestBuilder.addFormField(parts, "count", 42);

        // 验证结果
        assertEquals(1, parts.size());
        assertEquals("42", parts.getFirst("count"));
    }

    @Test
    void testAddFormField_WithNullValue() {
        // 准备测试数据
        MultiValueMap<String, Object> parts = new LinkedMultiValueMap<>();

        // 执行测试
        requestBuilder.addFormField(parts, "nullable", null);

        // 验证结果 - null 值不应该被添加
        assertEquals(0, parts.size());
    }

    @Test
    void testAddFileField() {
        // 准备测试数据
        MultiValueMap<String, Object> parts = new LinkedMultiValueMap<>();

        // 执行测试
        requestBuilder.addFileField(parts, "file", mockFilePart);

        // 验证结果
        assertEquals(1, parts.size());
        assertTrue(parts.containsKey("file"));
    }

    @Test
    void testAddFileField_WithNullFile() {
        // 准备测试数据
        MultiValueMap<String, Object> parts = new LinkedMultiValueMap<>();

        // 执行测试
        requestBuilder.addFileField(parts, "file", null);

        // 验证结果 - null 文件不应该被添加
        assertEquals(0, parts.size());
    }

    // 测试用的内部类
    static class TestRequest {
        private final String name;
        private final int value;

        TestRequest(String name, int value) {
            this.name = name;
            this.value = value;
        }

        public String getName() {
            return name;
        }

        public int getValue() {
            return value;
        }
    }
}
