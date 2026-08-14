package org.unreal.modelrouter.router.adapter.persistence;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.unreal.modelrouter.persistence.store.StoreManager;
import org.unreal.modelrouter.router.adapter.config.AdapterDefinitionProperties;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 适配器定义持久化服务
 * 使用 StoreManager 持久化 Web UI 创建的适配器定义
 */
@Service
public class AdapterDefinitionPersistenceService {

    private static final Logger logger = LoggerFactory.getLogger(AdapterDefinitionPersistenceService.class);
    private static final String STORE_KEY = "adapter_definitions";

    private final StoreManager storeManager;
    private final ObjectMapper objectMapper;

    public AdapterDefinitionPersistenceService(final StoreManager storeManager,
                                                final ObjectMapper objectMapper) {
        this.storeManager = storeManager;
        this.objectMapper = objectMapper;
    }

    /**
     * 保存所有可配置适配器定义
     */
    public void saveAllDefinitions(final Map<String, AdapterDefinitionProperties.AdapterDefinition> definitions) {
        try {
            Map<String, Object> configMap = new HashMap<>();
            for (Map.Entry<String, AdapterDefinitionProperties.AdapterDefinition> entry : definitions.entrySet()) {
                configMap.put(entry.getKey(), entry.getValue());
            }
            storeManager.saveConfig(STORE_KEY, configMap);
            logger.debug("保存 {} 个适配器定义", definitions.size());
        } catch (Exception e) {
            logger.warn("保存适配器定义失败，降级为仅内存模式: {}", e.getMessage());
        }
    }

    /**
     * 加载所有已持久化的适配器定义
     */
    @SuppressWarnings("unchecked")
    public Map<String, AdapterDefinitionProperties.AdapterDefinition> loadAllDefinitions() {
        try {
            Map<String, Object> configMap = storeManager.getConfig(STORE_KEY);
            if (configMap == null || configMap.isEmpty()) {
                return new ConcurrentHashMap<>();
            }

            Map<String, AdapterDefinitionProperties.AdapterDefinition> definitions = new ConcurrentHashMap<>();
            for (Map.Entry<String, Object> entry : configMap.entrySet()) {
                try {
                    Object value = entry.getValue();
                    AdapterDefinitionProperties.AdapterDefinition definition;
                    if (value instanceof AdapterDefinitionProperties.AdapterDefinition def) {
                        definition = def;
                    } else {
                        // JSON 反序列化
                        String json = objectMapper.writeValueAsString(value);
                        definition = objectMapper.readValue(json, AdapterDefinitionProperties.AdapterDefinition.class);
                    }
                    definitions.put(entry.getKey(), definition);
                } catch (Exception e) {
                    logger.warn("反序列化适配器定义失败: {}, 跳过", entry.getKey());
                }
            }
            return definitions;
        } catch (Exception e) {
            logger.warn("加载适配器定义失败，返回空集合: {}", e.getMessage());
            return new ConcurrentHashMap<>();
        }
    }

    /**
     * 保存单个适配器定义
     */
    public void saveDefinition(final String name,
                               final AdapterDefinitionProperties.AdapterDefinition definition) {
        Map<String, AdapterDefinitionProperties.AdapterDefinition> definitions = loadAllDefinitions();
        definitions.put(name, definition);
        saveAllDefinitions(definitions);
    }

    /**
     * 删除单个适配器定义
     */
    public void removeDefinition(final String name) {
        Map<String, AdapterDefinitionProperties.AdapterDefinition> definitions = loadAllDefinitions();
        if (definitions.containsKey(name)) {
            definitions.remove(name);
            saveAllDefinitions(definitions);
            logger.debug("删除适配器定义: {}", name);
        }
    }
}
