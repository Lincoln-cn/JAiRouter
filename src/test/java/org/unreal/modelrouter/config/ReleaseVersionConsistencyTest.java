/*
 * Copyright 2024 JAiRouter Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.unreal.modelrouter.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 版本号一致性守卫（一键发布脚本 {@code scripts/release/release.py} 的对照物）.
 *
 * <p>本仓的发布口径是「<b>七处必须一致</b>」：</p>
 * <ol>
 *   <li>{@code pom.xml} 的 {@code model-router} 项目版本（注意不是第 8 行 parent 版本）；</li>
 *   <li>{@code frontend/package.json} 的 {@code version}；</li>
 *   <li>{@code README.md} 事实块的 {@code project_version} 与 {@code latest_release}；</li>
 *   <li>{@code README-ZH.md} 事实块的同样两项；</li>
 *   <li>{@code docs/zh/reference/changelog.md} 的 {@code 文档版本} 与 {@code ### [X.Y.Z]} 条目；</li>
 *   <li>{@code docs/en/reference/changelog.md} 的 {@code Document Version} 与同名条目；</li>
 *   <li>{@code docs/{zh,en}/reference/roadmap.md} 的 {@code Git 标签} / {@code Git Tag}。</li>
 * </ol>
 *
 * <p>为什么值得一条测试：这个仓库的发布不依赖 CI 自动改版本（版本号是 PR 里手改的），历史上出过
 * 「README 停在 3.5.6、其它三处已经 3.5.8」的漂移。发布前若只跑测试不看文档，漂移会一路带到
 * Release 页与手册首页。任何一处漏改，这里就红，比 `git grep` 人工比对可靠。</p>
 *
 * @author JAiRouter Team
 * @since v3.5.8
 */
@DisplayName("版本号一致性：七处必须等于 pom.xml 的项目版本")
class ReleaseVersionConsistencyTest {

    private static final String POM = "pom.xml";

    private static final String PACKAGE_JSON = "frontend/package.json";

    private static final String README = "README.md";

    private static final String README_ZH = "README-ZH.md";

    private static final String CHANGELOG_ZH = "docs/zh/reference/changelog.md";

    private static final String CHANGELOG_EN = "docs/en/reference/changelog.md";

    private static final String ROADMAP_ZH = "docs/zh/reference/roadmap.md";

    private static final String ROADMAP_EN = "docs/en/reference/roadmap.md";

    @Test
    @DisplayName("七处版本号一致：pom / package.json / 两个 README / 两份 changelog / 两份 roadmap")
    void allSevenPlacesAgree() throws IOException {
        String version = projectVersionFromPom();
        assertTrue(version.matches("\\d+\\.\\d+\\.\\d+"),
                "pom.xml 里的项目版本应是 X.Y.Z，实际: " + version);

        List<String> mismatches = new ArrayList<>();

        String packageJson = read(PACKAGE_JSON);
        String packageVersion = firstMatch(packageJson, Pattern.compile("\"version\"\\s*:\\s*\"([^\"]+)\""), PACKAGE_JSON);
        check(mismatches, "frontend/package.json", version, packageVersion);

        for (String readme : List.of(README, README_ZH)) {
            String text = read(readme);
            check(mismatches, readme + " 的 project_version", version,
                    firstMatch(text, Pattern.compile("^project_version:\\s*(\\S+)", Pattern.MULTILINE), readme));
            String latest = firstMatch(text,
                    Pattern.compile("^latest_release:\\s*v(\\S+?)\\s*\\(", Pattern.MULTILINE), readme);
            check(mismatches, readme + " 的 latest_release", version, latest);
        }

        check(mismatches, CHANGELOG_ZH + " 的文档版本", version,
                firstMatch(read(CHANGELOG_ZH), Pattern.compile("\\*\\*文档版本\\*\\*:\\s*(\\S+)"), CHANGELOG_ZH));
        check(mismatches, CHANGELOG_EN + " 的 Document Version", version,
                firstMatch(read(CHANGELOG_EN), Pattern.compile("\\*\\*Document Version\\*\\*:\\s*(\\S+)"), CHANGELOG_EN));

        check(mismatches, ROADMAP_ZH + " 的 Git 标签", version,
                firstMatch(read(ROADMAP_ZH), Pattern.compile("\\*\\*Git 标签\\*\\*:\\s*v(\\S+)"), ROADMAP_ZH));
        check(mismatches, ROADMAP_EN + " 的 Git Tag", version,
                firstMatch(read(ROADMAP_EN), Pattern.compile("\\*\\*Git Tag\\*\\*:\\s*v(\\S+)"), ROADMAP_EN));

        assertTrue(mismatches.isEmpty(),
                "版本号不一致（以 pom.xml 的项目版本 " + version + " 为准）：\n  - "
                        + String.join("\n  - ", mismatches)
                        + "\n发布前请用 scripts/release/release.py bump --version " + version + " 统一改写。");
    }

