/*
 * Copyright (c) 2025 JAiRouter Authors. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.unreal.modelrouter.persistence.jpa;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Flyway `V3__blacklist_reason_codes.sql` 的语义测试（issue #455）。
 *
 * <p>为什么需要这个测试：`application-test.yml` 里 `spring.flyway.enabled=false`
 * （为了跑内存库），所以**常规测试套件根本不会执行 V1–V3 任何一个脚本** ——
 * 也就是说，一个写错的迁移 SQL 可以让整套测试全绿而在真实启动时炸掉。</p>
 *
 * <p>做法：直接驱动 Flyway API ——
 * 先迁到 **V2**（建表完成）、插入一批**旧的中文原因值**、再迁到最新（应用 V3）、
 * 然后断言：7 个预设值都被改成稳定码，而**自由文本一个字都不许动**。</p>
 */
@DisplayName("Flyway V3：黑名单原因预设值改稳定码")
class BlacklistReasonMigrationTest {

    private static final String URL =
            "jdbc:h2:mem:blacklist_reason_migration;DB_CLOSE_DELAY=-1;MODE=MySQL;DATABASE_TO_UPPER=FALSE";
    private static final String USER = "sa";
    private static final String PASS = "";

    private static Flyway flyway() {
        return Flyway.configure()
                .dataSource(URL, USER, PASS)
                .locations("classpath:db/migration/h2")
                .cleanDisabled(false)
                .load();
    }

    @BeforeEach
    void resetDatabase() {
        Flyway flyway = flyway();
        flyway.clean();
        // 只迁到 V2：V1 建表、V2 做兼容收敛；此时 reason 仍是中文
        Flyway.configure()
                .dataSource(URL, USER, PASS)
                .locations("classpath:db/migration/h2")
                .target(MigrationVersion.fromVersion("2"))
                .load()
                .migrate();
    }

    private static void insertRow(final String targetValue, final String reason) throws Exception {
        try (Connection conn = DriverManager.getConnection(URL, USER, PASS);
             PreparedStatement ps = conn.prepareStatement(
                     "insert into security_blacklist "
                             + "(added_at, blacklist_type, target_value, status, reason) "
                             + "values (current_timestamp, 'TOKEN', ?, 'ACTIVE', ?)")) {
            ps.setString(1, targetValue);
            ps.setString(2, reason);
            ps.executeUpdate();
        }
    }

    private static String reasonOf(final String targetValue) throws Exception {
        try (Connection conn = DriverManager.getConnection(URL, USER, PASS);
             PreparedStatement ps = conn.prepareStatement(
                     "select reason from security_blacklist where target_value = ?")) {
            ps.setString(1, targetValue);
            try (ResultSet rs = ps.executeQuery()) {
                assertTrue(rs.next(), "应能查到 " + targetValue);
                return rs.getString(1);
            }
        }
    }

    @Test
    @DisplayName("7 个中文预设值被改成稳定码")
    void presetReasonsAreConvertedToCodes() throws Exception {
        insertRow("t-malicious", "恶意登录尝试");
        insertRow("t-abnormal", "异常访问行为");
        insertRow("t-compromised", "账户被盗");
        insertRow("t-violation", "违规操作");
        insertRow("t-security", "安全风险");
        insertRow("t-user-request", "用户请求封禁");
        insertRow("t-other", "其他");

        flyway().migrate();

        assertEquals("MALICIOUS_LOGIN", reasonOf("t-malicious"));
        assertEquals("ABNORMAL_ACCESS", reasonOf("t-abnormal"));
        assertEquals("COMPROMISED_ACCOUNT", reasonOf("t-compromised"));
        assertEquals("VIOLATION", reasonOf("t-violation"));
        assertEquals("SECURITY_RISK", reasonOf("t-security"));
        assertEquals("USER_REQUEST", reasonOf("t-user-request"));
        assertEquals("OTHER", reasonOf("t-other"));
    }

    @Test
    @DisplayName("自由文本不受影响（JWT 撤销的中文原因、用户自填、已是码的值）")
    void freeTextIsUntouched() throws Exception {
        insertRow("t-revoke", "手动撤销");
        insertRow("t-custom", "客户投诉-工单#1234");
        insertRow("t-already-code", "MALICIOUS_LOGIN");
        insertRow("t-null", null);

        flyway().migrate();

        assertEquals("手动撤销", reasonOf("t-revoke"),
                "后端自己写的中文原因不能被迁移改掉");
        assertEquals("客户投诉-工单#1234", reasonOf("t-custom"), "用户自填文本必须原样保留");
        assertEquals("MALICIOUS_LOGIN", reasonOf("t-already-code"), "已是码的值应保持");
        assertNull(reasonOf("t-null"), "NULL reason 不应被改成空串或其他值");
    }

    @Test
    @DisplayName("迁移可重复执行（幂等）：再跑一次 migrate 不改任何东西")
    void migrationIsIdempotent() throws Exception {
        insertRow("t-malicious", "恶意登录尝试");
        flyway().migrate();
        assertEquals("MALICIOUS_LOGIN", reasonOf("t-malicious"));

        // 再次启动：Flyway 会跳过已应用的 V3（schema_history 记录），值保持不变
        flyway().migrate();
        assertEquals("MALICIOUS_LOGIN", reasonOf("t-malicious"));
    }

    @Test
    @DisplayName("V3 之后 security_blacklist 表仍可用（列未被改坏）")
    void tableRemainsUsable() throws Exception {
        flyway().migrate();
        try (Connection conn = DriverManager.getConnection(URL, USER, PASS);
             Statement st = conn.createStatement()) {
            try (ResultSet rs = st.executeQuery("select count(*) from security_blacklist")) {
                assertTrue(rs.next());
                assertEquals(0, rs.getInt(1));
            }
        }
    }
}
