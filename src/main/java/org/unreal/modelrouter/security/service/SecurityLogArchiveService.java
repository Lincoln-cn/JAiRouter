package org.unreal.modelrouter.security.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.unreal.modelrouter.security.audit.SecurityAuditService;
import org.unreal.modelrouter.security.model.SecurityAuditEvent;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 安全日志归档服务
 * 提供日志的长期存储和归档功能
 */
@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "jairouter.security.archive.enabled", havingValue = "true", matchIfMissing = false)
public class SecurityLogArchiveService {
    
    private final SecurityAuditService auditService;
    
    // 归档配置
    private static final String ARCHIVE_BASE_PATH = "logs/security/archive";
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter TIMESTAMP_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");
    
    // 归档统计
    private final AtomicLong totalArchivedEvents = new AtomicLong(0);
    private final AtomicLong lastArchiveSize = new AtomicLong(0);
    private volatile LocalDateTime lastArchiveTime;
    
    /**
     * 定时归档任务 - 每天凌晨2点执行
     */
    @Scheduled(cron = "0 0 2 * * ?")
    public void scheduleArchive() {
        log.info("开始执行定时安全日志归档任务");
        
        LocalDateTime endTime = LocalDateTime.now().minusDays(1).withHour(23).withMinute(59).withSecond(59);
        LocalDateTime startTime = endTime.minusDays(1).withHour(0).withMinute(0).withSecond(0);
        
        archiveSecurityLogs(startTime, endTime)
                .subscribeOn(Schedulers.boundedElastic())
                .subscribe(
                        archivedCount -> log.info("定时归档完成，归档了 {} 条安全日志", archivedCount),
                        error -> log.error("定时归档失败", error)
                );
    }
    
    /**
     * 归档指定时间范围的安全日志
     */
    public Mono<Long> archiveSecurityLogs(LocalDateTime startTime, LocalDateTime endTime) {
        return Mono.fromCallable(() -> {
            log.info("开始归档安全日志: {} 到 {}", startTime, endTime);
            
            // 创建归档目录
            String archiveDate = startTime.format(DATE_FORMATTER);
            Path archiveDir = Paths.get(ARCHIVE_BASE_PATH, archiveDate);
            
            try {
                Files.createDirectories(archiveDir);
            } catch (IOException e) {
                throw new RuntimeException("创建归档目录失败: " + archiveDir, e);
            }
            
            // 归档文件路径
            Path archiveFile = archiveDir.resolve("security-audit-" + archiveDate + ".log");
            
            AtomicLong archivedCount = new AtomicLong(0);
            
            // 查询并写入日志
            auditService.queryEvents(startTime, endTime, null, null, Integer.MAX_VALUE)
                    .doOnNext(event -> {
                        try {
                            writeEventToArchive(archiveFile, event);
                            archivedCount.incrementAndGet();
                        } catch (IOException e) {
                            log.error("写入归档文件失败: {}", event.getEventId(), e);
                        }
                    })
                    .blockLast(); // 等待所有事件处理完成
            
            long finalCount = archivedCount.get();
            
            // 更新统计信息
            totalArchivedEvents.addAndGet(finalCount);
            lastArchiveSize.set(finalCount);
            lastArchiveTime = LocalDateTime.now();
            
            // 压缩归档文件（可选）
            compressArchiveFile(archiveFile);
            
            log.info("安全日志归档完成: 归档了 {} 条记录到 {}", finalCount, archiveFile);
            
            return finalCount;
        })
        .subscribeOn(Schedulers.boundedElastic());
    }
    
    /**
     * 将审计事件写入归档文件
     */
    private void writeEventToArchive(Path archiveFile, SecurityAuditEvent event) throws IOException {
        String logLine = formatEventForArchive(event);
        
        try (BufferedWriter writer = Files.newBufferedWriter(archiveFile, 
                StandardOpenOption.CREATE, StandardOpenOption.APPEND)) {
            writer.write(logLine);
            writer.newLine();
        }
    }
    
