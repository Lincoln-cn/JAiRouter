package org.unreal.modelrouter.api;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.unreal.modelrouter.dto.CreateServiceConfigRequest;
import org.unreal.modelrouter.dto.CreateServiceInstanceRequest;
import org.unreal.modelrouter.dto.ServiceConfigDTO;
import org.unreal.modelrouter.dto.ServiceInstanceDTO;

import java.time.Duration;

/**
 * API 集成测试
 * v1.5.4: 验证 API 可用性和数据存取
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = {
        "spring.security.enabled=false",
        "jairouter.security.api-key.enabled=false",
        "jairouter.security.jwt.enabled=false"
})
class ApiIntegrationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private WebTestClient webTestClient;

    @Test
    void testServiceConfigCrud() {
        // Create
        CreateServiceConfigRequest request = CreateServiceConfigRequest.builder()
                .serviceType("test-chat")
                .adapter("openai")
                .loadBalanceType("round-robin")
                .build();

        ServiceConfigDTO created = webTestClient.post()
                .uri("/api/services/test-chat")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus().isOk()
                .expectBody(ServiceConfigDTO.class)
                .returnResult()
                .getResponseBody();

        assert created != null;
        assert created.getServiceType().equals("test-chat");

        // Read
        webTestClient.get()
                .uri("/api/services/test-chat")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.serviceType").isEqualTo("test-chat");

        // Update
        request.setAdapter("anthropic");
        webTestClient.post()
                .uri("/api/services/test-chat")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.adapter").isEqualTo("anthropic");

        // Delete
        webTestClient.delete()
                .uri("/api/services/test-chat")
                .exchange()
                .expectStatus().isOk();

        // Verify deletion
        webTestClient.get()
                .uri("/api/services/test-chat")
                .exchange()
                .expectStatus().isNotFound();
    }

    @Test
    void testServiceInstanceCrud() {
        // First create a service config
        CreateServiceConfigRequest configRequest = CreateServiceConfigRequest.builder()
                .serviceType("test-service")
                .adapter("openai")
                .build();

        ServiceConfigDTO config = webTestClient.post()
                .uri("/api/services/test-service")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(configRequest)
                .exchange()
                .expectStatus().isOk()
                .expectBody(ServiceConfigDTO.class)
                .returnResult()
                .getResponseBody();

        Long serviceConfigId = config.getId();

        // Create instance
        CreateServiceInstanceRequest instanceRequest = CreateServiceInstanceRequest.builder()
                .name("test-instance")
                .baseUrl("http://localhost:8080")
                .path("/api")
                .weight(1)
                .build();

        ServiceInstanceDTO instance = webTestClient.post()
                .uri("/api/instances/service/{serviceConfigId}", serviceConfigId)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(instanceRequest)
                .exchange()
                .expectStatus().isOk()
                .expectBody(ServiceInstanceDTO.class)
                .returnResult()
                .getResponseBody();

        assert instance != null;
        assert instance.getName().equals("test-instance");
        Long instanceId = instance.getId();

        // Read instance
        webTestClient.get()
                .uri("/api/instances/{id}", instanceId)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.name").isEqualTo("test-instance");

        // Update instance
        instanceRequest.setName("updated-instance");
        webTestClient.put()
                .uri("/api/instances/{id}", instanceId)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(instanceRequest)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.name").isEqualTo("updated-instance");

        // Delete instance
        webTestClient.delete()
                .uri("/api/instances/{id}", instanceId)
                .exchange()
                .expectStatus().isOk();

        // Cleanup service config
        webTestClient.delete()
                .uri("/api/services/test-service")
                .exchange()
                .expectStatus().isOk();
    }

    @Test
    void testHealthEndpoint() {
        webTestClient.get()
                .uri("/actuator/health")
                .exchange()
                .expectStatus().isOk();
    }
}
