package org.unreal.modelrouter.security.service;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.authentication.ReactiveAuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.stereotype.Service;
import org.unreal.modelrouter.security.config.JwtUserProperties;
import org.unreal.modelrouter.security.config.SecurityProperties;
import reactor.core.publisher.Mono;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "jairouter.security.enabled", havingValue = "true")
public class UserAuthenticationService {
    
    private final ReactiveAuthenticationManager authenticationManager;
    private final UserDetailsService userDetailsService;
    private final SecurityProperties securityProperties;
    private final JwtUserProperties jwtUserProperties;
    
    /**
     * 验证用户名和密码并生成JWT令牌
     */
    public Mono<String> authenticateAndGenerateToken(String username, String password) {
        // 直接验证配置中的用户凭据
        return Mono.just(validateCredentials(username, password))
                .filter(valid -> valid)
                .switchIfEmpty(Mono.error(new RuntimeException("用户名或密码错误")))
                .map(valid -> generateJwtToken(username));
    }
    
    /**
     * 验证用户凭据
     */
    private boolean validateCredentials(String username, String password) {
        if (!jwtUserProperties.isEnabled()) {
            log.warn("JWT认证功能未启用");
            return false;
        }
        
        return jwtUserProperties.getAccounts().stream()
                .filter(account -> account.isEnabled())
                .anyMatch(account -> 
                    account.getUsername().equals(username) && 
                    account.getPassword().equals(password));
    }
    
    /**
     * 生成JWT令牌
     */
    private String generateJwtToken(String username) {
        // 获取用户详情
        UserDetails userDetails = userDetailsService.loadUserByUsername(username);
        
        // 获取用户角色
        List<String> roles = userDetails.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.toList());
        
        // 设置过期时间
        Date now = new Date();
        Date expiration = new Date(now.getTime() + 
            securityProperties.getJwt().getExpirationMinutes() * 60 * 1000L);
        
        // 创建签名密钥
        SecretKey signingKey = new SecretKeySpec(
                securityProperties.getJwt().getSecret().getBytes(StandardCharsets.UTF_8),
                SignatureAlgorithm.HS256.getJcaName());
        
        // 构建JWT令牌
        return Jwts.builder()
                .setSubject(userDetails.getUsername())
                .claim("roles", roles)
                .setIssuer(securityProperties.getJwt().getIssuer())
                .setIssuedAt(now)
                .setExpiration(expiration)
                .signWith(signingKey, SignatureAlgorithm.HS256)
                .compact();
    }
}