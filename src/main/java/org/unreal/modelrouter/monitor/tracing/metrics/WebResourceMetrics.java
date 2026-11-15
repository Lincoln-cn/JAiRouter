package org.unreal.modelrouter.monitor.tracing.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 静态资源（前端页面、JS/CSS、图片）的请求指标（#1156）。
 *
 * <p><b>为什么不给常规静态资源建链路</b>：控制台一打开就是几十个静态请求，
 * 全部进 {@code traceStore} 会把真正的业务链路挤出去。维护者口径是
 * 「web 只做异常，常规的滚动就行」——所以这里只累指标，不建 trace；
 * 只有 4xx/5xx 才值得留一条链路（暴力扫描、前端链接失效）。</p>
 *
 * <p>指标口径：</p>
 * <ul>
 *   <li>{@code jairouter_web_requests_total{status_class}} —— 2xx/3xx/4xx/5xx 计数</li>
 *   <li>{@code jairouter_web_errors_total{status,path_class}} —— 异常按状态码与路径分类</li>
 *   <li>{@code jairouter_web_errors_by_client_total{client_hash}} —— 按客户端指纹，用于识别扫描</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WebResourceMetrics {

    private static final String REQUESTS_TOTAL = "jairouter_web_requests_total";
    private static final String ERRORS_TOTAL = "jairouter_web_errors_total";
    private static final String ERRORS_BY_CLIENT = "jairouter_web_errors_by_client_total";

    /** 同一客户端在同一路径上，一分钟内最多留下一条异常链路（防止扫描刷爆 traceStore）。 */
    private static final long ERROR_TRACE_WINDOW_MILLIS = 60_000L;

    private final MeterRegistry meterRegistry;

    /** key = clientHash + "|" + pathClass，value = 上次建链路的时间戳。 */
    private final Map<String, Long> lastErrorTraceAt = new ConcurrentHashMap<>();

    /**
     * 记录一次静态资源请求。
     *
     * @param exchange 当前交换（用于取状态码、路径与远端地址）
     * @return {@code true} 表示这次异常值得建一条链路；{@code false} 表示只记指标
     */
    public boolean record(final ServerWebExchange exchange) {
        final int status = statusOf(exchange);
        final String statusClass = statusClass(status);
        Counter.builder(REQUESTS_TOTAL)
            .description("静态资源请求数（按状态类别）")
            .tag("status_class", statusClass)
            .register(meterRegistry)
            .increment();

        if (status < 400) {
            return false;
        }

        final String path = exchange.getRequest().getPath().value();
        final String pathClass = pathClass(path);
        final String clientHash = clientHash(exchange);

        Counter.builder(ERRORS_TOTAL)
            .description("静态资源异常请求数（按状态码与路径分类）")
            .tags("status", String.valueOf(status), "path_class", pathClass)
            .register(meterRegistry)
            .increment();
        Counter.builder(ERRORS_BY_CLIENT)
            .description("静态资源异常请求数（按客户端指纹）")
            .tag("client_hash", clientHash)
            .register(meterRegistry)
            .increment();

        final long now = System.currentTimeMillis();
        final String key = clientHash + "|" + pathClass;
        final Long last = lastErrorTraceAt.get(key);
        if (last != null && now - last < ERROR_TRACE_WINDOW_MILLIS) {
            return false;
        }
        lastErrorTraceAt.put(key, now);
        if (lastErrorTraceAt.size() > 5_000) {
            lastErrorTraceAt.clear();
            lastErrorTraceAt.put(key, now);
        }
        return true;
    }

    private static int statusOf(final ServerWebExchange exchange) {
        var statusCode = exchange.getResponse().getStatusCode();
        return statusCode != null ? statusCode.value() : 200;
    }

    private static String statusClass(final int status) {
        if (status < 300) {
            return "2xx";
        }
        if (status < 400) {
            return "3xx";
        }
        return status < 500 ? "4xx" : "5xx";
    }

    /**
     * 路径归一：把带哈希/数字的段替换成通配，避免每个静态文件都变成一个指标维度。
     * 例：{@code /admin/assets/index-a1b2c3.js} → {@code /admin/assets/:file}
     */
    private static String pathClass(final String path) {
        if (path == null || path.isEmpty()) {
            return "/";
        }
        final String[] parts = path.split("/");
        final StringBuilder sb = new StringBuilder();
        for (int i = 0; i < parts.length; i++) {
            if (parts[i].isEmpty()) {
                continue;
            }
            final String segment = parts[i];
            final boolean last = i == parts.length - 1;
            sb.append('/');
            // 只有多段路径的最后一段才当文件名；单段的 /wp-admin.php 这类要保留原文，
            // 它本身就是扫描探测的目标，归一成 :file 就看不出来了
            if (last && segment.contains(".") && i > 1) {
                // 带扩展名的静态文件：保留目录、丢掉文件名（文件名通常带哈希）
                sb.append(":file");
            } else if (segment.matches(".*[0-9].*") && segment.length() > 12) {
                sb.append(":id");
            } else {
                sb.append(segment);
            }
        }
        return sb.length() == 0 ? "/" : sb.toString();
    }

    private static String clientHash(final ServerWebExchange exchange) {
        final InetSocketAddress remote = exchange.getRequest().getRemoteAddress();
        final String raw = remote != null && remote.getAddress() != null
            ? remote.getAddress().getHostAddress() : "unknown";
        try {
            final MessageDigest digest = MessageDigest.getInstance("SHA-256");
            final byte[] hash = digest.digest(raw.getBytes(StandardCharsets.UTF_8));
            final StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 6; i++) {
                sb.append(String.format("%02x", hash[i]));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            return "unknown";
        }
    }
}
