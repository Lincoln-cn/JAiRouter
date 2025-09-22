import java.util.UUID;

public class TestInstanceId {
    public static void main(String[] args) {
        // 测试UUID生成
        String uuid1 = UUID.randomUUID().toString();
        String uuid2 = UUID.randomUUID().toString();
        
        System.out.println("UUID 1: " + uuid1);
        System.out.println("UUID 2: " + uuid2);
        System.out.println("UUIDs are different: " + !uuid1.equals(uuid2));
        
        // 验证UUID格式
        System.out.println("UUID 1 is valid: " + isValidUUID(uuid1));
        System.out.println("UUID 2 is valid: " + isValidUUID(uuid2));
        
        // 测试实例ID生成方法
        String instanceId1 = buildInstanceId("model1", "http://server1.com");
        String instanceId2 = buildInstanceId("model2", "http://server2.com");
        
        System.out.println("Instance ID 1: " + instanceId1);
        System.out.println("Instance ID 2: " + instanceId2);
        System.out.println("Instance IDs are different: " + !instanceId1.equals(instanceId2));
    }
    
    private static boolean isValidUUID(String uuid) {
        try {
            UUID.fromString(uuid);
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
    
    public static String buildInstanceId(String moduleName, String baseUrl) {
        if (moduleName != null && baseUrl != null) {
            // 使用UUID生成唯一ID
            return java.util.UUID.randomUUID().toString();
        }
        return "unknown";
    }
}