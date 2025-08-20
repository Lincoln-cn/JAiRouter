package org.unreal.modelrouter.tracing;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.StatusCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/**
 * Span状态管理器
 * 
 * 提供Span状态的统一管理，包括：
 * - HTTP状态码到Span状态的映射
 * - 错误状态的设置和管理
 * - 业务状态的标准化处理
 * - 状态变更的日志记录
 * 
 * @author JAiRouter Team
 * @since 1.0.0
 */
@Slf4j
@Component
public class SpanStatusManager {
    
    /**
     * 根据HTTP状态码设置Span状态
     * 
     * @param span 目标Span
     * @param httpStatus HTTP状态码
     */
    public void setHttpStatus(Span span, HttpStatus httpStatus) {
        if (span == null || !span.getSpanContext().isValid() || httpStatus == null) {
            return;
        }
        
        try {
            StatusCode statusCode = mapHttpStatusToSpanStatus(httpStatus);
            String description = httpStatus.getReasonPhrase();
            
            span.setStatus(statusCode, description);
            span.setAttribute(TracingConstants.HttpAttributes.STATUS_CODE, httpStatus.value());
            
            log.debug("设置Span HTTP状态: {} -> {} (spanId: {})", 
                    httpStatus.value(), statusCode, span.getSpanContext().getSpanId());
        } catch (Exception e) {
            log.debug("设置Span HTTP状态失败", e);
        }
    }
    
    /**
     * 根据HTTP状态码设置Span状态
     * 
     * @param span 目标Span
     * @param statusCode HTTP状态码
     */
    public void setHttpStatus(Span span, int statusCode) {
        try {
            HttpStatus httpStatus = HttpStatus.valueOf(statusCode);
            setHttpStatus(span, httpStatus);
        } catch (IllegalArgumentException e) {
            // 处理未知状态码
            setCustomStatus(span, statusCode >= 400 ? StatusCode.ERROR : StatusCode.OK, 
                    "HTTP " + statusCode);
        }
    }
    
    /**
     * 设置成功状态
     * 
     * @param span 目标Span
     */
    public void setSuccess(Span span) {
        setSuccess(span, "Operation completed successfully");
    }
    
    /**
     * 设置成功状态
     * 
     * @param span 目标Span
     * @param description 状态描述
     */
    public void setSuccess(Span span, String description) {
        if (span == null || !span.getSpanContext().isValid()) {
            return;
        }
        
        try {
            span.setStatus(StatusCode.OK, description);
            span.setAttribute("success", true);
            
            log.debug("设置Span成功状态: {} (spanId: {})", 
                    description, span.getSpanContext().getSpanId());
        } catch (Exception e) {
            log.debug("设置Span成功状态失败", e);
        }
    }
    
    /**
     * 设置错误状态
     * 
     * @param span 目标Span
     * @param error 错误信息
     */
    public void setError(Span span, Throwable error) {
        if (span == null || !span.getSpanContext().isValid() || error == null) {
            return;
        }
        
        try {
            // 记录异常
            span.recordException(error);
            span.setStatus(StatusCode.ERROR, error.getMessage());
            
            // 设置错误相关属性
            span.setAttribute(TracingConstants.ErrorAttributes.ERROR, true);
            span.setAttribute(TracingConstants.ErrorAttributes.ERROR_TYPE, error.getClass().getSimpleName());
            span.setAttribute(TracingConstants.ErrorAttributes.ERROR_MESSAGE, error.getMessage());
            
            log.debug("设置Span错误状态: {} (spanId: {})", 
                    error.getMessage(), span.getSpanContext().getSpanId());
        } catch (Exception e) {
            log.debug("设置Span错误状态失败", e);
        }
    }
    
    /**
     * 设置错误状态
     * 
     * @param span 目标Span
     * @param errorMessage 错误消息
     */
    public void setError(Span span, String errorMessage) {
        setError(span, errorMessage, null);
    }
    
    /**
     * 设置错误状态
     * 
     * @param span 目标Span
     * @param errorMessage 错误消息
     * @param errorCode 错误代码
     */
    public void setError(Span span, String errorMessage, String errorCode) {
        if (span == null || !span.getSpanContext().isValid()) {
            return;
        }
        
        try {
            span.setStatus(StatusCode.ERROR, errorMessage);
            
            // 设置错误相关属性
            span.setAttribute(TracingConstants.ErrorAttributes.ERROR, true);
            span.setAttribute(TracingConstants.ErrorAttributes.ERROR_MESSAGE, errorMessage);
            
            if (errorCode != null) {
                span.setAttribute(TracingConstants.ErrorAttributes.ERROR_CODE, errorCode);
            }
            
            log.debug("设置Span错误状态: {} (code: {}, spanId: {})", 
                    errorMessage, errorCode, span.getSpanContext().getSpanId());
        } catch (Exception e) {
            log.debug("设置Span错误状态失败", e);
        }
    }
    
