# 完整改造步骤

## 1. 配置文件修改

### 1.1 修改安全基础配置文件
修改`d:\IdeaProjects\model-router\src\main\resources\config\security\security-base.yml`中的JWT配置部分：

```yaml
    # ========================================
    # JWT 认证基础配置
    # ========================================
    jwt:
      enabled: false
      secret: ""
      algorithm: "HS256"
      expiration-minutes: 60
      refresh-expiration-days: 7
      issuer: "jairouter"
      blacklist-enabled: true
      blacklist-cache:
        expiration-seconds: 86400
        max-size: 10000
      # 用户账户配置
      accounts:
        - username: "admin"
          password: "{noop}admin123"  # 开发环境明文密码，生产环境应使用加密
          roles: [ "ADMIN", "USER" ]
          enabled: true
        - username: "user"
          password: "{noop}user123"
          roles: [ "USER" ]
          enabled: true
```

## 2. 创建配置属性类

### 2.1 创建JWT用户配置类
创建`d:\IdeaProjects\model-router\src\main\java\org\unreal\modelrouter\security\config\JwtUserProperties.java`：

```java
package org.unreal.modelrouter.security.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.List;

@Data
@Component
@ConfigurationProperties(prefix = "jairouter.security.jwt")
public class JwtUserProperties {
    
    private boolean enabled = false;
    private List<UserAccount> accounts;
    
    @Data
    public static class UserAccount {
        private String username;
        private String password;
        private List<String> roles;
        private boolean enabled = true;
    }
}
```

## 3. 创建数据传输对象(DTO)

### 3.1 创建登录请求DTO
创建`d:\IdeaProjects\model-router\src\main\java\org\unreal\modelrouter\dto\LoginRequest.java`：

```java
package org.unreal.modelrouter.dto;

import lombok.Data;

@Data
public class LoginRequest {
    private String username;
    private String password;
}
```

### 3.2 创建登录响应DTO
创建`d:\IdeaProjects\model-router\src\main\java\org\unreal\modelrouter\dto\LoginResponse.java`：

```java
package org.unreal.modelrouter.dto;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class LoginResponse {
    private String token;
    private String tokenType = "Bearer";
    private long expiresIn;
    private String message;
    private LocalDateTime timestamp;
}
```

## 4. 修改用户详情服务

### 4.1 修改UserDetailsServiceImpl
修改`d:\IdeaProjects\model-router\src\main\java\org\unreal\modelrouter\security\service\UserDetailsServiceImpl.java`：

```java
package org.unreal.modelrouter.security.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.unreal.modelrouter.security.config.JwtUserProperties;

import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserDetailsServiceImpl implements UserDetailsService {
    
    private final JwtUserProperties jwtUserProperties;
    
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
                                .map(role -> "ROLE_" + role.toUpperCase())
                                .collect(Collectors.toList()))
                        .build())
                .orElseThrow(() -> new UsernameNotFoundException("用户不存在或已被禁用: " + username));
    }
}
```

## 5. 创建用户认证服务

### 5.1 创建UserAuthenticationService
创建`d:\IdeaProjects\model-router\src\main\java\org\unreal\modelrouter\security\service\UserAuthenticationService.java`：

```java
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
```

## 6. 修改JwtTokenController

### 6.1 添加登录接口
修改`d:\IdeaProjects\model-router\src\main\java\org\unreal\modelrouter\controller\JwtTokenController.java`：

```java|CODE_EDIT_BLOCK|d:\IdeaProjects\model-router\src\main\java\org\unreal\modelrouter\controller\JwtTokenController.java
// ... existing code ...
import org.unreal.modelrouter.security.service.UserAuthenticationService;
import org.unreal.modelrouter.dto.LoginRequest;
import org.unreal.modelrouter.dto.LoginResponse;
import org.unreal.modelrouter.security.config.SecurityProperties;
import org.unreal.modelrouter.security.service.UserDetailsServiceImpl;
// ... existing code ...

@RequiredArgsConstructor
public class JwtTokenController {

    private final JwtTokenRefreshService tokenRefreshService;
    private final JwtTokenValidator jwtTokenValidator;
    private final UserAuthenticationService userAuthenticationService;
    private final SecurityProperties securityProperties;
    private final UserDetailsServiceImpl userDetailsService;

    // ... existing code ...

    /**
     * 用户登录获取JWT令牌
     */
    @PostMapping("/login")
    @Operation(summary = "用户登录", description = "使用用户名和密码登录获取JWT令牌")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "登录成功"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "请求参数错误"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "用户名或密码错误")
    })
    public Mono<ResponseEntity<RouterResponse<LoginResponse>>> login(
            @Parameter(description = "登录请求") @RequestBody LoginRequest request) {

        log.debug("收到用户登录请求: username={}", request.getUsername());

        return userAuthenticationService.authenticateAndGenerateToken(request.getUsername(), request.getPassword())
                .map(token -> {
                    LoginResponse response = new LoginResponse();
                    response.setToken(token);
                    response.setTokenType("Bearer");
                    response.setExpiresIn(securityProperties.getJwt().getExpirationMinutes() * 60L);
                    response.setMessage("登录成功");
                    response.setTimestamp(LocalDateTime.now());

                    return ResponseEntity.ok(RouterResponse.success(response, "登录成功"));
                })
                .onErrorResume(ex -> {
                    log.warn("用户登录失败: {}", ex.getMessage());

                    LoginResponse response = new LoginResponse();
                    response.setMessage("登录失败: " + ex.getMessage());
                    response.setTimestamp(LocalDateTime.now());

                    return Mono.just(ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                            .body(RouterResponse.error("登录失败: " + ex.getMessage(), "LOGIN_FAILED")));
                });
    }

    // ... existing code ...
```

