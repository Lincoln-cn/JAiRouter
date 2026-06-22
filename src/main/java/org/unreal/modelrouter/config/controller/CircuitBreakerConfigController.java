package org.unreal.modelrouter.config.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.unreal.modelrouter.common.dto.CircuitBreakerGlobalConfigDTO;
import org.unreal.modelrouter.common.dto.CircuitBreakerStateHistoryDTO;
import org.unreal.modelrouter.common.controller.response.RouterResponse;
import org.unreal.modelrouter.config.service.CircuitBreakerConfigService;
import org.unreal.modelrouter.persistence.jpa.entity.CircuitBreakerStateHistoryEntity;
import org.unreal.modelrouter.router.circuitbreaker.AdaptiveThresholdManager;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 熔断器配置管理控制器
 *
 * 提供全局配置和历史记录 API
 *
 * v2.6.13: 新增
 */
@Slf4j
@RestController
@RequestMapping("/api/config/circuit-breaker")
@RequiredArgsConstructor
public class CircuitBreakerConfigController {

    private final CircuitBreakerConfigService circuitBreakerConfigService;
    private final AdaptiveThresholdManager adaptiveThresholdManager;

    // ==================== 全局配置 API ====================

    /**
     * 获取熔断器全局配置
     */
    @GetMapping("/global-config")
    public ResponseEntity<RouterResponse<CircuitBreakerGlobalConfigDTO>> getGlobalConfig() {
        CircuitBreakerGlobalConfigDTO config = circuitBreakerConfigService.getGlobalConfig();
        return ResponseEntity.ok(RouterResponse.success(config));
    }

    /**
     * 保存熔断器全局配置
     */
    @PutMapping("/global-config")
    public ResponseEntity<RouterResponse<CircuitBreakerGlobalConfigDTO>> saveGlobalConfig(
            @RequestBody final CircuitBreakerGlobalConfigDTO config) {
        log.info("Saving circuit breaker global config: adaptiveThresholdEnabled={}, stateSyncIntervalMinutes={}",
                config.getAdaptiveThresholdEnabled(), config.getStateSyncIntervalMinutes());

        CircuitBreakerGlobalConfigDTO saved = circuitBreakerConfigService.saveGlobalConfig(config);

        // 同步更新自适应阈值管理器的启用状态
        if (config.getAdaptiveThresholdEnabled() != null) {
            adaptiveThresholdManager.setAdaptiveEnabled(config.getAdaptiveThresholdEnabled());
            log.info("自适应阈值调整已{}", config.getAdaptiveThresholdEnabled() ? "启用" : "禁用");
        }

        return ResponseEntity.ok(RouterResponse.success(saved, "全局配置保存成功"));
    }

    /**
     * 重置全局配置为默认值
     */
    @PostMapping("/global-config/reset")
    public ResponseEntity<RouterResponse<CircuitBreakerGlobalConfigDTO>> resetGlobalConfig() {
        log.info("Resetting circuit breaker global config to defaults");

        CircuitBreakerGlobalConfigDTO config = circuitBreakerConfigService.resetGlobalConfig();

        // 重置时禁用自适应阈值
        adaptiveThresholdManager.setAdaptiveEnabled(false);

        return ResponseEntity.ok(RouterResponse.success(config, "全局配置已重置为默认值"));
    }

    // ==================== 历史记录 API ====================

    /**
     * 获取熔断器状态变化历史记录（分页）
     */
    @GetMapping("/history")
    public ResponseEntity<RouterResponse<Page<CircuitBreakerStateHistoryDTO>>> getHistory(
            @RequestParam(defaultValue = "0") final int page,
            @RequestParam(defaultValue = "20") final int size) {
        
        PageRequest pageRequest = PageRequest.of(page, size, Sort.by("changedAt").descending());
        Page<CircuitBreakerStateHistoryEntity> historyPage = 
                circuitBreakerConfigService.getHistory(pageRequest);
        
        Page<CircuitBreakerStateHistoryDTO> dtoPage = historyPage.map(this::toDTO);
        return ResponseEntity.ok(RouterResponse.success(dtoPage));
    }

