package org.unreal.modelrouter.security.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.unreal.modelrouter.exception.SecurityAuthenticationException;
import org.unreal.modelrouter.security.authentication.JwtTokenValidator;
import org.unreal.modelrouter.security.config.properties.ApiKey;
import org.unreal.modelrouter.security.config.properties.JwtConfig;
import org.unreal.modelrouter.security.config.properties.SecurityProperties;
import org.unreal.modelrouter.security.model.ApiKeyAuthentication;
import org.unreal.modelrouter.security.model.JwtAuthentication;
import org.unreal.modelrouter.security.service.ApiKeyService;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

/**
 * CustomReactiveAuthenticationManager测试类
 */
@ExtendWith(MockitoExtension.class)
class CustomReactiveAuthenticationManagerTest {
    
    @Mock
    private ApiKeyService apiKeyService;
    
    @Mock
    private JwtTokenValidator jwtTokenValidator;
    
    @Mock
    private SecurityProperties securityProperties;
    
    @Mock
    private JwtConfig jwtConfig;
    
    private CustomReactiveAuthenticationManager authenticationManager;
    
    @BeforeEach
    void setUp() {
        authenticationManager = new CustomReactiveAuthenticationManager(
                apiKeyService, jwtTokenValidator, securityProperties
        );
    }
    
    @Test
    void testAuthenticateApiKey_Success() {
        // Given
        String apiKey = "test-api-key";
        ApiKey apiKeyInfo = ApiKey.builder()
                .keyId("test-key-id")
                .keyValue(apiKey)
                .permissions(List.of("read", "write"))
                .enabled(true)
                .expiresAt(LocalDateTime.now().plusDays(30))
                .build();
        
        ApiKeyAuthentication authentication = new ApiKeyAuthentication(apiKey);
        
        when(apiKeyService.validateApiKey(apiKey)).thenReturn(Mono.just(apiKeyInfo));
        
        // When & Then
        StepVerifier.create(authenticationManager.authenticate(authentication))
                .assertNext(auth -> {
                    assertTrue(auth.isAuthenticated());
                    assertEquals("test-key-id", auth.getPrincipal());
                    assertEquals(apiKey, auth.getCredentials());
                    assertTrue(auth.getAuthorities().contains(new SimpleGrantedAuthority("ROLE_READ")));
                    assertTrue(auth.getAuthorities().contains(new SimpleGrantedAuthority("ROLE_WRITE")));
                })
                .verifyComplete();
    }
    
    @Test
    void testAuthenticateApiKey_EmptyKey() {
        // Given
        ApiKeyAuthentication authentication = new ApiKeyAuthentication("");
        
        // When & Then
        StepVerifier.create(authenticationManager.authenticate(authentication))
                .expectErrorMatches(throwable -> 
                        throwable instanceof SecurityAuthenticationException &&
                        ((SecurityAuthenticationException) throwable).getErrorCode().equals("EMPTY_API_KEY")
                )
                .verify();
    }
    
    @Test
    void testAuthenticateApiKey_InvalidKey() {
        // Given
        String apiKey = "invalid-api-key";
        ApiKeyAuthentication authentication = new ApiKeyAuthentication(apiKey);
        
        when(apiKeyService.validateApiKey(apiKey))
                .thenReturn(Mono.error(new RuntimeException("Invalid API Key")));
        
        // When & Then
        StepVerifier.create(authenticationManager.authenticate(authentication))
                .expectErrorMatches(throwable -> 
                        throwable instanceof SecurityAuthenticationException &&
                        ((SecurityAuthenticationException) throwable).getErrorCode().equals("API_KEY_AUTH_FAILED")
                )
                .verify();
    }
    
    @Test
    void testAuthenticateJwt_Disabled() {
        // Given
        JwtAuthentication authentication = new JwtAuthentication("test-token");
        
        when(securityProperties.getJwt()).thenReturn(jwtConfig);
        when(jwtConfig.isEnabled()).thenReturn(false);
        
        // When & Then
        StepVerifier.create(authenticationManager.authenticate(authentication))
                .expectErrorMatches(throwable -> 
                        throwable instanceof SecurityAuthenticationException &&
                        ((SecurityAuthenticationException) throwable).getErrorCode().equals("JWT_DISABLED")
                )
                .verify();
    }
    
    @Test
    void testAuthenticateJwt_EmptyToken() {
        // Given
        JwtAuthentication authentication = new JwtAuthentication("");
        
        when(securityProperties.getJwt()).thenReturn(jwtConfig);
        when(jwtConfig.isEnabled()).thenReturn(true);
        
        // When & Then
        StepVerifier.create(authenticationManager.authenticate(authentication))
                .expectErrorMatches(throwable -> 
                        throwable instanceof SecurityAuthenticationException &&
                        ((SecurityAuthenticationException) throwable).getErrorCode().equals("EMPTY_JWT_TOKEN")
                )
                .verify();
    }
    
    @Test
    void testAuthenticateJwt_Success() {
        // Given
        String token = "valid-jwt-token";
        JwtAuthentication authentication = new JwtAuthentication(token);
        JwtAuthentication validatedAuth = new JwtAuthentication("user123", token, List.of("user"));
        validatedAuth.setAuthenticated(true);
        
        when(securityProperties.getJwt()).thenReturn(jwtConfig);
        when(jwtConfig.isEnabled()).thenReturn(true);
        when(jwtTokenValidator.validateToken(token)).thenReturn(Mono.just(validatedAuth));
        
        // When & Then
        StepVerifier.create(authenticationManager.authenticate(authentication))
                .assertNext(auth -> {
                    assertTrue(auth.isAuthenticated());
                    assertEquals("user123", auth.getPrincipal());
                })
                .verifyComplete();
    }
    
    @Test
    void testAuthenticateUnsupportedType() {
        // Given
        Authentication unsupportedAuth = new Authentication() {
            @Override
            public String getName() { return "test"; }
            @Override
            public Object getCredentials() { return null; }
            @Override
            public Object getDetails() { return null; }
            @Override
            public Object getPrincipal() { return null; }
            @Override
            public boolean isAuthenticated() { return false; }
            @Override
            public void setAuthenticated(boolean isAuthenticated) {}
            @Override
            public java.util.Collection getAuthorities() { return null; }
        };
        
        // When & Then
        StepVerifier.create(authenticationManager.authenticate(unsupportedAuth))
                .expectErrorMatches(throwable -> 
                        throwable instanceof SecurityAuthenticationException &&
                        ((SecurityAuthenticationException) throwable).getErrorCode().equals("UNSUPPORTED_AUTH_TYPE")
                )
                .verify();
    }
}