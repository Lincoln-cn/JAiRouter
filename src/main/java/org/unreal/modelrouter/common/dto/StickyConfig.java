package org.unreal.modelrouter.common.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 会话粘性配置 DTO
 *
 * <p>对应对外配置面里的 {@code sticky} 块（issue #1095）：
 *
 * <pre>
 * PUT /api/config/type/services/chat
 * {"sticky": {"enabled": false}}
 * </pre>
 *
 * <p>{@code enabled} 为 {@code null} 表示「不修改」。服务器侧默认值是
 * {@code true}（多实例时启用），因此关闭粘性必须显式提交 {@code false}。
 *
 * @since v3.8.3
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class StickyConfig {

    /**
     * 是否启用会话粘性路由；null 表示不修改
     */
    private Boolean enabled;

    /**
     * 粘性粒度：{@code tenant_model}（默认）或 {@code tenant}
     */
    private String affinityKeyScope;
}
