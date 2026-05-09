/*
 * Copyright (C) 2025 Daniel Lee <daniellee@unreal.ai>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <http://www.apache.org/licenses/LICENSE-2.0>
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.unreal.modelrouter.config.core.manager;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 配置比较与合并服务
 * 
 * 提供智能配置比较和配置合并功能，用于判断配置是否发生实质性变化，
 * 以及支持配置的递归合并操作。
 * 
 * 核心功能：
 * - 智能配置比较：排除元数据字段和动态字段，只比较业务逻辑相关的配置
 * - 配置标准化：统一配置格式，移除时间戳、状态等动态字段
 * - 深度比较：支持Map、List、基本类型的递归比较
 * - 配置合并：支持服务配置和实例配置的递归合并
 * 
 * @since v2.23.0
 * @author Daniel Lee
 */
@Service
public class ConfigComparisonService {

    private static final Logger logger = LoggerFactory.getLogger(ConfigComparisonService.class);

    /**
     * 智能配置比较，检查配置是否真正发生变化
     * 排除元数据字段和自动生成的字段，只比较业务逻辑相关的配置
     *
     * @param currentConfig 当前配置
     * @param newConfig     新配置
     * @return true如果配置发生了实质性变化，false如果配置相同
     */
    public boolean isConfigurationChanged(final Map<String, Object> currentConfig, final Map<String, Object> newConfig) {
        if (currentConfig == null && newConfig == null) {
            return false;
        }
        if (currentConfig == null || newConfig == null) {
            return true;
        }

        // 标准化配置，移除比较时不相关的字段
        Map<String, Object> normalizedCurrent = normalizeConfigForComparison(currentConfig);
        Map<String, Object> normalizedNew = normalizeConfigForComparison(newConfig);

        // 深度比较配置内容
        return !deepEquals(normalizedCurrent, normalizedNew);
    }

    /**
     * 标准化配置，移除比较时不相关的字段
     * 包括元数据字段、时间戳、自动生成的ID等
     *
     * @param config 原始配置
     * @return 标准化后的配置
     */
    public Map<String, Object> normalizeConfigForComparison(final Map<String, Object> config) {
        if (config == null) {
            return new HashMap<>();
        }

        Map<String, Object> normalized = new HashMap<>(config);

        // 移除元数据字段和所有时间戳相关字段
        normalized.remove("_metadata");
        normalized.remove("timestamp");
        normalized.remove("lastModified");
        normalized.remove("createdAt");
        normalized.remove("version");
        normalized.remove("versionInfo");
        normalized.remove("lastUpdated");
        normalized.remove("modifiedAt");
        normalized.remove("updatedAt");
        normalized.remove("saveTime");
        normalized.remove("createTime");

        // 递归移除嵌套对象中的时间戳字段
        removeTimestampFieldsRecursively(normalized);

        // 标准化服务实例配置
        if (normalized.containsKey("services")) {
            @SuppressWarnings("unchecked")
            Map<String, Object> services = (Map<String, Object>) normalized.get("services");
            if (services != null) {
                Map<String, Object> normalizedServices = new HashMap<>();
                for (Map.Entry<String, Object> serviceEntry : services.entrySet()) {
                    String serviceType = serviceEntry.getKey();
                    @SuppressWarnings("unchecked")
                    Map<String, Object> serviceConfig = (Map<String, Object>) serviceEntry.getValue();
                    if (serviceConfig != null) {
                        normalizedServices.put(serviceType, normalizeServiceConfigForComparison(serviceConfig));
                    }
                }
                normalized.put("services", normalizedServices);
            }
        }

        return normalized;
    }

    /**
     * 递归移除配置中的时间戳字段
     */
    @SuppressWarnings("unchecked")
    public void removeTimestampFieldsRecursively(final Map<String, Object> config) {
        if (config == null) {
            return;
        }

        // 定义所有可能的时间戳字段名
        Set<String> timestampFields = Set.of(
                "timestamp", "lastModified", "createdAt", "lastUpdated",
                "modifiedAt", "updatedAt", "saveTime", "createTime",
                "lastHealthCheck", "lastError", "lastSeen"
        );

        // 移除当前层级的时间戳字段
        timestampFields.forEach(config::remove);

        // 递归处理嵌套的Map和List
        for (Map.Entry<String, Object> entry : config.entrySet()) {
            Object value = entry.getValue();
            if (value instanceof Map) {
                removeTimestampFieldsRecursively((Map<String, Object>) value);
            } else if (value instanceof List<?> list) {
                for (Object item : list) {
                    if (item instanceof Map) {
                        removeTimestampFieldsRecursively((Map<String, Object>) item);
                    }
                }
            }
        }
    }

