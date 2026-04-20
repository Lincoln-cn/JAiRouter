package org.unreal.modelrouter.circuitbreaker.repository;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;
import org.unreal.modelrouter.circuitbreaker.CircuitBreaker;
import org.unreal.modelrouter.circuitbreaker.LockFreeCircuitBreaker;
import org.unreal.modelrouter.store.StoreManager;

import java.util.HashMap;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 熔断器状态持久化仓库
 * 
 * v2.0.0 新增功能：
 * - 支持熔断器状态的持久化存储
 * - 支持应用重启后状态恢复
 * - 支持 H2 和 Redis 两种存储方式
 * 
 * @author JAiRouter Team
 * @since 2.0.0
 */
@Repository
public class CircuitBreakerStateRepository {

    private static final Logger logger = LoggerFactory.getLogger(CircuitBreakerStateRepository.class);

    private static final String STORE_KEY_PREFIX = "circuitbreaker.state.";

    private final StoreManager storeManager;
    private final ObjectMapper objectMapper;

    // 本地缓存：instanceId -> stateData
    private final Map<String, Map<String, Object>> stateCache = new ConcurrentHashMap<>();

    @Autowired
    public CircuitBreakerStateRepository(StoreManager storeManager, ObjectMapper objectMapper) {
        this.storeManager = storeManager;
        this.objectMapper = objectMapper;
    }

    /**
     * 保存熔断器状态
     *
     * @param circuitBreaker 熔断器实例
     * @return 是否保存成功
     */
    public boolean save(CircuitBreaker circuitBreaker) {
        if (!(circuitBreaker instanceof LockFreeCircuitBreaker)) {
            logger.warn("只支持 LockFreeCircuitBreaker 的状态持久化");
            return false;
        }

        LockFreeCircuitBreaker lockFreeCircuitBreaker = (LockFreeCircuitBreaker) circuitBreaker;
        Map<String, Object> stateData = lockFreeCircuitBreaker.persistState();
        String instanceId = (String) stateData.get("instanceId");

        try {
            // 更新本地缓存
            stateCache.put(instanceId, stateData);

            // 持久化到 StoreManager
            storeManager.saveConfig(STORE_KEY_PREFIX + instanceId, stateData);

            logger.debug("熔断器状态已保存：instanceId={}, state={}", instanceId, stateData.get("state"));
            return true;
        } catch (Exception e) {
            logger.error("保存熔断器状态失败：{}", e.getMessage());
            return false;
        }
    }

    /**
     * 加载熔断器状态
     *
     * @param instanceId 实例 ID
     * @return 状态数据 Map
     */
    public Map<String, Object> load(String instanceId) {
        // 先从缓存加载
        Map<String, Object> cachedState = stateCache.get(instanceId);
        if (cachedState != null) {
            logger.debug("从缓存加载熔断器状态：instanceId={}", instanceId);
            return cachedState;
        }

        // 从 StoreManager 加载
        Map<String, Object> stateData = storeManager.getConfig(STORE_KEY_PREFIX + instanceId);
        if (stateData == null || stateData.isEmpty()) {
            logger.debug("未找到熔断器状态：instanceId={}", instanceId);
            return null;
        }
            
        // 更新缓存
        stateCache.put(instanceId, stateData);

        logger.debug("从存储加载熔断器状态：instanceId={}, state={}", instanceId, stateData.get("state"));
        return stateData;
    }

    /**
     * 恢复熔断器状态
     *
     * @param circuitBreaker 熔断器实例
     * @return 是否恢复成功
     */
    public boolean restore(CircuitBreaker circuitBreaker) {
        if (!(circuitBreaker instanceof LockFreeCircuitBreaker)) {
            logger.warn("只支持 LockFreeCircuitBreaker 的状态恢复");
            return false;
        }

        LockFreeCircuitBreaker lockFreeCircuitBreaker = (LockFreeCircuitBreaker) circuitBreaker;
        
        // 获取 instanceId（通过 getStateDetail 反射获取）
        try {
            Map<String, Object> stateDetail = lockFreeCircuitBreaker.getStateDetail();
            String instanceId = (String) stateDetail.get("instanceId");
            
            Map<String, Object> stateData = load(instanceId);
            if (stateData != null) {
                lockFreeCircuitBreaker.restoreState(stateData);
                return true;
            }
            return false;
        } catch (Exception e) {
            logger.error("恢复熔断器状态失败：{}", e.getMessage());
            return false;
        }
    }

    /**
     * 删除熔断器状态
     *
     * @param instanceId 实例 ID
     * @return 是否删除成功
     */
    public boolean delete(String instanceId) {
        try {
            // 删除缓存
            stateCache.remove(instanceId);

            // 删除存储
            storeManager.deleteConfig(STORE_KEY_PREFIX + instanceId);

            logger.info("熔断器状态已删除：instanceId={}", instanceId);
            return true;
        } catch (Exception e) {
            logger.error("删除熔断器状态失败：{}", e.getMessage());
            return false;
        }
    }

    /**
     * 清除所有熔断器状态
     */
    public void clearAll() {
        stateCache.clear();
        logger.info("所有熔断器状态缓存已清除");
    }

    /**
     * 保存状态数据为 JSON 字符串
     *
     * @param stateData 状态数据
     * @return JSON 字符串
     */
    private String toJson(Map<String, Object> stateData) {
        try {
            return objectMapper.writeValueAsString(stateData);
        } catch (JsonProcessingException e) {
            logger.error("序列化状态数据失败：{}", e.getMessage());
            return null;
        }
    }

    /**
     * 从 JSON 字符串读取状态数据
     *
     * @param json JSON 字符串
     * @return 状态数据 Map
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> fromJson(String json) {
        try {
            return objectMapper.readValue(json, Map.class);
        } catch (JsonProcessingException e) {
            logger.error("反序列化状态数据失败：{}", e.getMessage());
            return Collections.emptyMap();
        }
    }

    /**
     * 获取缓存的状态数量
     *
     * @return 缓存数量
     */
    public int getCacheSize() {
        return stateCache.size();
    }

    /**
     * 检查是否存在指定实例的状态
     *
     * @param instanceId 实例 ID
     * @return 是否存在
     */
    public boolean exists(String instanceId) {
        return stateCache.containsKey(instanceId) || 
               storeManager.exists(STORE_KEY_PREFIX + instanceId);
    }

    /**
     * 获取所有保存的熔断器状态
     *
     * @return 所有状态数据
     */
    public Map<String, Map<String, Object>> getAllStates() {
        return new HashMap<>(stateCache);
    }
}
