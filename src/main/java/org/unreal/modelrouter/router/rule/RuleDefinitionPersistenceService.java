package org.unreal.modelrouter.router.rule;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.unreal.modelrouter.persistence.store.StoreManager;
import org.unreal.modelrouter.router.rule.model.RuleDefinition;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 路由规则持久化服务
 * 使用 StoreManager 持久化 Web UI 创建的规则定义(镜像 AdapterDefinitionPersistenceService 模式)
 */
@Service
public class RuleDefinitionPersistenceService {

    private static final Logger logger = LoggerFactory.getLogger(RuleDefinitionPersistenceService.class);
    private static final String STORE_KEY = "rule_definitions";

    private final StoreManager storeManager;
    private final ObjectMapper objectMapper;

    public RuleDefinitionPersistenceService(final StoreManager storeManager,
                                            final ObjectMapper objectMapper) {
        this.storeManager = storeManager;
        this.objectMapper = objectMapper;
    }

    /**
     * 保存全部规则定义
     */
    public void saveAll(final List<RuleDefinition> rules) {
        try {
            Map<String, Object> configMap = new HashMap<>();
            for (RuleDefinition rule : rules) {
                configMap.put(rule.getId(), rule);
            }
            storeManager.saveConfig(STORE_KEY, configMap);
            logger.debug("保存 {} 条规则定义", rules.size());
        } catch (Exception e) {
            logger.warn("保存规则定义失败，降级为仅内存模式: {}", e.getMessage());
        }
    }

    /**
     * 加载全部已持久化的规则定义
     */
    @SuppressWarnings("unchecked")
    public List<RuleDefinition> loadAll() {
        try {
            Map<String, Object> configMap = storeManager.getConfig(STORE_KEY);
            if (configMap == null || configMap.isEmpty()) {
                return new ArrayList<>();
            }

            List<RuleDefinition> rules = new ArrayList<>();
            for (Map.Entry<String, Object> entry : configMap.entrySet()) {
                try {
                    Object value = entry.getValue();
                    RuleDefinition rule;
                    if (value instanceof RuleDefinition def) {
                        rule = def;
                    } else {
                        // JSON 反序列化
                        String json = objectMapper.writeValueAsString(value);
                        rule = objectMapper.readValue(json, new TypeReference<RuleDefinition>() { });
                    }
                    rules.add(rule);
                } catch (Exception e) {
                    logger.warn("反序列化规则定义失败: {}, 跳过", entry.getKey());
                }
            }
            return rules;
        } catch (Exception e) {
            logger.warn("加载规则定义失败，返回空集合: {}", e.getMessage());
            return new ArrayList<>();
        }
    }

    /**
     * 保存单条规则
     */
    public void save(final RuleDefinition rule) {
        List<RuleDefinition> rules = loadAll();
        rules.removeIf(r -> r.getId().equals(rule.getId()));
        rules.add(rule);
        saveAll(rules);
    }

    /**
     * 删除单条规则
     */
    public void remove(final String id) {
        List<RuleDefinition> rules = loadAll();
        if (rules.removeIf(r -> r.getId().equals(id))) {
            saveAll(rules);
            logger.debug("删除规则定义: {}", id);
        }
    }
}
