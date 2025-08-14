package org.unreal.modelrouter.monitoring.registry;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;
import org.unreal.modelrouter.monitoring.config.MonitoringEnabledCondition;
import org.unreal.modelrouter.monitoring.registry.model.MetricMetadata;
import org.unreal.modelrouter.monitoring.registry.model.MetricRegistrationRequest;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * 默认自定义指标注册器实现
 * 提供指标的动态注册、注销和生命周期管理
 */
@Component
@Conditional(MonitoringEnabledCondition.class)
public class DefaultCustomMeterRegistry implements CustomMeterRegistry {
    
    private static final Logger logger = LoggerFactory.getLogger(DefaultCustomMeterRegistry.class);
    
    private final MeterRegistry meterRegistry;
    private final Map<String, MetricMetadata> metricMetadataMap;
    private final Map<String, Meter> registeredMeters;
    private final ReadWriteLock lock;
    
    // 指标过期时间（小时）
    private static final long METRIC_EXPIRY_HOURS = 24;
    
    public DefaultCustomMeterRegistry(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
        this.metricMetadataMap = new ConcurrentHashMap<>();
        this.registeredMeters = new ConcurrentHashMap<>();
        this.lock = new ReentrantReadWriteLock();
        
        logger.info("DefaultCustomMeterRegistry initialized");
    }
    
    @Override
    public Counter registerCounter(MetricRegistrationRequest request) {
        validateRequest(request, Meter.Type.COUNTER);
        
        lock.writeLock().lock();
        try {
            
            String meterKey = createMeterKey(request.getName(), request.getTags());
            
            if (registeredMeters.containsKey(meterKey)) {
                logger.warn("Counter {} with tags {} already exists", request.getName(), request.getTags());
                return (Counter) registeredMeters.get(meterKey);
            }
            
            Counter.Builder counterBuilder = Counter.builder(request.getName())
                    .description(request.getDescription())
                    .baseUnit(request.getUnit());
            
            // Add tags
            for (Map.Entry<String, String> tag : request.getTags().entrySet()) {
                counterBuilder.tag(tag.getKey(), tag.getValue());
            }
            
            Counter counter = counterBuilder.register(meterRegistry);
            
            // 存储指标和元数据
            registeredMeters.put(meterKey, counter);
            MetricMetadata metadata = MetricMetadata.builder(request.getName(), request.getType())
                    .description(request.getDescription())
                    .unit(request.getUnit())
                    .baseTags(request.getTags())
                    .category(request.getCategory())
                    .enabled(request.isEnabled())
                    .samplingRate(request.getSamplingRate())
                    .build();
            metricMetadataMap.put(request.getName(), metadata);
            
            logger.info("Registered counter: {} with tags: {}", request.getName(), request.getTags());
            return counter;
            
        } catch (Exception e) {
            logger.error("Failed to register counter: {}", request.getName(), e);
            throw new RuntimeException("Failed to register counter: " + request.getName(), e);
        } finally {
            lock.writeLock().unlock();
        }
    }
    
    @Override
    public Gauge registerGauge(MetricRegistrationRequest request, Supplier<Number> valueSupplier) {
        validateRequest(request, Meter.Type.GAUGE);
        
        lock.writeLock().lock();
        try {
            
            String meterKey = createMeterKey(request.getName(), request.getTags());
            
            if (registeredMeters.containsKey(meterKey)) {
                logger.warn("Gauge {} with tags {} already exists", request.getName(), request.getTags());
                return (Gauge) registeredMeters.get(meterKey);
            }
            
            Gauge.Builder<Supplier<Number>> gaugeBuilder = Gauge.builder(request.getName(), valueSupplier)
                    .description(request.getDescription())
                    .baseUnit(request.getUnit());
            
            // Add tags
            for (Map.Entry<String, String> tag : request.getTags().entrySet()) {
                gaugeBuilder.tag(tag.getKey(), tag.getValue());
            }
            
            Gauge gauge = gaugeBuilder.register(meterRegistry);
            
            // 存储指标和元数据
            registeredMeters.put(meterKey, gauge);
            MetricMetadata metadata = MetricMetadata.builder(request.getName(), request.getType())
                    .description(request.getDescription())
                    .unit(request.getUnit())
                    .baseTags(request.getTags())
                    .category(request.getCategory())
                    .enabled(request.isEnabled())
                    .samplingRate(request.getSamplingRate())
                    .build();
            metricMetadataMap.put(request.getName(), metadata);
            
            logger.info("Registered gauge: {} with tags: {}", request.getName(), request.getTags());
            return gauge;
            
        } catch (Exception e) {
            logger.error("Failed to register gauge: {}", request.getName(), e);
            throw new RuntimeException("Failed to register gauge: " + request.getName(), e);
        } finally {
            lock.writeLock().unlock();
        }
    }
    
