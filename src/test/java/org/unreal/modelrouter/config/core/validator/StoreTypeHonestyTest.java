package org.unreal.modelrouter.config.core.validator;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

/**
 * #875：store.type 只支持 JPA，配置面必须说清楚；spring.r2dbc.* 不能再以「有效配置」的样子存在。
 */
@DisplayName("#875 存储类型与 R2DBC 配置的诚实性")
class StoreTypeHonestyTest {

    @Test
    @DisplayName("875-001 配置成未实现的 redis/memory 时校验直接报错，而不是静默按 JPA 运行")
    void unsupportedStoreTypeIsRejected() throws Exception {
        for (final String unsupported : List.of("redis", "memory")) {
            final List<String> errors = new ArrayList<>();
            invokeValidateStorage(unsupported, errors);
            assertTrue(errors.stream().anyMatch(e -> e.contains("store.type")),
                    "store.type=" + unsupported + " 应报错，实际: " + errors);
        }
    }

    @Test
    @DisplayName("875-002 jpa 与 h2（都是 JPA 承载）仍然通过")
    void jpaAndH2StayValid() throws Exception {
        for (final String supported : List.of("jpa", "h2", "JPA")) {
            final List<String> errors = new ArrayList<>();
            invokeValidateStorage(supported, errors);
            assertTrue(errors.isEmpty(), "store.type=" + supported + " 不应报错，实际: " + errors);
        }
    }

    @Test
    @DisplayName("875-003 任何 yml 里都不再声明 spring.r2dbc 前缀（工程没有 R2DBC 驱动）")
    void noYamlDeclaresR2dbc() throws Exception {
        final PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
        final Resource[] yamls = Stream.concat(
                Stream.of(resolver.getResources("classpath*:*.yml")),
                Stream.of(resolver.getResources("classpath*:config/**/*.yml")))
                .toArray(Resource[]::new);

        assertTrue(yamls.length > 10, "应扫描到应用的 yml 配置，实际: " + yamls.length);

        final List<String> offenders = new ArrayList<>();
        for (final Resource resource : yamls) {
            for (final String line : readLines(resource)) {
                final String trimmed = line.stripLeading();
                if (trimmed.startsWith("#")) {
                    continue;
                }
                if (trimmed.startsWith("r2dbc:") || trimmed.matches("^r2dbc\\..*:.*")) {
                    offenders.add(resource.getFilename() + " -> " + trimmed);
                }
            }
        }
        assertTrue(offenders.isEmpty(), "仍有 yml 声明 R2DBC 配置: " + offenders);
    }

    private List<String> readLines(final Resource resource) throws IOException {
        try (var reader = new java.io.BufferedReader(
                new java.io.InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8))) {
            return reader.lines().toList();
        }
    }

    private void invokeValidateStorage(final String storeType, final List<String> errors) throws Exception {
        final ConfigIntegrityValidator validator = new ConfigIntegrityValidator();
        final Field field = ConfigIntegrityValidator.class.getDeclaredField("storeType");
        field.setAccessible(true);
        field.set(validator, storeType);

        final Method method = ConfigIntegrityValidator.class
                .getDeclaredMethod("validateStorageConfig", List.class, List.class);
        method.setAccessible(true);
        method.invoke(validator, errors, new ArrayList<String>());

        assertFalse(errors == null);
    }
}
