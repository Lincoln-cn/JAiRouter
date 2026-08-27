package org.unreal.modelrouter.router.pool.model;

import java.util.ArrayList;
import java.util.List;

/**
 * 资源池定义
 * 一组同服务类型实例的命名集合;请求 model 使用 poolName(如 auto-model)时触发池路由
 */
public final class PoolDefinition {

    /** 池名 = 虚拟模型名(请求 model 用它触发池路由;auto-model 为约定名) */
    private String poolName;
    /** 显示名 */
    private String name;
    /** 单服务类型(chat/embedding/...) */
    private String serviceType;
    /** 是否启用 */
    private boolean enabled = true;
    /** 池级选择策略:weighted-random / round-robin / least-connections / ip-hash */
    private String strategy = "weighted-random";
    /** 描述 */
    private String description;
    /** 池成员(引用实例) */
    private List<PoolMember> members = new ArrayList<>();

    public String getPoolName() {
        return poolName;
    }

    public void setPoolName(final String poolName) {
        this.poolName = poolName;
    }

    public String getName() {
        return name;
    }

    public void setName(final String name) {
        this.name = name;
    }

    public String getServiceType() {
        return serviceType;
    }

    public void setServiceType(final String serviceType) {
        this.serviceType = serviceType;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(final boolean enabled) {
        this.enabled = enabled;
    }

    public String getStrategy() {
        return strategy;
    }

    public void setStrategy(final String strategy) {
        this.strategy = strategy;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(final String description) {
        this.description = description;
    }

    public List<PoolMember> getMembers() {
        return members;
    }

    public void setMembers(final List<PoolMember> members) {
        this.members = members;
    }
}
