package org.unreal.modelrouter.router.adapter.test;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import org.unreal.modelrouter.router.adapter.AdapterRegistry;
import org.unreal.modelrouter.router.adapter.ServiceCapability;
import reactor.core.publisher.Mono;

import java.net.ConnectException;
import java.net.UnknownHostException;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeoutException;

/**
 * 适配器测试服务
 * 提供适配器连通性验证功能
 */
@Service
public class AdapterTestService {

    private static final Logger logger = LoggerFactory.getLogger(AdapterTestService.class);
    private static final Duration TIMEOUT = Duration.ofSeconds(10);

    private final WebClient.Builder webClientBuilder;
    private final AdapterRegistry adapterRegistry;

    public AdapterTestService(final WebClient.Builder webClientBuilder,
                              final AdapterRegistry adapterRegistry) {
        this.webClientBuilder = webClientBuilder;
        this.adapterRegistry = adapterRegistry;
    }

    /**
     * PING 测试：验证连通性和认证
     * 向目标 API 发送 GET /models 请求
     */
    public Mono<AdapterTestResult> testPing(final String baseUrl,
                                            final String authHeaderName,
                                            final String authHeaderValue) {
        long startTime = System.currentTimeMillis();
        String url = baseUrl.endsWith("/") ? baseUrl + "models" : baseUrl + "/models";

        WebClient client = webClientBuilder.baseUrl(baseUrl).build();

        return client.get()
                .uri("/models")
                .header(authHeaderName != null ? authHeaderName : "Authorization",
                        authHeaderValue != null ? authHeaderValue : "")
                .retrieve()
                .toBodilessEntity()
                .map(response -> {
                    long latency = System.currentTimeMillis() - startTime;
                    AdapterTestResult result = AdapterTestResult.connected(latency,
                            "成功连接，延迟 " + latency + "ms");
                    result.setHttpStatusCode(response.getStatusCode().value());
                    return result;
                })
                .onErrorResume(WebClientResponseException.class, ex -> {
                    long latency = System.currentTimeMillis() - startTime;
                    int statusCode = ex.getStatusCode().value();
                    if (statusCode == 401 || statusCode == 403) {
                        return Mono.just(AdapterTestResult.authFailed(latency,
                                "认证失败 (" + statusCode + ")，请检查 API Key"));
                    }
                    return Mono.just(AdapterTestResult.error(
                            "请求失败 (" + statusCode + "): " + ex.getMessage()));
                })
                .onErrorResume(TimeoutException.class, ex ->
                        Mono.just(AdapterTestResult.timeout("连接超时，请检查网络或 Base URL")))
                .onErrorResume(UnknownHostException.class, ex ->
                        Mono.just(AdapterTestResult.error("DNS 解析失败: " + ex.getMessage())))
                .onErrorResume(ConnectException.class, ex ->
                        Mono.just(AdapterTestResult.error("连接被拒绝: " + ex.getMessage())))
                .onErrorResume(ex -> {
                    logger.warn("PING 测试异常: {}", ex.getMessage());
                    return Mono.just(AdapterTestResult.error("测试异常: " + ex.getMessage()));
                })
                .timeout(TIMEOUT, Mono.just(AdapterTestResult.timeout("连接超时（10秒）")));
    }

    /**
     * CHAT 测试：发送最小聊天请求验证完整链路
     */
    public Mono<AdapterTestResult> testChat(final String baseUrl,
                                            final String authHeaderName,
                                            final String authHeaderValue,
                                            final String model) {
        long startTime = System.currentTimeMillis();

        String chatUrl = baseUrl.endsWith("/") ? baseUrl + "chat/completions" : baseUrl + "/chat/completions";

        WebClient client = webClientBuilder.baseUrl(baseUrl).build();

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("model", model);
        requestBody.put("messages", new Object[]{
                Map.of("role", "user", "content", "Hi")
        });
        requestBody.put("max_tokens", 5);

        return client.post()
                .uri("/chat/completions")
                .header(authHeaderName != null ? authHeaderName : "Authorization",
                        authHeaderValue != null ? authHeaderValue : "")
                .bodyValue(requestBody)
                .retrieve()
                .bodyToMono(String.class)
                .map(responseBody -> {
                    long latency = System.currentTimeMillis() - startTime;
                    AdapterTestResult result = AdapterTestResult.connected(latency,
                            "成功连接并获取响应，延迟 " + latency + "ms");
                    Map<String, Object> details = new HashMap<>();
                    details.put("responsePreview",
                            responseBody.length() > 200 ? responseBody.substring(0, 200) : responseBody);
                    result.setDetails(details);
                    return result;
                })
                .onErrorResume(WebClientResponseException.class, ex -> {
                    long latency = System.currentTimeMillis() - startTime;
                    int statusCode = ex.getStatusCode().value();
                    if (statusCode == 401 || statusCode == 403) {
                        return Mono.just(AdapterTestResult.authFailed(latency,
                                "认证失败 (" + statusCode + ")，请检查 API Key"));
                    }
                    if (statusCode == 404) {
                        return Mono.just(AdapterTestResult.error(
                                "模型不存在或 API 路径错误 (" + statusCode + ")"));
                    }
                    return Mono.just(AdapterTestResult.error(
                            "请求失败 (" + statusCode + "): " + ex.getMessage()));
                })
                .onErrorResume(TimeoutException.class, ex ->
                        Mono.just(AdapterTestResult.timeout("请求超时，请检查网络或模型负载")))
                .onErrorResume(ex -> {
                    logger.warn("CHAT 测试异常: {}", ex.getMessage());
                    return Mono.just(AdapterTestResult.error("测试异常: " + ex.getMessage()));
                })
                .timeout(TIMEOUT, Mono.just(AdapterTestResult.timeout("请求超时（10秒）")));
    }

    /**
     * 测试已注册的适配器
     */
    public Mono<AdapterTestResult> testRegisteredAdapter(final String adapterName,
                                                          final String testType,
                                                          final String apiKey,
                                                          final String model,
                                                          final String baseUrl) {
        if (!adapterRegistry.isAdapterSupported(adapterName)) {
            throw new IllegalArgumentException("适配器不存在: " + adapterName);
        }

        // 获取适配器的配置信息
        ServiceCapability adapter = adapterRegistry.getAdapterByName(adapterName);
        String effectiveBaseUrl = baseUrl;

        // 如果没有提供 baseUrl，尝试从适配器配置获取
        if (effectiveBaseUrl == null || effectiveBaseUrl.isBlank()) {
            // 默认使用 localhost 作为 fallback
            effectiveBaseUrl = "http://localhost:8080";
        }

        String authHeader = apiKey != null && !apiKey.isBlank() ? "Bearer " + apiKey : "";

        if ("CHAT".equalsIgnoreCase(testType)) {
            return testChat(effectiveBaseUrl, "Authorization", authHeader, model);
        }
        return testPing(effectiveBaseUrl, "Authorization", authHeader);
    }

    /**
     * 预览测试（未注册的适配器配置）
     */
    public Mono<AdapterTestResult> testPreview(final String type,
                                                final String baseUrl,
                                                final String authHeaderName,
                                                final String authHeaderValue,
                                                final String testType,
                                                final String model) {
        if ("CHAT".equalsIgnoreCase(testType)) {
            return testChat(baseUrl, authHeaderName, authHeaderValue, model);
        }
        return testPing(baseUrl, authHeaderName, authHeaderValue);
    }
}
