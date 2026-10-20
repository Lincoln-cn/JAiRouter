package org.unreal.modelrouter.architecture;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Guard for the zero-consumer deprecation sweep: a type may carry
 * {@code @Deprecated(forRemoval = true)} only while nothing outside its own source file mentions it.
 *
 * <p>The scan walks the real source trees ({@code src/main/java} and {@code src/test/java}, resolved
 * from the {@code user.dir} system property) instead of the class path, so it sees test sources as
 * well and never depends on build output.
 *
 * <p>Three anti-vacuity checks keep "the scanner found nothing" from being reported as "passed":
 * <ol>
 *   <li>both source roots must exist and yield at least the expected number of files;</li>
 *   <li>at least fourteen types must carry the annotation;</li>
 *   <li>every type of the sweep must be present in the scan result, matched by simple name.</li>
 * </ol>
 *
 * <p>The expected simple names below are assembled from fragments on purpose: a whole-word mention of
 * one of them inside this file would itself count as a reference and the guard would fail on itself.
 */
@DisplayName("guards: @Deprecated(forRemoval = true) implies zero references")
class ZeroRefDeprecationGuardTest {

    private static final String MAIN_ROOT = "src/main/java";
    private static final String TEST_ROOT = "src/test/java";

    private static final int MIN_MAIN_FILES = 700;
    private static final int MIN_TEST_FILES = 350;
    private static final int MIN_DEPRECATED_TYPES = 14;

    private static final int MAX_ANNOTATION_LOOKAHEAD = 20;

    private static final Pattern PACKAGE_DECL = Pattern.compile("^\\s*package\\s+([\\w.]+)\\s*;");

    private static final Pattern TYPE_DECL = Pattern.compile(
            "^\\s*(?:(?:public|protected|private|abstract|final|static|sealed|non-sealed|strictfp)\\s+)*"
                    + "(?:class|interface|record|enum)\\s+(\\w+)");

    private static final List<String> EXPECTED_TYPE_NAMES = List.of(
            "Tracing" + "SpanBuilder",
            "Tracing" + "Operator",
            "SecurityAuditQuery" + "Response",
            "SecurityAuditQuery" + "Request",
            "Retry" + "Support",
            "RequestProcessing" + "Template",
            "Multipart" + "Support",
            "Tracing" + "Support",
            "Service" + "InstanceVO",
            "ReactiveVersioned" + "StoreManager",
            "SecurityConfiguration" + "ChangeEvent",
            "ApiKeyCreation" + "Response",
            "Create" + "ApiKey" + "Request",
            "Update" + "ApiKey" + "Request");

    @Test
    @DisplayName("both source roots are walked and yield the expected number of files")
    void sourceRootsAreScanned() throws IOException {
        final List<Path> mainFiles = javaFiles(MAIN_ROOT);
        final List<Path> testFiles = javaFiles(TEST_ROOT);

        assertTrue(mainFiles.size() >= MIN_MAIN_FILES,
                "expected at least " + MIN_MAIN_FILES + " java files under " + MAIN_ROOT + " but walked "
                        + mainFiles.size() + " (working directory: " + repoRoot() + ")");
        assertTrue(testFiles.size() >= MIN_TEST_FILES,
                "expected at least " + MIN_TEST_FILES + " java files under " + TEST_ROOT + " but walked "
                        + testFiles.size() + " (working directory: " + repoRoot() + ")");
        assertTrue(mainFiles.stream().allMatch(file -> file.toString().endsWith(".java")),
                "the file walker returned a non-java file");
    }

    @Test
    @DisplayName("every @Deprecated(forRemoval = true) type has zero references outside its own file")
    void deprecatedTypesHaveNoExternalReferences() throws IOException {
        final List<Path> mainFiles = javaFiles(MAIN_ROOT);
        final List<Path> testFiles = javaFiles(TEST_ROOT);

        final Map<Path, List<String>> sources = new LinkedHashMap<>();
        for (final Path file : concat(mainFiles, testFiles)) {
            sources.put(file, readLines(file));
        }
        assertEquals(mainFiles.size() + testFiles.size(), sources.size(),
                "duplicate paths while collecting sources");

        final List<DeprecatedType> deprecated = new ArrayList<>();
        for (final Path file : mainFiles) {
            deprecated.addAll(deprecatedTypesIn(file, sources.get(file)));
        }

        assertTrue(deprecated.size() >= MIN_DEPRECATED_TYPES,
                "expected at least " + MIN_DEPRECATED_TYPES + " types annotated with "
                        + "@Deprecated(forRemoval = true) under " + MAIN_ROOT + " but found "
                        + deprecated.size() + ": " + foundNames(deprecated)
                        + " (annotation parsing or the scan root broke; the guard must not pass vacuously)");

        final Set<String> found = foundNames(deprecated);
        for (final String expected : EXPECTED_TYPE_NAMES) {
            assertTrue(found.contains(expected), "the scanner did not see the expected deprecated type ["
                    + expected + "]; it found " + found + " under " + repoRoot());
        }

        final Map<String, List<String>> dangling = new LinkedHashMap<>();
        for (final DeprecatedType type : deprecated) {
            final Pattern mention = Pattern.compile("\\b" + Pattern.quote(type.simpleName()) + "\\b");
            final List<String> hits = new ArrayList<>();
            for (final Map.Entry<Path, List<String>> source : sources.entrySet()) {
                if (source.getKey().equals(type.file())) {
                    continue;
                }
                final List<String> lines = source.getValue();
                for (int index = 0; index < lines.size(); index++) {
                    if (mention.matcher(lines.get(index)).find()) {
                        hits.add(relative(source.getKey()) + ":" + (index + 1));
                    }
                }
            }
            if (!hits.isEmpty()) {
                dangling.put(type.qualifiedName(), hits);
            }
        }

        assertTrue(dangling.isEmpty(), () -> {
            final StringBuilder message = new StringBuilder(256)
                    .append("zero-reference guard violated: types marked @Deprecated(forRemoval = true)")
                    .append(" are still mentioned by other files (main + test).")
                    .append(" Delete the type, or drop the forRemoval flag if it still has consumers.\n");
            dangling.forEach((type, hits) -> message.append("  ").append(type).append(" -> ")
                    .append(hits.size()).append(" hit(s): ").append(hits).append('\n'));
            return message.toString();
        });
    }

