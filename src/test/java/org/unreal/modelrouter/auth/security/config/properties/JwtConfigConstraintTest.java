package org.unreal.modelrouter.auth.security.config.properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import java.util.Set;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * issue #821：{@code secret} 上原先的 {@code @Size(min = 32)} 会让 Bean Validation 在上下文启动时
 * 先于 {@code StartupSecurityGate}（{@code CommandLineRunner}）失败，于是
 * {@code JAIRouter_SKIP_AUTH_WARNING=true} 这个逃生舱还没运行就已被绕过。
 *
 * <p>本类钉住两件事：① secret 长度不再由启动期 Bean Validation 拦截（交给门闩与
 * {@code SecurityConfigurationValidator} 判定，后者的报错文案由
 * {@code SecurityConfigurationValidatorTest} 的 VAL-108 守着）；② 同一个 {@code Validator}
 * 仍然能抓到真正配错的字段，避免「约束全被删掉」也能通过。</p>
 */
class JwtConfigConstraintTest {

    private static ValidatorFactory factory;
    private static Validator validator;

    @BeforeAll
    static void setUpValidator() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll
    static void closeValidator() {
        if (factory != null) {
            factory.close();
        }
    }

    @Test
    @DisplayName("#821: 短 secret 不再由 Bean Validation 在启动期拦截")
    void shortSecretDoesNotFailStartupBeanValidation() {
        final JwtConfig config = new JwtConfig();
        config.setEnabled(true);
        config.setSecret("short");

        final Set<ConstraintViolation<JwtConfig>> violations = validator.validate(config);

        assertTrue(violations.isEmpty(),
                () -> "secret 长度不应在启动期由 Bean Validation 拦截（否则逃生舱失效），实际违规: "
                        + violations);
    }

    @Test
    @DisplayName("#821 反证: 同一 Validator 仍能拦下配错的 jwt-header（用例不会空转）")
    void validatorStillRejectsBlankHeader() {
        final JwtConfig config = new JwtConfig();
        config.setJwtHeader(" ");

        final Set<ConstraintViolation<JwtConfig>> violations = validator.validate(config);

        assertFalse(violations.isEmpty(), "空白的 jwt-header 必须被 @NotBlank 拦下，否则本类用例没有检测力");
        assertTrue(violations.stream()
                        .anyMatch(violation -> "jwtHeader".equals(violation.getPropertyPath().toString())),
                () -> "违规字段应指向 jwtHeader，实际: " + violations);
    }

    @Test
    @DisplayName("#805 收尾: Java 默认头名与配置默认值一致，且不含下划线")
    void defaultJwtHeaderHasNoUnderscore() {
        final String defaultHeader = new JwtConfig().getJwtHeader();

        assertEquals("X-Jairouter-Token", defaultHeader,
                "Java 默认值必须与 config/auth/jwt.yml 的默认值一致；否则缺该配置键的部署会退回下划线头名");
        assertFalse(defaultHeader.contains("_"),
                "nginx 默认 underscores_in_headers off 会丢弃含下划线的头，默认值不能带下划线");
    }
}
