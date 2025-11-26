import java.util.*;

public class TestInstanceId {
    public static void main(String[] args) {
        // 模拟配置文件中的实例
        Map<String, Object> instanceFromConfig = new HashMap<>();
        instanceFromConfig.put("name", "nomic-embed-text-v1.5");
        instanceFromConfig.put("base-url", "http://172.16.30.6:9090");  // 注意这里是base-url而不是baseUrl
        instanceFromConfig.put("path", "/v1/embeddings");
        instanceFromConfig.put("weight", 1);
        
        // 构建实例ID的方式1（使用base-url）
        String instanceId1 = instanceFromConfig.get("name") + "@" + instanceFromConfig.get("base-url");
        System.out.println("使用base-url构建的实例ID: " + instanceId1);
        
        // 构建实例ID的方式2（使用baseUrl）
        String instanceId2 = instanceFromConfig.get("name") + "@" + instanceFromConfig.get("baseUrl");
        System.out.println("使用baseUrl构建的实例ID: " + instanceId2);
        
        // 请求中的实例ID
        String requestId = "nomic-embed-text-v1.5@http://172.16.30.6:9090";
        System.out.println("请求中的实例ID: " + requestId);
        
        // 比较结果
        System.out.println("instanceId1.equals(requestId): " + instanceId1.equals(requestId));
        System.out.println("instanceId2.equals(requestId): " + instanceId2.equals(requestId));
    }
}