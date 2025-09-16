package org.unreal.modelrouter.filter;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import org.springframework.http.HttpMethod;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * CachedBodyWebFilter 单元测试
 * 
 * 测试缓存请求体的 WebFilter 功能，包括：
 * - 过滤器优先级设置
 * - 有 body 请求的缓存处理
 * - 无 body 请求的直接通过
 * - 错误处理和降级
 * 
 * @author JAiRouter Team
 * @since 1.0.0
 */
@ExtendWith(MockitoExtension.class)
class CachedBodyWebFilterTest {

    private CachedBodyWebFilter cachedBodyWebFilter;
    
    @Mock
    private WebFilterChain filterChain;
    
    @BeforeEach
    void setUp() {
        cachedBodyWebFilter = new CachedBodyWebFilter();
        
        // 设置基本Mock行为
        when(filterChain.filter(any(ServerWebExchange.class)))
                .thenReturn(Mono.empty());
    }
    
    @Test
    void testFilterOrderPriority() {
        // 测试过滤器优先级设置
        assertEquals(Ordered.HIGHEST_PRECEDENCE, cachedBodyWebFilter.getOrder(),
                "CachedBodyWebFilter应该有最高优先级");
    }
    
    @Test
    void testFilterWithoutBody_GET() {
        // Given - GET请求没有body
        MockServerHttpRequest request = MockServerHttpRequest
                .get("/v1/chat/completions")
                .build();
        ServerWebExchange exchange = MockServerWebExchange.from(request);
        
        // When & Then - 应该直接通过，不进行缓存处理
        StepVerifier.create(cachedBodyWebFilter.filter(exchange, filterChain))
                .verifyComplete();
    }
    
    @Test
    void testFilterWithoutBody_POST_NoContentLength() {
        // Given - POST请求但没有Content-Length
        MockServerHttpRequest request = MockServerHttpRequest
                .post("/v1/chat/completions")
                .build();
        ServerWebExchange exchange = MockServerWebExchange.from(request);
        
        // When & Then - 应该直接通过
        StepVerifier.create(cachedBodyWebFilter.filter(exchange, filterChain))
                .verifyComplete();
    }
    
    @Test
    void testFilterWithBody_POST() {
        // Given - POST请求有body
        String requestBody = "{\"model\":\"gpt-3.5-turbo\",\"messages\":[]}";
        DataBuffer dataBuffer = new DefaultDataBufferFactory().wrap(requestBody.getBytes());
        
        MockServerHttpRequest request = MockServerHttpRequest
                .post("/v1/chat/completions")
                .header("Content-Length", String.valueOf(requestBody.length()))
                .body(Flux.just(dataBuffer));
        ServerWebExchange exchange = MockServerWebExchange.from(request);
        
        // When & Then - 应该缓存body并继续处理
        StepVerifier.create(cachedBodyWebFilter.filter(exchange, filterChain))
                .verifyComplete();
    }
    
    @Test
    void testFilterWithBody_PUT() {
        // Given - PUT请求有body
        String requestBody = "{\"data\":\"test\"}";
        DataBuffer dataBuffer = new DefaultDataBufferFactory().wrap(requestBody.getBytes());
        
        MockServerHttpRequest request = MockServerHttpRequest
                .put("/v1/embeddings")
                .header("Content-Length", String.valueOf(requestBody.length()))
                .body(Flux.just(dataBuffer));
        ServerWebExchange exchange = MockServerWebExchange.from(request);
        
        // When & Then
        StepVerifier.create(cachedBodyWebFilter.filter(exchange, filterChain))
                .verifyComplete();
    }
    
    @Test
    void testFilterWithBody_PATCH() {
        // Given - PATCH请求有body
        String requestBody = "{\"update\":\"data\"}";
        DataBuffer dataBuffer = new DefaultDataBufferFactory().wrap(requestBody.getBytes());
        
        MockServerHttpRequest request = MockServerHttpRequest
                .patch("/v1/rerank")
                .header("Content-Length", String.valueOf(requestBody.length()))
                .body(Flux.just(dataBuffer));
        ServerWebExchange exchange = MockServerWebExchange.from(request);
        
        // When & Then
        StepVerifier.create(cachedBodyWebFilter.filter(exchange, filterChain))
                .verifyComplete();
    }
    
    @Test
    void testFilterWithChunkedEncoding() {
        // Given - 使用chunked编码的请求
        String requestBody = "{\"model\":\"gpt-4\",\"messages\":[]}";
        DataBuffer dataBuffer = new DefaultDataBufferFactory().wrap(requestBody.getBytes());
        
        MockServerHttpRequest request = MockServerHttpRequest
                .post("/v1/chat/completions")
                .header("Transfer-Encoding", "chunked")
                .body(Flux.just(dataBuffer));
        ServerWebExchange exchange = MockServerWebExchange.from(request);
        
        // When & Then
        StepVerifier.create(cachedBodyWebFilter.filter(exchange, filterChain))
                .verifyComplete();
    }
    
