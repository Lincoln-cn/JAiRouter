package org.unreal.modelrouter.router.adapter.request;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.unreal.modelrouter.auth.security.service.ApiKeyService;
import org.unreal.modelrouter.monitor.service.TokenUsageRecorder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * #712 的回归测试：非流式响应不带 usage 时，网关仍要估算并记账。
 *
 * <p>修复前这类响应（自建推理服务、部分国产模型、被裁剪的兼容层）既不估算也不记账，
 * 配额停在预估值、Token 统计与计费对本应记账的调用完全不可见。</p>
 *
 * @since 3.5.5
 */
@DisplayName("#712 无 usage 的非流式响应必须估算记账")
class TokenUsageExtractionWithoutUsageTest {

    private static final String API_KEY_ID = "key-712";

    private static final String ADAPTER = "openai-compat";

    private static final String INSTANCE = "inst-712";

    private TokenUsageRecorder recorder;

    private ApiKeyService apiKeyService;

    private TokenUsageExtractor extractor;

    @BeforeEach
    void setUp() {
        recorder = mock(TokenUsageRecorder.class);
        apiKeyService = mock(ApiKeyService.class);
        extractor = new TokenUsageExtractor(new ObjectMapper(), recorder, apiKeyService, null, null);
    }

    @Test
    @DisplayName("响应没有 usage 时要按请求与响应文本估算出用量")
    void estimatesWhenUsageIsAbsent() {
        final String requestBody = "{\"model\":\"gpt-4\",\"messages\":["
                + "{\"role\":\"user\",\"content\":\"请用中文介绍一下你自己，并给出三个要点。\"}]}";
        final String responseBody = "{\"model\":\"gpt-4\",\"choices\":[{\"message\":{\"role\":\"assistant\","
                + "\"content\":\"我是一个模型路由网关。要点如下：第一，支持多协议；第二，支持配额；第三，支持审计。\"}}]}";

        extractor.extractAndRecordTokenUsage(responseBody, requestBody, ADAPTER, INSTANCE, API_KEY_ID, null);

        final ArgumentCaptor<Long> promptCaptor = ArgumentCaptor.forClass(Long.class);
        final ArgumentCaptor<Long> completionCaptor = ArgumentCaptor.forClass(Long.class);
        final ArgumentCaptor<Long> totalCaptor = ArgumentCaptor.forClass(Long.class);
        verify(recorder).recordTokenUsageNoAuth(eq("CHAT"), eq("gpt-4"), eq(ADAPTER), eq(INSTANCE),
                isNull(), eq(API_KEY_ID), promptCaptor.capture(), completionCaptor.capture(), totalCaptor.capture(),
                any(), isNull(), eq(true), isNull(), isNull(), isNull());

        assertThat(promptCaptor.getValue())
                .as("请求文本非空，prompt 估算必须为正")
                .isPositive();
        assertThat(completionCaptor.getValue())
                .as("响应文本非空，completion 估算必须为正")
                .isPositive();
        assertThat(totalCaptor.getValue())
                .as("total 必须等于两段估算之和")
                .isEqualTo(promptCaptor.getValue() + completionCaptor.getValue());
        verify(apiKeyService).updateTokenUsage(eq(API_KEY_ID), eq(totalCaptor.getValue()));
    }

    @Test
    @DisplayName("响应自带 usage 时必须用上游数值，不能被估算值覆盖")
    void usesUpstreamUsageWhenPresent() {
        final String responseBody = "{\"model\":\"gpt-4\",\"usage\":{\"prompt_tokens\":11,"
                + "\"completion_tokens\":7,\"total_tokens\":18},\"choices\":[{\"message\":{\"content\":\"ok\"}}]}";

        extractor.extractAndRecordTokenUsage(responseBody, "{\"messages\":[]}", ADAPTER, INSTANCE, API_KEY_ID, null);

        final ArgumentCaptor<Long> promptCaptor = ArgumentCaptor.forClass(Long.class);
        final ArgumentCaptor<Long> completionCaptor = ArgumentCaptor.forClass(Long.class);
        final ArgumentCaptor<Long> totalCaptor = ArgumentCaptor.forClass(Long.class);
        verify(recorder).recordTokenUsageNoAuth(eq("CHAT"), eq("gpt-4"), eq(ADAPTER), eq(INSTANCE),
                isNull(), eq(API_KEY_ID), promptCaptor.capture(), completionCaptor.capture(), totalCaptor.capture(),
                any(), isNull(), eq(true), isNull(), isNull(), isNull());

        assertThat(promptCaptor.getValue()).isEqualTo(11L);
        assertThat(completionCaptor.getValue()).isEqualTo(7L);
        assertThat(totalCaptor.getValue()).isEqualTo(18L);
    }

    @Test
    @DisplayName("既无 usage 也无任何文本时不写 0 token 记录")
    void recordsNothingWhenNothingToEstimate() {
        extractor.extractAndRecordTokenUsage("{}", null, ADAPTER, INSTANCE, API_KEY_ID, null);

        verify(recorder, never()).recordTokenUsageNoAuth(any(), any(), any(), any(), any(), any(), any(), any(),
                any(), any(), any(), any(), any(), any(), any());
        verifyNoInteractions(apiKeyService);
    }
}
