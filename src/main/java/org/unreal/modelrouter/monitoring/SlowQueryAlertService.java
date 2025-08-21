package org.unreal.modelrouter.monitoring;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * 慢查询告警通知服务
 * 提供慢查询告警通知功能
 */
@Service
public class SlowQueryAlertService {
    
    private static final Logger logger = LoggerFactory.getLogger(SlowQueryAlertService.class);
    
    /**
     * 发送慢查询告警通知
     * @param operationName 操作名称
     * @param durationMillis 操作耗时(毫秒)
     * @param thresholdMillis 阈值(毫秒)
     * @param context 上下文信息
     */
    public void sendAlert(String operationName, long durationMillis, long thresholdMillis, Map<String, String> context) {
        // 构建告警消息
        StringBuilder message = new StringBuilder();
        message.append("Slow Query Alert!\n");
        message.append("Operation: ").append(operationName).append("\n");
        message.append("Duration: ").append(durationMillis).append(" ms\n");
        message.append("Threshold: ").append(thresholdMillis).append(" ms\n");
        
        if (context != null && !context.isEmpty()) {
            message.append("Context:\n");
            for (Map.Entry<String, String> entry : context.entrySet()) {
                message.append("  ").append(entry.getKey()).append(": ").append(entry.getValue()).append("\n");
            }
        }
        
        // 记录告警日志
        logger.error(message.toString());
        
        // TODO: 实现具体的告警通知逻辑，如：
        // 1. 发送邮件通知
        // 2. 发送短信通知
        // 3. 调用Webhook通知
        // 4. 发送到消息队列
        // 5. 集成第三方告警系统（如Prometheus Alertmanager、钉钉、企业微信等）
    }
    
    /**
     * 发送慢查询统计报告
     * @param stats 慢查询统计信息
     */
    public void sendStatsReport(Map<String, SlowQueryDetector.SlowQueryStats> stats) {
        // 构建统计报告
        StringBuilder report = new StringBuilder();
        report.append("Slow Query Statistics Report\n");
        report.append("============================\n");
        
        if (stats.isEmpty()) {
            report.append("No slow queries detected.\n");
        } else {
            for (Map.Entry<String, SlowQueryDetector.SlowQueryStats> entry : stats.entrySet()) {
                String operationName = entry.getKey();
                SlowQueryDetector.SlowQueryStats stat = entry.getValue();
                
                report.append("Operation: ").append(operationName).append("\n");
                report.append("  Count: ").append(stat.getCount()).append("\n");
                report.append("  Total Duration: ").append(stat.getTotalDuration()).append(" ms\n");
                report.append("  Average Duration: ").append(String.format("%.2f", stat.getAverageDuration())).append(" ms\n");
                report.append("  Max Duration: ").append(stat.getMaxDuration()).append(" ms\n");
                report.append("  Min Duration: ").append(stat.getMinDuration()).append(" ms\n");
                report.append("\n");
            }
        }
        
        // 记录统计报告日志
        logger.info(report.toString());
        
        // TODO: 实现具体的统计报告发送逻辑
    }
}