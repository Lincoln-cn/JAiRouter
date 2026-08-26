package org.unreal.modelrouter.router.pool;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.unreal.modelrouter.persistence.store.StoreManager;
import org.unreal.modelrouter.router.pool.model.PoolDefinition;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 资源池持久化服务
 * 使用 StoreManager 持久化 Web UI 创建的资源池定义(镜像 RuleDefinitionPersistenceService 模式)
 */
@Service
public class PoolPersistenceService {

    private static final Logger logger = LoggerFactory.getLogger(PoolPersistenceService.class);
    private static final String STORE_KEY = "pool_definitions";

    private final StoreManager storeManager;
    private final ObjectMapper objectMapper;

    public PoolPersistenceService(final StoreManager storeManager,
                                  final ObjectMapper objectMapper) {
        this.storeManager = storeManager;
        this.objectMapper = objectMapper;
    }

    /**
     * 保存全部资源池定义
     */
    public void saveAll(final List<PoolDefinition> pools) {
        try {
            Map<String, Object> configMap = new HashMap<>();
            for (PoolDefinition pool : pools) {
                configMap.put(pool.getPoolName(), pool);
            }
            storeManager.saveConfig(STORE_KEY, configMap);
            logger.debug("保存 {} 条资源池定义", pools.size());
        } catch (Exception e) {
            logger.warn("保存资源池定义失败，降级为仅内存模式: {}", e.getMessage());
        }
    }

    /**
     * 加载全部已持久化的资源池定义
     */
    @SuppressWarnings("unchecked")
    public List<PoolDefinition> loadAll() {
        try {
            Map<String, Object> configMap = storeManager.getConfig(STORE_KEY);
            if (configMap == null || configMap.isEmpty()) {
                return new ArrayList<>();
            }

            List<PoolDefinition> pools = new ArrayList<>();
            for (Map.Entry<String, Object> entry : configMap.entrySet()) {
                try {
                    Object value = entry.getValue();
                    PoolDefinition pool;
                    if (value instanceof PoolDefinition def) {
                        pool = def;
                    } else {
                        // JSON 反序列化
                        String json = objectMapper.writeValueAsString(value);
                        pool = objectMapper.readValue(json, new TypeReference<PoolDefinition>() { });
                    }
                    pools.add(pool);
                } catch (Exception e) {
                    logger.warn("反序列化资源池定义失败: {}, 跳过", entry.getKey());
                }
            }
            return pools;
        } catch (Exception e) {
            logger.warn("加载资源池定义失败，返回空集合: {}", e.getMessage());
            return new ArrayList<>();
        }
    }

    /**
     * 保存单条资源池
     */
    public void save(final PoolDefinition pool) {
        List<PoolDefinition> pools = loadAll();
        pools.removeIf(p -> p.getPoolName().equals(pool.getPoolName()));
        pools.add(pool);
        saveAll(pools);
    }

    /**
     * 删除单条资源池
     */
    public void remove(final String poolName) {
        List<PoolDefinition> pools = loadAll();
        if (pools.removeIf(p -> p.getPoolName().equals(poolName))) {
            saveAll(pools);
            logger.debug("删除资源池定义: {}", poolName);
        }
    }
}
