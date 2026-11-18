package org.unreal.modelrouter.common.dto;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 审计事件类型与库级枚举的同步守卫（B4 期间发现，2026-11-18）。
 *
 * <p><b>为什么必须有这条测试</b>：{@code security_audit.event_type} 在 H2 里是 ENUM 列类型、
 * 在 PostgreSQL 里是具名 CHECK 约束，两者都不会跟着 Java 枚举自动扩张。新增一个
 * {@link AuditEventType} 值却忘了写迁移时，写入会报
 * {@code Value not permitted for column ...}，而所有调用方（审计监听器、脱敏过滤器、
 * 暴力破解守卫）都吞掉异常 —— 表现是「审计轨迹静默缺项」，没有任何报错。
 * 这个坑在 V7、V15、V16、V17 各踩过一次（脚本注释里都写着），因此用测试钉住。</p>
 *
 * <p>判据：<b>Java 枚举的每个值都必须出现在最新一份「重设 event_type 枚举」的迁移里</b>。
 * 反方向（库里多出枚举已删除的值）不做断言 —— 历史值可能被保留以兼容旧数据。</p>
 */
@DisplayName("AuditEventType 与迁移脚本的枚举同步")
class AuditEventTypeMigrationGuardTest {

    /** 匹配 `alter table security_audit ... set data type enum ( ... )` 里的单引号取值。 */
    private static final Pattern ENUM_BLOCK = Pattern.compile(
            "alter\\s+table\\s+security_audit[\\s\\S]*?set\\s+data\\s+type\\s+enum\\s*\\(([\\s\\S]*?)\\)\\s*;",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern QUOTED_VALUE = Pattern.compile("'([A-Z0-9_]+)'");

    @Test
    @DisplayName("每个 AuditEventType 值都落在最新迁移的库级枚举里")
    void everyEnumValueIsInLatestMigration() throws IOException {
        final Set<String> migrated = latestMigratedEventTypes();
        final List<String> missing = new ArrayList<>();
        for (final AuditEventType type : AuditEventType.values()) {
            if (!migrated.contains(type.name())) {
                missing.add(type.name());
            }
        }
        assertTrue(missing.isEmpty(),
                "以下事件类型没有出现在迁移脚本的 security_audit.event_type 枚举里：" + missing
                        + "（新增 AuditEventType 时必须同步 db/migration/{h2,postgres} 的迁移，"
                        + "否则写入会静默失败）");
    }

    @Test
    @DisplayName("解析器自身有效：能从迁移里读出足够多的取值")
    void parserActuallyReadsValues() throws IOException {
        // 反向守卫：解析逻辑一旦失效（例如正则匹配不到），上一条会「零缺失」地假通过。
        final Set<String> migrated = latestMigratedEventTypes();
        assertFalse(migrated.isEmpty(), "没解析到任何枚举取值，说明迁移文件路径或正则失效");
        assertTrue(migrated.size() >= 20, "解析到的取值过少：" + migrated.size());
        assertTrue(migrated.contains("SYSTEM_MAINTENANCE"), "缺少一个已知的基础取值");
    }

    /** 读取 h2 迁移目录，按版本号取最后一次 enum 重设的取值集合。 */
    private Set<String> latestMigratedEventTypes() throws IOException {
        final Path dir = resolveMigrationDir();
        final List<Path> scripts = new ArrayList<>();
        try (var stream = Files.list(dir)) {
            stream.filter(p -> p.getFileName().toString().endsWith(".sql")).forEach(scripts::add);
        }
        scripts.sort(Comparator.comparingInt(this::versionNumberOf));

        Set<String> latest = Set.of();
        for (final Path script : scripts) {
            final String sql = Files.readString(script, StandardCharsets.UTF_8);
            final Matcher block = ENUM_BLOCK.matcher(sql);
            while (block.find()) {
                final Set<String> values = new LinkedHashSet<>();
                final Matcher value = QUOTED_VALUE.matcher(block.group(1));
                while (value.find()) {
                    values.add(value.group(1));
                }
                latest = values;
            }
        }
        return latest;
    }

    private Path resolveMigrationDir() {
        final Path fromCwd = Paths.get("src/main/resources/db/migration/h2");
        if (Files.isDirectory(fromCwd)) {
            return fromCwd;
        }
        // 兜底：从 classpath 上的资源位置推（构建产物里迁移脚本随 resources 一起复制）
        try {
            final Path marker = Paths.get(getClass().getResource("/db/migration/h2").toURI());
            if (Files.isDirectory(marker)) {
                return marker;
            }
        } catch (Exception ignored) {
            // 落到下面的失败提示
        }
        throw new IllegalStateException("找不到迁移脚本目录，当前工作目录：" + Paths.get("").toAbsolutePath());
    }

    /** 解析 `V18__xxx.sql` 里的 18。 */
    private int versionNumberOf(final Path script) {
        final String name = script.getFileName().toString();
        final Matcher matcher = Pattern.compile("^V(\\d+)__").matcher(name);
        return matcher.find() ? Integer.parseInt(matcher.group(1)) : -1;
    }
}
