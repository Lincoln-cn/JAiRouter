package org.unreal.modelrouter.auth.security.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.util.AntPathMatcher;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * K8s 探针路径必须落在匿名放行清单内（issue #203）。
 *
 * <p>背景：`deploy/k8s/base/deployment.yaml` 的探针打的是 {@code /actuator/health/liveness} 与
 * {@code /actuator/health/readiness}，而安全配置当时只 `permitAll` 了**精确路径**
 * {@code /actuator/health}，兜底规则 {@code /actuator/**} 又要求 {@code ROLE_ADMIN}
 * ⇒ 无凭据探针被拒 ⇒ 真实集群里三个 pod 全部 `Running` 但 `0/1 Ready`、Deployment `0/3`。
 *
 * <p>本测试把「清单里的探针路径」与「代码里的放行清单」直接对上 —— 这正是此前缺失的那一环：
 * 两侧各自看都没错，错在**它们之间的耦合**，而单测/集群外的检查都看不见这个耦合。
 */
@DisplayName("K8s 探针路径必须落在匿名放行清单内（issue #203）")
class AnonymousEndpointPathsTest {

    /** 从清单里抠出探针的 httpGet path。清单结构稳定，用正则足够，避免为一个测试引入 YAML 依赖。 */
    private static final Pattern PROBE_PATH = Pattern.compile("^\\s*path:\\s*(/actuator/\\S+)\\s*$",
            Pattern.MULTILINE);

    @Test
    @DisplayName("放行清单必须覆盖 /actuator/health 及其探测组子路径")
    void listCoversHealthSubPaths() {
        for (String path : List.of("/actuator/health", "/actuator/health/liveness", "/actuator/health/readiness")) {
            assertTrue(isAnonymous(path), "匿名放行清单应覆盖 " + path);
        }
    }

    @Test
    @DisplayName("部署清单里的每个 /actuator 探针路径都必须被放行清单覆盖")
    void manifestProbePathsAreCovered() throws IOException {
        final List<String> probePaths = probePathsOf("deploy/k8s/base/deployment.yaml");

        assertFalse(probePaths.isEmpty(),
                "应从清单解析出至少一个 /actuator 探针路径；解析不到说明本测试已失效，需要修测试本身");

        for (String path : probePaths) {
            assertTrue(isAnonymous(path),
                    "部署清单的探针路径 " + path + " 未被匿名放行清单覆盖 ⇒ 无凭据探针会被 /actuator/** 的"
                            + " ADMIN 规则拒绝，pod 永远不会就绪（issue #203）");
        }
    }

    private static List<String> probePathsOf(final String relativePath) throws IOException {
        final Path file = Path.of(relativePath);
        assertTrue(Files.exists(file), "找不到清单文件（工作目录应为仓库根）：" + file.toAbsolutePath());

        final Matcher matcher = PROBE_PATH.matcher(Files.readString(file));
        return matcher.results()
                .map(r -> r.group(1))
                .distinct()
                .collect(Collectors.toList());
    }

    private static boolean isAnonymous(final String path) {
        final AntPathMatcher matcher = new AntPathMatcher();
        return AnonymousEndpointPaths.HEALTH_AND_OPS.stream().anyMatch(pattern -> matcher.match(pattern, path));
    }
}
