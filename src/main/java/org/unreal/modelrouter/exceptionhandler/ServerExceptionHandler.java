package org.unreal.modelrouter.exceptionhandler;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.unreal.modelrouter.controller.response.RouterResponse;
import org.unreal.modelrouter.monitoring.error.ErrorTracker;
import org.unreal.modelrouter.tracing.TracingContext;
import org.unreal.modelrouter.tracing.TracingContextHolder;

import java.util.HashMap;
import java.util.Map;

// @RestControllerAdvice - 已禁用，使用 ReactiveGlobalExceptionHandler 替代
// @RequiredArgsConstructor
public class ServerExceptionHandler {

    private static final Logger logger = LoggerFactory.getLogger(ServerExceptionHandler.class);
    
    private final ErrorTracker errorTracker;

    @ExceptionHandler(Exception.class)
    @ResponseBody
    public RouterResponse<Void> handleException(Exception e) {
        try {
            // 记录异常到追踪系统
            Map<String, Object> additionalInfo = new HashMap<>();
            additionalInfo.put("handler", "ServerExceptionHandler");
            
            TracingContext context = TracingContextHolder.getCurrentContext();
            if (context != null && context.isActive()) {
                additionalInfo.put("traceId", context.getTraceId());
                additionalInfo.put("spanId", context.getSpanId());
            }
            
            // 根据异常类型设置不同的响应状态
            if (e instanceof org.springframework.web.server.ServerWebInputException) {
                additionalInfo.put("responseStatus", "400");
            } else if (e instanceof org.springframework.web.server.ResponseStatusException) {
                org.springframework.web.server.ResponseStatusException rse = (org.springframework.web.server.ResponseStatusException) e;
                additionalInfo.put("responseStatus", String.valueOf(rse.getStatusCode().value()));
            } else {
                additionalInfo.put("responseStatus", "500");
            }
            
            errorTracker.trackError(e, "global_exception_handling", additionalInfo);
        } catch (Exception trackingException) {
            // 如果错误追踪失败，只记录日志，不影响主要的异常处理流程
            logger.warn("错误追踪失败: {}", trackingException.getMessage());
        }
        
        // 特别处理 ServerWebInputException
        if (e instanceof org.springframework.web.server.ServerWebInputException) {
            logger.error("请求体读取异常: {}", e.getMessage());
            return RouterResponse.error("请求体无效或缺失: " + e.getMessage(), "400");
        }
        
        // 特别处理 ResponseStatusException
        if (e instanceof org.springframework.web.server.ResponseStatusException) {
            org.springframework.web.server.ResponseStatusException rse = (org.springframework.web.server.ResponseStatusException) e;
            logger.error("响应状态异常: status={}, message={}", rse.getStatusCode(), rse.getMessage());
            return RouterResponse.error("请求处理失败: " + rse.getReason(), String.valueOf(rse.getStatusCode().value()));
        }
        
        logger.error("系统异常", e);
        
        // 安全地格式化错误消息，避免null值导致的问题
        String errorMessage = e.getMessage();
        if (errorMessage == null) {
            errorMessage = e.getClass().getSimpleName();
        }
        
        return RouterResponse.error("系统异常: " + errorMessage, "500");
    }
}