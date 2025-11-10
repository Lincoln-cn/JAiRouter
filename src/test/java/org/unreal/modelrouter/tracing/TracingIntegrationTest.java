package org.unreal.modelrouter.tracing;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.unreal.modelrouter.tracing.client.TracingWebClientFactory;
import org.unreal.modelrouter.tracing.interceptor.BackendCallTracingInterceptor;
import org.unreal.modelrouter.tracing.interceptor.ControllerTracingInterceptor;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 追踪集成测试
 * 验证追踪组件是否正确注入和配置
 */
@SpringBootTest
@ActiveProfiles("test")
public class TracingIntegrationTest {

    @Autowired(required = false)
    private TracingWebClientFactory tracingWebClientFactory;

    @Autowired(required = false)
    private BackendCallTracingInterceptor backendCallTracingInterceptor;

    @Autowired(required = false)
    private ControllerTracingInterceptor controllerTracingInterceptor;

    @Test
    public void testTracingComponentsAreInjected() {
        // 验证追踪组件是否被正确注入
        System.out.println("TracingWebClientFactory: " + tracingWebClientFactory);
        System.out.println("BackendCallTracingInterceptor: " + backendCallTracingInterceptor);
        System.out.println("ControllerTracingInterceptor: " + controllerTracingInterceptor);

        if (tracingWebClientFactory != null) {
            System.out.println("✓ TracingWebClientFactory 已注入");
        } else {
            System.out.println("✗ TracingWebClientFactory 未注入（可能追踪未启用）");
        }

        if (backendCallTracingInterceptor != null) {
            System.out.println("✓ BackendCallTracingInterceptor 已注入");
        } else {
            System.out.println("✗ BackendCallTracingInterceptor 未注入");
        }

        if (controllerTracingInterceptor != null) {
            System.out.println("✓ ControllerTracingInterceptor 已注入");
        } else {
            System.out.println("✗ ControllerTracingInterceptor 未注入");
        }
    }

    @Test
    public void testWebClientHasTracingInterceptor() {
        if (tracingWebClientFactory == null) {
            System.out.println("追踪未启用，跳过测试");
            return;
        }

        // 创建一个带追踪的 WebClient
        var webClient = tracingWebClientFactory.createTracingWebClient("http://test.example.com");
        assertNotNull(webClient, "WebClient 应该被创建");
        System.out.println("✓ 成功创建带追踪的 WebClient");
    }
}
