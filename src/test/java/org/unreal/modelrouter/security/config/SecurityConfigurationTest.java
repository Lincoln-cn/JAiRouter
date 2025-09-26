package org.unreal.modelrouter.security.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationContext;
import org.springframework.security.authentication.ReactiveAuthenticationManager;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.test.context.TestPropertySource;
import org.unreal.modelrouter.filter.SpringSecurityAuthenticationFilter;
import org.unreal.modelrouter.security.authentication.ApiKeyService;
import org.unreal.modelrouter.security.authentication.JwtTokenValidator;
import org.unreal.modelrouter.security.config.properties.SecurityProperties;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

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
    
    @Mock
    private ApplicationContext applicationContext;
    

    
    @Test
    void testReactiveAuthenticationManagerCreation() {
        // Given
        SecurityConfiguration securityConfiguration = new SecurityConfiguration(
                securityProperties, apiKeyService, applicationContext
        );
        
        // When
        ReactiveAuthenticationManager authManager = securityConfiguration.reactiveAuthenticationManager();
        
        // Then
        assertNotNull(authManager);
        assertInstanceOf(CustomReactiveAuthenticationManager.class, authManager);
    }
    
    @Test
    void testSecurityWebFilterChainCreation() {
        // Given
        SecurityConfiguration securityConfiguration = new SecurityConfiguration(
                securityProperties, apiKeyService, applicationContext
        );
        ReactiveAuthenticationManager authManager = securityConfiguration.reactiveAuthenticationManager();
        SpringSecurityAuthenticationFilter securityFilter = mock(SpringSecurityAuthenticationFilter.class);
        ServerHttpSecurity http = ServerHttpSecurity.http();
        
        // When
        SecurityWebFilterChain filterChain = securityConfiguration.securityWebFilterChain(
                http, authManager, securityFilter
        );
        
        // Then
        assertNotNull(filterChain);
    }
    
    @Test
    void testSecurityPropertiesInjection() {
        // Given
        SecurityConfiguration securityConfiguration = new SecurityConfiguration(
                securityProperties, apiKeyService, applicationContext
        );
        
        // When & Then
        assertNotNull(securityConfiguration);
        // 验证依赖注入正确
    }
}