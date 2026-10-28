package org.unreal.modelrouter.monitor.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.unreal.modelrouter.monitor.dto.TokenUsageRecordDTO;

/**
 * #713 守卫测试：TokenUsage 落库记录必须带上 API Key。
 *
 * <p>缺陷背景：{@code TokenUsageRecorder} 组装 DTO 时从不设置 {@code apiKeyId}，而
 * {@code TokenUsageRepository} 的按 Key 聚合 SQL 带 {@code AND t.apiKeyId IS NOT NULL}
 * ⇒ 任何「按 API Key 统计 token」的报表恒为空集。</p>
 */
@DisplayName("#713 TokenUsage 记录必须携带 apiKeyId")
class TokenUsageRecorderApiKeyTest {

    @Test
    @DisplayName("REC-713-001 传入 API Key 时必须落进 DTO")
    void apiKeyIsPropagatedToRecord() {
        final TokenUsageService service = mock(TokenUsageService.class);
        final TokenUsageRecorder recorder = new TokenUsageRecorder(service);

        recorder.recordTokenUsageNoAuth("CHAT", "gpt-4o", "openai", "inst-1", "http://upstream",
                "key-abc", 10L, 20L, 30L, "trace-1", "203.0.113.9", true, null, null, 120L);

        final ArgumentCaptor<TokenUsageRecordDTO> captor = ArgumentCaptor.forClass(TokenUsageRecordDTO.class);
        verify(service).recordTokenUsage(captor.capture());
        assertThat(captor.getValue().getApiKeyId())
                .as("按 Key 统计依赖这个字段，null 等于没记")
                .isEqualTo("key-abc");
        assertThat(captor.getValue().getTotalTokens()).isEqualTo(30L);
    }

    @Test
    @DisplayName("REC-713-002 历史签名（无 Key）落 null，不得凭空造 Key")
    void legacyOverloadRecordsNullApiKey() {
        final TokenUsageService service = mock(TokenUsageService.class);
        final TokenUsageRecorder recorder = new TokenUsageRecorder(service);

        recorder.recordTokenUsageNoAuth("CHAT", "gpt-4o", "openai", "inst-1", "http://upstream",
                1L, 2L, 3L, "trace-2", "203.0.113.9", true, null, null, 10L);

        final ArgumentCaptor<TokenUsageRecordDTO> captor = ArgumentCaptor.forClass(TokenUsageRecordDTO.class);
        verify(service).recordTokenUsage(captor.capture());
        assertThat(captor.getValue().getApiKeyId()).isNull();
        assertThat(captor.getValue().getTraceId()).isEqualTo("trace-2");
    }
}