    private static List<DeprecatedType> deprecatedTypesIn(final Path file, final List<String> lines) {
        final List<DeprecatedType> found = new ArrayList<>();
        if (lines == null) {
            return found;
        }
        final String pkg = packageOf(lines);
        for (int index = 0; index < lines.size(); index++) {
            final Matcher declaration = TYPE_DECL.matcher(lines.get(index));
            if (!declaration.find() || !carriesRemoveForRemoval(lines, index)) {
                continue;
            }
            found.add(new DeprecatedType(file, pkg, declaration.group(1)));
        }
        return found;
    }

    private static boolean carriesRemoveForRemoval(final List<String> lines, final int declarationIndex) {
        final StringBuilder annotations = new StringBuilder();
        for (int index = declarationIndex - 1;
                index >= 0 && declarationIndex - index <= MAX_ANNOTATION_LOOKAHEAD;
                index--) {
            final String line = lines.get(index).strip();
            if (line.isEmpty() || line.startsWith("*") || line.startsWith("/*") || line.startsWith("//")) {
                break;
            }
            annotations.append(line);
        }
        final String squeezed = annotations.toString().replaceAll("\\s+", "");
        return squeezed.contains("@Deprecated(") && squeezed.contains("forRemoval=true");
    }

    private static String packageOf(final List<String> lines) {
        for (final String line : lines) {
            final Matcher matcher = PACKAGE_DECL.matcher(line);
            if (matcher.find()) {
                return matcher.group(1);
            }
        }
        return "";
    }

    private static Set<String> foundNames(final List<DeprecatedType> types) {
        return types.stream().map(DeprecatedType::simpleName)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private static List<Path> javaFiles(final String relativeRoot) throws IOException {
        final Path root = repoRoot().resolve(relativeRoot).normalize();
        assertTrue(Files.isDirectory(root), "source root is missing: " + root);
        try (Stream<Path> walk = Files.walk(root)) {
            return walk.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(".java"))
                    .map(path -> path.toAbsolutePath().normalize())
                    .sorted()
                    .collect(Collectors.toList());
        }
    }

    private static List<Path> concat(final List<Path> first, final List<Path> second) {
        final List<Path> all = new ArrayList<>(first.size() + second.size());
        all.addAll(first);
        all.addAll(second);
        return all;
    }

    private static List<String> readLines(final Path file) throws IOException {
        final List<String> lines = new ArrayList<>(Files.readAllLines(file, StandardCharsets.UTF_8));
        if (!lines.isEmpty() && !lines.get(0).isEmpty() && lines.get(0).charAt(0) == 0xFEFF) {
            lines.set(0, lines.get(0).substring(1));
        }
        return lines;
    }

    private static String relative(final Path file) {
        try {
            return repoRoot().relativize(file).toString().replace('\\', '/');
        } catch (final IllegalArgumentException exception) {
            return file.toString();
        }
    }

    private static Path repoRoot() {
        final String userDir = System.getProperty("user.dir");
        assertTrue(userDir != null && !userDir.isEmpty(), "system property user.dir is not set");
        return Paths.get(userDir).toAbsolutePath().normalize();
    }

    private static final class DeprecatedType {

        private final Path file;
        private final String qualifiedName;
        private final String simpleName;

        private DeprecatedType(final Path file, final String pkg, final String simpleName) {
            this.file = file;
            this.simpleName = simpleName;
            this.qualifiedName = pkg.isEmpty() ? simpleName : pkg + "." + simpleName;
        }

        private Path file() {
            return this.file;
        }

        private String qualifiedName() {
            return this.qualifiedName;
        }

        private String simpleName() {
            return this.simpleName;
        }
    }
}
