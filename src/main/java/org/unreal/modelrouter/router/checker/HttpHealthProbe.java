package org.unreal.modelrouter.router.checker;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * HTTP 探活（v3.5.4，#650）。
 *
 * <p>TCP 只能证明端口能连上：进程僵死、鉴权配置错、上游 5xx 都仍会被判「健康」。
 * 开启 {@code jairouter.health-check.mode=http} 后会真正请求一次实例路径，
 * 判定规则是「能拿到响应且状态码 &lt; 500」——4xx 说明网关链路是通的（鉴权/参数问题不是实例可用性问题），
 * 其中 404 / 405 也算健康（端点不存在或方法不允许，但进程在处理请求），5xx 与连接/超时才判不健康。</p>
 *
 * <p>#833 起该模式成为默认：默认请求 {@code /v1/models}，可用
 * {@code jairouter.health-check.http-path} 改成上游自己的健康端点。</p>
 *
 * @since v3.5.4
 */
public class HttpHealthProbe {

    /**
     * 一次探活的结果。
     *
     * @param healthy 是否健康
     * @param detail  写进日志/数据库错误字段的说明
     */
    public record Result(boolean healthy, String detail) {
    }

    private final HttpClient client;

    public HttpHealthProbe(final int timeoutMs) {
        this.client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(timeoutMs))
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
    }

    /**
     * 对 {@code baseUrl + path} 发起一次 GET。
     */
    public Result probe(final String baseUrl, final String path, final int timeoutMs) {
        HttpRequest request;
        try {
            request = HttpRequest.newBuilder(join(baseUrl, path))
                    .timeout(Duration.ofMillis(timeoutMs))
                    .GET()
                    .build();
        } catch (IllegalArgumentException e) {
            return new Result(false, "无效的 base-url: " + baseUrl);
        }

        try {
            HttpResponse<Void> response = client.send(request, HttpResponse.BodyHandlers.discarding());
            int status = response.statusCode();
            if (status < 500) {
                return new Result(true, "HTTP " + status);
            }
            return new Result(false, "HTTP " + status);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new Result(false, "HTTP 探活被中断");
        } catch (IOException | RuntimeException e) {
            // #604：只捕获可预期的失败（连接被拒 / 超时 / 协议错），其余异常继续冒泡
            return new Result(false, "HTTP 探活失败: " + e.getClass().getSimpleName()
                    + (e.getMessage() == null ? "" : " " + e.getMessage()));
        }
    }

    static URI join(final String baseUrl, final String path) {
        String base = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        if (path == null || path.isBlank()) {
            return URI.create(base);
        }
        String normalizedPath = path.startsWith("/") ? path : "/" + path;
        return URI.create(base + normalizedPath);
    }
}
