package org.unreal.modelrouter.common.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link InstanceIdentity} 测试：本副本标识必须可直接用作文件名与 Redis key。
 *
 * @author JAiRouter Team
 * @since 3.2.3
 */
@DisplayName("本副本标识测试")
class InstanceIdentityTest {

    @Test
    @DisplayName("标识非空且可安全用作文件名/Redis key")
    void id_isSafeForFileNameAndKey() {
        String id = InstanceIdentity.id();

        assertNotNull(id, "标识不应为 null");
        assertTrue(!id.isEmpty(), "标识不应为空");
        assertTrue(id.length() <= 64, "标识不应超长（避免破坏文件名），实际长度 " + id.length());
        assertTrue(id.matches("[A-Za-z0-9._-]+"),
                "标识只应含字母数字与 . _ -，实际：" + id);
    }

    @Test
    @DisplayName("同一进程内标识稳定不变")
    void id_isStable() {
        assertEquals(InstanceIdentity.id(), InstanceIdentity.id());
    }
}
