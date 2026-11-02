package org.unreal.modelrouter.config.core;

import org.unreal.modelrouter.router.model.ModelRouterProperties;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 启动期实例配置校验（#963）。
 *
 * <p>存在的理由：{@code model.services.<type>.instances} 是一个 List，而 Spring Boot 的
 * relaxed binding 对 {@code instances[0].xxx} 这类属性会新建一个只含该元素的列表 ——
 * 不是「在原列表上改第 0 个元素」。于是用环境变量覆盖实例级属性（例如
 * {@code MODEL_SERVICES_CHAT_INSTANCES_0_RATELIMIT_ENABLED=false}）会把整个实例列表
 * 替换成「只有 rate-limit 的一个空壳实例」，name 与 baseUrl 全为 null。</p>
 *
 * <p>不拦的后果是落库时撞 {@code NULL not allowed for column "base_url"}，
 * 报错指向 JDBC 而不是配置，读的人很难从那里回到「环境变量覆盖」这一步。</p>
 *
 * <p>这里只做「让它失败得可理解」，不尝试让这种覆盖方式生效 —— 那需要改配置结构
 * （例如把 instances 换成按名键控的 Map），属破坏性变更，另议。</p>
 *
 * @since 3.6.1
 */
public final class ModelInstanceConfigValidator {

    private ModelInstanceConfigValidator() {
    }

    /**
     * 收集实例配置里的问题，按「哪个服务的第几个实例、缺了什么」描述。
     *
     * @param properties 已绑定的模型路由配置
     * @return 问题清单，为空表示通过
     */
    public static List<String> collectProblems(final ModelRouterProperties properties) {
        final List<String> problems = new ArrayList<>();
        final Map<String, ModelRouterProperties.ServiceConfig> services = properties.getServices();
        if (services == null || services.isEmpty()) {
            return problems;
        }
        for (Map.Entry<String, ModelRouterProperties.ServiceConfig> entry : services.entrySet()) {
            final String serviceType = entry.getKey();
            final List<ModelRouterProperties.ModelInstance> instances = entry.getValue().getInstances();
            if (instances == null || instances.isEmpty()) {
                continue;
            }
            for (int i = 0; i < instances.size(); i++) {
                final ModelRouterProperties.ModelInstance instance = instances.get(i);
                final String where = "服务 " + serviceType + " 的第 " + (i + 1) + " 个实例";
                if (instance == null) {
                    problems.add(where + "：整个实例条目为空");
                    continue;
                }
                if (isBlank(instance.getName())) {
                    problems.add(where + "：name 为空");
                }
                if (isBlank(instance.getBaseUrl())) {
                    problems.add(where + "：baseUrl 为空");
                }
            }
        }
        return problems;
    }

    /**
     * 与 {@link #collectProblems(ModelRouterProperties)} 相同，但发现问题即抛。
     *
     * @param properties 已绑定的模型路由配置
     * @throws IllegalStateException 存在不完整的实例配置
     */
    public static void assertValid(final ModelRouterProperties properties) {
        final List<String> problems = collectProblems(properties);
        if (problems.isEmpty()) {
            return;
        }
        throw new IllegalStateException(buildMessage(problems));
    }

    private static String buildMessage(final List<String> problems) {
        final StringBuilder message = new StringBuilder("实例配置不完整，启动中止：");
        for (String problem : problems) {
            message.append(System.lineSeparator()).append("  - ").append(problem);
        }
        message.append(System.lineSeparator())
                .append("每个实例都必须有 name 与 baseUrl。若你正用 ")
                .append("MODEL_SERVICES_<TYPE>_INSTANCES_0_<属性> 这类环境变量覆盖实例级配置，")
                .append("注意 Spring Boot 会把整个 instances 列表替换成只含被覆盖字段的一个元素、")
                .append("其余字段留空 —— 请改用配置文件覆盖，或提供完整的实例列表。");
        return message.toString();
    }

    private static boolean isBlank(final String value) {
        return value == null || value.trim().isEmpty();
    }
}
