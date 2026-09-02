package org.unreal.modelrouter.auth.security.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
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
import org.unreal.modelrouter.auth.security.config.properties.JwtAccountProperties;
import org.unreal.modelrouter.auth.security.config.properties.SecurityProperties;
import org.unreal.modelrouter.auth.security.audit.ExtendedSecurityAuditService;
import org.unreal.modelrouter.persistence.jpa.entity.JwtAccountEntity;
import org.unreal.modelrouter.persistence.jpa.repository.JwtAccountRepository;
import reactor.core.publisher.Mono;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "jairouter.security.enabled", havingValue = "true")
public class AccountManager implements UserDetailsService {

    private final SecurityProperties securityProperties;
    private final PasswordEncoder passwordEncoder;
    private final RolePermissionService rolePermissionService;
    private final JwtAccountRepository jwtAccountRepository;
    private final ObjectMapper objectMapper;

    @jakarta.annotation.PostConstruct
    public void init() {
        log.info("=== AccountManager initialized ===");
        log.info("JWT enabled: {}", securityProperties.getJwt().isEnabled());
        log.info("JWT accounts count: {}", 
            securityProperties.getJwt().getAccounts() != null ? securityProperties.getJwt().getAccounts().size() : 0);
    }

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
    public boolean validateCredentials(final String username, final String password) {
        if (!securityProperties.getJwt().isEnabled()) {
            log.warn("JWT认证功能未启用");
            return false;
        }

        // 1. YAML 静态账户优先，行为与旧版本完全一致
        List<JwtAccountProperties> accounts = securityProperties.getJwt().getAccounts();
        if (accounts == null || accounts.isEmpty()) {
            log.warn("未配置YAML JWT账户，尝试数据库账户");
        } else {
            log.info("验证用户凭据: username={}, accountsCount={}", username, accounts.size());
            Optional<JwtAccountProperties> yamlAccount = accounts.stream()
                    .filter(JwtAccountProperties::isEnabled)
                    .filter(account -> account.getUsername().equals(username))
                    .findFirst();
            if (yamlAccount.isPresent()) {
                boolean matches = passwordEncoder.matches(password, yamlAccount.get().getPassword());
                log.info("YAML账户密码匹配结果: username={}, matches={}", username, matches);
                return matches;
            }
            log.info("YAML账户未命中，尝试数据库账户: username={}", username);
        }

        // 2. DB 账户兜底（Web 账户管理 API 创建的账户）
        Optional<JwtAccountEntity> dbAccount = jwtAccountRepository.findByUsername(username);
        if (dbAccount.isEmpty()) {
            log.info("数据库账户未找到: username={}", username);
            return false;
        }
        JwtAccountEntity entity = dbAccount.get();
        if (Boolean.FALSE.equals(entity.getEnabled())) {
            log.warn("数据库账户已禁用: username={}", username);
            return false;
        }
        boolean matches = passwordEncoder.matches(password, entity.getPassword());
        log.info("数据库账户密码匹配结果: username={}, matches={}", username, matches);
        return matches;
    }

    /**
     * 根据用户名查找账户信息
     *
     * @param username 用户名
     * @return 账户信息Optional
     */
    public Optional<JwtAccountProperties> findAccountByUsername(final String username) {
        if (!securityProperties.getJwt().isEnabled()) {
            log.warn("JWT认证功能未启用");
            return Optional.empty();
        }

        List<JwtAccountProperties> accounts = securityProperties.getJwt().getAccounts();
        if (accounts == null || accounts.isEmpty()) {
            return Optional.empty();
        }

        return accounts.stream()
                .filter(JwtAccountProperties::isEnabled)
                .filter(account -> account.getUsername().equals(username))
                .findFirst();
    }

    /**
     * 验证用户名和密码并生成JWT令牌
     */
    public Mono<String> authenticateAndGenerateToken(
            final String username, final String password,
            final SecurityProperties securityProperties) {
        return authenticateAndGenerateToken(username, password, securityProperties, null, null);
    }

