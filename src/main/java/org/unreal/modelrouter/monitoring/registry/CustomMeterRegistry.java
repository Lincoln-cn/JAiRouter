package org.unreal.modelrouter.monitoring.registry;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.Timer;
import org.unreal.modelrouter.monitoring.registry.model.MetricMetadata;
import org.unreal.modelrouter.monitoring.registry.model.MetricRegistrationRequest;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * 自定义指标注册器接口
 * 提供动态指标注册、注销和元数据管理功能
 */
public interface CustomMeterRegistry {
    
    /**
     * 注册Counter类型指标
     * 
     * @param request 指标注册请求
     * @return 注册的Counter实例
     */
    Counter registerCounter(MetricRegistrationRequest request);
    
    /**
     * 注册Gauge类型指标
     * 
     * @param request 指标注册请求
     * @param valueSupplier 值提供器
     * @return 注册的Gauge实例
     */
    Gauge registerGauge(MetricRegistrationRequest request, Supplier<Number> valueSupplier);
    
    /**
     * 注册Timer类型指标
     * 
     * @param request 指标注册请求
     * @return 注册的Timer实例
     */
    Timer registerTimer(MetricRegistrationRequest request);
    
    /**
     * 注销指标
     * 
     * @param metricName 指标名称
     * @param tags 指标标签
     * @return 是否成功注销
     */
    boolean unregisterMeter(String metricName, Map<String, String> tags);
    
    /**
     * 获取指标元数据
     * 
     * @param metricName 指标名称
     * @return 指标元数据
     */
    Optional<MetricMetadata> getMetricMetadata(String metricName);
    
    /**
     * 获取所有已注册指标的元数据
     * 
     * @return 所有指标元数据列表
     */
    List<MetricMetadata> getAllMetricMetadata();
    
    /**
     * 更新指标元数据
     * 
     * @param metricName 指标名称
     * @param metadata 新的元数据
     * @return 是否成功更新
     */
    boolean updateMetricMetadata(String metricName, MetricMetadata metadata);
    
    /**
     * 检查指标是否存在
     * 
     * @param metricName 指标名称
     * @param tags 指标标签
     * @return 是否存在
     */
    boolean meterExists(String metricName, Map<String, String> tags);
    
    /**
     * 获取指标实例
     * 
     * @param metricName 指标名称
     * @param tags 指标标签
     * @return 指标实例
     */
    Optional<Meter> getMeter(String metricName, Map<String, String> tags);
    
    /**
     * 获取所有已注册的指标
     * 
     * @return 指标列表
     */
    List<Meter> getAllMeters();
    
    /**
     * 清理过期或无效的指标
     * 
     * @return 清理的指标数量
     */
    int cleanupExpiredMeters();
}