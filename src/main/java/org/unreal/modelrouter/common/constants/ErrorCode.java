package org.unreal.modelrouter.common.constants;

import org.springframework.http.HttpStatus;

import java.util.Arrays;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 错误码注册表（issue #370 方案 A：**根治**错误码无单一事实来源的问题）。
 *
 * <p><b>现状与动机</b>：本仓的错误码此前散落在控制器与异常类里的字符串字面量中（实测 241 个「码形状」字面量、
 * 其中 62 个会被真正发出；本表登记 63 个，多出的一个是 issue #404 拆码新增的
 * {@code RATE_LIMITED_API_KEY}），HTTP 状态码则由 {@code ApiException.resolveStatus} 的**命名规则**推导
 * （改名即改状态码，且没有编译期检查）；文档表只能靠人工对账（issue #342 记录过 17 个「全仓不存在却写在表里」的码）。</p>
 *
 * <p><b>本枚举的定位</b>：把「**会被真正发出**的码」登记为单一事实来源（`码 + 客户端可见状态 + 渲染路径 + 说明`）。
 * 引入本枚举**不改变任何运行时行为**：{@code ApiException.resolveStatus} 只对
 * {@link RenderPath#API_EXCEPTION} 的码查表（其余路径的状态不由规则链决定，见
 * {@link #isResolvedByRuleChain(String)}），且这些码的登记状态与其在规则链下的推导值逐条一致
 * （由 {@code ErrorCodeRegistryTest} 守卫）。</p>
 *
 * <p><b>为什么需要 {@link RenderPath}</b>（issue #370 阶段 2 取证结论）：错误码不由同一条链决定状态 ——</p>
 * <ul>
 *   <li>{@link RenderPath#API_EXCEPTION}：码由 {@code ApiException} 承载、或发出点显式调用
 *       {@code ApiException.resolveStatus(code)} 取状态 ⇒ 状态 = **规则链推导值**；</li>
 *   <li>{@link RenderPath#RESPONSE_BODY}：码只被写进响应体，状态由发出点的 {@code ResponseEntity} 显式指定
 *       （如 {@code ResponseEntity.badRequest().body(RouterResponse.error(msg, "INVALID_NAME"))}）；</li>
 *   <li>{@link RenderPath#AUTHENTICATION}：码由 {@code AuthenticationException} /
 *       {@code SecurityAuthenticationException} 承载，状态由**异常类型/构造器**决定（今天均为 401，
 *       经 {@code SpringSecurityAuthenticationFilter} 或 {@code SecurityExceptionHandler} 渲染）；</li>
 *   <li>{@link RenderPath#SANITIZATION}：码由 {@code SanitizationException} 承载（500）；</li>
 *   <li>{@link RenderPath#ERROR_BODY}：码由错误体构造器直写，状态**不由规则链决定** ——
 *       {@code DOWNSTREAM_SERVICE_ERROR} 随上下文（取下游原状态，故 {@link #status()} 为 {@code null}），
 *       {@code RATE_LIMIT_EXCEEDED} 则由管理面限流器固定直写 429（故登记了 429）。</li>
 * </ul>
 *
 * <p><b>尚未纳入</b>（后续批次）：把各调用点的裸字面量替换为本枚举常量（机械改动，按模块分批，见 issue #370 阶段 3）。</p>
 *
 * <p>📌 <b>码的书写约定</b>（本表据此登记，并由 {@code ErrorCodeRegistryTest} 守卫）：类别**放在前缀位** ——
 * {@code FORBIDDEN_*} / {@code INVALID_*} / {@code CONFLICT*} / {@code UNAUTHORIZED*} 等由规则链按前缀映射；
 * 全表**唯一**的历史后缀例外是 {@code *_NOT_FOUND}。凡「类别写在后缀」的码（如曾经的 `TOKEN_USAGE_FORBIDDEN`）
 * 已统一改为前缀式 {@code FORBIDDEN_TOKEN_USAGE}（issue #401）；同族的 `RESOURCE_FORBIDDEN` 虽也改了前缀式
 * （`FORBIDDEN_RESOURCE`），但它**全仓没有发出点**，已随未接线代码一并删除（issue #406）。</p>
 */
public enum ErrorCode {

    // ===================================================================================
    // 1) API_EXCEPTION：状态由 ApiException.resolveStatus 的命名规则决定（登记值 = 现有行为）
    // ===================================================================================
    ADAPTER_NOT_FOUND("ADAPTER_NOT_FOUND", HttpStatus.NOT_FOUND, RenderPath.API_EXCEPTION, "适配器不存在"),
    ADD_FAILED("ADD_FAILED", HttpStatus.INTERNAL_SERVER_ERROR, RenderPath.API_EXCEPTION,
            "新增失败（黑名单等）；发出点用 resolveStatus 取状态"),
    BATCH_TOKEN_REVOKE_FAILED("BATCH_TOKEN_REVOKE_FAILED", HttpStatus.INTERNAL_SERVER_ERROR, RenderPath.API_EXCEPTION,
            "批量吊销令牌失败"),
    BLACKLIST_STATS_FAILED("BLACKLIST_STATS_FAILED", HttpStatus.INTERNAL_SERVER_ERROR, RenderPath.API_EXCEPTION,
            "获取黑名单统计失败"),
    CLEANUP_FAILED("CLEANUP_FAILED", HttpStatus.INTERNAL_SERVER_ERROR, RenderPath.API_EXCEPTION, "清理失败"),
    CONFLICT_STATE("CONFLICT_STATE", HttpStatus.CONFLICT, RenderPath.API_EXCEPTION, "资源冲突"),
    FORBIDDEN("FORBIDDEN", HttpStatus.FORBIDDEN, RenderPath.API_EXCEPTION, "禁止访问"),
    GET_CLEANUP_STATS_FAILED("GET_CLEANUP_STATS_FAILED", HttpStatus.INTERNAL_SERVER_ERROR, RenderPath.API_EXCEPTION,
            "获取清理统计失败"),
    GET_TOKENS_FAILED("GET_TOKENS_FAILED", HttpStatus.INTERNAL_SERVER_ERROR, RenderPath.API_EXCEPTION,
            "获取令牌列表失败"),
    GET_TOKEN_DETAILS_FAILED("GET_TOKEN_DETAILS_FAILED", HttpStatus.INTERNAL_SERVER_ERROR, RenderPath.API_EXCEPTION,
            "获取令牌详情失败"),
    INTERNAL_ERROR("INTERNAL_ERROR", HttpStatus.INTERNAL_SERVER_ERROR, RenderPath.API_EXCEPTION, "服务器内部错误"),
    INVALID_PAGE("INVALID_PAGE", HttpStatus.BAD_REQUEST, RenderPath.API_EXCEPTION, "分页参数不合法"),
    INVALID_BASE_URL("INVALID_BASE_URL", HttpStatus.BAD_REQUEST, RenderPath.RESPONSE_BODY,
            "出站 base URL 不合法（协议、凭据或目标地址被拒）"),
    INVALID_PARAM("INVALID_PARAM", HttpStatus.BAD_REQUEST, RenderPath.API_EXCEPTION,
            "参数无效；发出点用 resolveStatus 取状态"),
    INVALID_REQUEST("INVALID_REQUEST", HttpStatus.BAD_REQUEST, RenderPath.API_EXCEPTION, "请求不合法"),
    INVALID_SIZE("INVALID_SIZE", HttpStatus.BAD_REQUEST, RenderPath.API_EXCEPTION, "分页大小不合法"),
    INVALID_TOKEN_ID("INVALID_TOKEN_ID", HttpStatus.BAD_REQUEST, RenderPath.API_EXCEPTION, "令牌 ID 不合法"),
    INVALID_TYPE("INVALID_TYPE", HttpStatus.BAD_REQUEST, RenderPath.API_EXCEPTION,
            "类型不合法；发出点用 resolveStatus 取状态"),
    LOGIN_FAILED("LOGIN_FAILED", HttpStatus.UNAUTHORIZED, RenderPath.API_EXCEPTION, "登录失败"),
    NOT_FOUND("NOT_FOUND", HttpStatus.NOT_FOUND, RenderPath.API_EXCEPTION, "资源未找到"),
    RATE_LIMITED_LOGIN("RATE_LIMITED_LOGIN", HttpStatus.TOO_MANY_REQUESTS, RenderPath.API_EXCEPTION,
            "登录失败次数达到阈值后的临时锁定（issue #697）；429 由 RATE_LIMITED_ 前缀的规则链推导"),
    SERVICE_NOT_AVAILABLE("SERVICE_NOT_AVAILABLE", HttpStatus.SERVICE_UNAVAILABLE, RenderPath.API_EXCEPTION,
            "服务不可用"),
    TOKEN_NOT_FOUND("TOKEN_NOT_FOUND", HttpStatus.NOT_FOUND, RenderPath.API_EXCEPTION, "令牌不存在"),
    TOKEN_REFRESH_FAILED("TOKEN_REFRESH_FAILED", HttpStatus.INTERNAL_SERVER_ERROR, RenderPath.API_EXCEPTION,
            "令牌刷新失败"),
    TOKEN_REVOKE_FAILED("TOKEN_REVOKE_FAILED", HttpStatus.INTERNAL_SERVER_ERROR, RenderPath.API_EXCEPTION,
            "令牌吊销失败"),

    // ===================================================================================
    // 2) RESPONSE_BODY：码只进响应体，状态由发出点的 ResponseEntity 显式指定
    //    （实测站点见 ErrorCodeRegistryTest 的「站点实测状态」用例）
    // ===================================================================================
    BUILTIN_READONLY("BUILTIN_READONLY", HttpStatus.FORBIDDEN, RenderPath.RESPONSE_BODY,
            "内置项只读，不允许修改；端点与规则链推导值一致，均为 403（issue #405 混合方案）"),
    CONFLICT_BUILTIN("CONFLICT_BUILTIN", HttpStatus.CONFLICT, RenderPath.RESPONSE_BODY, "与内置项冲突"),
    CONFLICT_EXISTS("CONFLICT_EXISTS", HttpStatus.CONFLICT, RenderPath.RESPONSE_BODY, "资源已存在"),
    FORBIDDEN_TOKEN_USAGE("FORBIDDEN_TOKEN_USAGE", HttpStatus.FORBIDDEN, RenderPath.RESPONSE_BODY,
            "无权查看该令牌的用量"),
    INVALID_NAME("INVALID_NAME", HttpStatus.BAD_REQUEST, RenderPath.RESPONSE_BODY, "名称不合法"),
    INVALID_PERMISSION("INVALID_PERMISSION", HttpStatus.BAD_REQUEST, RenderPath.RESPONSE_BODY, "权限项不合法"),
    INVALID_RECORD_LEVEL("INVALID_RECORD_LEVEL", HttpStatus.BAD_REQUEST, RenderPath.RESPONSE_BODY,
            "记录级别不合法"),
    INVALID_ROLE("INVALID_ROLE", HttpStatus.BAD_REQUEST, RenderPath.RESPONSE_BODY, "角色名不合法"),
    RESTART_REQUIRED("RESTART_REQUIRED", HttpStatus.BAD_REQUEST, RenderPath.RESPONSE_BODY,
            "需要重启后生效；端点与规则链推导值一致，均为 400（规则链已对齐，见 issue #405）"),
    TEMPLATE_NOT_FOUND("TEMPLATE_NOT_FOUND", HttpStatus.NOT_FOUND, RenderPath.RESPONSE_BODY, "模板不存在"),

    // ===================================================================================
    // 3) AUTHENTICATION：状态由异常携带（经校验链的码今天均为 401，唯一例外见 RATE_LIMITED_API_KEY）
    // ===================================================================================
    API_KEY_AUTH_FAILED("API_KEY_AUTH_FAILED", HttpStatus.UNAUTHORIZED, RenderPath.AUTHENTICATION,
            "API Key 认证失败（校验链未预期异常）"),
    API_KEY_DISABLED("API_KEY_DISABLED", HttpStatus.UNAUTHORIZED, RenderPath.AUTHENTICATION,
            "API Key 认证功能未启用"),
    AUTH_ERROR("AUTH_ERROR", HttpStatus.UNAUTHORIZED, RenderPath.AUTHENTICATION,
            "认证过程中发生未知错误（认证过滤器兜底）"),
    AUTH_FAILED("AUTH_FAILED", HttpStatus.UNAUTHORIZED, RenderPath.AUTHENTICATION,
            "认证失败（认证过滤器默认码）"),
    AUTH_MISSING("AUTH_MISSING", HttpStatus.UNAUTHORIZED, RenderPath.AUTHENTICATION, "请求缺少认证信息"),
    DAILY_QUOTA_EXCEEDED("DAILY_QUOTA_EXCEEDED", HttpStatus.UNAUTHORIZED, RenderPath.AUTHENTICATION,
            "超过每日请求配额限制"),
    EMPTY_API_KEY("EMPTY_API_KEY", HttpStatus.UNAUTHORIZED, RenderPath.AUTHENTICATION, "未携带 API Key"),
    EMPTY_JWT_TOKEN("EMPTY_JWT_TOKEN", HttpStatus.UNAUTHORIZED, RenderPath.AUTHENTICATION, "未携带 JWT 令牌"),
    EXPIRED_API_KEY("EXPIRED_API_KEY", HttpStatus.UNAUTHORIZED, RenderPath.AUTHENTICATION, "API Key 已过期"),
    EXPIRED_JWT_TOKEN("EXPIRED_JWT_TOKEN", HttpStatus.UNAUTHORIZED, RenderPath.AUTHENTICATION,
            "JWT 令牌已过期（认证管理器按 expired 归类）"),
    INVALID_API_KEY("INVALID_API_KEY", HttpStatus.UNAUTHORIZED, RenderPath.AUTHENTICATION, "无效的 API Key"),
    JWT_AUTH_FAILED("JWT_AUTH_FAILED", HttpStatus.UNAUTHORIZED, RenderPath.AUTHENTICATION,
            "JWT 令牌认证失败（认证管理器未预期异常）"),
    JWT_BLACKLISTED("JWT_BLACKLISTED", HttpStatus.UNAUTHORIZED, RenderPath.AUTHENTICATION,
            "JWT 令牌已被列入黑名单"),
    JWT_DISABLED("JWT_DISABLED", HttpStatus.UNAUTHORIZED, RenderPath.AUTHENTICATION, "JWT 认证功能未启用"),
    JWT_EXPIRED("JWT_EXPIRED", HttpStatus.UNAUTHORIZED, RenderPath.AUTHENTICATION, "JWT 令牌已过期"),
    JWT_INVALID("JWT_INVALID", HttpStatus.UNAUTHORIZED, RenderPath.AUTHENTICATION, "JWT 令牌验证失败"),
    JWT_REFRESH_ERROR("JWT_REFRESH_ERROR", HttpStatus.UNAUTHORIZED, RenderPath.AUTHENTICATION,
            "JWT 令牌刷新过程中发生错误"),
    JWT_REFRESH_EXPIRED("JWT_REFRESH_EXPIRED", HttpStatus.UNAUTHORIZED, RenderPath.AUTHENTICATION,
            "JWT 令牌超出刷新窗口"),
    JWT_REFRESH_FAILED("JWT_REFRESH_FAILED", HttpStatus.UNAUTHORIZED, RenderPath.AUTHENTICATION,
            "JWT 令牌刷新失败"),
    JWT_USER_ID_EXTRACTION_FAILED("JWT_USER_ID_EXTRACTION_FAILED", HttpStatus.UNAUTHORIZED,
            RenderPath.AUTHENTICATION, "无法从 JWT 令牌中提取用户 ID"),
    JWT_VALIDATION_ERROR("JWT_VALIDATION_ERROR", HttpStatus.UNAUTHORIZED, RenderPath.AUTHENTICATION,
            "JWT 令牌验证过程中发生错误"),
    RATE_LIMITED_API_KEY("RATE_LIMITED_API_KEY", HttpStatus.TOO_MANY_REQUESTS, RenderPath.AUTHENTICATION,
            "API Key 每分钟限速超限（校验链，429）；管理面限流器见 RATE_LIMIT_EXCEEDED（issue #404 拆码）"),
    TOKEN_QUOTA_EXCEEDED("TOKEN_QUOTA_EXCEEDED", HttpStatus.UNAUTHORIZED, RenderPath.AUTHENTICATION,
            "超过每日 Token 使用配额限制"),
    TOKEN_REQUIRED("TOKEN_REQUIRED", HttpStatus.UNAUTHORIZED, RenderPath.AUTHENTICATION,
            "刷新令牌时未携带当前令牌"),
    UNSUPPORTED_AUTH_TYPE("UNSUPPORTED_AUTH_TYPE", HttpStatus.UNAUTHORIZED, RenderPath.AUTHENTICATION,
            "不支持的认证类型"),

    // ===================================================================================
    // 4) SANITIZATION：码由 SanitizationException 承载（500）
    // ===================================================================================
    CONTENT_PROCESSING_FAILED("CONTENT_PROCESSING_FAILED", HttpStatus.INTERNAL_SERVER_ERROR,
            RenderPath.SANITIZATION, "内容处理失败"),
    INVALID_SANITIZATION_RULE("INVALID_SANITIZATION_RULE", HttpStatus.INTERNAL_SERVER_ERROR,
            RenderPath.SANITIZATION, "无效的脱敏规则"),
    SANITIZATION_FAILED("SANITIZATION_FAILED", HttpStatus.INTERNAL_SERVER_ERROR, RenderPath.SANITIZATION,
            "数据脱敏失败"),

    // ===================================================================================
    // 5) ERROR_BODY：码由错误体直写，状态不由规则链决定
    //    DOWNSTREAM_SERVICE_ERROR 随上下文（status = null）；RATE_LIMIT_EXCEEDED 固定 429
    // ===================================================================================
    DOWNSTREAM_SERVICE_ERROR("DOWNSTREAM_SERVICE_ERROR", null, RenderPath.ERROR_BODY,
            "下游服务异常；状态码原样取下游返回的状态（401 归一到 401 认证分支）"),
    RATE_LIMIT_EXCEEDED("RATE_LIMIT_EXCEEDED", HttpStatus.TOO_MANY_REQUESTS, RenderPath.ERROR_BODY,
            "管理面限流器（/api/auth/api-keys 前缀）直写的 429 扁平体；"
                    + "API Key 校验链的超限改用 RATE_LIMITED_API_KEY（issue #404 拆码）"),

    ;

    /**
     * 该码的状态由哪条链决定（issue #370 阶段 2）。
     *
     * <p>它的存在意义 = 「同名码在不同链上会被赋予不同状态」这件事必须写在码上，否则注册表只能登记一个
     * 谁都对不上的状态值。</p>
     */
    public enum RenderPath {
        /** 状态由 {@code ApiException.resolveStatus} 的命名规则决定（含发出点显式调用它取状态的情形） */
        API_EXCEPTION,
        /** 码只进响应体，状态由发出点的 {@code ResponseEntity} 显式指定 */
        RESPONSE_BODY,
        /** 状态由 {@code AuthenticationException} / {@code SecurityAuthenticationException} 携带 */
        AUTHENTICATION,
        /** 状态由 {@code SanitizationException} 携带 */
        SANITIZATION,
        /** 码由错误体直写，状态不由规则链决定（{@link ErrorCode#status()} 可为 {@code null}，如随下游上下文的码） */
        ERROR_BODY
    }

    private static final Map<String, ErrorCode> BY_CODE = Arrays.stream(values())
            .collect(Collectors.toUnmodifiableMap(ErrorCode::code, Function.identity()));

    private final String code;
    private final HttpStatus status;
    private final RenderPath renderPath;
    private final String description;

    ErrorCode(final String code, final HttpStatus status, final RenderPath renderPath, final String description) {
        this.code = code;
        this.status = status;
        this.renderPath = renderPath;
        this.description = description;
    }

    /** 错误码字面量（对外契约，勿改名） */
    public String code() {
        return code;
    }

    /**
     * 该码的客户端可见 HTTP 状态码；{@code null} 表示「状态随上下文、不由本表固定」
     * （当前仅 {@code DOWNSTREAM_SERVICE_ERROR} 为 {@code null}；同为错误体直写路径的
     * {@code RATE_LIMIT_EXCEEDED} 由管理面限流器固定写 429，故登记 429）。
     */
    public HttpStatus status() {
        return status;
    }

    /** 该码的状态由哪条链决定 */
    public RenderPath renderPath() {
        return renderPath;
    }

    /** 人类可读说明（文档与排查用） */
    public String description() {
        return description;
    }

    /** 按码查注册表；未登记或 {@code null} 返回 {@link Optional#empty()} */
    public static Optional<ErrorCode> of(final String code) {
        if (code == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(BY_CODE.get(code.trim()));
    }

    /** 该码是否已登记在注册表里 */
    public static boolean isRegistered(final String code) {
        return of(code).isPresent();
    }

    /**
     * 该码的状态是否由规则链（{@code ApiException.resolveStatus}）决定。
     *
     * <p>{@code resolveStatus} 只对这一类查表，其余路径的码仍走历史规则链 —— 这样引入注册表
     * 对「未走规则链的码」保持零行为变化（例如 {@code INVALID_API_KEY} 的规则链推导值是 400，
     * 而它真实的端点状态是 401：查表不得把它改成 401，那会改变任何将来经 {@code ApiException}
     * 发出该码的路径的对外状态）。</p>
     */
    public static boolean isResolvedByRuleChain(final String code) {
        return of(code).map(c -> c.renderPath == RenderPath.API_EXCEPTION).orElse(false);
    }
}
