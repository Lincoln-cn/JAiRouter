package org.unreal.modelrouter.router.ratelimit.enduser;

import org.unreal.modelrouter.common.dto.ChatDTO;
import org.unreal.modelrouter.common.dto.EmbeddingDTO;
import org.unreal.modelrouter.common.dto.ImageEditDTO;
import org.unreal.modelrouter.common.dto.ImageGenerateDTO;
import org.unreal.modelrouter.router.anthropic.AnthropicMessagesRequest;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * 终端用户标识解析（用户级限流）。
 *
 * <p>解析顺序：请求头 {@value EndUserRateLimitProperties#HEADER_NAME}（可关闭）→ 请求体
 * {@code user}（OpenAI 兼容字段，按 DTO 分别取值）→ Anthropic {@code metadata.user_id}。
 * 请求体<b>不重新解析</b>：调用方把已反序列化好的 DTO 传进来（主链路由
 * {@code ServiceRequestHandler} 的 {@code JAIR_REQUEST_DTO} 属性提供）。</p>
 *
 * <p><b>为什么写成纯函数</b>：这套取值与规范化规则是限流最容易出错的地方（头部为空、
 * 字符集越界、超长、DTO 类型不匹配），做成无 Spring 依赖的静态工具才能穷举覆盖。</p>
 *
 * <p><b>隐私约束</b>：终端用户标识的原文绝不进入 Redis key、指标标签、日志或错误消息——
 * 对外只暴露 {@link #fingerprint(String, int)} 的不可逆摘要。</p>
 *
 * @author JAiRouter Team
 * @since 3.5.2
 */
public final class EndUserIdentityResolver {

    /** 标识原文的最大长度：超过即摘要化，避免超长串撑大 key。 */
    private static final int MAX_IDENTITY_LENGTH = 64;

    /** 允许直接作为标识使用的字符集（越界一律摘要化）。 */
    private static final Pattern SAFE_IDENTITY =
            Pattern.compile("^[A-Za-z0-9._:@/-]{1," + MAX_IDENTITY_LENGTH + "}$");

    /** 限流桶键的摘要长度（hex 字符数）。 */
    public static final int BUCKET_KEY_LENGTH = 16;

    /** Anthropic 请求里约定俗成的终端用户字段。 */
    private static final String ANTHROPIC_USER_KEY = "user_id";

    /** 十六进制字符表。 */
    private static final char[] HEX = "0123456789abcdef".toCharArray();

    private EndUserIdentityResolver() {
        // 工具类，禁止实例化
    }

    /**
     * 标识来源。
     */
    public enum Source {

        /** 请求头 {@value EndUserRateLimitProperties#HEADER_NAME}。 */
        HEADER,

        /** 请求体 {@code user}（Chat / Embedding / 图片生成 / 图片编辑）。 */
        BODY_USER,

        /** Anthropic 请求的 {@code metadata.user_id}。 */
        ANTHROPIC_METADATA,

        /** 三个来源都没取到可用值。 */
        NONE
    }

    /**
     * 解析结果。
     *
     * @param source      生效来源；缺失时为 {@link Source#NONE}
     * @param identity    规范化后的标识（可直接参与桶键计算），缺失时为 {@code null}
     * @param fingerprint 标识的不可逆摘要（日志 / 指标用），缺失时为 {@code null}
     */
    public record Resolution(Source source, String identity, String fingerprint) {

        /**
         * 是否取到了可用的终端用户标识。
         *
         * @return 标识是否存在
         */
        public boolean present() {
            return source != Source.NONE;
        }
    }

    /**
     * 解析终端用户标识。
     *
     * @param headerValue        请求头取值，可为 {@code null}
     * @param requestDto         已反序列化的请求体 DTO，可为 {@code null} 或其它类型
     * @param headerReadEnabled  是否读取请求头（{@code header-enabled}）
     * @param fingerprintLength  摘要长度（hex 字符数），由配置钳制后传入
     * @return 解析结果，绝不返回 {@code null}
     */
    public static Resolution resolve(final String headerValue,
                                     final Object requestDto,
                                     final boolean headerReadEnabled,
                                     final int fingerprintLength) {
        final int length = Math.max(1, fingerprintLength);

        if (headerReadEnabled) {
            final String fromHeader = normalize(headerValue);
            if (fromHeader != null) {
                return new Resolution(Source.HEADER, fromHeader, fingerprint(fromHeader, length));
            }
        }

        final String fromBody = normalize(bodyUser(requestDto));
        if (fromBody != null) {
            return new Resolution(Source.BODY_USER, fromBody, fingerprint(fromBody, length));
        }

        final String fromAnthropic = normalize(anthropicUser(requestDto));
        if (fromAnthropic != null) {
            return new Resolution(Source.ANTHROPIC_METADATA, fromAnthropic, fingerprint(fromAnthropic, length));
        }

        return new Resolution(Source.NONE, null, null);
    }

    /**
     * 计算限流桶键：把「API Key + 终端用户」压成一个定长摘要。
     *
     * <p>带 API Key 参与摘要的原因：同一个终端用户标识在不同 Key 下必须互不影响
     * （否则共享同一标识的两个租户会互相抢配额）。</p>
     *
     * @param apiKeyId API Key ID，不可为 {@code null}
     * @param identity 规范化后的终端用户标识，不可为 {@code null}
     * @return 16 位 hex 桶键
     */
    public static String bucketKey(final String apiKeyId, final String identity) {
        return sha256Hex(apiKeyId + "|" + identity).substring(0, BUCKET_KEY_LENGTH);
    }

    /**
     * 计算不可逆摘要（用于日志与指标，原文不外泄）。
     *
     * @param value  原始值，可为 {@code null}
     * @param length 需要的 hex 字符数（会被钳制在 1 ~ 64）
     * @return 摘要；入参为 {@code null} 时返回 {@code null}
     */
    public static String fingerprint(final String value, final int length) {
        if (value == null) {
            return null;
        }
        final String hex = sha256Hex(value);
        final int size = Math.max(1, Math.min(length, hex.length()));
        return hex.substring(0, size);
    }

    /**
     * 规范化标识：去空白；字符集或长度不合规时改用摘要（保证可安全拼进 key）。
     *
     * @param raw 原始取值
     * @return 规范化结果；空白时返回 {@code null}
     */
    private static String normalize(final String raw) {
        if (raw == null) {
            return null;
        }
        final String trimmed = raw.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        if (SAFE_IDENTITY.matcher(trimmed).matches()) {
            return trimmed;
        }
        return sha256Hex(trimmed);
    }

    /**
     * 从请求体中取 OpenAI 兼容的 {@code user} 字段。
     *
     * @param requestDto 请求体 DTO
     * @return 原始取值，取不到时为 {@code null}
     */
    private static String bodyUser(final Object requestDto) {
        if (requestDto instanceof ChatDTO.Request chat) {
            return chat.user();
        }
        if (requestDto instanceof EmbeddingDTO.Request embedding) {
            return embedding.user();
        }
        if (requestDto instanceof ImageGenerateDTO.Request imageGenerate) {
            return imageGenerate.user();
        }
        if (requestDto instanceof ImageEditDTO.Request imageEdit) {
            return imageEdit.user();
        }
        return null;
    }

    /**
     * 从 Anthropic 请求的 {@code metadata} 里取 {@code user_id}。
     *
     * @param requestDto 请求体 DTO
     * @return 原始取值，取不到时为 {@code null}
     */
    private static String anthropicUser(final Object requestDto) {
        if (requestDto instanceof AnthropicMessagesRequest anthropic && anthropic.metadata() != null) {
            final Object value = anthropic.metadata().get(ANTHROPIC_USER_KEY);
            if (value instanceof String text) {
                return text;
            }
        }
        return null;
    }

    /**
     * SHA-256 十六进制摘要。
     *
     * @param value 原文
     * @return 64 位小写 hex
     */
    private static String sha256Hex(final String value) {
        try {
            final MessageDigest digest = MessageDigest.getInstance("SHA-256");
            final byte[] bytes = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            final StringBuilder builder = new StringBuilder(bytes.length * 2);
            for (final byte b : bytes) {
                builder.append(HEX[(b >> 4) & 0x0F]).append(HEX[b & 0x0F]);
            }
            return builder.toString().toLowerCase(Locale.ROOT);
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 是 JDK 强制要求提供的算法，走到这里说明运行环境已损坏
            throw new IllegalStateException("SHA-256 不可用", e);
        }
    }

}
