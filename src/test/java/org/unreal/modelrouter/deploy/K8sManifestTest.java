package org.unreal.modelrouter.deploy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * K8s 制品清单断言（#165）。
 *
 * <p>锁定的都是「部署期造成真实故障、但普通单元测试覆盖不到」的关系：探针路径与容器端口、
 * 停机宽限期与优雅停机相位的匹配、非 root 运行、PDB 存在性、共享态开关是否随制品一起下发。
 * 改动清单若破坏这些关系，会在这里失败，而不是等线上滚动升级时才暴露。</p>
 *
 * <p>渲染层面的正确性（overlay 覆盖、patch 是否生效）由 {@code kubectl kustomize} 验证，
 * 不在此处重复——这里只解析未渲染的纯 YAML 源文件。</p>
 *
 * @author JAiRouter Team
 * @since 3.2.4
 */
class K8sManifestTest {

    private static final Path BASE = Paths.get("deploy", "k8s", "base");

    /** 与 config/common/server.yml 的 spring.lifecycle.timeout-per-shutdown-phase 一致 */
    private static final long SHUTDOWN_PHASE_SECONDS = 30L;

    /** Dockerfile 中的非 root uid */
    private static final long NON_ROOT_UID = 10010L;

    @Test
    @DisplayName("Deployment：探针指向 health 分组路径，且端口与容器监听一致")
    void probesUseHealthGroupsOnContainerPort() throws IOException {
        Map<String, Object> container = firstContainer("deployment.yaml");

        List<Object> ports = asList(container.get("ports"));
        Map<String, Object> httpPort = asMap(ports.get(0));
        assertEquals(8080, httpPort.get("containerPort"), "容器端口必须与探针/Service 一致");
        assertEquals("http", httpPort.get("name"));

        assertEquals("/actuator/health/liveness", probePath(container, "livenessProbe"));
        assertEquals("/actuator/health/readiness", probePath(container, "readinessProbe"));
        // 冷启动可能久于 liveness 的 initialDelay，用 startupProbe 兜住
        assertEquals("/actuator/health/liveness", probePath(container, "startupProbe"));
    }

    @Test
    @DisplayName("Deployment：停机宽限期大于优雅停机相位，否则 kubelet 会先 SIGKILL")
    void terminationGraceExceedsShutdownPhase() throws IOException {
        Map<String, Object> podSpec = podSpec("deployment.yaml");
        Number grace = (Number) podSpec.get("terminationGracePeriodSeconds");
        assertNotNull(grace, "必须显式设置 terminationGracePeriodSeconds");
        assertTrue(grace.longValue() > SHUTDOWN_PHASE_SECONDS,
                "terminationGracePeriodSeconds(" + grace + "s) 必须大于 "
                        + "spring.lifecycle.timeout-per-shutdown-phase(" + SHUTDOWN_PHASE_SECONDS + "s)，"
                        + "否则在途 AI 流式响应会被截断");
    }

    @Test
    @DisplayName("Deployment：以非 root 运行并丢弃全部 capabilities")
    void runsAsNonRootWithoutPrivileges() throws IOException {
        Map<String, Object> podSpec = podSpec("deployment.yaml");
        Map<String, Object> podSecurity = asMap(podSpec.get("securityContext"));
        assertEquals(Boolean.TRUE, podSecurity.get("runAsNonRoot"));
        assertEquals(NON_ROOT_UID, ((Number) podSecurity.get("runAsUser")).longValue());

        Map<String, Object> containerSecurity = asMap(firstContainer("deployment.yaml").get("securityContext"));
        assertEquals(Boolean.FALSE, containerSecurity.get("allowPrivilegeEscalation"));
        assertTrue(asMap(containerSecurity.get("capabilities")).containsKey("drop"),
                "应丢弃全部 capabilities");
    }

    @Test
    @DisplayName("Deployment：注入 POD_NAME，供副本标识与多副本自检使用")
    void injectsPodNameFromDownwardApi() throws IOException {
        List<Object> env = asList(firstContainer("deployment.yaml").get("env"));
        Map<String, Object> podName = null;
        for (Object item : env) {
            Map<String, Object> entry = asMap(item);
            if ("POD_NAME".equals(entry.get("name"))) {
                podName = entry;
            }
        }
        assertNotNull(podName, "必须注入 POD_NAME：InstanceIdentity 用它给本副本产物命名，"
                + "多副本自检也依赖它判定是否处于多副本环境");
        Map<String, Object> valueFrom = asMap(podName.get("valueFrom"));
        Map<String, Object> fieldRef = asMap(valueFrom.get("fieldRef"));
        assertEquals("metadata.name", fieldRef.get("fieldPath"));
    }

