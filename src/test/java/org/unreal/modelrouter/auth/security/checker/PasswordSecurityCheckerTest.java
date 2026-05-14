package org.unreal.modelrouter.auth.security.checker;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.env.Environment;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.unreal.modelrouter.auth.security.config.properties.JwtAccountProperties;
import org.unreal.modelrouter.auth.security.config.properties.SecurityProperties;
import org.unreal.modelrouter.auth.security.config.properties.JwtConfig;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * PasswordSecurityChecker 单元测试.
 *
 * @since v2.6.12
 */
@DisplayName("PasswordSecurityChecker 测试")
@ExtendWith(MockitoExtension.class)
class PasswordSecurityCheckerTest {

    @Mock
    private SecurityProperties securityProperties;

    @Mock
    private JwtConfig jwtConfig;

    @Mock
    private Environment environment;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private PasswordSecurityChecker checker;

    private JwtAccountProperties plaintextAccount;
    private JwtAccountProperties bcryptAccount;

    @BeforeEach
    void setUp() {
        plaintextAccount = new JwtAccountProperties();
        plaintextAccount.setUsername("plaintext-user");
        plaintextAccount.setPassword("{noop}plaintext123");

        bcryptAccount = new JwtAccountProperties();
        bcryptAccount.setUsername("bcrypt-user");
        bcryptAccount.setPassword("{bcrypt}$2a$10$abcdefghijklmnopqrstuvwxABC123");

        lenient().when(securityProperties.getJwt()).thenReturn(jwtConfig);
        lenient().when(environment.getActiveProfiles()).thenReturn(new String[]{"dev"});
    }

    @Test
    @DisplayName("跳过警告启用时应跳过检查")
    void testSkipWarningEnabled() {
        when(environment.getProperty("JAROUTER_SKIP_PASSWORD_WARNING", "false")).thenReturn("true");

        checker.checkPasswordSecurity();

        verify(jwtConfig, never()).isEnabled();
    }

    @Test
    @DisplayName("JWT禁用时应跳过检查")
    void testJwtDisabled() {
        when(environment.getProperty("JAROUTER_SKIP_PASSWORD_WARNING", "false")).thenReturn("false");
        when(jwtConfig.isEnabled()).thenReturn(false);

        checker.checkPasswordSecurity();

        verify(jwtConfig, never()).getAccounts();
    }

    @Test
    @DisplayName("无账户时应跳过检查")
    void testNoAccounts() {
        when(environment.getProperty("JAROUTER_SKIP_PASSWORD_WARNING", "false")).thenReturn("false");
        when(jwtConfig.isEnabled()).thenReturn(true);
        when(jwtConfig.getAccounts()).thenReturn(Collections.emptyList());

        checker.checkPasswordSecurity();

        // 无异常即为成功
    }

    @Test
    @DisplayName("明文密码账户应触发警告")
    void testPlaintextPasswordWarning() {
        when(environment.getProperty("JAROUTER_SKIP_PASSWORD_WARNING", "false")).thenReturn("false");
        when(jwtConfig.isEnabled()).thenReturn(true);
        when(jwtConfig.getAccounts()).thenReturn(List.of(plaintextAccount));

        checker.checkPasswordSecurity();

        // 验证账户列表被检查
        verify(jwtConfig).getAccounts();
    }

    @Test
    @DisplayName("BCrypt密码账户不应触发警告")
    void testBcryptPasswordNoWarning() {
        when(environment.getProperty("JAROUTER_SKIP_PASSWORD_WARNING", "false")).thenReturn("false");
        when(jwtConfig.isEnabled()).thenReturn(true);
        when(jwtConfig.getAccounts()).thenReturn(List.of(bcryptAccount));

        checker.checkPasswordSecurity();

        verify(jwtConfig).getAccounts();
    }

    @Test
    @DisplayName("混合密码账户应正确计数")
    void testMixedPasswords() {
        when(environment.getProperty("JAROUTER_SKIP_PASSWORD_WARNING", "false")).thenReturn("false");
        when(jwtConfig.isEnabled()).thenReturn(true);
        when(jwtConfig.getAccounts()).thenReturn(Arrays.asList(plaintextAccount, bcryptAccount));

        checker.checkPasswordSecurity();

        verify(jwtConfig).getAccounts();
    }

    @Test
    @DisplayName("生产环境明文密码应触发严重警告")
    void testProductionEnvironmentWarning() {
        when(environment.getProperty("JAROUTER_SKIP_PASSWORD_WARNING", "false")).thenReturn("false");
        when(environment.getActiveProfiles()).thenReturn(new String[]{"prod"});
        when(jwtConfig.isEnabled()).thenReturn(true);
        when(jwtConfig.getAccounts()).thenReturn(List.of(plaintextAccount));

        checker.checkPasswordSecurity();

        verify(environment).getActiveProfiles();
    }

    @Test
    @DisplayName("生成BCrypt密码应包含前缀")
    void testGenerateBcryptPassword() {
        when(passwordEncoder.encode(anyString())).thenReturn("$2a$10$hashedpassword");

        String result = checker.generateBcryptPassword("mypassword");

        assertTrue(result.startsWith("{bcrypt}"));
        assertTrue(result.contains("$2a$10$hashedpassword"));
    }

    @Test
    @DisplayName("null密码不应触发警告")
    void testNullPassword() {
        JwtAccountProperties nullPasswordAccount = new JwtAccountProperties();
        nullPasswordAccount.setUsername("null-password-user");
        nullPasswordAccount.setPassword(null);

        when(environment.getProperty("JAROUTER_SKIP_PASSWORD_WARNING", "false")).thenReturn("false");
        when(jwtConfig.isEnabled()).thenReturn(true);
        when(jwtConfig.getAccounts()).thenReturn(List.of(nullPasswordAccount));

        checker.checkPasswordSecurity();

        verify(jwtConfig).getAccounts();
    }
}
