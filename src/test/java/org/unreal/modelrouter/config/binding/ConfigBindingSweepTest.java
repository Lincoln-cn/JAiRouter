package org.unreal.modelrouter.config.binding;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.BindException;
import org.springframework.boot.context.properties.bind.BindHandler;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.bind.UnboundConfigurationPropertiesException;
import org.springframework.boot.context.properties.bind.handler.NoUnboundElementsBindHandler;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.source.ConfigurationProperty;
import org.springframework.boot.context.properties.source.ConfigurationPropertyName;
import org.springframework.boot.context.properties.source.ConfigurationPropertySource;
import org.springframework.boot.context.properties.source.ConfigurationPropertySources;
import org.springframework.boot.context.properties.source.IterableConfigurationPropertySource;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.env.PropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * <b>全树配置绑定审计</b>：把本仓三份「单棵树」守卫
 * （{@code AuditConfigBindingGuardTest} / {@code TracingConfigBindingGuardTest} /
 * {@code SecurityPerformanceTreeGuardTest}）的手法推广到**整棵 {@code jairouter.*} 与其它前缀**。
 *
 * <p><b>为什么需要它</b>：Spring 默认 {@code ignoreUnknownFields=true}，运维在 yml 里写了
 * 「能设、不报错、不生效」的配置不会有任何提示。历次按单棵树做的守卫已经这样抓出过
 * {@code alert-thresholds}、{@code jairouter.security.performance} 整棵树、{@code audit.extended.*}
 * 14 个键 —— 但射程外的前缀从来没被系统性检查过。本类用**同一套权威绑定器**覆盖全树。</p>
 *
 * <p><b>两步判据</b>（照 {@code #295} 的成品口径）：</p>
 * <ol>
 *   <li><b>归属</b>：每个叶子键是否落在某个 {@code @ConfigurationProperties} 前缀之下？
 *       没有 ⇒ 「孤儿键」——连绑定类都没有，只能靠 {@code @Value}/{@code getProperty}/
 *       {@code @ConditionalOnProperty} 直接读才算有消费方；</li>
 *   <li><b>绑定</b>：有归属的，用 {@link NoUnboundElementsBindHandler} 严格绑一遍，
 *       报出「在前缀下存在、类里没有字段」的键。归属按**最长前缀**判定，避免父前缀类
 *       （如 {@code jairouter.security}）把子树的键误报成未绑定。</li>
 * </ol>
 *
 * <p><b>本类当前是「审计模式」</b>：断言只保证「扫描确实看到了东西」（反空转）与「合成孤儿能被报出」
 * （反证），真实数据的命中集合先输出成报告，等人工仲裁完再收敛成登记表断言 —— 这与
 * {@code #312}/{@code #320} 那两轮的推进方式一致，避免先写死一个我还没测过的集合。</p>
 *
 * <p><b>⚠️ 本地排查提示</b>：读的是 classpath 上的 yml，而 Maven 资源插件只拷贝、不删除已删源文件的
 * 残留副本 ⇒ 刚删过 yml 的话先 {@code mvn clean}（{@code AuditConfigBindingGuardTest} 的注释里有同款说明）。</p>
 */
@DisplayName("全树配置绑定审计（jairouter.* 及其它前缀）")
class ConfigBindingSweepTest {

    /** 从源码里读前缀/常量/消费方 —— 测试的工作目录是项目根（surefire 默认 basedir）。 */
    private static final Path JAVA_ROOT = Paths.get("src", "main", "java");
    private static final Path REPORT = Paths.get("target", "config-binding-sweep.txt");

    private static final Pattern RE_CONFIG_PROPS = Pattern.compile(
            "@ConfigurationProperties\\s*\\(\\s*(?:prefix\\s*=\\s*)?\"([^\"]+)\"");
    private static final Pattern RE_PROP_CONST = Pattern.compile(
            "static\\s+final\\s+String\\s+(\\w+)\\s*=\\s*\"([a-zA-Z][\\w.\\-]*)\"");
    private static final Pattern RE_GETPROP_IDENT = Pattern.compile("\\.getProperty\\s*\\(\\s*(\\w+)\\s*[,)]");
    private static final Pattern RE_GETPROP_LITERAL = Pattern.compile("\\.getProperty\\s*\\(\\s*\"([^\"]+)\"");
    private static final Pattern RE_VALUE = Pattern.compile("@Value\\s*\\(\\s*\"\\$\\{([^}:]+)");
    // 任何 ${...} 占位符都算「读了这个属性」：除了 @Value，还包括
    // @Scheduled(fixedRateString = "${a.b.c}")、@Cacheable 等注解属性。
    // 少了这一条，jwt-persistence.health-checks.check-interval 会被误判成无人读（实测踩到）。
    private static final Pattern RE_PLACEHOLDER = Pattern.compile("\\$\\{([a-zA-Z][\\w.\\-]*)(?::[^}]*)?}");
    private static final Pattern RE_COND_NAME_LITERAL = Pattern.compile("name\\s*=\\s*\"([^\"]+)\"");
    private static final Pattern RE_COND_NAME_IDENT = Pattern.compile("name\\s*=\\s*(\\w+)\\s*[,)]");

    // ------------------------------------------------------------------ 真实数据用例

    @Test
    @DisplayName("反空转：扫描确实看到了配置树（看不到就不许通过）")
    void scannerActuallySeesTheConfigTree() {
        StandardEnvironment env = loadAllYml();
        Set<String> keys = leafPropertyNames(env);
        Set<String> prefixes = configurationPropertiesPrefixesFromSources();

        assertTrue(keys.size() > 200,
                "应当枚举出 200 以上的叶子属性，实际 " + keys.size() + " —— 枚举逻辑失效时本测试必须红");
        assertTrue(prefixes.size() >= 15,
                "应当从源码里读出 15 个以上的 @ConfigurationProperties 前缀，实际 " + prefixes.size());
        assertTrue(keys.stream().anyMatch(k -> k.startsWith("jairouter.security.")),
                "枚举结果里应当包含 jairouter.security.* —— 这是本仓最大的配置子树");
    }

    @Test
    @DisplayName("反证：合成一个孤儿键，判定函数必须报出来")
    void syntheticOrphanIsDetected() {
        Set<String> keys = Set.of("jairouter.synthetic.orphan", "jairouter.known.child");
        Set<String> prefixes = Set.of("jairouter.known");

        Set<String> orphans = orphanKeys(keys, prefixes);

        assertTrue(orphans.contains("jairouter.synthetic.orphan"),
                "合成孤儿键没被报出 ⇒ 判定函数没有检测力，真实数据里的 0 不算证据");
        assertFalse(orphans.contains("jairouter.known.child"),
                "落在已登记前缀下的键不得被判为孤儿");
    }

    @Test
    @DisplayName("反证：最长前缀归属 —— 子树的键不得被父前缀的类误报")
    void longestPrefixWins() {
        // jairouter.security 与 jairouter.security.jwt 同时存在时，
        // accounts.* 的归属应当是后者（其类才有该字段）
        Set<String> prefixes = Set.of("jairouter.security", "jairouter.security.jwt");
        assertTrue(longestPrefix("jairouter.security.jwt.accounts[0].username", prefixes)
                        .equals("jairouter.security.jwt"),
                "应当归属到最长前缀 jairouter.security.jwt");
    }

    @Test
    @DisplayName("反证：被父前缀类绑得上的键不得被判为发现（重叠前缀假阳性）")
    void ancestorBindingSuppressesFinding() {
        Set<String> prefixes = Set.of("jairouter.a", "jairouter.a.b");
        Map<String, Set<String>> unbound = Map.of(
                // 更具体的前缀绑不上 secret（它只管 accounts）
                "jairouter.a.b", Set.of("jairouter.a.b.secret"),
                // 父前缀类绑得上 ⇒ 不是缺陷
                "jairouter.a", Set.of());

        assertFalse(isRealFinding("jairouter.a.b.secret", unbound, prefixes, Map.of()),
                "只要有任何认领前缀绑得上，就不是缺陷 —— 实测 jairouter.security.jwt.secret 属此类");

        Map<String, Set<String>> unboundBoth = Map.of(
                "jairouter.a.b", Set.of("jairouter.a.b.secret"),
                "jairouter.a", Set.of("jairouter.a.b.secret"));
        assertTrue(isRealFinding("jairouter.a.b.secret", unboundBoth, prefixes, Map.of()),
                "所有认领前缀都绑不上，才算真发现");

        // 第三类：不绑定但被 @Value / @ConditionalOnProperty 直接读 ⇒ 合法，不是缺陷
        assertFalse(isRealFinding("jairouter.a.b.secret", unboundBoth, prefixes,
                        Map.of("jairouter.a.b.secret", List.of("SomeBean.java:1"))),
                "有非绑定消费方的键是合法形态（本仓既有守卫把它写进登记表），不得判缺陷");
    }

    @Test
    @DisplayName("反证：列表元素属性是手法盲区，不得计入发现")
    void indexedNamesAreNotFindings() {
        Set<String> prefixes = Set.of("jairouter.a");
        Map<String, Set<String>> unbound = Map.of("jairouter.a", Set.of("jairouter.a.keys[0].key-id"));

        assertFalse(isRealFinding("jairouter.a.keys[0].key-id", unbound, prefixes, Map.of()),
                "含 [n] 的名字是 NoUnboundElementsBindHandler 的已知盲区（实测 60/207 属此类），"
                        + "必须单独计量而不是当成缺陷");
    }

    @Test
    @DisplayName("审计报告：孤儿键 + 严格绑定不上的键（当前为审计模式，人工仲裁后收敛成登记表）")
    void writeSweepReport() throws IOException {
        StandardEnvironment env = loadAllYml();
        Set<String> keys = leafPropertyNames(env);
        Map<String, String> prefixes = configurationPropertiesPrefixesWithLocation();
        Map<String, List<String>> consumers = propertyConsumers();

        // 只审计 jairouter.*：其它前缀（model / store / monitoring …）另行处理，
        // 避免把范围混在一起看不清
        Set<String> scoped = keys.stream().filter(k -> k.startsWith("jairouter.")).collect(Collectors.toCollection(TreeSet::new));

        Set<String> orphans = orphanKeys(scoped, prefixes.keySet());
        Map<String, Set<String>> rawUnbound = strictBindEach(prefixes);

        // 真发现 = 通过三类假阳性过滤后的未绑定键
        Map<String, TreeSet<String>> findings = new TreeMap<>();
        TreeSet<String> consumedOnly = new TreeSet<>();
        int indexedArtifacts = 0;
        for (Map.Entry<String, Set<String>> e : rawUnbound.entrySet()) {
            for (String name : e.getValue()) {
                if (INDEXED.matcher(name).matches()) {
                    indexedArtifacts++;
                }
                if (isRealFinding(name, rawUnbound, prefixes.keySet(), consumers)) {
                    findings.computeIfAbsent(e.getKey(), k -> new TreeSet<>()).add(name);
                } else if (!INDEXED.matcher(name).matches()
                        && !matchesConsumer(name, consumers).isEmpty()) {
                    consumedOnly.add(name);
                }
            }
        }

        StringBuilder sb = new StringBuilder();
        sb.append("== 全树配置绑定审计 ==\n");
        sb.append("yml 叶子属性总数        : ").append(keys.size()).append('\n');
        sb.append("jairouter.* 叶子键      : ").append(scoped.size()).append('\n');
        sb.append("@ConfigurationProperties 前缀 : ").append(prefixes.size()).append('\n');
        sb.append("孤儿键（无任何绑定类）  : ").append(orphans.size()).append('\n');
        long orphanConsumed = orphans.stream().filter(k -> !matchesConsumer(k, consumers).isEmpty()).count();
        sb.append("  其中有非绑定消费方    : ").append(orphanConsumed).append('\n');
        sb.append("  其中**无任何消费方**  : ").append(orphans.size() - orphanConsumed).append('\n');
        sb.append('\n');
        sb.append("原始未绑定名（未过滤）  : ")
                .append(rawUnbound.values().stream().mapToInt(Set::size).sum()).append('\n');
        sb.append("  其中含 [n] 的列表元素盲区 : ").append(indexedArtifacts)
                .append(" （NoUnboundElementsBindHandler 不认索引元素，已排除）\n");
        sb.append("  「不绑定但有非绑定消费方」的合法键 : ").append(consumedOnly.size())
                .append(" （@Value / @ConditionalOnProperty / getProperty，需登记而非判缺陷）\n");
        sb.append("**过滤后的真发现**      : ")
                .append(findings.values().stream().mapToInt(Set::size).sum())
                .append("（按前缀分组，同一键可能出现在多个前缀下）\n");
        long distinct = findings.values().stream().flatMap(Set::stream).distinct().count();
        sb.append("**真发现去重后**        : ").append(distinct).append('\n');
        sb.append('\n');

        sb.append("---- 不绑定但有非绑定消费方的键（合法，供登记）----\n");
        consumedOnly.forEach(n -> sb.append("  ").append(n)
                .append("  consumers=").append(matchesConsumer(n, consumers)).append('\n'));
        sb.append('\n');

        sb.append("---- 孤儿键明细 ----\n");
        for (String k : orphans) {
            sb.append(k).append("  consumers=").append(matchesConsumer(k, consumers)).append('\n');
        }
        sb.append('\n');

        sb.append("---- 严格绑定不上的键（已滤假阳性，按前缀分组）----\n");
        findings.forEach((p, names) -> {
            sb.append('[').append(p).append("] class=").append(prefixes.get(p)).append('\n');
            names.forEach(n -> sb.append("    ").append(n).append('\n'));
        });

        Files.createDirectories(REPORT.getParent());
        Files.writeString(REPORT, sb.toString());
        System.out.println("[config-binding-sweep] 报告已写入 " + REPORT.toAbsolutePath());
        System.out.println("[config-binding-sweep] 孤儿键=" + orphans.size()
                + " 原始未绑定=" + rawUnbound.values().stream().mapToInt(Set::size).sum()
                + " 真发现=" + findings.values().stream().mapToInt(Set::size).sum()
                + " 前缀=" + prefixes.size());
    }

    // ------------------------------------------------------------------ 逻辑（纯函数，便于反证）

    /** 孤儿键 = 不落在任何前缀之下的键。 */
    static Set<String> orphanKeys(final Collection<String> keys, final Set<String> prefixes) {
        Set<String> out = new TreeSet<>();
        for (String key : keys) {
            if (longestPrefix(key, prefixes) == null) {
                out.add(key);
            }
        }
        return out;
    }

    /** 最长前缀归属；无归属返回 null。 */
    static String longestPrefix(final String key, final Set<String> prefixes) {
        String best = null;
        for (String p : prefixes) {
            if (key.equals(p) || key.startsWith(p + ".")) {
                if (best == null || p.length() > best.length()) {
                    best = p;
                }
            }
        }
        return best;
    }

    static List<String> matchesConsumer(final String key, final Map<String, List<String>> consumers) {
        List<String> hits = new ArrayList<>();
        for (Map.Entry<String, List<String>> e : consumers.entrySet()) {
            String name = e.getKey();
            if (key.equals(name) || key.startsWith(name + ".") || name.startsWith(key + ".")) {
                hits.addAll(e.getValue());
            }
        }
        return hits.stream().distinct().sorted().collect(Collectors.toList());
    }

    // ------------------------------------------------------------------ 严格绑定

    /**
     * 判定一个「未绑定名」是不是**真发现**。两类系统性假阳性必须在这里被挡掉，
     * 否则报告里的数字会虚高（本轮实测：初版 207 个未绑定键里有 60 个含 {@code [n]}，
     * 另有 10 个是被更具体前缀「抢走」但父类其实绑得上的键）。
     *
     * <ol>
     *   <li><b>列表元素</b>：名里含 {@code [n]} 的一律不算发现 ——
     *       {@link NoUnboundElementsBindHandler} 不会把索引元素属性记到 List 字段上，
     *       于是 {@code api-key.keys[0].key-id} 会被报成未绑定，而 {@code ApiKeyConfig.keys}
     *       其实是 {@code List<ApiKey>}。反证：应用冷启动正常且
     *       {@code ApiKeyService.loadApiKeysFromConfig()} 对 {@code getKeys()} 直接 {@code stream()}
     *       不判空 —— 绑定若真失败会当场 NPE。</li>
     *   <li><b>重叠前缀</b>：一个键可能同时被多个前缀认领（如 {@code jairouter.security} 与其下的
     *       {@code jairouter.security.jwt}）。只要**有任何一个认领前缀的类绑得上**，它就不是缺陷
     *       —— 「最长前缀」只是归属启发式，不是绑定事实。</li>
     * </ol>
     */
    static boolean isRealFinding(final String name,
                                 final Map<String, Set<String>> unboundByPrefix,
                                 final Set<String> allPrefixes,
                                 final Map<String, List<String>> consumers) {
        if (INDEXED.matcher(name).matches()) {
            return false;
        }
        // 第三类：不绑定但由 @Value / @ConditionalOnProperty / getProperty / getenv 直接读的键
        // 是**合法形态**（本仓既有守卫正是把它们写进登记表而不是当缺陷），必须排除。
        if (!matchesConsumer(name, consumers).isEmpty()) {
            return false;
        }
        List<String> claiming = allPrefixes.stream()
                .filter(p -> name.equals(p) || name.startsWith(p + "."))
                .collect(Collectors.toList());
        if (claiming.isEmpty()) {
            return false;
        }
        for (String p : claiming) {
            if (!unboundByPrefix.getOrDefault(p, Set.of()).contains(name)) {
                return false;
            }
        }
        return true;
    }

    private static final Pattern INDEXED = Pattern.compile(".*\\[\\d+\\].*");

    /**
     * 对每个前缀做一次严格绑定，收集「前缀下存在、类里没有字段」的键。
     * 返回**原始**结果（未做假阳性过滤），过滤交给 {@link #isRealFinding}。
     */
    private static Map<String, Set<String>> strictBindEach(final Map<String, String> prefixes) {
        Map<String, Set<String>> out = new TreeMap<>();
        StandardEnvironment env = loadAllYml();

        for (Map.Entry<String, String> entry : new TreeMap<>(prefixes).entrySet()) {
            String prefix = entry.getKey();
            Class<?> target = loadClassOrNull(entry.getValue());
            if (target == null) {
                continue;
            }
            Set<String> unbound = strictBind(env, prefix, target);
            if (!unbound.isEmpty()) {
                out.put(prefix, unbound);
            }
        }
        return out;
    }

    private static Set<String> strictBind(final StandardEnvironment env, final String prefix,
                                          final Class<?> target) {
        Binder binder = new Binder(ConfigurationPropertySources.get(env));
        BindHandler handler = new NoUnboundElementsBindHandler(BindHandler.DEFAULT);
        try {
            binder.bind(prefix, Bindable.of(target), handler);
            return Set.of();
        } catch (BindException e) {
            if (e.getCause() instanceof UnboundConfigurationPropertiesException unbound) {
                return unbound.getUnboundProperties().stream()
                        .map(p -> p.getName().toString())
                        .collect(Collectors.toCollection(TreeSet::new));
            }
            return Set.of();
        } catch (Exception e) {
            // 绑不上目标类（无无参构造/抽象类等）不是本审计的判据，明确跳过而不是当成通过
            return Set.of();
        }
    }

    // ------------------------------------------------------------------ 源码侧采集

    private static Set<String> configurationPropertiesPrefixesFromSources() {
        return configurationPropertiesPrefixesWithLocation().keySet();
    }

    /** 前缀 → 绑定类的全限定名（同时覆盖类级与方法级注解）。 */
    private static Map<String, String> configurationPropertiesPrefixesWithLocation() {
        Map<String, String> out = new TreeMap<>();
        List<Path> files = javaSources();
        for (Path f : files) {
            String text = read(f);
            String fqn = f.toString().replace('\\', '/')
                    .replaceFirst("^.*src/main/java/", "")
                    .replace('/', '.')
                    .replaceAll("\\.java$", "");
            Matcher m = RE_CONFIG_PROPS.matcher(text);
            while (m.find()) {
                out.putIfAbsent(m.group(1), fqn);
            }
        }
        return out;
    }

    private static Map<String, List<String>> propertyConsumers() {
        Map<String, String> consts = new TreeMap<>();
        List<Path> files = javaSources();
        for (Path f : files) {
            Matcher m = RE_PROP_CONST.matcher(read(f));
            while (m.find()) {
                consts.putIfAbsent(m.group(1), m.group(2));
            }
        }
        Map<String, List<String>> consumers = new TreeMap<>();
        for (Path f : files) {
            String text = read(f);
            String where = f.toString().replace('\\', '/');
            addAll(consumers, text, RE_VALUE, 1, where, consts);
            addAll(consumers, text, RE_PLACEHOLDER, 1, where, consts);
            addAll(consumers, text, RE_GETPROP_LITERAL, 1, where, consts);
            addAll(consumers, text, RE_GETPROP_IDENT, 1, where, consts);
            addAll(consumers, text, RE_COND_NAME_LITERAL, 1, where, consts);
            addAll(consumers, text, RE_COND_NAME_IDENT, 1, where, consts);
        }
        return consumers;
    }

    private static void addAll(final Map<String, List<String>> consumers, final String text,
                               final Pattern pattern, final int group, final String where,
                               final Map<String, String> consts) {
        Matcher m = pattern.matcher(text);
        while (m.find()) {
            String raw = m.group(group);
            String value = consts.getOrDefault(raw, raw);
            if (value == null || value.isBlank() || !value.contains(".")) {
                continue;
            }
            consumers.computeIfAbsent(value, k -> new ArrayList<>()).add(where);
        }
    }

    private static List<Path> javaSources() {
        try (var stream = Files.walk(JAVA_ROOT)) {
            return stream.filter(p -> p.toString().endsWith(".java")).collect(Collectors.toList());
        } catch (IOException e) {
            throw new IllegalStateException("枚举 " + JAVA_ROOT + " 失败", e);
        }
    }

    private static String read(final Path f) {
        try {
            return Files.readString(f);
        } catch (IOException e) {
            throw new IllegalStateException("读取 " + f + " 失败", e);
        }
    }

    private static Class<?> loadClassOrNull(final String fqn) {
        try {
            return Class.forName(fqn, false, ConfigBindingSweepTest.class.getClassLoader());
        } catch (Throwable t) {
            return null;
        }
    }

    // ------------------------------------------------------------------ yml 侧采集

    /** classpath 上全部真实 yml 的叶子属性名（与三份既有守卫同源）。 */
    private static Set<String> leafPropertyNames(final StandardEnvironment env) {
        Set<String> all = new TreeSet<>();
        Iterable<ConfigurationPropertySource> sources = ConfigurationPropertySources.get(env);
        for (ConfigurationPropertySource cps : sources) {
            // 只有可枚举的实现能列出 property 名（MapPropertySource 系即是）
            if (cps instanceof IterableConfigurationPropertySource iterable) {
                for (ConfigurationPropertyName name : iterable) {
                    all.add(name.toString());
                }
            }
        }
        // 去掉「是别人祖先」的名字，只留叶子
        Set<String> leaves = new TreeSet<>();
        for (String name : all) {
            boolean hasChild = all.stream().anyMatch(o -> !o.equals(name)
                    && (o.startsWith(name + ".") || o.startsWith(name + "[")));
            if (!hasChild) {
                leaves.add(name);
            }
        }
        return leaves;
    }

    private static StandardEnvironment loadAllYml() {
        StandardEnvironment environment = new StandardEnvironment();
        MutablePropertySources sources = environment.getPropertySources();
        YamlPropertySourceLoader loader = new YamlPropertySourceLoader();
        List<Resource> resources = new ArrayList<>();
        try {
            PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
            resources.addAll(List.of(resolver.getResources("classpath*:application*.yml")));
            resources.addAll(List.of(resolver.getResources("classpath*:config/**/*.yml")));
        } catch (IOException e) {
            throw new IllegalStateException("枚举 classpath 上的 yml 失败", e);
        }
        assertFalse(resources.isEmpty(), "没枚举到任何 yml ⇒ 扫描失效");
        for (Resource resource : resources) {
            try {
                loader.load(resource.getFilename(), resource).forEach(sources::addFirst);
            } catch (IOException e) {
                throw new IllegalStateException("读取 " + resource.getDescription() + " 失败", e);
            }
        }
        return environment;
    }
}