    @Test
    @DisplayName("PodDisruptionBudget 存在且设置了 minAvailable")
    void disruptionBudgetProtectsAvailability() throws IOException {
        Map<String, Object> pdb = findDoc("pdb.yaml", "PodDisruptionBudget");
        Map<String, Object> spec = asMap(pdb.get("spec"));
        assertNotNull(spec.get("minAvailable"),
                "节点驱逐/滚动升级时必须保证最小可用副本数");
    }

    @Test
    @DisplayName("迁移 Job 有超时兜底，避免无独立迁移入口时挂住部署流程")
    void migrationJobHasDeadlineAndCleanup() throws IOException {
        Map<String, Object> job = findDoc("migration-job.yaml", "Job");
        Map<String, Object> spec = asMap(job.get("spec"));
        assertNotNull(spec.get("activeDeadlineSeconds"),
                "当前无独立 migrate-only 入口，必须用 activeDeadlineSeconds 兜底");
        assertNotNull(spec.get("ttlSecondsAfterFinished"),
                "已完成的 Job 应自动清理");
    }

    @Test
    @DisplayName("ConfigMap 随制品下发多副本必开的共享态开关")
    void configMapEnablesSharedStateSwitches() throws IOException {
        Map<String, Object> configMap = findDoc("configmap.yaml", "ConfigMap", "jairouter-config-files");
        String applicationYml = String.valueOf(asMap(configMap.get("data")).get("application.yml"));

        // 与 MultiReplicaSharedStateGate.SHARED_STATE_SWITCHES 对齐
        String[] requiredSwitches = {
            "jairouter.security.jwt.blacklist.redis.enabled",
            "jairouter.security.jwt.persistence.redis.enabled",
            "jairouter.security.cache.redis.enabled",
            "jairouter.quota.distributed.enabled",
            "jairouter.persistence.redis.enabled",
        };
        for (String key : requiredSwitches) {
            String leaf = key.substring(key.lastIndexOf('.') + 1);
            assertTrue(applicationYml.contains(leaf),
                    "多副本必开开关应随制品下发: " + key);
        }
    }

    // ---------- 辅助 ----------

    private static Map<String, Object> podSpec(final String fileName) throws IOException {
        Map<String, Object> deployment = findDoc(fileName, "Deployment");
        Map<String, Object> spec = asMap(deployment.get("spec"));
        Map<String, Object> template = asMap(spec.get("template"));
        return asMap(template.get("spec"));
    }

    private static Map<String, Object> firstContainer(final String fileName) throws IOException {
        List<Object> containers = asList(podSpec(fileName).get("containers"));
        return asMap(containers.get(0));
    }

    private static String probePath(final Map<String, Object> container, final String probeName) {
        Map<String, Object> probe = asMap(container.get(probeName));
        assertNotNull(probe, probeName + " 未配置");
        return String.valueOf(asMap(probe.get("httpGet")).get("path"));
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asMap(final Object value) {
        assertNotNull(value, "期望一个映射，实际为 null");
        assertTrue(value instanceof Map, "期望一个映射，实际为 " + value.getClass());
        return (Map<String, Object>) value;
    }

    @SuppressWarnings("unchecked")
    private static List<Object> asList(final Object value) {
        assertNotNull(value, "期望一个列表，实际为 null");
        assertTrue(value instanceof List, "期望一个列表，实际为 " + value.getClass());
        return (List<Object>) value;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> findDoc(final String fileName,
                                               final String kind,
                                               final String... name) throws IOException {
        for (Map<String, Object> doc : loadAll(fileName)) {
            if (!kind.equals(doc.get("kind"))) {
                continue;
            }
            if (name.length > 0) {
                Map<String, Object> metadata = asMap(doc.get("metadata"));
                if (!name[0].equals(metadata.get("name"))) {
                    continue;
                }
            }
            return doc;
        }
        fail("未找到 kind=" + kind + " 的清单，文件: " + fileName);
        return null;
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> loadAll(final String fileName) throws IOException {
        Path file = BASE.resolve(fileName);
        assertTrue(Files.exists(file), "清单文件不存在: " + file.toAbsolutePath());
        List<Map<String, Object>> docs = new ArrayList<>();
        try (InputStream in = Files.newInputStream(file)) {
            for (Object doc : new Yaml().loadAll(in)) {
                if (doc instanceof Map) {
                    docs.add((Map<String, Object>) doc);
                }
            }
        }
        assertTrue(!docs.isEmpty(), "清单文件为空: " + file);
        return docs;
    }
}