    @Override
    public Timer registerTimer(MetricRegistrationRequest request) {
        validateRequest(request, Meter.Type.TIMER);
        
        lock.writeLock().lock();
        try {
            
            String meterKey = createMeterKey(request.getName(), request.getTags());
            
            if (registeredMeters.containsKey(meterKey)) {
                logger.warn("Timer {} with tags {} already exists", request.getName(), request.getTags());
                return (Timer) registeredMeters.get(meterKey);
            }
            
            Timer.Builder timerBuilder = Timer.builder(request.getName())
                    .description(request.getDescription());
            
            // Add tags
            for (Map.Entry<String, String> tag : request.getTags().entrySet()) {
                timerBuilder.tag(tag.getKey(), tag.getValue());
            }
            
            Timer timer = timerBuilder.register(meterRegistry);
            
            // 存储指标和元数据
            registeredMeters.put(meterKey, timer);
            MetricMetadata metadata = MetricMetadata.builder(request.getName(), request.getType())
                    .description(request.getDescription())
                    .unit(request.getUnit())
                    .baseTags(request.getTags())
                    .category(request.getCategory())
                    .enabled(request.isEnabled())
                    .samplingRate(request.getSamplingRate())
                    .build();
            metricMetadataMap.put(request.getName(), metadata);
            
            logger.info("Registered timer: {} with tags: {}", request.getName(), request.getTags());
            return timer;
            
        } catch (Exception e) {
            logger.error("Failed to register timer: {}", request.getName(), e);
            throw new RuntimeException("Failed to register timer: " + request.getName(), e);
        } finally {
            lock.writeLock().unlock();
        }
    }
    
    @Override
    public boolean unregisterMeter(String metricName, Map<String, String> tags) {
        lock.writeLock().lock();
        try {
            String meterKey = createMeterKey(metricName, tags);
            Meter meter = registeredMeters.get(meterKey);
            
            if (meter != null) {
                meterRegistry.remove(meter);
                registeredMeters.remove(meterKey);
                metricMetadataMap.remove(metricName);
                
                logger.info("Unregistered meter: {} with tags: {}", metricName, tags);
                return true;
            }
            
            logger.warn("Meter {} with tags {} not found for unregistration", metricName, tags);
            return false;
            
        } catch (Exception e) {
            logger.error("Failed to unregister meter: {}", metricName, e);
            return false;
        } finally {
            lock.writeLock().unlock();
        }
    }
    
    @Override
    public Optional<MetricMetadata> getMetricMetadata(String metricName) {
        lock.readLock().lock();
        try {
            return Optional.ofNullable(metricMetadataMap.get(metricName));
        } finally {
            lock.readLock().unlock();
        }
    }
    
    @Override
    public List<MetricMetadata> getAllMetricMetadata() {
        lock.readLock().lock();
        try {
            return List.copyOf(metricMetadataMap.values());
        } finally {
            lock.readLock().unlock();
        }
    }
    