## 7. 修改安全配置

### 7.1 修改SecurityConfiguration
修改`d:\IdeaProjects\model-router\src\main\java\org\unreal\modelrouter\security\config\SecurityConfiguration.java`，允许登录接口匿名访问：

```java|CODE_EDIT_BLOCK|d:\IdeaProjects\model-router\src\main\java\org\unreal\modelrouter\security\config\SecurityConfiguration.java
// ... existing code ...

    @Bean
    public SecurityWebFilterChain securityWebFilterChain(ServerHttpSecurity http) {
        log.info("配置WebFlux安全过滤器链");
        
        return http
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                .cors(ServerHttpSecurity.CorsSpec::disable)
                .authorizeExchange(exchanges -> exchanges
                        // 允许匿名访问的端点
                        .pathMatchers("/actuator/health").permitAll()
                        .pathMatchers("/swagger-ui/**").permitAll()
                        .pathMatchers("/v3/api-docs/**").permitAll()
                        // JWT登录端点允许匿名访问
                        .pathMatchers(HttpMethod.POST, "/api/auth/jwt/login").permitAll()
                        // 其他JWT管理端点需要认证
                        .pathMatchers(HttpMethod.POST, "/api/auth/jwt/refresh").authenticated()
                        .pathMatchers(HttpMethod.POST, "/api/auth/jwt/revoke").authenticated()
                        .pathMatchers(HttpMethod.POST, "/api/auth/jwt/revoke/batch").hasRole("ADMIN")
                        .pathMatchers(HttpMethod.POST, "/api/auth/jwt/validate").permitAll()
                        .pathMatchers(HttpMethod.GET, "/api/auth/jwt/blacklist/stats").hasRole("ADMIN")
                        // API路由端点需要认证和相应权限
                        .pathMatchers(HttpMethod.POST, "/v1/chat/completions").hasAnyRole("CHAT", "ADMIN")
                        .pathMatchers(HttpMethod.POST, "/v1/embeddings").hasAnyRole("EMBEDDING", "ADMIN")
                        .pathMatchers(HttpMethod.POST, "/v1/rerank").hasAnyRole("RERANK", "ADMIN")
                        .pathMatchers(HttpMethod.GET, "/v1/models").hasAnyRole("READ", "CHAT", "EMBEDDING", "RERANK", "ADMIN")
                        .pathMatchers(HttpMethod.POST, "/v1/audio/speech").hasAnyRole("TTS", "ADMIN")
                        .pathMatchers(HttpMethod.POST, "/v1/audio/transcriptions").hasAnyRole("STT", "ADMIN")
                        .pathMatchers(HttpMethod.POST, "/v1/images/generations").hasAnyRole("IMAGE_GENERATION", "ADMIN")
                        .pathMatchers(HttpMethod.POST, "/v1/images/edits").hasAnyRole("IMAGE_EDITING", "ADMIN")
                        .pathMatchers(HttpMethod.PUT, "/v1/**").hasAnyRole("WRITE", "ADMIN")
                        .pathMatchers(HttpMethod.PATCH, "/v1/**").hasAnyRole("WRITE", "ADMIN")
                        .pathMatchers(HttpMethod.DELETE, "/v1/**").hasAnyRole("DELETE", "ADMIN")
                        // 其他所有请求需要认证
                        .anyExchange().authenticated()
                )
                // 添加自定义的认证过滤器
                .addFilterBefore(securityIntegratedApiKeyFilter(), SecurityWebFiltersOrder.AUTHENTICATION)
                // 设置认证管理器
                .authenticationManager(authenticationManager)
                .build();
    }

// ... existing code ...
```

### 7.2 添加密码编码器配置
在`d:\IdeaProjects\model-router\src\main\java\org\unreal\modelrouter\security\config\SecurityConfiguration.java`中添加密码编码器Bean：

