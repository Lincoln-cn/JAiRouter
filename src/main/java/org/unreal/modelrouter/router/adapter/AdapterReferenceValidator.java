package org.unreal.modelrouter.router.adapter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.stereotype.Component;
import org.unreal.modelrouter.router.model.ModelRouterProperties;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * adapter 引用校验器（#638）.
 *
 * <p>{@code model.adapter}、服务级 {@code adapter}、实例级 {@code adapter} 里写了未注册的 adapter 名时，
 * 旧实现要等第一个命中该服务/实例的请求才抛 {@code IllegalArgumentException}（客户端看到 500），
 * 启动、健康检查与 {@code /v1/models} 全部正常，排障成本很高。
 *
 * <p>本校验器在 {@link ApplicationReadyEvent} 阶段把整棵 {@code model.services} 的 adapter 引用
 * 与注册表比对一次：
 * <ul>
 *   <li>{@code jairouter.config.validation.enabled=false}（默认 true）时整体跳过；</li>
 *   <li>{@code jairouter.config.validation.strict-mode=true}：存在未注册引用 ⇒ 抛
 *       {@link IllegalStateException}，启动失败；</li>
 *   <li>否则只记 {@code ERROR} 日志并继续启动（与 {@code SensitiveConfigValidator} 的取向一致，
 *       避免升级瞬间因为一个拼写错误把存量部署拦在门口）。</li>
 * </ul>
 *
 * @author JAiRouter Team
 * @since v3.5.3
 */
@Component
public class AdapterReferenceValidator implements ApplicationListener<ApplicationReadyEvent> {

    private static final Logger logger = LoggerFactory.getLogger(AdapterReferenceValidator.class);

    private final ModelRouterProperties properties;
    private final AdapterRegistry adapterRegistry;
    private final boolean validationEnabled;
    private final boolean strictMode;

    public AdapterReferenceValidator(
            final ModelRouterProperties properties,
            final AdapterRegistry adapterRegistry,
            @Value("${jairouter.config.validation.enabled:true}") final boolean validationEnabled,
            @Value("${jairouter.config.validation.strict-mode:false}") final boolean strictMode) {
        this.properties = properties;
        this.adapterRegistry = adapterRegistry;
        this.validationEnabled = validationEnabled;
        this.strictMode = strictMode;
    }

    /**
     * 一条「引用了未注册 adapter」的记录.
     *
     * @param scope       引用所在层级：{@code global} / {@code service} / {@code instance}
     * @param serviceType 服务类型（global 引用为 {@code null}）
     * @param instanceName 实例名（仅 instance 层级有值）
     * @param adapterName 配置里写的 adapter 名
     */
    public record UnknownAdapterReference(
            String scope,
            String serviceType,
            String instanceName,
            String adapterName) {

        /**
         * 面向日志/异常的一条可读描述。
         *
         * @return 形如 {@code scope=instance service=chat instance=qwen3:4b adapter=openai-compat}
         */
        public String describe() {
            final StringBuilder builder = new StringBuilder("scope=").append(scope);
            if (serviceType != null) {
                builder.append(" service=").append(serviceType);
            }
            if (instanceName != null) {
                builder.append(" instance=").append(instanceName);
            }
            return builder.append(" adapter=").append(adapterName).toString();
        }
    }

    @Override
    public void onApplicationEvent(final ApplicationReadyEvent event) {
        validate();
    }

    /**
     * 执行一次 adapter 引用校验（幂等，可在测试中直接调用）。
     *
     * @throws IllegalStateException strict-mode 打开且存在未注册引用时
     */
    public void validate() {
        if (!validationEnabled) {
            logger.debug("adapter 引用校验已禁用（jairouter.config.validation.enabled=false）");
            return;
        }

        final List<UnknownAdapterReference> unknown = findUnknownReferences();
        if (unknown.isEmpty()) {
            logger.info("✓ adapter 引用校验通过（未发现未注册的 adapter）");
            return;
        }

        final List<String> lines = unknown.stream().map(UnknownAdapterReference::describe).toList();
        if (strictMode) {
            logger.error("adapter 引用校验失败（strict-mode）：{} 处未注册的 adapter", unknown.size());
            lines.forEach(line -> logger.error("  ❌ {}", line));
            throw new IllegalStateException("adapter 引用校验失败，存在 " + unknown.size()
                    + " 处未注册的 adapter: " + String.join("; ", lines));
        }

        logger.error("发现 {} 处引用了未注册的 adapter，首个命中该服务/实例的请求将返回 500；"
                + "可用 GET /api/config/adapter/list 核对已注册清单", unknown.size());
        lines.forEach(line -> logger.error("  ⚠️ {}", line));
    }

    /**
     * 扫描配置，返回所有「引用了未注册 adapter」的记录（按 scope/service/instance 排序，便于比对）。
     *
     * @return 未注册引用清单，空表示全部可解析
     */
    public List<UnknownAdapterReference> findUnknownReferences() {
        final List<UnknownAdapterReference> unknown = new ArrayList<>();

        checkReference("global", null, null, properties.getAdapter(), unknown);

        final var services = properties.getServices();
        if (services == null) {
            return unknown;
        }

        for (final var entry : services.entrySet()) {
            final String serviceType = entry.getKey();
            final ModelRouterProperties.ServiceConfig service = entry.getValue();
            if (service == null) {
                continue;
            }
            checkReference("service", serviceType, null, service.getAdapter(), unknown);
            if (service.getInstances() == null) {
                continue;
            }
            for (final ModelRouterProperties.ModelInstance instance : service.getInstances()) {
                if (instance == null) {
                    continue;
                }
                checkReference("instance", serviceType, instance.getName(), instance.getAdapter(), unknown);
            }
        }

        unknown.sort(Comparator.comparing(UnknownAdapterReference::describe));
        return unknown;
    }

    private void checkReference(final String scope,
                                final String serviceType,
                                final String instanceName,
                                final String adapterName,
                                final List<UnknownAdapterReference> unknown) {
        if (adapterName == null || adapterName.isBlank()) {
            return;
        }
        if (!adapterRegistry.isAdapterSupported(adapterName.trim())) {
            unknown.add(new UnknownAdapterReference(scope, serviceType, instanceName, adapterName.trim()));
        }
    }
}