    @Override
    public boolean updateMetricMetadata(String metricName, MetricMetadata metadata) {
        lock.writeLock().lock();
        try {
            if (metricMetadataMap.containsKey(metricName)) {
                MetricMetadata updatedMetadata = metadata.toBuilder().build();
                metricMetadataMap.put(metricName, updatedMetadata);
                
                logger.info("Updated metadata for metric: {}", metricName);
                return true;
            }
            
            logger.warn("Metric {} not found for metadata update", metricName);
            return false;
            
        } catch (Exception e) {
            logger.error("Failed to update metadata for metric: {}", metricName, e);
            return false;
        } finally {
            lock.writeLock().unlock();
        }
    }
    
    @Override
    public boolean meterExists(String metricName, Map<String, String> tags) {
        lock.readLock().lock();
        try {
            String meterKey = createMeterKey(metricName, tags);
            return registeredMeters.containsKey(meterKey);
        } finally {
            lock.readLock().unlock();
        }
    }
    
    @Override
    public Optional<Meter> getMeter(String metricName, Map<String, String> tags) {
        lock.readLock().lock();
        try {
            String meterKey = createMeterKey(metricName, tags);
            return Optional.ofNullable(registeredMeters.get(meterKey));
        } finally {
            lock.readLock().unlock();
        }
    }
    
    @Override
    public List<Meter> getAllMeters() {
        lock.readLock().lock();
        try {
            return List.copyOf(registeredMeters.values());
        } finally {
            lock.readLock().unlock();
        }
    }
    
    @Override
    public int cleanupExpiredMeters() {
        lock.writeLock().lock();
        try {
            Instant cutoffTime = Instant.now().minus(METRIC_EXPIRY_HOURS, ChronoUnit.HOURS);
            int cleanedCount = 0;
            
            List<String> expiredMetrics = metricMetadataMap.entrySet().stream()
                    .filter(entry -> entry.getValue().getLastUpdatedAt().isBefore(cutoffTime))
                    .map(Map.Entry::getKey)
                    .collect(Collectors.toList());
            
            for (String metricName : expiredMetrics) {
                MetricMetadata metadata = metricMetadataMap.get(metricName);
                if (metadata != null) {
                    String meterKey = createMeterKey(metricName, metadata.getBaseTags());
                    Meter meter = registeredMeters.get(meterKey);
                    
                    if (meter != null) {
                        meterRegistry.remove(meter);
                        registeredMeters.remove(meterKey);
                        metricMetadataMap.remove(metricName);
                        cleanedCount++;
                    }
                }
            }
            
            if (cleanedCount > 0) {
                logger.info("Cleaned up {} expired metrics", cleanedCount);
            }
            
            return cleanedCount;
            
        } catch (Exception e) {
            logger.error("Failed to cleanup expired meters", e);
            return 0;
        } finally {
            lock.writeLock().unlock();
        }
    }
    
    private void validateRequest(MetricRegistrationRequest request, Meter.Type expectedType) {
        if (request == null) {
            throw new IllegalArgumentException("MetricRegistrationRequest cannot be null");
        }
        
        if (request.getName() == null || request.getName().trim().isEmpty()) {
            throw new IllegalArgumentException("Metric name cannot be null or empty");
        }
        
        if (request.getType() != expectedType) {
            throw new IllegalArgumentException("Expected metric type " + expectedType + " but got " + request.getType());
        }
        
        if (request.getSamplingRate() < 0.0 || request.getSamplingRate() > 1.0) {
            throw new IllegalArgumentException("Sampling rate must be between 0.0 and 1.0");
        }
    }
    
    private String createMeterKey(String metricName, Map<String, String> tags) {
        StringBuilder keyBuilder = new StringBuilder(metricName);
        
        if (tags != null && !tags.isEmpty()) {
            tags.entrySet().stream()
                    .sorted(Map.Entry.comparingByKey())
                    .forEach(entry -> keyBuilder.append("|").append(entry.getKey()).append("=").append(entry.getValue()));
        }
        
        return keyBuilder.toString();
    }
}