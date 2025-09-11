package org.unreal.modelrouter.security.authentication;

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
}