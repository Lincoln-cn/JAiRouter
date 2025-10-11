package org.unreal.modelrouter.security.service;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.unreal.modelrouter.security.config.properties.JwtUserProperties;
import org.unreal.modelrouter.security.config.properties.SecurityProperties;
import reactor.core.publisher.Mono;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "jairouter.security.enabled", havingValue = "true")
public class AccountManager implements UserDetailsService {
    
    private final JwtUserProperties jwtUserProperties;
    private final PasswordEncoder passwordEncoder;
    
    // 审计服务（用于记录认证操作）
    @Autowired(required = false)
    private ExtendedSecurityAuditService auditService;
    
    /**
     * 验证用户凭据
     * 
     * @param username 用户名
     * @param password 密码
     * @return 如果凭据有效返回true，否则返回false
     */
    public boolean validateCredentials(String username, String password) {
        if (!jwtUserProperties.isEnabled()) {
            log.warn("JWT认证功能未启用");
            return false;
        }
        
        return jwtUserProperties.getAccounts().stream()
                .filter(account -> account.isEnabled())
                .filter(account -> account.getUsername().equals(username))
                .anyMatch(account -> passwordEncoder.matches(password, account.getPassword()));
    }
    
    /**
     * 根据用户名查找账户信息
     * 
     * @param username 用户名
     * @return 账户信息Optional
     */
    public Optional<JwtUserProperties.UserAccount> findAccountByUsername(String username) {
        if (!jwtUserProperties.isEnabled()) {
            log.warn("JWT认证功能未启用");
            return Optional.empty();
        }
        
        return jwtUserProperties.getAccounts().stream()
                .filter(account -> account.isEnabled())
                .filter(account -> account.getUsername().equals(username))
                .findFirst();
    }
    
    /**
     * 验证用户名和密码并生成JWT令牌
     */
    public Mono<String> authenticateAndGenerateToken(String username, String password, SecurityProperties securityProperties) {
        return authenticateAndGenerateToken(username, password, securityProperties, null, null);
    }
    
    /**
     * 验证用户名和密码并生成JWT令牌（带上下文信息用于审计）
     */
    public Mono<String> authenticateAndGenerateToken(String username, String password, SecurityProperties securityProperties, String ipAddress, String userAgent) {
        // 验证配置中的用户凭据
        return Mono.just(validateCredentials(username, password))
                .filter(valid -> valid)
                .switchIfEmpty(Mono.defer(() -> {
                    // 记录认证失败审计
                    if (auditService != null) {
                        auditService.auditSecurityEvent("AUTHENTICATION_FAILED", 
                            "用户名或密码错误: " + username, username, ipAddress)
                            .onErrorResume(ex -> {
                                log.warn("记录认证失败审计失败: {}", ex.getMessage());
                                return Mono.empty();
                            })
                            .subscribe();
                    }
                    return Mono.error(new RuntimeException("用户名或密码错误"));
                }))
                .map(valid -> generateJwtToken(username, securityProperties));
    }
    
    /**
     * 生成JWT令牌
     */
    private String generateJwtToken(String username, SecurityProperties securityProperties) {
        // 获取用户详情
        UserDetails userDetails = loadUserByUsername(username);
        
        // 获取用户角色
        List<String> roles = userDetails.getAuthorities().stream()
                .map(org.springframework.security.core.GrantedAuthority::getAuthority)
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
    
    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        if (!jwtUserProperties.isEnabled()) {
            throw new UsernameNotFoundException("JWT认证功能未启用");
        }
        
        return jwtUserProperties.getAccounts().stream()
                .filter(account -> account.getUsername().equals(username) && account.isEnabled())
                .findFirst()
                .map(account -> User.builder()
                        .username(account.getUsername())
                        .password(account.getPassword())
                        .authorities(account.getRoles().stream()
                                .map(role -> role.toUpperCase())
                                .map(SimpleGrantedAuthority::new)
                                .collect(Collectors.toList()))
                        .build())
                .orElseThrow(() -> new UsernameNotFoundException("用户不存在或已被禁用: " + username));
    }
}