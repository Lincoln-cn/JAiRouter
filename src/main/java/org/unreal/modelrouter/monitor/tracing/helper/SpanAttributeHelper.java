package org.unreal.modelrouter.monitor.tracing.helper;

import io.opentelemetry.api.trace.Span;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;

import java.util.HashMap;
import java.util.Map;

/**
 * Span 属性设置辅助类
 *
 * 提供统一的 Span 属性设置功能，包括：
 * - HTTP 请求属性设置
 * - HTTP 响应属性设置
 * - 客户端 IP 提取
 * - 请求属性提取
 *
 * @author JAiRouter Team
 * @since 2.27.0
 */
@Slf4j
@Component
public class SpanAttributeHelper {

    /**
     * 设置 HTTP 请求相关属性到 Span
     *
     * @param span OpenTelemetry Span
     * @param request HTTP 请求
     */
    public void setHttpAttributes(final Span span, final ServerHttpRequest request) {
        try {
            // HTTP 方法
            if (request.getMethod() != null) {
                span.setAttribute("http.method", request.getMethod().name());
            }

            // URL 相关
            span.setAttribute("http.url", request.getURI().toString());
            span.setAttribute("http.scheme", request.getURI().getScheme());
            span.setAttribute("http.host", request.getURI().getHost());
            span.setAttribute("http.target", request.getPath().value());

            // 客户端信息
            String clientIp = getClientIp(request);
            if (clientIp != null) {
                span.setAttribute("http.client_ip", clientIp);
            }

            String userAgent = request.getHeaders().getFirst("User-Agent");
            if (userAgent != null) {
                span.setAttribute("http.user_agent", userAgent);
            }

            // 请求大小
            String contentLength = request.getHeaders().getFirst("Content-Length");
            if (contentLength != null) {
                try {
                    span.setAttribute("http.request_content_length", Long.parseLong(contentLength));
                } catch (NumberFormatException e) {
                    // 忽略无效的 Content-Length
                }
            }
        } catch (Exception e) {
            log.debug("设置 HTTP 请求属性时发生错误", e);
        }
    }

    /**
     * 设置 HTTP 响应相关属性到 Span
     *
     * @param span OpenTelemetry Span
     * @param exchange Web 交换对象
     * @param duration 请求处理时长（毫秒）
     */
    public void setResponseAttributes(final Span span, final ServerWebExchange exchange, final long duration) {
        try {
            // 响应状态码
            if (exchange.getResponse().getStatusCode() != null) {
                span.setAttribute("http.status_code", exchange.getResponse().getStatusCode().value());
            }

            // 响应时间
            span.setAttribute("http.response_time_ms", duration);

            // 安全地获取响应大小，避免修改 headers
            try {
                long contentLength = exchange.getResponse().getHeaders().getContentLength();
                if (contentLength > 0) {
                    span.setAttribute("http.response_content_length", contentLength);
                }
            } catch (Exception e) {
                log.debug("获取响应大小时发生错误", e);
            }
        } catch (Exception e) {
            log.debug("设置 HTTP 响应属性时发生错误", e);
        }
    }

    /**
     * 获取客户端 IP 地址
     *
     * @param request HTTP 请求
     * @return 客户端 IP 地址，如果无法获取则返回 null
     */
    public String getClientIp(final ServerHttpRequest request) {
        // 检查 X-Forwarded-For 头部
        String xForwardedFor = request.getHeaders().getFirst("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            // 取第一个 IP 地址
            return xForwardedFor.split(",")[0].trim();
        }

        // 检查 X-Real-IP 头部
        String xRealIp = request.getHeaders().getFirst("X-Real-IP");
        if (xRealIp != null && !xRealIp.isEmpty()) {
            return xRealIp;
        }

        // 使用远程地址
        if (request.getRemoteAddress() != null) {
            return request.getRemoteAddress().getAddress().getHostAddress();
        }

        return null;
    }

    /**
     * 从 HTTP 请求提取属性映射
     *
     * @param request HTTP 请求
     * @return 属性映射
     */
    public Map<String, Object> extractRequestAttributes(final ServerHttpRequest request) {
        Map<String, Object> attributes = new HashMap<>();

        // HTTP 相关属性
        if (request.getMethod() != null) {
            attributes.put("http.method", request.getMethod().name());
        }
        attributes.put("http.url", request.getURI().toString());
        attributes.put("http.path", request.getPath().value());

        if (request.getURI().getScheme() != null) {
            attributes.put("http.scheme", request.getURI().getScheme());
        }
        if (request.getURI().getHost() != null) {
            attributes.put("http.host", request.getURI().getHost());
        }

        // 客户端信息
        String clientIp = getClientIp(request);
        if (clientIp != null) {
            attributes.put("http.client_ip", clientIp);
        }

        String userAgent = request.getHeaders().getFirst("User-Agent");
        if (userAgent != null) {
            attributes.put("http.user_agent", userAgent);
        }

        // 请求大小
        String contentLength = request.getHeaders().getFirst("Content-Length");
        if (contentLength != null) {
            try {
                attributes.put("http.request_content_length", Long.parseLong(contentLength));
            } catch (NumberFormatException e) {
                // 忽略无效的 Content-Length
            }
        }

        return attributes;
    }

    /**
     * 从 Web 交换对象提取完整的属性映射（包括请求和响应）
     *
     * @param exchange Web 交换对象
     * @return 属性映射
     */
    public Map<String, Object> extractExchangeAttributes(final ServerWebExchange exchange) {
        Map<String, Object> attributes = extractRequestAttributes(exchange.getRequest());

        // 响应状态码
        if (exchange.getResponse().getStatusCode() != null) {
            attributes.put("http.status_code", exchange.getResponse().getStatusCode().value());
        }

        // 响应大小
        if (exchange.getResponse().getHeaders().getContentLength() > 0) {
            attributes.put("http.response_content_length",
                    exchange.getResponse().getHeaders().getContentLength());
        }

        return attributes;
    }
}