```java|CODE_EDIT_BLOCK|d:\IdeaProjects\model-router\src\main\java\org\unreal\modelrouter\security\config\SecurityConfiguration.java
// ... existing code ...

    /**
     * 配置响应式认证管理器
     * 处理不同类型的认证请求
     */
    @Bean
    public ReactiveAuthenticationManager reactiveAuthenticationManager() {
        log.info("创建响应式认证管理器");
        return new CustomReactiveAuthenticationManager(
                apiKeyService, 
                jwtTokenValidator, 
                securityProperties
        );
    }
    
    /**
     * 配置密码编码器
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new org.springframework.security.crypto.password.DelegatingPasswordEncoder(
                "bcrypt", 
                java.util.Map.of(
                        "bcrypt", new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder(),
                        "noop", org.springframework.security.crypto.password.NoOpPasswordEncoder.getInstance()
                )
        );
    }
    
    /**
     * 配置认证管理器
     */
    @Bean
    public AuthenticationManager authenticationManager() {
        return new org.springframework.security.authentication.ProviderManager(
                java.util.Arrays.asList(
                        new org.springframework.security.authentication.dao.DaoAuthenticationProvider(passwordEncoder()) {{
                            setUserDetailsService(userDetailsService);
                        }}
                )
        );
    }

// ... existing code ...
```

## 8. 创建单元测试

### 8.1 创建UserAuthenticationService测试
创建`d:\IdeaProjects\model-router\src\test\java\org\unreal\modelrouter\security\service\UserAuthenticationServiceTest.java`：

```java
package org.unreal.modelrouter.security.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.unreal.modelrouter.security.config.SecurityProperties;
import reactor.test.StepVerifier;

import java.util.Collections;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserAuthenticationServiceTest {

    @Mock
    private AuthenticationManager authenticationManager;

    @Mock
    private UserDetailsService userDetailsService;

    @Mock
    private SecurityProperties securityProperties;

    @Mock
    private SecurityProperties.JwtConfig jwtConfig;

    private UserAuthenticationService userAuthenticationService;

    @BeforeEach
    void setUp() {
        when(securityProperties.getJwt()).thenReturn(jwtConfig);
        when(jwtConfig.getSecret()).thenReturn("test-secret");
        when(jwtConfig.getExpirationMinutes()).thenReturn(60);
        when(jwtConfig.getIssuer()).thenReturn("test-issuer");

        userAuthenticationService = new UserAuthenticationService(
                authenticationManager, userDetailsService, securityProperties);
    }

    @Test
    void testAuthenticateAndGenerateToken_Success() {
        // Given
        String username = "testuser";
        String password = "testpass";

        // Mock authentication
        Authentication authentication = new UsernamePasswordAuthenticationToken(
                username, password, Collections.singletonList(new SimpleGrantedAuthority("ROLE_USER")));
        when(authenticationManager.authenticate(any())).thenReturn(authentication);

        // Mock user details
        UserDetails userDetails = org.springframework.security.core.userdetails.User.builder()
                .username(username)
                .password("encodedPassword")
                .authorities(Collections.singletonList(new SimpleGrantedAuthority("ROLE_USER")))
                .build();
        when(userDetailsService.loadUserByUsername(username)).thenReturn(userDetails);

        // When & Then
        StepVerifier.create(userAuthenticationService.authenticateAndGenerateToken(username, password))
                .expectNextMatches(token -> token != null && !token.isEmpty())
                .verifyComplete();
    }

    @Test
    void testAuthenticateAndGenerateToken_Failure() {
        // Given
        String username = "testuser";
        String password = "wrongpass";

        when(authenticationManager.authenticate(any()))
                .thenThrow(new RuntimeException("认证失败"));

        // When & Then
        StepVerifier.create(userAuthenticationService.authenticateAndGenerateToken(username, password))
                .expectError(RuntimeException.class)
                .verify();
    }
}
```

## 9. 更新文档

### 9.1 更新JWT认证文档
更新`d:\IdeaProjects\model-router\docs\zh\security\jwt-authentication.md`，添加登录接口说明：

在文档中添加以下内容：

```markdown
## 登录获取JWT令牌

### 登录端点

```
POST /api/auth/jwt/login
```

### 请求示例

```bash
curl -X POST http://localhost:8080/api/auth/jwt/login \
     -H "Content-Type: application/json" \
     -d '{
           "username": "admin",
           "password": "admin123"
         }'
```

### 响应示例

```json
{
  "success": true,
  "message": "登录成功",
  "data": {
    "token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
    "tokenType": "Bearer",
    "expiresIn": 3600,
    "message": "登录成功",
    "timestamp": "2023-01-01T12:00:00"
  },
  "errorCode": null
}
```

### 错误响应

```json
{
  "success": false,
  "message": "登录失败: 用户名或密码错误",
  "data": null,
  "errorCode": "LOGIN_FAILED"
}
```


## 10. 验证和测试

### 10.1 启动应用并测试
1. 启动应用
2. 使用curl或Postman测试登录接口
3. 验证获取的JWT token可以用于其他需要认证的接口
4. 确认token过期和验证逻辑正常工作

### 10.2 测试用例验证
运行所有测试确保没有破坏现有功能：
```bash
mvn test
```

这个完整的改造方案将用户账户配置直接集成在JWT配置下，更符合项目的配置结构规范，同时保持了与现有API Key认证方式的兼容性。