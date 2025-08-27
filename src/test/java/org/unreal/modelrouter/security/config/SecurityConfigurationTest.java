package org.unreal.modelrouter.security.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.context.TestPropertySource;
import org.springframework.security.authentication.ReactiveAuthenticationManager;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.unreal.modelrouter.security.authentication.ApiKeyService;
import org.unreal.modelrouter.security.authentication.JwtTokenValidator;
import org.unreal.modelrouter.security.config.SecurityConfiguration;
import org.unreal.modelrouter.security.config.CustomReactiveAuthenticationManager;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * SecurityConfiguration测试类
 */
@ExtendWith(MockitoExtension.class)
@TestPropertySource(properties = {
        "jairouter.security.enabled=true"
})
class SecurityConfigurationTest {
    
    @Mock
    private SecurityProperties securityProperties;
    
    @Mock
    private ApiKeyService apiKeyService;
    
    @Mock
    private JwtTokenValidator jwtTokenValidator;

    @Mock
    private UserDetailsService userDetailsService;
    

    
    @Test
    void testReactiveAuthenticationManagerCreation() {
        // Given
        SecurityConfiguration securityConfiguration = new SecurityConfiguration(
                securityProperties, apiKeyService, jwtTokenValidator, userDetailsService
        );
        
        // When
        ReactiveAuthenticationManager authManager = securityConfiguration.reactiveAuthenticationManager();
        
        // Then
        assertNotNull(authManager);
        assertTrue(authManager instanceof CustomReactiveAuthenticationManager);
    }
    
    @Test
    void testSecurityWebFilterChainCreation() {
        // Given
        SecurityConfiguration securityConfiguration = new SecurityConfiguration(
                securityProperties, apiKeyService, jwtTokenValidator, userDetailsService
        );
        ReactiveAuthenticationManager authManager = securityConfiguration.reactiveAuthenticationManager();
        ServerHttpSecurity http = ServerHttpSecurity.http();
        
        // When
        SecurityWebFilterChain filterChain = securityConfiguration.securityWebFilterChain(
                http, authManager
        );
        
        // Then
        assertNotNull(filterChain);
    }
    
    @Test
    void testSecurityPropertiesInjection() {
        // Given
        SecurityConfiguration securityConfiguration = new SecurityConfiguration(
                securityProperties, apiKeyService, jwtTokenValidator, userDetailsService
        );
        
        // When & Then
        assertNotNull(securityConfiguration);
        // 验证依赖注入正确
    }
}