    /**
     * 按实例 ID 获取熔断器状态变化历史记录
     */
    @GetMapping("/history/instance")
    public ResponseEntity<RouterResponse<List<CircuitBreakerStateHistoryDTO>>> getHistoryByInstance(
            @RequestParam final String instanceId,
            @RequestParam(defaultValue = "100") final int limit) {
        
        List<CircuitBreakerStateHistoryEntity> historyList = 
                circuitBreakerConfigService.getHistoryByInstance(instanceId, limit);
        
        List<CircuitBreakerStateHistoryDTO> dtoList = historyList.stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
        
        return ResponseEntity.ok(RouterResponse.success(dtoList));
    }

    /**
     * 按服务类型获取熔断器状态变化历史记录
     */
    @GetMapping("/history/service-type")
    public ResponseEntity<RouterResponse<Page<CircuitBreakerStateHistoryDTO>>> getHistoryByServiceType(
            @RequestParam final String serviceType,
            @RequestParam(defaultValue = "0") final int page,
            @RequestParam(defaultValue = "20") final int size) {
        
        PageRequest pageRequest = PageRequest.of(page, size, Sort.by("changedAt").descending());
        Page<CircuitBreakerStateHistoryEntity> historyPage = 
                circuitBreakerConfigService.getHistoryByServiceType(serviceType, pageRequest);
        
        Page<CircuitBreakerStateHistoryDTO> dtoPage = historyPage.map(this::toDTO);
        return ResponseEntity.ok(RouterResponse.success(dtoPage));
    }

    /**
     * 按时间范围获取熔断器状态变化历史记录
     */
    @GetMapping("/history/time-range")
    public ResponseEntity<RouterResponse<List<CircuitBreakerStateHistoryDTO>>> getHistoryByTimeRange(
            @RequestParam final String startTime,
            @RequestParam final String endTime) {
        
        LocalDateTime start = LocalDateTime.parse(startTime);
        LocalDateTime end = LocalDateTime.parse(endTime);
        
        List<CircuitBreakerStateHistoryEntity> historyList = 
                circuitBreakerConfigService.getHistoryByTimeRange(start, end);
        
        List<CircuitBreakerStateHistoryDTO> dtoList = historyList.stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
        
        return ResponseEntity.ok(RouterResponse.success(dtoList));
    }

    /**
     * 清理过期的历史记录
     */
    @DeleteMapping("/history/cleanup")
    public ResponseEntity<RouterResponse<Integer>> cleanupHistory() {
        log.info("Cleaning up expired circuit breaker history records");
        
        int deleted = circuitBreakerConfigService.cleanupExpiredHistory();
        return ResponseEntity.ok(RouterResponse.success(deleted, 
                "已清理 " + deleted + " 条过期历史记录"));
    }

    /**
     * 获取历史记录统计信息
     */
    @GetMapping("/history/stats")
    public ResponseEntity<RouterResponse<CircuitBreakerConfigService.CircuitBreakerHistoryStats>> getHistoryStats() {
        CircuitBreakerConfigService.CircuitBreakerHistoryStats stats = circuitBreakerConfigService.getHistoryStats();
        return ResponseEntity.ok(RouterResponse.success(stats));
    }

    // ==================== 辅助方法 ====================

    /**
     * 将实体转换为 DTO
     */
    private CircuitBreakerStateHistoryDTO toDTO(final CircuitBreakerStateHistoryEntity entity) {
        String triggerReasonDesc = null;
        if (entity.getTriggerReason() != null) {
            try {
                CircuitBreakerStateHistoryDTO.TriggerReason reason = 
                        CircuitBreakerStateHistoryDTO.TriggerReason.valueOf(entity.getTriggerReason());
                triggerReasonDesc = reason.getDescription();
            } catch (IllegalArgumentException e) {
                triggerReasonDesc = entity.getTriggerReason();
            }
        }

        return CircuitBreakerStateHistoryDTO.builder()
                .id(entity.getId())
                .instanceId(entity.getInstanceId())
                .instanceName(entity.getInstanceName())
                .serviceType(entity.getServiceType())
                .previousState(entity.getPreviousState() != null ? entity.getPreviousState().name() : null)
                .currentState(entity.getCurrentState() != null ? entity.getCurrentState().name() : null)
                .triggerReason(entity.getTriggerReason())
                .triggerReasonDesc(triggerReasonDesc)
                .failureCount(entity.getFailureCount())
                .successCount(entity.getSuccessCount())
                .changedAt(entity.getChangedAt())
                .build();
    }
}
