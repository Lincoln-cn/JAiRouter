package org.unreal.modelrouter.version.strategy;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 版本号生成策略工厂
 * 管理所有版本生成策略，提供策略的注册和获取功能
 */
@Component
public class VersionGeneratorFactory {

    private static final Logger logger = LoggerFactory.getLogger(VersionGeneratorFactory.class);

    private final Map<String, VersionGenerator> strategies = new HashMap<>();

    @Autowired
    private List<VersionGenerator> generatorList;

    @PostConstruct
    public void init() {
        // 自动注册所有策略
        for (VersionGenerator generator : generatorList) {
            registerStrategy(generator);
        }
        logger.info("已注册 {} 种版本号生成策略: {}",
                strategies.size(), strategies.keySet());
    }

    /**
     * 注册策略
     *
     * @param generator 策略实现
     */
    public void registerStrategy(VersionGenerator generator) {
        strategies.put(generator.getStrategyName(), generator);
        logger.debug("注册版本号生成策略: {}", generator.getStrategyName());
    }

    /**
     * 获取策略
     *
     * @param strategyName 策略名称
     * @return 策略实现，如果不存在则返回 Optional.empty()
     */
    public Optional<VersionGenerator> getStrategy(String strategyName) {
        return Optional.ofNullable(strategies.get(strategyName));
    }

    /**
     * 获取默认策略（顺序递增）
     *
     * @return 默认策略
     */
    public VersionGenerator getDefaultStrategy() {
        return strategies.getOrDefault(
                SequentialVersionGenerator.class.getSimpleName(),
                new SequentialVersionGenerator()
        );
    }

    /**
     * 获取所有可用策略名称
     *
     * @return 策略名称列表
     */
    public List<String> getAvailableStrategies() {
        return List.copyOf(strategies.keySet());
    }

    /**
     * 检查策略是否存在
     *
     * @param strategyName 策略名称
     * @return true 如果策略存在
     */
    public boolean hasStrategy(String strategyName) {
        return strategies.containsKey(strategyName);
    }
}
