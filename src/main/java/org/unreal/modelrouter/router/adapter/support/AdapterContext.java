package org.unreal.modelrouter.router.adapter.support;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import org.unreal.modelrouter.persistence.repository.ModelCallStatsRepository;
import org.unreal.modelrouter.router.model.ModelServiceRegistry;

/**
 * AdapterContext - BaseAdapter 核心上下文
 * 聚合核心基础设施依赖，减少构造函数参数数量。
 *
 * @since v2.28.0
 */
@Component
public class AdapterContext {

    private final ModelServiceRegistry registry;
    private final ObjectMapper objectMapper;
    private final ModelCallStatsRepository statsRepository;

    public AdapterContext(final ModelServiceRegistry registry,
                          final ObjectMapper objectMapper,
                          final ModelCallStatsRepository statsRepository) {
        this.registry = registry;
        this.objectMapper = objectMapper;
        this.statsRepository = statsRepository;
    }

    public ModelServiceRegistry getRegistry() {
        return registry;
    }

    public ObjectMapper getObjectMapper() {
        return objectMapper;
    }

    public ModelCallStatsRepository getStatsRepository() {
        return statsRepository;
    }
}
