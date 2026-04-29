package org.unreal.modelrouter.common.api.persistence;

import java.util.List;
import java.util.Map;

/**
 * 配置存储接口 - persistence 模块对外暴露的配置存储能力。
 *
 * <p>此接口用于解耦 persistence 与 config 业务模块：
 * <ul>
 *   <li>persistence 模块实现此接口，提供配置持久化能力</li>
 *   <li>config 模块通过此接口访问 persistence，不再直接依赖</li>
 * </ul>
 *
 * <p>微服务拆分后，此接口将成为服务间通信协议。
 *
 * @since v2.8.0
 */
public interface ConfigRepositoryApi {

    // === 服务配置存储 ===

    /**
     * 保存服务配置。
     *
     * @param serviceId 服务 ID
     * @param config 配置数据（JSON 格式）
     */
    void saveServiceConfig(String serviceId, Map<String, Object> config);

    /**
     * 加载服务配置。
     *
     * @param serviceId 服务 ID
     * @return 配置数据，不存在时返回 null
     */
    Map<String, Object> loadServiceConfig(String serviceId);

    /**
     * 删除服务配置。
     *
     * @param serviceId 服务 ID
     */
    void deleteServiceConfig(String serviceId);

    /**
     * 获取所有服务配置。
     *
     * @return 服务 ID -> 配置映射
     */
    Map<String, Map<String, Object>> loadAllServiceConfigs();

    // === 实例配置存储 ===

    /**
     * 保存实例配置。
     *
     * @param instanceId 实例 ID
     * @param config 配置数据（JSON 格式）
     */
    void saveInstanceConfig(String instanceId, Map<String, Object> config);

    /**
     * 加载实例配置。
     *
     * @param instanceId 实例 ID
     * @return 配置数据，不存在时返回 null
     */
    Map<String, Object> loadInstanceConfig(String instanceId);

    /**
     * 删除实例配置。
     *
     * @param instanceId 实例 ID
     */
    void deleteInstanceConfig(String instanceId);

    // === 版本历史存储 ===

    /**
     * 保存配置版本。
     *
     * @param serviceId 服务 ID
     * @param version 版本号
     * @param config 配置数据
     * @param description 版本描述
     */
    void saveConfigVersion(String serviceId, int version, Map<String, Object> config, String description);

    /**
     * 加载配置版本历史。
     *
     * @param serviceId 服务 ID
     * @return 版本列表（按时间倒序）
     */
    List<ConfigVersionInfo> loadConfigVersions(String serviceId);

    /**
     * 加载指定版本的配置。
     *
     * @param serviceId 服务 ID
     * @param version 版本号
     * @return 配置数据，不存在时返回 null
     */
    Map<String, Object> loadConfigByVersion(String serviceId, int version);

    // === 配置元数据 ===

    /**
     * 获取配置版本信息。
     */
    class ConfigVersionInfo {
        private int version;
        private String description;
        private long createTime;
        private String createdBy;

        public ConfigVersionInfo() {
        }

        public ConfigVersionInfo(int version, String description, long createTime, String createdBy) {
            this.version = version;
            this.description = description;
            this.createTime = createTime;
            this.createdBy = createdBy;
        }

        public int getVersion() {
            return version;
        }

        public void setVersion(int version) {
            this.version = version;
        }

        public String getDescription() {
            return description;
        }

        public void setDescription(String description) {
            this.description = description;
        }

        public long getCreateTime() {
            return createTime;
        }

        public void setCreateTime(long createTime) {
            this.createTime = createTime;
        }

        public String getCreatedBy() {
            return createdBy;
        }

        public void setCreatedBy(String createdBy) {
            this.createdBy = createdBy;
        }
    }
}