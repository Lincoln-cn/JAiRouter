package org.unreal.modelrouter.monitor.tracing.helper;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 控制台写操作的可读名字映射（#1154）。
 */
@DisplayName("控制台操作名映射（#1154）")
class ConsoleOperationNamesTest {

    @Nested
    @DisplayName("写方法映射")
    class WriteMappings {

        @Test
        @DisplayName("实例更新")
        void instanceUpdate() {
            assertEquals("config.instance.update",
                ConsoleOperationNames.resolve("PUT", "/api/config/instance/chat/10"));
        }

        @Test
        @DisplayName("带 /admin 前缀与直连得到同一个名字")
        void adminPrefixIsStripped() {
            assertEquals("config.instance.update",
                ConsoleOperationNames.resolve("PUT", "/admin/api/config/instance/chat/10"));
        }

        @Test
        @DisplayName("实例删除先于新增匹配")
        void instanceDeleteBeforeCreate() {
            assertEquals("config.instance.delete",
                ConsoleOperationNames.resolve("DELETE", "/api/config/instance/chat/10"));
            assertEquals("config.instance.create",
                ConsoleOperationNames.resolve("POST", "/api/config/instance/chat"));
        }

        @Test
        @DisplayName("密钥相关")
        void apiKeyOperations() {
            assertEquals("apikey.create", ConsoleOperationNames.resolve("POST", "/api/auth/api-keys"));
            assertEquals("apikey.revoke",
                ConsoleOperationNames.resolve("DELETE", "/api/auth/api-keys/sk-1"));
        }

        @Test
        @DisplayName("集群与健康探针配置")
        void clusterAndHealth() {
            assertEquals("cluster.config.update", ConsoleOperationNames.resolve("PUT", "/api/config/cluster"));
            assertEquals("health.config.update", ConsoleOperationNames.resolve("PUT", "/api/config/health"));
        }
    }

    @Nested
    @DisplayName("不映射的情形")
    class NotMapped {

        @Test
        @DisplayName("读方法不映射")
        void readMethodsAreNotMapped() {
            assertNull(ConsoleOperationNames.resolve("GET", "/api/config/instance/chat/10"));
        }

        @Test
        @DisplayName("未登记端点回落为 null")
        void unknownEndpointsFallBackToNull() {
            assertNull(ConsoleOperationNames.resolve("PUT", "/api/some/unknown/endpoint"));
        }

        @Test
        @DisplayName("空值安全")
        void nullSafe() {
            assertNull(ConsoleOperationNames.resolve(null, "/api/config/cluster"));
            assertNull(ConsoleOperationNames.resolve("PUT", null));
        }
    }

    @Nested
    @DisplayName("可读名判定")
    class ReadableCheck {

        @Test
        @DisplayName("点分无空格视为可读名")
        void dottedNamesAreReadable() {
            assertTrue(ConsoleOperationNames.isReadable("config.instance.update"));
            assertTrue(ConsoleOperationNames.isReadable("apikey.create"));
        }

        @Test
        @DisplayName("方法加路径视为原始名")
        void methodPlusPathIsNotReadable() {
            assertFalse(ConsoleOperationNames.isReadable("PUT /api/config/instance/chat/10"));
            assertFalse(ConsoleOperationNames.isReadable("GET /api/config/type"));
        }

        @Test
        @DisplayName("空值不算可读名")
        void emptyIsNotReadable() {
            assertFalse(ConsoleOperationNames.isReadable(null));
            assertFalse(ConsoleOperationNames.isReadable(""));
        }
    }
}
