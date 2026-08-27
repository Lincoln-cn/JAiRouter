package org.unreal.modelrouter.router.pool.model;

/**
 * 资源池成员
 * 引用一个实例;池级权重在选择时覆盖实例自身权重
 */
public final class PoolMember {

    /** 实例 ID(稳定键,对应 ModelInstance.instanceId) */
    private String instanceId;
    /** 池级权重(选择时覆盖实例 weight;consistent-hash 策略下无效) */
    private int weight = 1;
    /** 可选:对外展示的模型名(响应回显用,缺省用实例 name) */
    private String modelName;

    public String getInstanceId() {
        return instanceId;
    }

    public void setInstanceId(final String instanceId) {
        this.instanceId = instanceId;
    }

    public int getWeight() {
        return weight;
    }

    public void setWeight(final int weight) {
        this.weight = weight;
    }

    public String getModelName() {
        return modelName;
    }

    public void setModelName(final String modelName) {
        this.modelName = modelName;
    }
}
