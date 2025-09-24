package org.unreal.modelrouter.tracing.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.unreal.modelrouter.tracing.config.TracingConfiguration;
import org.unreal.modelrouter.tracing.sampler.SamplingStrategyManager;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 采样配置控制器
 * 
 * 提供采样配置的动态更新和管理接口
 * 
 * @author JAiRouter Team
 * @since 1.0.0
 */
@Slf4j
@RestController
@RequestMapping("/api/tracing/sampling")
@RequiredArgsConstructor
public class SamplingConfigurationController {
    
    private final TracingConfiguration tracingConfig;
    private final SamplingStrategyManager samplingStrategyManager;
    
    // 用于存储采样统计信息
    private final Map<String, Object> samplingStatistics = new ConcurrentHashMap<>();
    
    /**
     * 更新采样配置
     * 
     * @param newConfig 新的采样配置
     * @return 响应结果
     */
    @PutMapping("/config")
    public ResponseEntity<Map<String, Object>> updateSamplingConfiguration(
            @RequestBody TracingConfiguration.SamplingConfig newConfig) {
        try {
            // 更新采样配置
            tracingConfig.setSampling(newConfig);
            
            // 通知采样策略管理器更新配置
            samplingStrategyManager.updateSamplingConfiguration(newConfig);
            
            // 记录更新时间
            samplingStatistics.put("lastUpdate", LocalDateTime.now());
            samplingStatistics.put("status", "success");
            
            Map<String, Object> response = Map.of(
                "message", "采样配置更新成功",
                "timestamp", LocalDateTime.now(),
                "status", "success"
            );
            
            log.info("采样配置更新成功，时间: {}", LocalDateTime.now());
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("采样配置更新失败", e);
            
            Map<String, Object> response = Map.of(
                "message", "采样配置更新失败: " + e.getMessage(),
                "timestamp", LocalDateTime.now(),
                "status", "error"
            );
            
            return ResponseEntity.internalServerError().body(response);
        }
    }
    
    /**
     * 重置采样配置为默认值
     * 
     * @return 响应结果
     */
    @DeleteMapping("/config")
    public ResponseEntity<Map<String, Object>> resetSamplingConfiguration() {
        try {
            // 重置为默认配置
            tracingConfig.setSampling(new TracingConfiguration.SamplingConfig());
            
            // 通知采样策略管理器更新配置
            samplingStrategyManager.updateSamplingConfiguration(tracingConfig.getSampling());
            
            // 记录更新时间
            samplingStatistics.put("lastReset", LocalDateTime.now());
            samplingStatistics.put("status", "reset");
            
            Map<String, Object> response = Map.of(
                "message", "采样配置已重置为默认值",
                "timestamp", LocalDateTime.now(),
                "status", "success"
            );
            
            log.info("采样配置重置成功，时间: {}", LocalDateTime.now());
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("采样配置重置失败", e);
            
            Map<String, Object> response = Map.of(
                "message", "采样配置重置失败: " + e.getMessage(),
                "timestamp", LocalDateTime.now(),
                "status", "error"
            );
            
            return ResponseEntity.internalServerError().body(response);
        }
    }
    
    /**
     * 获取当前采样配置
     * 
     * @return 当前采样配置
     */
    @GetMapping("/config")
    public ResponseEntity<TracingConfiguration.SamplingConfig> getCurrentSamplingConfiguration() {
        return ResponseEntity.ok(tracingConfig.getSampling());
    }
    
    /**
     * 获取采样统计信息
     * 
     * @return 采样统计信息
     */
    @GetMapping("/statistics")
    public ResponseEntity<Map<String, Object>> getSamplingStatistics() {
        try {
            // 添加更多统计信息
            samplingStatistics.put("currentStrategy", samplingStrategyManager.getCurrentStrategy().getDescription());
            samplingStatistics.put("timestamp", LocalDateTime.now());
            
            return ResponseEntity.ok(samplingStatistics);
        } catch (Exception e) {
            log.error("获取采样统计信息失败", e);
            
            Map<String, Object> errorResponse = Map.of(
                "error", "获取采样统计信息失败: " + e.getMessage(),
                "timestamp", LocalDateTime.now()
            );
            
            return ResponseEntity.internalServerError().body(errorResponse);
        }
    }
    
    /**
     * 切换采样策略
     * 
     * @param strategyName 策略名称
     * @return 响应结果
     */
    @PostMapping("/strategy/{strategyName}")
    public ResponseEntity<Map<String, Object>> switchSamplingStrategy(
            @PathVariable("strategyName") String strategyName) {
        try {
            samplingStrategyManager.updateStrategy(strategyName);
            
            Map<String, Object> response = Map.of(
                "message", "采样策略切换成功",
                "strategy", strategyName,
                "timestamp", LocalDateTime.now()
            );
            
            log.info("采样策略切换成功: {}", strategyName);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("采样策略切换失败", e);
            
            Map<String, Object> response = Map.of(
                "message", "采样策略切换失败: " + e.getMessage(),
                "timestamp", LocalDateTime.now()
            );
            
            return ResponseEntity.internalServerError().body(response);
        }
    }
}