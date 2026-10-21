package org.unreal.modelrouter.auth.security.config.properties;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 数据脱敏配置类
 */
@Data
public class SanitizationConfig {
    /**
     * 请求脱敏配置
     */
    @Valid
    @NotNull
    private RequestSanitization request = new RequestSanitization();

    /**
     * 响应脱敏配置
     */
    @Valid
    @NotNull
    private ResponseSanitization response = new ResponseSanitization();

    /**
     * 请求脱敏配置
     */
    @Data
    public static class RequestSanitization {
        /**
         * 是否启用请求脱敏
         */
        private boolean enabled = true;

        /**
         * 敏感词列表
         */
        private List<String> sensitiveWords = new ArrayList<>();

        /**
         * PII数据模式列表
         */
        private List<String> piiPatterns = new ArrayList<>();

        /**
         * 掩码字符
         */
        @NotBlank
        @Size(min = 1, max = 5)
        private String maskingChar = "*";

        /**
         * 白名单用户列表
         */
        private List<String> whitelistUsers = new ArrayList<>();

        /**
         * 是否记录脱敏操作
         */
        private boolean logSanitization = true;

        /**
         * 脱敏失败时是否中断请求处理
         */
        private boolean failOnError = false;
    }

    /**
     * 响应脱敏配置
     */
    @Data
    public static class ResponseSanitization {
        /**
         * 是否启用响应脱敏
         */
        private boolean enabled = true;

        /**
         * 敏感词列表
         */
        private List<String> sensitiveWords = new ArrayList<>();

        /**
         * PII数据模式列表
         */
        private List<String> piiPatterns = new ArrayList<>();

        /**
         * 掩码字符
         */
        @NotBlank
        @Size(min = 1, max = 5)
        private String maskingChar = "*";

        /**
         * 是否记录脱敏操作
         */
        private boolean logSanitization = true;

        /**
         * 脱敏失败时是否中断响应处理
         */
        private boolean failOnError = false;

        /**
         * 是否保留 JSON 结构（仅对 application/json 有效）
         * 为 true 时，正则匹配仅作用于 JSON 字符串字面量内部，不破坏数值和结构
         */
        private boolean preserveJsonStructure = true;

        /**
         * 生效范围（issue #560 可配置化 A 档）。
         *
         * <p>不配置时沿用内置排除集合，行为与历史版本一致。解析逻辑见
         * {@code org.unreal.modelrouter.auth.security.config.DataMaskingScope}。</p>
         */
        private Scope scope = new Scope();
    }

    /**
     * 响应脱敏的路径生效范围。
     */
    @Data
    public static class Scope {
        /**
         * {@code exclude}（默认）= patterns 是不脱敏的例外；{@code include} = 只有命中 patterns 才脱敏。
         */
        private String mode = "exclude";

        /**
         * Ant 风格路径模式；为空且 mode=exclude 时使用内置排除集合。
         */
        private List<String> patterns = new ArrayList<>();
    }
}
