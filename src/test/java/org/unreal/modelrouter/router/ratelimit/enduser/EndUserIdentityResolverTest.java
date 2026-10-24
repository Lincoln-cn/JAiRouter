package org.unreal.modelrouter.router.ratelimit.enduser;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.unreal.modelrouter.common.dto.ChatDTO;
import org.unreal.modelrouter.common.dto.EmbeddingDTO;
import org.unreal.modelrouter.common.dto.ImageEditDTO;
import org.unreal.modelrouter.common.dto.ImageGenerateDTO;
import org.unreal.modelrouter.router.anthropic.AnthropicMessagesRequest;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link EndUserIdentityResolver} 的身份解析与摘要化。
 *
 * <p>身份由调用方自报，**不是安全边界**；本类守的是三件事：来源优先级、
 * 规范化/摘要化（原文不落 key 与日志）、以及桶键同时绑定 API Key 与用户。</p>
 */
@DisplayName("终端用户标识解析：来源优先级 + 规范化 + 摘要")
class EndUserIdentityResolverTest {

    private static final int FP_LENGTH = 16;

    @Test
    @DisplayName("请求头优先于请求体")
    void headerWinsOverBody() {
        final EndUserIdentityResolver.Resolution resolution = EndUserIdentityResolver.resolve(
                "alice-from-header", chat("bob-from-body"), true, FP_LENGTH);

        assertEquals(EndUserIdentityResolver.Source.HEADER, resolution.source(), "请求头应优先");
        assertEquals("alice-from-header", resolution.identity());
        assertTrue(resolution.present());
    }

    @Test
    @DisplayName("header-enabled=false 时忽略请求头，改用请求体")
    void headerIgnoredWhenDisabled() {
        final EndUserIdentityResolver.Resolution resolution = EndUserIdentityResolver.resolve(
                "alice-from-header", chat("bob-from-body"), false, FP_LENGTH);

        assertEquals(EndUserIdentityResolver.Source.BODY_USER, resolution.source());
        assertEquals("bob-from-body", resolution.identity());
    }

    @Test
    @DisplayName("空白请求头不算来源，回退到请求体")
    void blankHeaderFallsBackToBody() {
        final EndUserIdentityResolver.Resolution resolution = EndUserIdentityResolver.resolve(
                "   ", chat("bob-from-body"), true, FP_LENGTH);

        assertEquals(EndUserIdentityResolver.Source.BODY_USER, resolution.source());
        assertEquals("bob-from-body", resolution.identity());
    }

    @Test
    @DisplayName("四种请求体的 user 字段都能取到")
    void bodyUserCoversFourDtos() {
        assertEquals("chat-user", EndUserIdentityResolver.resolve(null, chat("chat-user"), true, FP_LENGTH).identity());
        assertEquals("emb-user", EndUserIdentityResolver.resolve(null,
                new EmbeddingDTO.Request("m", "input", null, null, "emb-user", null), true, FP_LENGTH).identity());
        assertEquals("gen-user", EndUserIdentityResolver.resolve(null,
                new ImageGenerateDTO.Request("prompt", "m", 1, null, null, null, null, "gen-user"),
                true, FP_LENGTH).identity());
        assertEquals("edit-user", EndUserIdentityResolver.resolve(null,
                new ImageEditDTO.Request(null, "prompt", null, null, null, "m", 1, null, null, null,
                        null, null, null, null, "edit-user"), true, FP_LENGTH).identity());
    }

    @Test
    @DisplayName("Anthropic 请求走 metadata.user_id")
    void anthropicMetadataUserId() {
        final AnthropicMessagesRequest request = new AnthropicMessagesRequest("claude", null, null,
                List.of(), null, null, null, null, null, null, Map.of("user_id", "anthropic-user"));

        final EndUserIdentityResolver.Resolution resolution =
                EndUserIdentityResolver.resolve(null, request, true, FP_LENGTH);

        assertEquals(EndUserIdentityResolver.Source.ANTHROPIC_METADATA, resolution.source());
        assertEquals("anthropic-user", resolution.identity());
    }

    @Test
    @DisplayName("三个来源都没有时为 NONE（identity 与 fingerprint 均为 null）")
    void noSourceYieldsNone() {
        final EndUserIdentityResolver.Resolution resolution =
                EndUserIdentityResolver.resolve(null, chat(null), true, FP_LENGTH);

        assertFalse(resolution.present());
        assertEquals(EndUserIdentityResolver.Source.NONE, resolution.source());
        assertNull(resolution.identity());
        assertNull(resolution.fingerprint());
    }

    @Test
    @DisplayName("合法字符原样保留（只做 trim）")
    void safeIdentityKeptAsIs() {
        final EndUserIdentityResolver.Resolution resolution = EndUserIdentityResolver.resolve(
                "  alice@example.com  ", null, true, FP_LENGTH);

        assertEquals("alice@example.com", resolution.identity(), "合法字符集内的标识应原样使用");
    }

    @Test
    @DisplayName("非法字符或超长标识一律摘要化（原文不进 key）")
    void unsafeIdentityIsHashed() {
        final EndUserIdentityResolver.Resolution withSpace =
                EndUserIdentityResolver.resolve("alice smith", null, true, FP_LENGTH);
        final EndUserIdentityResolver.Resolution tooLong =
                EndUserIdentityResolver.resolve("a".repeat(65), null, true, FP_LENGTH);

        for (final EndUserIdentityResolver.Resolution resolution : List.of(withSpace, tooLong)) {
            assertTrue(resolution.present());
            assertTrue(resolution.identity().matches("[0-9a-f]{64}"),
                    "越界标识应被摘要成 64 位 hex，实际是：" + resolution.identity());
        }
        assertNotEquals(EndUserIdentityResolver.resolve("alice smith", null, true, FP_LENGTH).identity(),
                EndUserIdentityResolver.resolve("alice  smith", null, true, FP_LENGTH).identity(),
                "不同原文的摘要必须不同（否则碰撞即等于共用配额）");
    }

    @Test
    @DisplayName("桶键定长 16 位且同时绑定 API Key 与用户")
    void bucketKeyBindsApiKeyAndUser() {
        final String bucket = EndUserIdentityResolver.bucketKey("key-1", "alice");

        assertEquals(EndUserIdentityResolver.BUCKET_KEY_LENGTH, bucket.length(), "定长 16 位 hex");
        assertEquals(bucket, EndUserIdentityResolver.bucketKey("key-1", "alice"), "同输入同输出");
        assertNotEquals(bucket, EndUserIdentityResolver.bucketKey("key-2", "alice"),
                "不同 API Key 下同一用户标识必须互不影响");
        assertNotEquals(bucket, EndUserIdentityResolver.bucketKey("key-1", "bob"),
                "同一 API Key 下不同用户必须分开计数");
    }

    @Test
    @DisplayName("指纹长度可控、确定性、null 安全")
    void fingerprintIsDeterministicAndBounded() {
        final String first = EndUserIdentityResolver.fingerprint("alice", 12);

        assertNotNull(first);
        assertEquals(12, first.length());
        assertEquals(first, EndUserIdentityResolver.fingerprint("alice", 12), "同输入同输出");
        assertEquals(64, EndUserIdentityResolver.fingerprint("alice", 200).length(), "超长请求被钳制在 64");
        assertNull(EndUserIdentityResolver.fingerprint(null, 12), "null 入参返回 null");
    }

    private static ChatDTO.Request chat(final String user) {
        return new ChatDTO.Request("gpt-test", List.of(), false, null, null, null, null,
                null, null, null, user, null);
    }
}
