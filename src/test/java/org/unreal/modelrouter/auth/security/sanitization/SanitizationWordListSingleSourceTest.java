package org.unreal.modelrouter.auth.security.sanitization;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * 敏感词表的「单一定义源」守卫。
 *
 * <p>背景（P0/P1 审计发现）：{@code jairouter.security.sanitization.response.sensitive-words}
 * 曾在两处 yml 里各写一份 —— {@code config/auth/sanitization.yml} 写了 4 个词，
 * {@code config/security/security-base.yml} 写了 2 个。YAML 的 list **不会合并**，
 * 后导入的一处会把前一处整体顶掉（{@code application.yml} 里 security-base 在 :56 导入、
 * 晚于 :37 的 sanitization.yml）⇒ 运行时只生效 password/account，
 * {@code secret} / {@code token} 不再被脱敏，而两份文件各自的注释都写着「词表是完整的」。
 *
 * <p>既有测试发现不了它：{@code SanitizationCorpusRegressionTest} 只读
 * {@code config/auth/sanitization.yml} 单文件做比对，不看合并后的生效值。
 *
 * <p>因此这里守两件事：① config 树下该键**只能出现一次**；② 那一次的值与语料期望一致。
 * 「只出现一次」是根因守卫 —— 未来任何人在第二个文件里补一份词表都会立刻变红。
 *
 * @author JAiRouter Team
 */
@DisplayName("敏感词表的单一定义源")
class SanitizationWordListSingleSourceTest {

    /** 允许且必须定义该键的那一处（相对 classpath 根）。 */
    private static final String SOLE_DEFINITION = "config/auth/sanitization.yml";

    /** 与 src/test/resources/sanitization/corpus.json 的 config.sensitiveWords 保持一致。 */
    private static final List<String> EXPECTED_WORDS =
            List.of("password", "account", "secret", "token");

    @Test
    @DisplayName("config 树下 sensitive-words 只定义一次，且就是 sanitization.yml 的 4 个词")
    void wordListHasSingleDefinition() throws Exception {
        final Map<String, List<String>> definitions = collectDefinitions();

        assertEquals(Set.of(SOLE_DEFINITION), definitions.keySet(),
                "敏感词表在 config 树下必须只有一处定义：多处定义会因 list 整体覆盖而丢失词条，"
                        + "实际定义点 = " + definitions.keySet());
        assertEquals(EXPECTED_WORDS, definitions.get(SOLE_DEFINITION),
                "敏感词表内容与语料期望不一致（secret/token 若缺失，凭据类字段就不会被打码）");
    }

    @Test
    @DisplayName("反向守卫：扫描器确实能读到 yml（防止路径写错导致空转通过）")
    void scannerActuallyReadsConfigTree() throws Exception {
        final Resource[] files =
                new PathMatchingResourcePatternResolver().getResources("classpath*:config/**/*.yml");
        assertFalse(files.length < 20, "config 树下的 yml 数量异常（实际 " + files.length + "），扫描路径可能写错了");
    }

    private Map<String, List<String>> collectDefinitions() throws Exception {
        final Resource[] files =
                new PathMatchingResourcePatternResolver().getResources("classpath*:config/**/*.yml");
        final Map<String, List<String>> found = new LinkedHashMap<>();
        for (final Resource file : files) {
            final String raw = file.getURL().toString();
            final int idx = raw.indexOf("/config/");
            final String key = idx >= 0 ? raw.substring(idx + 1) : file.getFilename();
            try (InputStream in = file.getInputStream()) {
                collectNode(new Yaml().load(in), key, found);
            }
        }
        return found;
    }

    private void collectNode(final Object node, final String file, final Map<String, List<String>> out) {
        if (node instanceof Map<?, ?> map) {
            for (final Map.Entry<?, ?> entry : map.entrySet()) {
                final Object value = entry.getValue();
                if ("sensitive-words".equals(String.valueOf(entry.getKey()))) {
                    out.put(file, asWordList(value));
                }
                collectNode(value, file, out);
            }
        } else if (node instanceof List<?> list) {
            for (final Object item : list) {
                collectNode(item, file, out);
            }
        }
    }

    private List<String> asWordList(final Object value) {
        final List<String> words = new ArrayList<>();
        if (value instanceof List<?> list) {
            for (final Object item : list) {
                words.add(String.valueOf(item));
            }
        }
        return words;
    }
}