    /**
     * 格式化审计事件为归档格式
     */
    private String formatEventForArchive(SecurityAuditEvent event) {
        StringBuilder sb = new StringBuilder();
        
        // 基本信息
        sb.append(event.getTimestamp().format(TIMESTAMP_FORMATTER))
          .append(" | ").append(event.getEventType())
          .append(" | ").append(event.getUserId() != null ? event.getUserId() : "N/A")
          .append(" | ").append(event.getClientIp() != null ? event.getClientIp() : "N/A")
          .append(" | ").append(event.getAction() != null ? event.getAction() : "N/A")
          .append(" | ").append(event.isSuccess() ? "SUCCESS" : "FAILURE");
        
        // 失败原因
        if (!event.isSuccess() && event.getFailureReason() != null) {
            sb.append(" | ").append(event.getFailureReason());
        }
        
        // 资源信息
        if (event.getResource() != null) {
            sb.append(" | Resource: ").append(event.getResource());
        }
        
        // 用户代理
        if (event.getUserAgent() != null) {
            sb.append(" | UserAgent: ").append(event.getUserAgent());
        }
        
        // 请求ID
        if (event.getRequestId() != null) {
            sb.append(" | RequestId: ").append(event.getRequestId());
        }
        
        // 附加数据
        if (event.getAdditionalData() != null && !event.getAdditionalData().isEmpty()) {
            sb.append(" | AdditionalData: ").append(event.getAdditionalData().toString());
        }
        
        return sb.toString();
    }
    
    /**
     * 压缩归档文件
     */
    private void compressArchiveFile(Path archiveFile) {
        try {
            // 这里可以实现文件压缩逻辑，例如使用gzip
            // 为了简化，这里只是记录日志
            long fileSize = Files.size(archiveFile);
            log.info("归档文件大小: {} bytes, 文件: {}", fileSize, archiveFile);
            
            // 实际实现中可以添加压缩逻辑
            // Path compressedFile = Paths.get(archiveFile.toString() + ".gz");
            // 使用 GZIPOutputStream 压缩文件
            
        } catch (IOException e) {
            log.warn("获取归档文件大小失败: {}", archiveFile, e);
        }
    }
    
    /**
     * 清理过期的归档文件
     */
    @Scheduled(cron = "0 30 2 * * ?") // 每天凌晨2:30执行
    public void cleanupExpiredArchives() {
        log.info("开始清理过期的归档文件");
        
        Mono.fromRunnable(() -> {
            try {
                Path archiveBaseDir = Paths.get(ARCHIVE_BASE_PATH);
                if (!Files.exists(archiveBaseDir)) {
                    return;
                }
                
                LocalDateTime cutoffDate = LocalDateTime.now().minusMonths(12); // 保留12个月
                
                Files.walk(archiveBaseDir, 2)
                        .filter(Files::isDirectory)
                        .filter(path -> !path.equals(archiveBaseDir))
                        .forEach(dateDir -> {
                            try {
                                String dirName = dateDir.getFileName().toString();
                                LocalDateTime dirDate = LocalDateTime.parse(dirName + "T00:00:00");
                                
                                if (dirDate.isBefore(cutoffDate)) {
                                    Files.walk(dateDir)
                                            .sorted((a, b) -> b.compareTo(a)) // 先删除文件，再删除目录
                                            .forEach(file -> {
                                                try {
                                                    Files.deleteIfExists(file);
                                                    log.debug("删除过期归档文件: {}", file);
                                                } catch (IOException e) {
                                                    log.warn("删除归档文件失败: {}", file, e);
                                                }
                                            });
                                }
                            } catch (Exception e) {
                                log.warn("处理归档目录失败: {}", dateDir, e);
                            }
                        });
                
                log.info("过期归档文件清理完成");
                
            } catch (IOException e) {
                log.error("清理过期归档文件失败", e);
            }
        })
        .subscribeOn(Schedulers.boundedElastic())
        .subscribe();
    }
    
    /**
     * 手动触发归档
     */
    public Mono<Long> manualArchive(LocalDateTime startTime, LocalDateTime endTime) {
        log.info("手动触发安全日志归档: {} 到 {}", startTime, endTime);
        return archiveSecurityLogs(startTime, endTime);
    }
    
    /**
     * 获取归档统计信息
     */
    public ArchiveStatistics getArchiveStatistics() {
        return ArchiveStatistics.builder()
                .totalArchivedEvents(totalArchivedEvents.get())
                .lastArchiveSize(lastArchiveSize.get())
                .lastArchiveTime(lastArchiveTime)
                .archiveBasePath(ARCHIVE_BASE_PATH)
                .build();
    }
    
    /**
     * 归档统计信息
     */
    @lombok.Data
    @lombok.Builder
    public static class ArchiveStatistics {
        private long totalArchivedEvents;
        private long lastArchiveSize;
        private LocalDateTime lastArchiveTime;
        private String archiveBasePath;
    }
}