    /**
     * 验证用户名和密码并生成JWT令牌（带上下文信息用于审计）
     */
    public Mono<String> authenticateAndGenerateToken(
            final String username, final String password,
            final SecurityProperties securityProperties,
            final String ipAddress, final String userAgent) {
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
    private String generateJwtToken(final String username, final SecurityProperties securityProperties) {
        // 获取用户详情
        UserDetails userDetails = loadUserByUsername(username);

        // 获取用户角色
        List<String> roles = userDetails.getAuthorities().stream()
                .map(org.springframework.security.core.GrantedAuthority::getAuthority)
                .collect(Collectors.toList());

        // v2.9.8 RBAC: 按角色从 role_permissions 表查询权限码，写入 permissions claim
        List<String> permissions = rolePermissionService.getPermissionCodesForRoles(roles);

        // 设置过期时间
        Date now = new Date();
        Date expiration = new Date(now.getTime() 
        + securityProperties.getJwt().getExpirationMinutes() * 60 * 1000L);

        // 创建签名密钥（使用JJWT 0.12.x API）
        SecretKey signingKey = Keys.hmacShaKeyFor(
                securityProperties.getJwt().getSecret().getBytes(StandardCharsets.UTF_8));

        // 构建JWT令牌（使用JJWT 0.12.x API）
        return Jwts.builder()
                .subject(userDetails.getUsername())
                .claim("roles", roles)
                .claim("permissions", permissions)
                .issuer(securityProperties.getJwt().getIssuer())
                .issuedAt(now)
                .expiration(expiration)
                .id(UUID.randomUUID().toString()) // 设置JTI用于黑名单管理
                .signWith(signingKey)
                .compact();
    }

    @Override
    public UserDetails loadUserByUsername(final String username) throws UsernameNotFoundException {
        if (!securityProperties.getJwt().isEnabled()) {
            throw new UsernameNotFoundException("JWT认证功能未启用");
        }

        // 1. YAML 静态账户优先，行为与旧版本完全一致
        List<JwtAccountProperties> accounts = securityProperties.getJwt().getAccounts();
        if (accounts != null && !accounts.isEmpty()) {
            Optional<JwtAccountProperties> yamlAccount = accounts.stream()
                    .filter(account -> account.getUsername().equals(username) && account.isEnabled())
                    .findFirst();
            if (yamlAccount.isPresent()) {
                JwtAccountProperties account = yamlAccount.get();
                return User.builder()
                        .username(account.getUsername())
                        .password(account.getPassword())
                        .authorities(buildAuthorities(account.getRoles()))
                        .build();
            }
        }

        // 2. DB 账户兜底（Web 账户管理 API 创建的账户）
        JwtAccountEntity entity = jwtAccountRepository.findByUsername(username)
                .filter(dbAccount -> dbAccount.getEnabled() == null || dbAccount.getEnabled())
                .orElseThrow(() -> new UsernameNotFoundException("用户不存在或已被禁用: " + username));

        return User.builder()
                .username(entity.getUsername())
                .password(entity.getPassword())
                .authorities(buildAuthorities(parseRoles(entity.getRoles())))
                .build();
    }

    /**
     * 解析数据库账户的角色 JSON 字符串（如 ["ADMIN", "USER"]）
     *
     * <p>解析失败时降级为空列表，避免因角色数据异常导致登录失败。
     *
     * @param rolesJson 角色 JSON 字符串
     * @return 角色列表（可能为空）
     */
    private List<String> parseRoles(final String rolesJson) {
        if (rolesJson == null || rolesJson.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readerForListOf(String.class).readValue(rolesJson);
        } catch (JsonProcessingException e) {
            log.warn("解析数据库账户角色失败，按无角色处理: {}", e.getMessage());
            return List.of();
        }
    }

    /**
     * 将角色列表转为大写并封装为授权对象（与 YAML 账户逻辑一致）
     *
     * @param roles 角色列表（可为空）
     * @return 授权列表
     */
    private List<SimpleGrantedAuthority> buildAuthorities(final List<String> roles) {
        return roles.stream()
                .filter(role -> role != null && !role.isBlank())
                .map(role -> role.toUpperCase())
                .map(SimpleGrantedAuthority::new)
                .collect(Collectors.toList());
    }
}