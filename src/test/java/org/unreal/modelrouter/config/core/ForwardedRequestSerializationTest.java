package org.unreal.modelrouter.config.core;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.unreal.modelrouter.common.dto.ChatDTO;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 转发请求体的序列化契约（#976）。
 *
 * <p>背景：Spring Boot 4.1 的 HTTP 转换器用 Jackson 3，而业务代码当时还在用 Jackson 2。
 * 两套并存时，反序列化出的节点被另一个序列化器处理，转发给上游的 body 变成了
 * {@code {"array":false,"nodeType":"OBJECT",...}} 这种 getter 输出 —— 代理链路整体不可用，
 * 而 4997 例单测全绿（测试里的请求体是手工拼的，没走「反序列化 → 再序列化」）。
 *
 * <p>这条用例守的是那一环：用<b>与生产同一个 mapper</b> 把请求反序列化成 DTO，
 * 再序列化回 JSON，断言关键字段还在。它不能替代端到端验证，但能在 CI 里拦住
 * 「又换了一套序列化器」这类回归。
 *
 * @since 3.7.0
 */
@DisplayName("转发请求体的序列化契约（#976）")
class ForwardedRequestSerializationTest {

    private final ObjectMapper mapper = new JacksonConfig().objectMapper();

    private static final String CLIENT_BODY = """
            {"model":"qwen3:4b","messages":[{"role":"user","content":"hi"}],"temperature":0.7}
            """;

    @Test
    @DisplayName("客户端 JSON 反序列化后再序列化，model 与 messages 都还在")
    void roundTripKeepsModelAndMessages() {
        final ChatDTO.Request request = mapper.readValue(CLIENT_BODY, ChatDTO.Request.class);

        assertThat(request.model()).isEqualTo("qwen3:4b");
        assertThat(request.messages()).hasSize(1);

        final String forwarded = mapper.writeValueAsString(request);

        assertThat(forwarded)
                .as("转发体必须是正常的 chat 请求，而不是 JsonNode 的属性输出")
                .contains("\"model\":\"qwen3:4b\"")
                .contains("\"messages\"")
                .contains("\"role\":\"user\"")
                .contains("\"content\":\"hi\"")
                .doesNotContain("nodeType")
                .doesNotContain("containerNode");
    }

    @Test
    @DisplayName("Jackson 3 下 primitive 字段缺省不抛异常（保持 3.x 行为）")
    void missingPrimitiveFieldsDoNotFail() {
        // ApiKey 等 DTO 用 primitive 字段且允许缺省；Jackson 3 默认会抛
        // 「Cannot map null into type long」，本仓显式关了该 feature。
        final String sparse = "{\"model\":\"m\",\"messages\":[]}";

        final ChatDTO.Request request = mapper.readValue(sparse, ChatDTO.Request.class);

        assertThat(request.model()).isEqualTo("m");
        assertThat(request.messages()).isEmpty();
    }
}
