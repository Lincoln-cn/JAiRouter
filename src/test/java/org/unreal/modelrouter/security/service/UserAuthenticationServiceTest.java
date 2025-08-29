package org.unreal.modelrouter.security.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.ReactiveAuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.context.TestPropertySource;
import org.unreal.modelrouter.security.config.SecurityProperties;
import reactor.test.StepVerifier;

import java.util.Collections;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@TestPropertySource(properties = {
        "jairouter.security.enabled=true"
})
class UserAuthenticationServiceTest {

    @Mock
    private ReactiveAuthenticationManager authenticationManager;

    @Mock
    private UserDetailsService userDetailsService;

    @Mock
    private SecurityProperties securityProperties;

    @Mock
    private SecurityProperties.JwtConfig jwtConfig;

    private UserAuthenticationService userAuthenticationService;

    @BeforeEach
    void setUp() {
        lenient().when(securityProperties.getJwt()).thenReturn(jwtConfig);
        // 使用更长的密钥以满足HS256算法要求(至少256位)
        lenient().when(jwtConfig.getSecret()).thenReturn("test-secret-key-with-sufficient-length-for-HS256-algorithm");
        lenient().when(jwtConfig.getExpirationMinutes()).thenReturn(60L);
        lenient().when(jwtConfig.getIssuer()).thenReturn("test-issuer");

        userAuthenticationService = new UserAuthenticationService(
                authenticationManager, userDetailsService, securityProperties);
    }

    @Test
    void testAuthenticateAndGenerateToken_Success() {
        // Given
        String username = "testuser";
        String password = "testpass";

        // Mock authentication
        UserDetails userDetails = org.springframework.security.core.userdetails.User.builder()
                .username(username)
                .password("encodedPassword")
                .authorities(Collections.singletonList(new SimpleGrantedAuthority("ROLE_USER")))
                .build();
        
        Authentication authentication = new UsernamePasswordAuthenticationToken(
                userDetails, password, Collections.singletonList(new SimpleGrantedAuthority("ROLE_USER")));
        lenient().when(authenticationManager.authenticate(any()))
                .thenReturn(Mono.just(authentication));

        // Mock user details
        lenient().when(userDetailsService.loadUserByUsername(username)).thenReturn(userDetails);

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

        lenient().when(authenticationManager.authenticate(any()))
                .thenReturn(Mono.error(new RuntimeException("认证失败")));

        // When & Then
        StepVerifier.create(userAuthenticationService.authenticateAndGenerateToken(username, password))
                .expectError(RuntimeException.class)
                .verify();
    }
}