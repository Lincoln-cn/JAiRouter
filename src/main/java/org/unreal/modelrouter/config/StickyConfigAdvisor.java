package org.unreal.modelrouter.config;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.unreal.modelrouter.router.model.ModelRouterProperties;
import org.unreal.modelrouter.router.model.ModelRouterProperties.ServiceConfig;

/**
 * 会话粘性配置提示（#1126）。
 *
 * <p>粘性（{@code model.services.<type>.sticky.enabled=true}）开启后，同一客户端会被固定到
 * 同一个上游实例 —— 这是为「同一会话落到同一上游、复用前缀缓存」设计的，但它的<b>默认值曾经是开启</b>
 * （issue #1095 之前只能从 YAML 设置，控制台没有入口）。于是存量部署里常常留着一条显式的
 * {@code sticky.enabled=true}：只要该服务配了两个以上实例，<b>任何负载均衡策略都不会真正分流</b>，
 * 表现为「流量只走一个实例」，而控制台、健康检查和路由监控都不会报错。</p>
 *
 * <p>#1095 关掉的是<b>代码里的默认值</b>，管不到已经写进库里的存量配置。本类在启动时就这类配置
 * 输出一条告警，把「当前看起来像负载均衡失效」的原因摆到日志里；日志首行带固定标识
 * {@value #LOG_MARKER}，便于 sla 等外部巡检工具 grep。</p>
 *
 * <p>核心判定做成静态纯函数，便于单元测试，也与 {@code MultiReplicaSharedStateGate} 的写法一致。</p>
 *
 * @since 3.8.6
 */
@Slf4j
@Component
public class StickyConfigAdvisor {

    /** 日志标识：外部巡检工具按这个字符串检索即可命中本提示。 */
    public static final String LOG_MARKER = "STICKY_INEFFECTIVE";

    private final ModelRouterProperties properties;

    /**
     * 构造。
     *
     * @param properties 运行时服务配置
     */
    public StickyConfigAdvisor(final ModelRouterProperties properties) {
        this.properties = properties;
    }

    /**
     * 启动完成后检查一次粘性与实例数的组合。
     *
     * <p>用 {@link ApplicationReadyEvent} 而不是构造期：配置属性绑定与配置中心加载都已完成，
     * 读到的就是真实生效的配置。</p>
     */
    @EventListener(ApplicationReadyEvent.class)
    public void reportIneffectiveStickiness() {
        final List<IneffectiveSticky> findings = findIneffectiveStickiness(properties);
        if (findings.isEmpty()) {
            return;
        }
        log.warn(describe(findings));
    }

    /**
     * 一个「开了粘性但配了多个实例」的服务。
     *
     * @param serviceType   服务类型（{@code chat} / {@code embedding} 等）
     * @param instanceCount 该服务配置的实例数
     */
    public record IneffectiveSticky(String serviceType, int instanceCount) {
    }

    /**
     * 找出开了粘性且实例数大于 1 的服务。
     *
     * <p>只保留 {@code >1} 个实例的服务：单实例下粘性不会造成任何分流损失，不必提示。
     * {@code sticky} 缺失或 {@code enabled} 非 {@code TRUE} 都不算 —— 与
     * {@code ModelServiceRegistry.getStickyScope} 的判定保持一致，避免「提示说开了、实际没开」。</p>
     *
     * @param properties 运行时服务配置，可为 {@code null}
     * @return 命中项，按服务名排序，绝不返回 {@code null}
     */
    public static List<IneffectiveSticky> findIneffectiveStickiness(final ModelRouterProperties properties) {
        if (properties == null || properties.getServices() == null) {
            return List.of();
        }
        final List<IneffectiveSticky> findings = new ArrayList<>();
        for (final Map.Entry<String, ServiceConfig> entry : properties.getServices().entrySet()) {
            final ServiceConfig service = entry.getValue();
            if (service == null || service.getSticky() == null) {
                continue;
            }
            if (!Boolean.TRUE.equals(service.getSticky().getEnabled())) {
                continue;
            }
            final int instanceCount = service.getInstances() == null ? 0 : service.getInstances().size();
            if (instanceCount <= 1) {
                continue;
            }
            findings.add(new IneffectiveSticky(entry.getKey(), instanceCount));
        }
        findings.sort((left, right) -> left.serviceType().compareTo(right.serviceType()));
        return List.copyOf(findings);
    }

    /**
     * 生成可读的告警消息。
     *
     * <p>首行必须带 {@value #LOG_MARKER}：外部工具只按这一行判断「有没有命中」，
     * 后面的解释是给运维看的。</p>
     *
     * @param findings 命中项
     * @return 多行消息
     */
    public static String describe(final List<IneffectiveSticky> findings) {
        final StringBuilder sb = new StringBuilder();
        sb.append(LOG_MARKER).append(" 以下服务开启了会话粘性（sticky.enabled=true）且配置了多个实例，")
                .append("同一客户端会被固定到同一个上游，负载均衡不会真正分流：");
        for (final IneffectiveSticky item : findings) {
            sb.append(System.lineSeparator())
                    .append("  - ").append(item.serviceType())
                    .append(" → ").append(item.instanceCount()).append(" 个实例");
        }
        sb.append(System.lineSeparator())
                .append("粘性是为「同一会话落到同一上游」设计的；当前不需要该语义时请关闭，例如：")
                .append(System.lineSeparator())
                .append("  PUT /api/config/type/services/<serviceType> {\"sticky\":{\"enabled\":false}}")
                .append("（控制台「服务配置」同样可改）")
                .append(System.lineSeparator())
                .append("该提示只在启动时输出一次；巡检时请直接核对 /api/config/type 里 ")
                .append("services.<type>.sticky.enabled 与该服务的 instances 数量。");
        return sb.toString();
    }
}
