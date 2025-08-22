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

@RestControllerAdvice
@RequiredArgsConstructor
public class ServerExceptionHandler {

    private static final Logger logger = LoggerFactory.getLogger(ServerExceptionHandler.class);
    
    private final ErrorTracker errorTracker;

    @ExceptionHandler(Exception.class)
    @ResponseBody
    public RouterResponse<Void> handleException(Exception e) {
        // 记录异常到追踪系统
        Map<String, Object> additionalInfo = new HashMap<>();
        additionalInfo.put("handler", "ServerExceptionHandler");
        additionalInfo.put("responseStatus", "500");
        
        TracingContext context = TracingContextHolder.getCurrentContext();
        if (context != null && context.isActive()) {
            additionalInfo.put("traceId", context.getTraceId());
            additionalInfo.put("spanId", context.getSpanId());
        }
        
        errorTracker.trackError(e, "global_exception_handling", additionalInfo);
        
        logger.error("系统异常", e);
        return RouterResponse.error("系统异常:%s".formatted(e.getMessage()), "500");
    }
}