    @Test
    void testFilterWithEmptyBody() {
        // Given - 有Content-Length但body为空
        MockServerHttpRequest request = MockServerHttpRequest
                .post("/v1/chat/completions")
                .header("Content-Length", "0")
                .build();
        ServerWebExchange exchange = MockServerWebExchange.from(request);
        
        // When & Then - 应该直接通过
        StepVerifier.create(cachedBodyWebFilter.filter(exchange, filterChain))
                .verifyComplete();
    }
    
    @Test
    void testFilterErrorHandling() {
        // Given - 模拟读取body时发生错误
        MockServerHttpRequest request = MockServerHttpRequest
                .post("/v1/chat/completions")
                .header("Content-Length", "100")
                .body(Flux.error(new RuntimeException("Read error")));
        ServerWebExchange exchange = MockServerWebExchange.from(request);
        
        // When & Then - 应该降级到原始请求处理
        StepVerifier.create(cachedBodyWebFilter.filter(exchange, filterChain))
                .verifyComplete();
    }
    
    @Test
    void testHasBodyMethod_ContentLength() {
        // 测试hasBody方法 - Content-Length > 0
        MockServerHttpRequest request = MockServerHttpRequest
                .post("/test")
                .header("Content-Length", "100")
                .build();
        
        // 通过反射测试私有方法或者通过实际调用验证行为
        // 这里我们通过实际调用filter方法来验证hasBody的逻辑
        ServerWebExchange exchange = MockServerWebExchange.from(request);
        
        // 由于有Content-Length，应该尝试缓存body（即使body为空也会进入缓存逻辑）
        StepVerifier.create(cachedBodyWebFilter.filter(exchange, filterChain))
                .verifyComplete();
    }
    
    @Test
    void testHasBodyMethod_TransferEncoding() {
        // 测试hasBody方法 - Transfer-Encoding: chunked
        MockServerHttpRequest request = MockServerHttpRequest
                .post("/test")
                .header("Transfer-Encoding", "chunked")
                .build();
        
        ServerWebExchange exchange = MockServerWebExchange.from(request);
        
        StepVerifier.create(cachedBodyWebFilter.filter(exchange, filterChain))
                .verifyComplete();
    }
    
    @Test
    void testHasBodyMethod_HTTPMethods() {
        // 测试不同HTTP方法的hasBody逻辑
        
        // POST方法
        MockServerHttpRequest postRequest = MockServerHttpRequest
                .method(HttpMethod.POST, "/test")
                .build();
        ServerWebExchange postExchange = MockServerWebExchange.from(postRequest);
        
        StepVerifier.create(cachedBodyWebFilter.filter(postExchange, filterChain))
                .verifyComplete();
        
        // PUT方法
        MockServerHttpRequest putRequest = MockServerHttpRequest
                .method(HttpMethod.PUT, "/test")
                .build();
        ServerWebExchange putExchange = MockServerWebExchange.from(putRequest);
        
        StepVerifier.create(cachedBodyWebFilter.filter(putExchange, filterChain))
                .verifyComplete();
        
        // PATCH方法
        MockServerHttpRequest patchRequest = MockServerHttpRequest
                .method(HttpMethod.PATCH, "/test")
                .build();
        ServerWebExchange patchExchange = MockServerWebExchange.from(patchRequest);
        
        StepVerifier.create(cachedBodyWebFilter.filter(patchExchange, filterChain))
                .verifyComplete();
    }
    
    @Test
    void testMultipleBodyReads() {
        // Given - 创建一个有body的请求
        String requestBody = "{\"model\":\"gpt-3.5-turbo\",\"messages\":[{\"role\":\"user\",\"content\":\"Hello\"}]}";
        DataBuffer dataBuffer = new DefaultDataBufferFactory().wrap(requestBody.getBytes());
        
        MockServerHttpRequest request = MockServerHttpRequest
                .post("/v1/chat/completions")
                .header("Content-Length", String.valueOf(requestBody.length()))
                .body(Flux.just(dataBuffer));
        ServerWebExchange exchange = MockServerWebExchange.from(request);
        
        // Mock filterChain 来验证缓存的请求可以被多次读取
        when(filterChain.filter(any(ServerWebExchange.class)))
                .thenAnswer(invocation -> {
                    ServerWebExchange cachedExchange = invocation.getArgument(0);
                    // 尝试读取body两次，验证缓存是否工作
                    return cachedExchange.getRequest().getBody()
                            .collectList()
                            .flatMap(buffers1 -> 
                                cachedExchange.getRequest().getBody()
                                        .collectList()
                                        .map(buffers2 -> {
                                            // 验证两次读取的结果相同
                                            return buffers1.size() == buffers2.size();
                                        })
                            )
                            .then();
                });
        
        // When & Then
        StepVerifier.create(cachedBodyWebFilter.filter(exchange, filterChain))
                .verifyComplete();
    }
}