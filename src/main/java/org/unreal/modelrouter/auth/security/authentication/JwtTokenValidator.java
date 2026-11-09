package org.unreal.modelrouter.auth.security.authentication;

import org.springframework.security.core.Authentication;
import reactor.core.publisher.Mono;

/**
 * JWT令牌验证器接口
 * 提供JWT令牌的验证、解析和刷新功能
 */
public interface JwtTokenValidator {
    
    /**
     * 验证JWT令牌
     * @param token JWT令牌字符串
     * @return 认证信息
     */
    Mono<Authentication> validateToken(String token);
    
    /**
     * 刷新JWT令牌
     * @param token 当前JWT令牌
     * @return 新的JWT令牌
     */
    Mono<String> refreshToken(String token);
    
    /**
     * 检查令牌是否在黑名单中
     * @param token JWT令牌
     * @return 是否在黑名单中
     */
    Mono<Boolean> isTokenBlacklisted(String token);
    
    /**
     * 将令牌加入黑名单
     * @param token JWT令牌
     * @return 操作结果
     */
    Mono<Void> blacklistToken(String token);
    
    /**
     * 从令牌中提取用户ID
     * @param token JWT令牌
     * @return 用户ID
     */
    Mono<String> extractUserId(String token);

    /**
     * 从令牌中提取主体（subject）。
     *
     * <p>与 {@link #extractUserId(String)} 的区别：那个优先取 {@code userId} 声明、缺失才回落到
     * subject，而本方法**只取 subject** —— 它要与 {@code Authentication#getName()} 对齐
     * （后者在 {@code DefaultJwtTokenValidator#validateToken} 里正是用 subject 构造的）。</p>
     *
     * <p>issue #1060 引入：令牌刷新端点需要比对「令牌是谁的」与「调用者是谁」。</p>
     *
     * @param token JWT令牌
     * @return 令牌主体；令牌无效时以 {@code AuthenticationException} 终止
     */
    Mono<String> extractSubject(String token);
}