    /**
     * 标准化服务配置，统一实例ID格式和移除不相关字段
     *
     * @param serviceConfig 服务配置
     * @return 标准化后的服务配置
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> normalizeServiceConfigForComparison(final Map<String, Object> serviceConfig) {
        Map<String, Object> normalized = new HashMap<>(serviceConfig);

        // 标准化实例列表
        if (normalized.containsKey("instances")) {
            List<Map<String, Object>> instances = (List<Map<String, Object>>) normalized.get("instances");
            if (instances != null) {
                List<Map<String, Object>> normalizedInstances = new ArrayList<>();
                for (Map<String, Object> instance : instances) {
                    normalizedInstances.add(normalizeInstanceConfigForComparison(instance));
                }
                // 按照name和baseUrl排序，确保比较时顺序一致
                normalizedInstances.sort((a, b) -> {
                    String nameA = (String) a.get("name");
                    String nameB = (String) b.get("name");
                    String urlA = (String) a.get("baseUrl");
                    String urlB = (String) b.get("baseUrl");

                    int nameCompare = (nameA != null ? nameA : "").compareTo(nameB != null ? nameB : "");
                    if (nameCompare != 0) {
                        return nameCompare;
                    }
                    return (urlA != null ? urlA : "").compareTo(urlB != null ? urlB : "");
                });
                normalized.put("instances", normalizedInstances);
            }
        }

        return normalized;
    }

    /**
     * 标准化实例配置，统一ID格式和移除动态字段
     *
     * @param instanceConfig 实例配置
     * @return 标准化后的实例配置
     */
    public Map<String, Object> normalizeInstanceConfigForComparison(final Map<String, Object> instanceConfig) {
        Map<String, Object> normalized = new HashMap<>(instanceConfig);

        // 移除动态字段和时间戳字段
        normalized.remove("health");
        normalized.remove("lastHealthCheck");
        normalized.remove("healthCheckCount");
        normalized.remove("lastError");
        normalized.remove("timestamp");
        normalized.remove("lastModified");
        normalized.remove("createdAt");
        normalized.remove("lastUpdated");
        normalized.remove("modifiedAt");
        normalized.remove("updatedAt");
        normalized.remove("saveTime");
        normalized.remove("createTime");

        // 移除状态字段 - 状态变化不应该触发新版本创建
        // 状态变化通常是运行时的动态变化，不是配置变化
        normalized.remove("status");

        // 统一实例ID格式
        String name = (String) normalized.get("name");
        String baseUrl = (String) normalized.get("baseUrl");
        if (name != null && baseUrl != null) {
            // 使用标准格式的instanceId
            String standardInstanceId = name + "@" + baseUrl;
            normalized.put("instanceId", standardInstanceId);
        }

        // 确保weight字段有默认值
        if (!normalized.containsKey("weight")) {
            normalized.put("weight", 1);
        }

        return normalized;
    }

    /**
     * 深度比较两个对象是否相等
     * 支持Map、List、基本类型的递归比较
     *
     * @param obj1 对象1
     * @param obj2 对象2
     * @return true如果对象相等，false如果不相等
     */
    @SuppressWarnings("unchecked")
    public boolean deepEquals(final Object obj1, final Object obj2) {
        if (obj1 == obj2) {
            return true;
        }
        if (obj1 == null || obj2 == null) {
            return false;
        }

        // 如果类型不同，直接返回false
        if (!obj1.getClass().equals(obj2.getClass())) {
            return false;
        }

        // Map类型的深度比较
        if (obj1 instanceof Map) {
            Map<String, Object> map1 = (Map<String, Object>) obj1;
            Map<String, Object> map2 = (Map<String, Object>) obj2;

            if (map1.size() != map2.size()) {
                return false;
            }

            for (Map.Entry<String, Object> entry : map1.entrySet()) {
                String key = entry.getKey();
                if (!map2.containsKey(key)) {
                    return false;
                }
                if (!deepEquals(entry.getValue(), map2.get(key))) {
                    return false;
                }
            }
            return true;
        }

        // List类型的深度比较
        if (obj1 instanceof List) {
            List<Object> list1 = (List<Object>) obj1;
            List<Object> list2 = (List<Object>) obj2;

            if (list1.size() != list2.size()) {
                return false;
            }

            for (int i = 0; i < list1.size(); i++) {
                if (!deepEquals(list1.get(i), list2.get(i))) {
                    return false;
                }
            }
            return true;
        }

        // 基本类型比较
        return obj1.equals(obj2);
    }

    /**
     * 合并服务配置
     * 递归合并Map类型字段，instances字段直接替换
     *
     * @param existing 现有配置
     * @param updates  更新配置
     * @return 合并后的配置
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> mergeServiceConfig(final Map<String, Object> existing, final Map<String, Object> updates) {
        Map<String, Object> merged = new HashMap<>(existing);

        for (Map.Entry<String, Object> entry : updates.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();

            if ("instances".equals(key) && value instanceof List) {
                // instances字段不合并，直接替换
                merged.put(key, value);
            } else if (existing.containsKey(key)
                    && existing.get(key) instanceof Map
                    && value instanceof Map) {
                // 递归合并Map类型字段
                Map<String, Object> existingMap = (Map<String, Object>) existing.get(key);
                Map<String, Object> updateMap = (Map<String, Object>) value;
                merged.put(key, mergeServiceConfig(existingMap, updateMap));
            } else {
                merged.put(key, value);
            }
        }

        return merged;
    }

    /**
     * 合并实例配置
     *
     * @param existing 现有实例配置
     * @param updates  更新实例配置
     * @return 合并后的实例配置
     */
    public Map<String, Object> mergeInstanceConfig(final Map<String, Object> existing, final Map<String, Object> updates) {
        Map<String, Object> merged = new HashMap<>(existing);
        merged.putAll(updates);
        return merged;
    }
}