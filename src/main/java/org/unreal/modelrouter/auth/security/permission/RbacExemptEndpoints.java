package org.unreal.modelrouter.auth.security.permission;

import org.springframework.http.HttpMethod;
import org.springframework.http.server.PathContainer;
import org.springframework.web.util.pattern.PathPattern;
import org.springframework.web.util.pattern.PathPatternParser;

import java.util.List;
import java.util.Set;

/**
 * RBAC 显式豁免清单（#128 Phase 2）——取代「未登记 ⇒ 放行」的隐式行为。
 *
 * <p>清单中的端点<b>有意不登记 URL 权限规则</b>：它们要么是自助端点（方法级
 * {@code @PreAuthorize} 已约束属主），要么是服务代理面（授权由 API-Key 服务类型
 * 权限 + 配额控制，而非 URL 权限矩阵）。写方法 fail-closed 策略跳过这些端点，
 * 覆盖自检把它们报告为 EXEMPT 而非 MISSING。
 *
 * <p><b>判定顺序</b>：先 {@link PermissionRuleRegistry}（规则命中即 covered），
 * 再本清单（命中即 EXEMPT），都未命中才是 MISSING（可行动信号）。
 *
 * <p>每条豁免必须写明理由（{@link Exemption#reason()}），审查时逐条可追溯。
 *
 * @author JAiRouter Team
 * @since 3.0.4
 */
public final class RbacExemptEndpoints {

    /** 匹配任意 HTTP 方法 */
    private static final Set<HttpMethod> ANY_METHODS = Set.of();

    private static final List<CompiledExemption> COMPILED = compile();

    private RbacExemptEndpoints() {
    }

    /**
     * 一条豁免：HTTP 方法集合（空 = 任意）+ 路径模式 + 豁免理由。
     *
     * @param methods     匹配的 HTTP 方法（空集合 = 任意方法）
     * @param pathPattern Spring PathPattern 路径模式
     * @param reason      豁免理由（审查用，必填）
     */
    public record Exemption(Set<HttpMethod> methods, String pathPattern, String reason) {

        public Exemption {
            methods = methods == null ? Set.of() : Set.copyOf(methods);
        }

        /** 仅 GET */
        public static Exemption get(final String pathPattern, final String reason) {
            return new Exemption(Set.of(HttpMethod.GET), pathPattern, reason);
        }

        /** 仅 POST */
        public static Exemption post(final String pathPattern, final String reason) {
            return new Exemption(Set.of(HttpMethod.POST), pathPattern, reason);
        }

        /** 写操作（POST/PUT/DELETE/PATCH） */
        public static Exemption write(final String pathPattern, final String reason) {
            return new Exemption(
                    Set.of(HttpMethod.POST, HttpMethod.PUT, HttpMethod.DELETE, HttpMethod.PATCH),
                    pathPattern, reason);
        }
    }

    /** 全部豁免条目（只读，供审计与测试逐条断言） */
    public static List<Exemption> exemptions() {
        return COMPILED.stream().map(CompiledExemption::exemption).toList();
    }

    /**
     * 判断指定方法与具体路径是否命中豁免清单。
     *
     * @param method 请求方法（可为 null）
     * @param path   具体请求路径
     * @return true 表示豁免（不走 fail-closed、报告为 EXEMPT）
     */
    public static boolean isExempt(final HttpMethod method, final String path) {
        for (CompiledExemption entry : COMPILED) {
            if (entry.matches(method, path)) {
                return true;
            }
        }
        return false;
    }

    private static List<CompiledExemption> compile() {
        PathPatternParser parser = new PathPatternParser();
        return defaultExemptions().stream()
                .map(e -> new CompiledExemption(e, parser.parse(e.pathPattern())))
                .toList();
    }

    private static List<Exemption> defaultExemptions() {
        return List.of(
                // ===== AI 服务代理面（UniversalController，7 个 POST） =====
                // 授权语义是 API-Key 服务类型权限 + 配额，不走 URL 权限矩阵。
                // API-Key 主体仅携带 ROLE_<SERVICE>（ApiKeyAuthentication:44），
                // 登记权限码规则会 403 全部 API-Key 服务调用（产品破坏性变更）。
                Exemption.write("/api/v1/**",
                        "AI 服务代理面（chat/embeddings/rerank/audio/images）：授权由 API-Key 服务类型权限 + 配额控制；"
                                + "API-Key 主体携带 ROLE_<SERVICE> 而非权限码，URL 权限码规则会阻断全部服务调用"),

                // ===== JWT 自助端点（方法级 @PreAuthorize 约束属主） =====
                Exemption.post("/api/auth/jwt/refresh",
                        "自助刷新本人令牌（前端 stores/user.ts），任意登录用户可用；无属主越权面（令牌即身份）"),
                Exemption.post("/api/auth/jwt/revoke",
                        "自助撤销本人令牌；方法级 @PreAuthorize(hasRole('ADMIN') or authentication.name == #request.userId) 已约束属主"),
                Exemption.get("/api/auth/jwt/tokens",
                        "自助令牌列表；方法级 @PreAuthorize(hasRole('ADMIN') or (hasRole('USER') and #userId == authentication.name)) 已约束属主，"
                                + "登记权限码规则会破坏普通用户管理本人令牌"),

                // ===== 控制台/客户端公共读取 =====
                Exemption.get("/api/auth/permissions",
                        "当前登录用户自身权限码（PermissionManagementController javadoc 明示任意已登录用户）；控制台菜单渲染依赖"),
                Exemption.get("/api/models",
                        "模型目录（ModelInfoController），UI 与客户端公共读取，无敏感操作面"),

                // ===== Token 用量上报（外部摄取面） =====
                // 内部代理的用量落库走进程内 TokenUsageRecorder → TokenUsageService，
                // 不经过此 HTTP 端点；这两个 POST 是对外文档公开的摄取 API
                // （docs/*/api-reference/management-api.md），调用方认证形态未知
                // （可能使用 API-Key，同样只携带 ROLE_<SERVICE>），登记权限码会破坏上报链路。
                Exemption.post("/api/token-usage/record",
                        "Token 用量上报摄取面：内部代理经进程内 TokenUsageRecorder 落库，不经此端点；"
                                + "对外公开摄取 API，调用方可能为 API-Key（仅 ROLE_<SERVICE>），登记权限码会阻断上报"),
                Exemption.post("/api/token-usage/record/batch",
                        "批量 Token 用量上报摄取面：同 /api/token-usage/record，外部摄取 API 不宜绑定权限码")
        );
    }

    /** 预编译条目 */
    private record CompiledExemption(Exemption exemption, PathPattern pattern) {

        boolean matches(final HttpMethod method, final String path) {
            if (!pattern.matches(PathContainer.parsePath(path))) {
                return false;
            }
            return exemption.methods().isEmpty()
                    || (method != null && exemption.methods().contains(method));
        }
    }
}