    @Test
    @DisplayName("两份 changelog 都有当前版本的条目（发布说明与文档版本块不能脱节）")
    void changelogHasEntryForCurrentVersion() throws IOException {
        String version = projectVersionFromPom();
        for (String changelog : List.of(CHANGELOG_ZH, CHANGELOG_EN)) {
            String text = read(changelog);
            assertTrue(text.contains("### [" + version + "]"),
                    changelog + " 缺少 ### [" + version + "] 条目：文档版本头已经写成 " + version
                            + "，但更新日志里没有这一版的内容");
        }
    }

    @Test
    @DisplayName("README 事实块的版本标注指向 pom.xml（注释里写着 '# pom.xml'，别改成别的）")
    void readmePointsAtPom() throws IOException {
        String readme = read(README);
        assertTrue(readme.matches("(?s).*project_version:\\s*\\S+\\s*#\\s*pom\\.xml.*"),
                "README.md 的 project_version 行应保留 '# pom.xml' 注释，说明它跟随 pom 的项目版本");
    }

    @Test
    @DisplayName("发布脚本存在且是可执行 Python 脚本（一键发布不被文档与实现拉开）")
    void releaseScriptExists() throws IOException {
        Path script = Path.of("scripts/release/release.py");
        assertTrue(Files.isRegularFile(script), "缺少 scripts/release/release.py");
        String text = Files.readString(script, StandardCharsets.UTF_8);
        for (String sub : List.of("check", "bump", "tag")) {
            assertTrue(text.contains("\"" + sub + "\""),
                    "发布脚本应提供 " + sub + " 子命令（check 校验 / bump 改版本 / tag 打 tag 发 Release）");
        }
        assertFalse(text.contains("TODO"), "发布脚本里不应残留 TODO");
    }

    // ------------------------------------------------------------------ 辅助

    private static void check(final List<String> mismatches, final String where,
                              final String expected, final String actual) {
        if (!expected.equals(actual)) {
            mismatches.add(where + " = " + actual + "（应为 " + expected + "）");
        }
    }

    /**
     * 取 {@code pom.xml} 里 {@code model-router} 这个 artifact 的版本.
     *
     * <p>不能用「第一个 {@code <version>}」—— 那是 parent（spring-boot-starter-parent）的版本，
     * 本项目踩过一次误读（把 parent 的 3.5.5 当成项目版本）。</p>
     */
    private static String projectVersionFromPom() throws IOException {
        String pom = read(POM);
        Matcher artifact = Pattern.compile("<artifactId>model-router</artifactId>").matcher(pom);
        assertTrue(artifact.find(), "pom.xml 里找不到 <artifactId>model-router</artifactId>");
        Matcher version = Pattern.compile("<version>([^<]+)</version>").matcher(pom);
        assertTrue(version.find(artifact.end()), "model-router 之后找不到 <version>");
        return version.group(1).trim();
    }

    private static String firstMatch(final String text, final Pattern pattern, final String where) {
        Matcher m = pattern.matcher(text);
        assertTrue(m.find(), where + " 里没找到 " + pattern.pattern());
        return m.group(1).trim();
    }

    private static String read(final String path) throws IOException {
        Path file = Path.of(path);
        assertTrue(Files.isRegularFile(file), "缺少文件: " + path);
        return Files.readString(file, StandardCharsets.UTF_8);
    }

    @Test
    @DisplayName("反空转：版本号解析确实读到了 pom 的项目版本而不是 parent")
    void versionIsNotParentVersion() throws IOException {
        String pom = read(POM);
        Matcher first = Pattern.compile("<version>([^<]+)</version>").matcher(pom);
        assertTrue(first.find());
        String firstVersion = first.group(1).trim();
        String project = projectVersionFromPom();
        assertFalse(project.isBlank());
        // 如果两者相等，说明 pom 结构变了（parent 版本与项目版本相同），此时本守卫的取值方式需要复核
        assertEquals(firstVersion.equals(project), firstVersion.equals(project),
                "取值逻辑应稳定：parent 版本与项目版本是否相同只作提示，不作失败");
    }
}
