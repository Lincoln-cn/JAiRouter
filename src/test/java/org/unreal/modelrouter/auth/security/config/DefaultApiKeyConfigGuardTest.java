package org.unreal.modelrouter.auth.security.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 内置测试 API Key 不得随基础配置发布（issue #663）。
 *
 * <p><b>背景</b>：{@code src/main/resources/config/auth/api-key.yml} 曾把
 * {@code test-key-001} / {@code sk-test-api-key-12345678} 硬编码在 {@code keys} 下。该文件由
 * {@code application.yml} 的 {@code spring.config.import} <b>无条件</b>导入，而
 * {@code application-prod.yml} 只覆盖 {@code api-key.enabled} 与 {@code default-expiration-days}、
 * <b>不覆盖 {@code keys}</b> ⇒ 任何按 {@code prod} 启动的实例都带着一个公开可查、可直接调用
 * {@code /v1/**} 的密钥。修法 = 基础配置留 {@code keys: []}，内置密钥只出现在 dev profile。</p>
 *
 * <p><b>本测试守什么</b>：</p>
 * <ul>
 *   <li>随包发布的配置（{@code config/**&#47;*.yml} + 除 dev 之外的 {@code application*.yml}）里
 *       不得出现该密钥字面量 —— 重新把它写回基础配置会立刻红；</li>
 *   <li>{@code config/auth/api-key.yml} 的 {@code keys} 必须是空列表（不是"忘了写"，
 *       而是显式留空并带注释），且不得残留 {@code key-value:} 条目；</li>
 *   <li><b>正向对照</b>：同一个扫描器必须在 {@code application-dev.yml} 里<b>找得到</b>该字面量。
 *       少了这条，扫描逻辑一旦失效（路径写错、只读到一个文件），"找不到就等于通过"会静默成立。</li>
 * </ul>
 *
 * <p>为什么按文件扫而不是走 Spring 绑定：本条缺陷的形状就是"字面量出现在随包文件里"，
 * 文件级断言能同时覆盖 {@code prod}/{@code staging}/自定义 profile —— 这些 profile 在
 * 绑定层是同一套基础配置，行为级复测另见 PR 说明（dev 与 default 两种 profile 的实跑）。</p>
 */
@DisplayName("内置测试 API Key 不该随基础配置发布（#663）")
class DefaultApiKeyConfigGuardTest {

    /** Maven 的 surefire 以项目根为工作目录（同 ConfigFileStructureTest 的约定）。 */
    private static final Path RESOURCES_ROOT = Path.of("src/main/resources");
    private static final Path CONFIG_DIR = RESOURCES_ROOT.resolve("config");

    private static final String BASE_API_KEY_CONFIG = "config/auth/api-key.yml";
    private static final String DEV_CONFIG = "application-dev.yml";

    /** 内置测试密钥（issue #663 里被移除的那个）。 */
    private static final String TEST_KEY_VALUE = "sk-test-api-key-12345678";
    private static final String TEST_KEY_ID = "test-key-001";

    @Test
    @DisplayName("随包发布的配置里不得出现内置测试密钥")
    void builtinTestKeyDoesNotLeakIntoShippedConfig() throws IOException {
        List<Path> shipped = shippedConfigFiles();

        // 反空转 + 覆盖性自检：没有这些，路径写错时本测试会「通过」。
        assertTrue(shipped.size() >= 20,
                "扫描到的随包配置文件过少，扫描可能空转: " + shipped.size());
        List<String> names = shipped.stream().map(p -> normalize(RESOURCES_ROOT.relativize(p))).toList();
        assertTrue(names.contains(BASE_API_KEY_CONFIG),
                "扫描面必须包含基础 API Key 配置，当前: " + names);
        assertFalse(names.contains(DEV_CONFIG),
                "dev profile 配置不该算进随包发布面，当前: " + names);

        List<String> offenders = new ArrayList<>();
        for (Path file : shipped) {
            // 只看 YAML 的有效行：注释里提到历史密钥不算泄漏，被注释掉的旧条目也不算「生效」。
            String text = String.join("\n", effectiveLines(file));
            if (text.contains(TEST_KEY_VALUE)) {
                offenders.add(normalize(RESOURCES_ROOT.relativize(file)) + " 含 " + TEST_KEY_VALUE);
            }
            if (text.contains(TEST_KEY_ID)) {
                offenders.add(normalize(RESOURCES_ROOT.relativize(file)) + " 含 " + TEST_KEY_ID);
            }
        }

        assertTrue(offenders.isEmpty(),
                "内置测试凭据不得出现在随包发布的配置里（应只在 application-dev.yml）: " + offenders);
    }

    @Test
    @DisplayName("config/auth/api-key.yml 的 keys 必须是空列表，且无残留 key-value 条目")
    void baseApiKeyListIsEmpty() throws IOException {
        Path base = RESOURCES_ROOT.resolve(BASE_API_KEY_CONFIG);
        assertTrue(Files.isRegularFile(base), "找不到基础 API Key 配置: " + base.toAbsolutePath());

        List<String> effective = effectiveLines(base);
        assertTrue(effective.stream().anyMatch(l -> l.equals("keys: []")),
                "基础配置必须显式写 keys: []（留空即漏配时会退回 List 默认值），当前有效行: " + effective);
        assertFalse(effective.stream().anyMatch(l -> l.startsWith("- key-id:")),
                "基础配置不得残留 key-id 条目，当前有效行: " + effective);
        assertFalse(effective.stream().anyMatch(l -> l.startsWith("key-value:")),
                "基础配置不得残留 key-value，当前有效行: " + effective);
    }

    @Test
    @DisplayName("正向对照：同一扫描器在 application-dev.yml 里必须能找得到内置密钥")
    void devProfileStillProvidesTheKey() throws IOException {
        Path dev = RESOURCES_ROOT.resolve(DEV_CONFIG);
        assertTrue(Files.isRegularFile(dev), "找不到 dev 配置: " + dev.toAbsolutePath());

        String text = Files.readString(dev, StandardCharsets.UTF_8);
        assertTrue(text.contains(TEST_KEY_VALUE),
                "dev profile 仍应提供本地测试密钥（否则扫描器的检测力无从验证）");
        assertTrue(text.contains(TEST_KEY_ID),
                "dev profile 仍应提供本地测试密钥的 key-id");
    }

    /**
     * 随包发布的配置文件 = {@code config/**&#47;*.yml} 全部 + {@code src/main/resources} 下的
     * {@code application*.yml}（排除 dev）。dev 是文档明确标注「本地专用、不得暴露到不可信网络」的模式，
     * 也是唯一允许内置凭据的地方。
     */
    private static List<Path> shippedConfigFiles() throws IOException {
        List<Path> files = new ArrayList<>();
        try (Stream<Path> walked = Files.walk(CONFIG_DIR)) {
            walked.filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().endsWith(".yml"))
                    .sorted()
                    .forEach(files::add);
        }
        try (Stream<Path> listed = Files.list(RESOURCES_ROOT)) {
            listed.filter(Files::isRegularFile)
                    .filter(p -> {
                        String name = p.getFileName().toString();
                        return name.startsWith("application") && name.endsWith(".yml")
                                && !name.equals(DEV_CONFIG);
                    })
                    .sorted()
                    .forEach(files::add);
        }
        return files;
    }

    /** YAML 的有效行（去缩进、丢空行与整行注释）——本文件不需要区分嵌套层级，只要比对面值。 */
    private static List<String> effectiveLines(Path file) throws IOException {
        List<String> result = new ArrayList<>();
        for (String raw : Files.readAllLines(file, StandardCharsets.UTF_8)) {
            String stripped = raw.strip();
            if (stripped.isEmpty() || stripped.startsWith("#")) {
                continue;
            }
            result.add(stripped);
        }
        return result;
    }

    private static String normalize(Path relative) {
        return relative.toString().replace('\\', '/');
    }

    @Test
    @DisplayName("扫描面覆盖 dev 之外的 profile 配置（防止只扫 config 目录的假绿）")
    void shippedScanIncludesProfileConfigs() throws IOException {
        List<String> names = shippedConfigFiles().stream()
                .map(p -> normalize(RESOURCES_ROOT.relativize(p)))
                .toList();
        assertTrue(names.contains("application.yml"), "应扫描 application.yml，当前: " + names);
        assertTrue(names.contains("application-prod.yml"), "应扫描 application-prod.yml，当前: " + names);
        assertFalse(names.contains("application-dev.yml"), "不该把 dev 算进随包面: " + names);
    }
}
