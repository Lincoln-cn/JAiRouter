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
 * 其中 33 个在发出点被使用），HTTP 状态码则由 {@code ApiException.resolveStatus} 的**命名规则**推导
 * （改名即改状态码，且没有编译期检查）；文档表只能靠人工对账（issue #342 记录过 17 个「全仓不存在却写在表里」的码）。</p>
 *
 * <p><b>本枚举的定位</b>：把「**会被真正发出**的码」登记为单一事实来源（`码 + HTTP 状态 + 说明`）。
 * 状态码取值 = **登记时的现有行为**（由 `resolveStatus` 的规则逐条推导），因此引入本枚举**不改变任何运行时行为**；
 * {@code ApiException.resolveStatus} 现在先查本表、未登记才落回原有规则链（见其 javadoc 与
 * {@code ErrorCodeRegistryTest} 的不变量断言）。</p>
 *
 * <p><b>尚未纳入</b>（后续批次）：① 走 {@code AuthenticationException} 构造器发出的 6 个认证码
 * （`JWT_EXPIRED` / `JWT_BLACKLISTED` / `JWT_REFRESH_EXPIRED` / `JWT_REFRESH_ERROR` / `JWT_VALIDATION_ERROR` /
 * `TOKEN_REQUIRED`）—— 它们的客户端可见状态由认证渲染路径决定（401），与 {@code resolveStatus} 不是同一条链；
 * ② 把各调用点的裸字面量替换为本枚举常量（机械改动，按模块分批）。</p>
 *
 * <p>⚠️ 本表**暴露了一处既有语义异常**：{@code TOKEN_USAGE_FORBIDDEN} 按现有规则解析为 **500** ——
 * 因为 `resolveStatus` 只识别**前缀** `FORBIDDEN`，而该码以 `_FORBIDDEN` 结尾。本枚举**如实登记现有行为**
 * （500）并另立 issue 记录，不在本批静默改变对外状态码。</p>
 */
public enum ErrorCode {

    // ===== 会被发出的错误码（33 个；按码字典序）=====
    ADAPTER_NOT_FOUND("ADAPTER_NOT_FOUND", HttpStatus.NOT_FOUND, "适配器不存在"),
    ADD_FAILED("ADD_FAILED", HttpStatus.INTERNAL_SERVER_ERROR, "新增失败（黑名单等）"),
    BATCH_TOKEN_REVOKE_FAILED("BATCH_TOKEN_REVOKE_FAILED", HttpStatus.INTERNAL_SERVER_ERROR, "批量吊销令牌失败"),
    BLACKLIST_STATS_FAILED("BLACKLIST_STATS_FAILED", HttpStatus.INTERNAL_SERVER_ERROR, "获取黑名单统计失败"),
    BUILTIN_READONLY("BUILTIN_READONLY", HttpStatus.FORBIDDEN, "内置项只读，不允许修改"),
    CLEANUP_FAILED("CLEANUP_FAILED", HttpStatus.INTERNAL_SERVER_ERROR, "清理失败"),
    CONFLICT_BUILTIN("CONFLICT_BUILTIN", HttpStatus.CONFLICT, "与内置项冲突"),
    CONFLICT_EXISTS("CONFLICT_EXISTS", HttpStatus.CONFLICT, "资源已存在"),
    CONFLICT_STATE("CONFLICT_STATE", HttpStatus.CONFLICT, "资源冲突"),
    FORBIDDEN("FORBIDDEN", HttpStatus.FORBIDDEN, "禁止访问"),
    GET_CLEANUP_STATS_FAILED("GET_CLEANUP_STATS_FAILED", HttpStatus.INTERNAL_SERVER_ERROR, "获取清理统计失败"),
    GET_TOKENS_FAILED("GET_TOKENS_FAILED", HttpStatus.INTERNAL_SERVER_ERROR, "获取令牌列表失败"),
    GET_TOKEN_DETAILS_FAILED("GET_TOKEN_DETAILS_FAILED", HttpStatus.INTERNAL_SERVER_ERROR, "获取令牌详情失败"),
    INTERNAL_ERROR("INTERNAL_ERROR", HttpStatus.INTERNAL_SERVER_ERROR, "服务器内部错误"),
    INVALID_NAME("INVALID_NAME", HttpStatus.BAD_REQUEST, "名称不合法"),
    INVALID_PAGE("INVALID_PAGE", HttpStatus.BAD_REQUEST, "分页参数不合法"),
    INVALID_PARAM("INVALID_PARAM", HttpStatus.BAD_REQUEST, "参数无效"),
    INVALID_PERMISSION("INVALID_PERMISSION", HttpStatus.BAD_REQUEST, "权限项不合法"),
    INVALID_RECORD_LEVEL("INVALID_RECORD_LEVEL", HttpStatus.BAD_REQUEST, "记录级别不合法"),
    INVALID_REQUEST("INVALID_REQUEST", HttpStatus.BAD_REQUEST, "请求不合法"),
    INVALID_ROLE("INVALID_ROLE", HttpStatus.BAD_REQUEST, "角色名不合法"),
    INVALID_SIZE("INVALID_SIZE", HttpStatus.BAD_REQUEST, "分页大小不合法"),
    INVALID_TOKEN_ID("INVALID_TOKEN_ID", HttpStatus.BAD_REQUEST, "令牌 ID 不合法"),
    INVALID_TYPE("INVALID_TYPE", HttpStatus.BAD_REQUEST, "类型不合法"),
    LOGIN_FAILED("LOGIN_FAILED", HttpStatus.UNAUTHORIZED, "登录失败"),
    NOT_FOUND("NOT_FOUND", HttpStatus.NOT_FOUND, "资源未找到"),
    RESTART_REQUIRED("RESTART_REQUIRED", HttpStatus.FORBIDDEN, "需要重启后生效"),
    SERVICE_NOT_AVAILABLE("SERVICE_NOT_AVAILABLE", HttpStatus.SERVICE_UNAVAILABLE, "服务不可用"),
    TEMPLATE_NOT_FOUND("TEMPLATE_NOT_FOUND", HttpStatus.NOT_FOUND, "模板不存在"),
    TOKEN_NOT_FOUND("TOKEN_NOT_FOUND", HttpStatus.NOT_FOUND, "令牌不存在"),
    TOKEN_REFRESH_FAILED("TOKEN_REFRESH_FAILED", HttpStatus.INTERNAL_SERVER_ERROR, "令牌刷新失败"),
    TOKEN_REVOKE_FAILED("TOKEN_REVOKE_FAILED", HttpStatus.INTERNAL_SERVER_ERROR, "令牌吊销失败"),
    TOKEN_USAGE_FORBIDDEN("TOKEN_USAGE_FORBIDDEN", HttpStatus.INTERNAL_SERVER_ERROR, "无权查看该令牌的用量"),

    ;

    private static final Map<String, ErrorCode> BY_CODE = Arrays.stream(values())
            .collect(Collectors.toUnmodifiableMap(ErrorCode::code, Function.identity()));

    private final String code;
    private final HttpStatus status;
    private final String description;

    ErrorCode(final String code, final HttpStatus status, final String description) {
        this.code = code;
        this.status = status;
        this.description = description;
    }

    /** 错误码字面量（对外契约，勿改名） */
    public String code() {
        return code;
    }

    /** 该码的客户端可见 HTTP 状态码 */
    public HttpStatus status() {
        return status;
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
}
