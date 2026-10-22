package org.unreal.modelrouter.auth.security.config.properties;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
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

        /**
         * 单次响应允许缓冲的字节上限（issue #594）。
         *
         * <p>响应脱敏需要把整个响应体读进内存做正则替换，因此必须给缓冲设上限，否则
         * 大响应 + 高并发会把堆打满。超过上限时的行为分两种：</p>
         * <ul>
         *   <li><b>长度已知</b>（有 {@code Content-Length}）：跳过脱敏、原样放行，并记告警与审计
         *       —— 这是「宁可漏脱敏也不破坏响应」的既定降级口径；</li>
         *   <li><b>长度未知</b>（chunked）：无法在已消费流之后回放原始内容，按脱敏失败处理，
         *       由中央异常处理器渲染错误响应（不会输出部分内容）。</li>
         * </ul>
         *
         * <p>下限 1 KiB，默认 1 MiB。</p>
         */
        @Min(1024)
        private long maxBufferedBytes = 1048576L;

        /**
         * 是否对 {@code text/event-stream} 流式响应逐帧脱敏（issue #560 第二阶段）。
         *
         * <p>开启后只处理 SSE 的 {@code data:} 行负载，按行流式替换、不做整段缓冲；
         * {@code event:}/{@code id:}/{@code retry:}/注释行与 {@code [DONE]} 哨兵原样透传，
         * 分帧语义不变。单帧解析失败只回退该帧，不会中断整条流。</p>
         *
         * <p>默认开启：开启前 SSE 完全在脱敏覆盖之外，等于「开关为开、流式不设防」。</p>
         */
        private boolean sanitizeSse = true;
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
