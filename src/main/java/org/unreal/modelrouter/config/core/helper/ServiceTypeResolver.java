package org.unreal.modelrouter.config.core.helper;

import org.springframework.stereotype.Component;
import org.unreal.modelrouter.router.model.ModelServiceRegistry;

/**
 * 服务类型解析器
 *
 * 负责解析和验证服务类型，支持多种格式（连字符、下划线、驼峰等）。
 *
 * <p>具体的名称判定逻辑集中在 {@link ServiceTypeNames}——本类只做 Spring 注入入口，
 * 不再自带别名表（此前 {@code ServiceTypeResolver}、{@code ConfigurationValidator}、
 * {@code ConfigValidator} 各有一份副本，且 {@code img-edit} / {@code imgEdit} 在三份里
 * 都被解析为 {@code null}，详见 {@link ServiceTypeNames} 的类注释）。</p>
 *
 * @author JAiRouter Team
 * @since v2.13.1
 */
@Component
public class ServiceTypeResolver {

    /**
     * 解析服务类型（支持多种格式）
     *
     * @param serviceKey 服务键
     * @return 服务类型枚举，如果无法识别则返回null
     */
    public ModelServiceRegistry.ServiceType parseServiceType(final String serviceKey) {
        return ServiceTypeNames.resolve(serviceKey);
    }

    /**
     * 获取服务配置键
     *
     * @param serviceType 服务类型
     * @return 服务配置键（连字符格式）
     */
    public String getServiceConfigKey(final ModelServiceRegistry.ServiceType serviceType) {
        if (serviceType == null) {
            return null;
        }

        // 使用连字符格式作为标准键名
        return serviceType.name().replaceAll("([a-z])([A-Z])", "$1-$2").toLowerCase();
    }

    /**
     * 验证服务类型有效性
     *
     * @param serviceType 服务类型字符串
     * @return 是否有效
     */
    public boolean isValidServiceType(final String serviceType) {
        return ServiceTypeNames.isValid(serviceType);
    }
}
