package org.unreal.modelrouter.support;

import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * {@code TestRestTemplate} 的替代（Spring Boot 4.1 已删除该类）。
 *
 * <p>只实现本仓端到端测试用到的那一个方法：{@code exchange(url, method, entity, String.class)}，
 * 且同样**不把 4xx/5xx 当异常抛** —— 这些测试断言的就是「客户端看到的状态码与错误体」，
 * 抛异常会直接判红。
 *
 * <p>实现用 JDK 自带的 {@link HttpClient}，不引入新依赖；请求体与响应体都按 UTF-8 处理。
 *
 * @since 3.6.1
 */
public final class TestHttpClient {

    private final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();

    /**
     * 发一次请求并返回状态码、响应头、响应体。
     *
     * @param url    完整的绝对地址
     * @param method HTTP 方法
     * @param entity 请求实体（可空；体按 UTF-8 编码）
     * @param type   期望的响应体类型，目前只支持 {@link String}
     * @return 响应实体（4xx/5xx 同样正常返回，不抛异常）
     */
    public ResponseEntity<String> exchange(final String url, final HttpMethod method,
                                           final HttpEntity<String> entity, final Class<String> type) {
        final HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(30))
                .method(method.name(), bodyOf(entity));
        if (entity != null && entity.getHeaders() != null) {
            for (String name : entity.getHeaders().headerNames()) {
                for (String value : entity.getHeaders().getOrDefault(name, List.of())) {
                    builder.header(name, value);
                }
            }
        }
        try {
            final HttpResponse<String> response =
                    client.send(builder.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            final HttpHeaders headers = new HttpHeaders();
            response.headers().map().forEach(headers::put);
            return ResponseEntity.status(response.statusCode()).headers(headers).body(response.body());
        } catch (IOException ex) {
            throw new IllegalStateException("HTTP 请求失败: " + method + " " + url, ex);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("HTTP 请求被中断: " + method + " " + url, ex);
        }
    }

    /** 便捷方法：GET 并返回响应实体（对齐 TestRestTemplate 的同名方法）。 */
    public ResponseEntity<String> getForEntity(final String url, final Class<String> type) {
        return exchange(url, HttpMethod.GET, null, type);
    }

    private HttpRequest.BodyPublisher bodyOf(final HttpEntity<String> entity) {
        if (entity == null || entity.getBody() == null) {
            return HttpRequest.BodyPublishers.noBody();
        }
        return HttpRequest.BodyPublishers.ofString(entity.getBody(), StandardCharsets.UTF_8);
    }
}
