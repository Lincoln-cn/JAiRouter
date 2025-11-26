package org.unreal.modelrouter.security.config.properties;

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
    }
}
