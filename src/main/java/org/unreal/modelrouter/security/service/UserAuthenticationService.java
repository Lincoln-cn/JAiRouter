package org.unreal.modelrouter.security.service;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.stereotype.Service;
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
public class UserAuthenticationService {
    
    private final AuthenticationManager authenticationManager;
    private final UserDetailsService userDetailsService;
    private final SecurityProperties securityProperties;
    
    /**
     * 验证用户名和密码并生成JWT令牌
     */
    public Mono<String> authenticateAndGenerateToken(String username, String password) {
        try {
            // 创建认证令牌
            UsernamePasswordAuthenticationToken authToken = 
                new UsernamePasswordAuthenticationToken(username, password);
            
            // 进行认证
            Authentication authentication = authenticationManager.authenticate(authToken);
            
            // 认证成功后生成JWT令牌
            return Mono.just(generateJwtToken(authentication));
        } catch (Exception e) {
            log.warn("用户认证失败: {}", e.getMessage());
            return Mono.error(new RuntimeException("用户名或密码错误"));
        }
    }
    
    /**
     * 生成JWT令牌
     */
    private String generateJwtToken(Authentication authentication) {
        // 获取用户详情
        UserDetails userDetails = (UserDetails) authentication.getPrincipal();
        
        // 获取用户角色
        List<String> roles = authentication.getAuthorities().stream()
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