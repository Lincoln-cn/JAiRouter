package org.unreal.modelrouter.security.authentication;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.unreal.modelrouter.security.audit.SecurityAuditService;
import org.unreal.modelrouter.security.config.SecurityProperties;
import org.unreal.modelrouter.security.exception.SecurityAuthenticationException;
import org.unreal.modelrouter.security.model.ApiKeyAuthentication;
import org.unreal.modelrouter.security.model.ApiKeyInfo;
import org.unreal.modelrouter.security.model.SecurityAuditEvent;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * ApiKeyAuthenticationProvider测试类
 */
@ExtendWith(MockitoExtension.class)
class ApiKeyAuthenticationProviderTest {
    
    @Mock
    private ApiKeyService apiKeyService;
    
    @Mock
    private SecurityProperties securityProperties;
    
    @Mock
    private ApplicationEventPublisher eventPublisher;
    
    @Mock
    private SecurityAuditService auditService;
    
    private ApiKeyAuthenticationProvider authenticationProvider;
    
    @BeforeEach
    void setUp() {
        authenticationProvider = new ApiKeyAuthenticationProvider(
                apiKeyService, securityProperties, eventPublisher, auditService
        );
    }
    
    @Test
    void testSupports() {
        // Test that it supports ApiKeyAuthentication
        assertTrue(authenticationProvider.supports(ApiKeyAuthentication.class));
        
        // Test that it doesn't support other authentication types
        assertFalse(authenticationProvider.supports(Authentication.class));
    }
    
    @Test
    void testAuthenticate_Success() {
        // Given
        String apiKey = "test-api-key";
        ApiKeyInfo apiKeyInfo = ApiKeyInfo.builder()
                .keyId("test-key-id")
                .keyValue(apiKey)
                .description("Test API Key")
                .permissions(List.of("read", "write"))
                .enabled(true)
                .expiresAt(LocalDateTime.now().plusDays(30))
                .build();
        
        ApiKeyAuthentication authentication = new ApiKeyAuthentication(apiKey);
        
        when(apiKeyService.validateApiKey(apiKey)).thenReturn(Mono.just(apiKeyInfo));
        when(auditService.recordEvent(any(SecurityAuditEvent.class))).thenReturn(Mono.empty());
        
        // When
        Authentication result = authenticationProvider.authenticate(authentication);
        
        // Then
        assertNotNull(result);
        assertTrue(result.isAuthenticated());
        assertEquals("test-key-id", result.getPrincipal());
        assertEquals(apiKey, result.getCredentials());
        assertTrue(result.getAuthorities().contains(new SimpleGrantedAuthority("ROLE_READ")));
        assertTrue(result.getAuthorities().contains(new SimpleGrantedAuthority("ROLE_WRITE")));
        assertEquals(apiKeyInfo, result.getDetails());
        
        // Verify events were published
        verify(eventPublisher).publishEvent(any(ApiKeyAuthenticationProvider.ApiKeyAuthenticationSuccessEvent.class));
        verify(auditService).recordEvent(any(SecurityAuditEvent.class));
    }
    
    @Test
    void testAuthenticate_InvalidApiKey() {
        // Given
        String apiKey = "invalid-api-key";
        ApiKeyAuthentication authentication = new ApiKeyAuthentication(apiKey);
        
        when(apiKeyService.validateApiKey(apiKey)).thenReturn(Mono.empty());
        when(auditService.recordEvent(any(SecurityAuditEvent.class))).thenReturn(Mono.empty());
        
        // When & Then
        SecurityAuthenticationException exception = assertThrows(
                SecurityAuthenticationException.class,
                () -> authenticationProvider.authenticate(authentication)
        );
        
        assertEquals("INVALID_API_KEY", exception.getErrorCode());
        assertEquals("无效的API Key", exception.getMessage());
        
        // Verify failure event was published
        verify(eventPublisher).publishEvent(any(ApiKeyAuthenticationProvider.ApiKeyAuthenticationFailureEvent.class));
        verify(auditService).recordEvent(any(SecurityAuditEvent.class));
    }
    
    @Test
    void testAuthenticate_ServiceException() {
        // Given
        String apiKey = "test-api-key";
        ApiKeyAuthentication authentication = new ApiKeyAuthentication(apiKey);
        
        when(apiKeyService.validateApiKey(apiKey))
                .thenReturn(Mono.error(new RuntimeException("Service error")));
        when(auditService.recordEvent(any(SecurityAuditEvent.class))).thenReturn(Mono.empty());
        
        // When & Then
        SecurityAuthenticationException exception = assertThrows(
                SecurityAuthenticationException.class,
                () -> authenticationProvider.authenticate(authentication)
        );
        
        assertEquals("API_KEY_AUTH_ERROR", exception.getErrorCode());
        assertTrue(exception.getMessage().contains("Service error"));
        
        // Verify failure event was published
        verify(eventPublisher).publishEvent(any(ApiKeyAuthenticationProvider.ApiKeyAuthenticationFailureEvent.class));
        verify(auditService).recordEvent(any(SecurityAuditEvent.class));
    }
    
    @Test
    void testAuthenticate_UnsupportedAuthenticationType() {
        // Given
        Authentication unsupportedAuth = mock(Authentication.class);
        
        // When
        Authentication result = authenticationProvider.authenticate(unsupportedAuth);
        
        // Then
        assertNull(result);
        
        // Verify no events were published
        verify(eventPublisher, never()).publishEvent(any());
        verify(auditService, never()).recordEvent(any(SecurityAuditEvent.class));
    }
    
    @Test
    void testAuthenticate_EventPublishingFailure() {
        // Given
        String apiKey = "test-api-key";
        ApiKeyInfo apiKeyInfo = ApiKeyInfo.builder()
                .keyId("test-key-id")
                .keyValue(apiKey)
                .permissions(List.of("read"))
                .enabled(true)
                .build();
        
        ApiKeyAuthentication authentication = new ApiKeyAuthentication(apiKey);
        
        when(apiKeyService.validateApiKey(apiKey)).thenReturn(Mono.just(apiKeyInfo));
        when(auditService.recordEvent(any(SecurityAuditEvent.class))).thenReturn(Mono.empty());
        doThrow(new RuntimeException("Event publishing failed"))
                .when(eventPublisher)
                .publishEvent(any(ApiKeyAuthenticationProvider.ApiKeyAuthenticationSuccessEvent.class));
        
        // When - should not throw exception even if event publishing fails
        Authentication result = authenticationProvider.authenticate(authentication);
        
        // Then
        assertNotNull(result);
        assertTrue(result.isAuthenticated());
        assertEquals("test-key-id", result.getPrincipal());
    }
}