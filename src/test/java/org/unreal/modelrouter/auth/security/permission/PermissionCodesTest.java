package org.unreal.modelrouter.auth.security.permission;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * PermissionCodes 权限码清单守卫（#567 权限语义化）
 *
 * <p>三类防漂移断言：
 * <ul>
 *   <li><b>总数 = 63</b>：权限码总数同时出现在 README 与中英两版 rbac-permissions 文档里，
 *   改这里就必须同步改那三处（本断言是「代码侧唯一真值」的钉子）。</li>
 *   <li><b>常量集合 ≡ {@link PermissionCodes#ALL_PERMISSION_CODES}</b>：新增常量却忘记登记进清单、
 *   或清单里残留已删除的常量，都会立刻红 —— 否则该码永远不会被角色模板/ADMIN 短路覆盖。</li>
 *   <li><b>格式与唯一性</b>：全小写 {@code module:resource:action}，无重复、无空白。</li>
 * </ul>
 *
 * @author JAiRouter Team
 * @since 3.3.1
 */
@DisplayName("PermissionCodes 权限码清单（#567）")
class PermissionCodesTest {

    /** 权威总数：63 = 35 个 :read + 18 个 :write + 10 个 manage/view/use。 */
    private static final int EXPECTED_TOTAL = 63;

    @Test
    @DisplayName("权限码总数为 63")
    void totalCodeCountIsSixtyThree() {
        assertEquals(EXPECTED_TOTAL, PermissionCodes.ALL_PERMISSION_CODES.size(),
                "权限码总数变化必须同步 README 与 docs/{zh,en}/security/rbac-permissions.md 的计数");
        assertEquals(EXPECTED_TOTAL, declaredCodeConstants().size(),
                "常量数与清单数不一致：有常量未登记进 ALL_PERMISSION_CODES，或清单多登记了码");
    }

    @Test
    @DisplayName("清单无重复、无空白，且与声明的常量集合完全一致")
    void allCodesAreRegisteredExactlyOnce() {
        List<String> all = PermissionCodes.ALL_PERMISSION_CODES;

        assertEquals(all.size(), new LinkedHashSet<>(all).size(),
                "ALL_PERMISSION_CODES 存在重复权限码");
        assertTrue(all.stream().noneMatch(code -> code == null || code.trim().isEmpty()),
                "ALL_PERMISSION_CODES 存在空权限码");
        assertEquals(declaredCodeConstants(), new LinkedHashSet<>(all),
                "声明的常量集合必须与 ALL_PERMISSION_CODES 一一对应（新增常量必须登记进清单）");
    }

    @Test
    @DisplayName("权限码格式统一：全小写、冒号分隔的 module:resource:action")
    void codesFollowModuleResourceActionFormat() {
        List<String> malformed = PermissionCodes.ALL_PERMISSION_CODES.stream()
                .filter(code -> !code.equals(code.toLowerCase(Locale.ROOT))
                        || !code.matches("[a-z][a-z0-9]*(?::[a-z][a-z0-9]*)+"))
                .collect(Collectors.toList());
        assertTrue(malformed.isEmpty(), "存在不符合 module:resource:action 约定的权限码: " + malformed);
    }

    @Test
    @DisplayName("#567 新增的 8 个 read 码存在且与写/管理码配对")
    void newReadCodesExistAndPairWithManageCodes() {
        List<String> newReadCodes = List.of(
                PermissionCodes.LB_CONFIG_READ,
                PermissionCodes.TRACING_CONFIG_READ,
                PermissionCodes.SECURITY_APIKEYS_READ,
                PermissionCodes.SECURITY_JWTTOKENS_READ,
                PermissionCodes.SECURITY_BLACKLIST_READ,
                PermissionCodes.SECURITY_SANITIZATION_READ,
                PermissionCodes.SYSTEM_ACCOUNTS_READ,
                PermissionCodes.SYSTEM_PERMISSIONS_READ);

        for (String readCode : newReadCodes) {
            assertTrue(PermissionCodes.ALL_PERMISSION_CODES.contains(readCode),
                    "新 read 码必须在 ALL_PERMISSION_CODES 内: " + readCode);
            String prefix = readCode.substring(0, readCode.length() - ":read".length());
            boolean hasPairedWrite = PermissionCodes.ALL_PERMISSION_CODES.contains(prefix + ":write");
            boolean hasPairedManage = PermissionCodes.ALL_PERMISSION_CODES.contains(prefix + ":manage");
            assertTrue(hasPairedWrite || hasPairedManage,
                    "新 read 码必须与 :write 或 :manage 配对（B11 不变式的前提）: " + readCode);
        }
    }

    /** 反射取 {@link PermissionCodes} 里所有 {@code static final String} 常量的值（排除清单本身）。 */
    private static Set<String> declaredCodeConstants() {
        Set<String> values = new TreeSet<>();
        for (Field field : PermissionCodes.class.getDeclaredFields()) {
            int modifiers = field.getModifiers();
            if (!Modifier.isStatic(modifiers) || !Modifier.isFinal(modifiers)
                    || field.getType() != String.class) {
                continue;
            }
            try {
                field.setAccessible(true);
                Object value = field.get(null);
                if (value instanceof String) {
                    values.add((String) value);
                }
            } catch (IllegalAccessException ex) {
                throw new IllegalStateException("无法读取权限码常量: " + field.getName(), ex);
            }
        }
        return values;
    }
}