    /**
     * 设置业务错误状态
     * 
     * @param span 目标Span
     * @param businessErrorCode 业务错误代码
     * @param businessErrorMessage 业务错误消息
     */
    public void setBusinessError(Span span, String businessErrorCode, String businessErrorMessage) {
        if (span == null || !span.getSpanContext().isValid()) {
            return;
        }
        
        try {
            span.setStatus(StatusCode.ERROR, businessErrorMessage);
            
            // 设置业务错误相关属性
            span.setAttribute(TracingConstants.ErrorAttributes.ERROR, true);
            span.setAttribute(TracingConstants.ErrorAttributes.ERROR_TYPE, "BusinessError");
            span.setAttribute(TracingConstants.ErrorAttributes.ERROR_CODE, businessErrorCode);
            span.setAttribute(TracingConstants.ErrorAttributes.ERROR_MESSAGE, businessErrorMessage);
            span.setAttribute("business.error", true);
            
            log.debug("设置Span业务错误状态: {} - {} (spanId: {})", 
                    businessErrorCode, businessErrorMessage, span.getSpanContext().getSpanId());
        } catch (Exception e) {
            log.debug("设置Span业务错误状态失败", e);
        }
    }
    
    /**
     * 设置超时状态
     * 
     * @param span 目标Span
     * @param timeoutMs 超时时间（毫秒）
     */
    public void setTimeout(Span span, long timeoutMs) {
        if (span == null || !span.getSpanContext().isValid()) {
            return;
        }
        
        try {
            span.setStatus(StatusCode.ERROR, "Operation timeout");
            
            // 设置超时相关属性
            span.setAttribute(TracingConstants.ErrorAttributes.ERROR, true);
            span.setAttribute(TracingConstants.ErrorAttributes.ERROR_TYPE, "TimeoutError");
            span.setAttribute(TracingConstants.ErrorAttributes.ERROR_MESSAGE, "Operation timeout");
            span.setAttribute("timeout.ms", timeoutMs);
            span.setAttribute("timeout", true);
            
            log.debug("设置Span超时状态: {}ms (spanId: {})", 
                    timeoutMs, span.getSpanContext().getSpanId());
        } catch (Exception e) {
            log.debug("设置Span超时状态失败", e);
        }
    }
    
    /**
     * 设置限流状态
     * 
     * @param span 目标Span
     * @param rateLimited 是否被限流
     */
    public void setRateLimited(Span span, boolean rateLimited) {
        if (span == null || !span.getSpanContext().isValid()) {
            return;
        }
        
        try {
            if (rateLimited) {
                span.setStatus(StatusCode.ERROR, "Rate limited");
                span.setAttribute(TracingConstants.ErrorAttributes.ERROR, true);
                span.setAttribute(TracingConstants.ErrorAttributes.ERROR_TYPE, "RateLimitError");
                span.setAttribute(TracingConstants.ErrorAttributes.ERROR_MESSAGE, "Request rate limited");
            }
            
            span.setAttribute("rate_limited", rateLimited);
            
            log.debug("设置Span限流状态: {} (spanId: {})", 
                    rateLimited, span.getSpanContext().getSpanId());
        } catch (Exception e) {
            log.debug("设置Span限流状态失败", e);
        }
    }
    
    /**
     * 设置熔断状态
     * 
     * @param span 目标Span
     * @param circuitOpen 熔断器是否开启
     */
    public void setCircuitBreakerOpen(Span span, boolean circuitOpen) {
        if (span == null || !span.getSpanContext().isValid()) {
            return;
        }
        
        try {
            if (circuitOpen) {
                span.setStatus(StatusCode.ERROR, "Circuit breaker open");
                span.setAttribute(TracingConstants.ErrorAttributes.ERROR, true);
                span.setAttribute(TracingConstants.ErrorAttributes.ERROR_TYPE, "CircuitBreakerError");
                span.setAttribute(TracingConstants.ErrorAttributes.ERROR_MESSAGE, "Circuit breaker is open");
            }
            
            span.setAttribute("circuit_breaker_open", circuitOpen);
            
            log.debug("设置Span熔断状态: {} (spanId: {})", 
                    circuitOpen, span.getSpanContext().getSpanId());
        } catch (Exception e) {
            log.debug("设置Span熔断状态失败", e);
        }
    }
    
    /**
     * 设置自定义状态
     * 
     * @param span 目标Span
     * @param statusCode 状态码
     * @param description 状态描述
     */
    public void setCustomStatus(Span span, StatusCode statusCode, String description) {
        if (span == null || !span.getSpanContext().isValid()) {
            return;
        }
        
        try {
            span.setStatus(statusCode, description);
            
            log.debug("设置Span自定义状态: {} - {} (spanId: {})", 
                    statusCode, description, span.getSpanContext().getSpanId());
        } catch (Exception e) {
            log.debug("设置Span自定义状态失败", e);
        }
    }
    
    /**
     * 将HTTP状态码映射到Span状态码
     */
    private StatusCode mapHttpStatusToSpanStatus(HttpStatus httpStatus) {
        if (httpStatus.is2xxSuccessful()) {
            return StatusCode.OK;
        } else if (httpStatus.is4xxClientError() || httpStatus.is5xxServerError()) {
            return StatusCode.ERROR;
        } else {
            // 1xx, 3xx 等其他状态码
            return StatusCode.UNSET;
        }
    }
}