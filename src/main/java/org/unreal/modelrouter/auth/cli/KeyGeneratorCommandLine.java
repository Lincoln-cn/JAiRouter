package org.unreal.modelrouter.auth.cli;

import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import org.unreal.modelrouter.auth.security.util.SecretKeyGenerator;
import org.unreal.modelrouter.auth.security.util.SecretKeyValidator;

/**
 * 密钥生成命令行工具
 * 
 * 使用方式：
 * java -jar jairouter.jar --generate-key
 * java -jar jairouter.jar --generate-api-token
 * java -jar jairouter.jar --generate-password
 */
@Component
public class KeyGeneratorCommandLine implements CommandLineRunner {

    private static final String ANSI_RESET = "\u001B[0m";
    private static final String ANSI_GREEN = "\u001B[32m";
    private static final String ANSI_YELLOW = "\u001B[33m";
    private static final String ANSI_BLUE = "\u001B[34m";
    private static final String ANSI_CYAN = "\u001B[36m";

    @Override
    public void run(final String... args) throws Exception {
        // 检查是否有生成密钥的参数
        if (args == null || args.length == 0) {
            return;
        }

        boolean shouldRun = false;
        for (String arg : args) {
            if (arg.equals("--generate-key") || 
                arg.equals("--generate-api-token") || 
                arg.equals("--generate-password")) {
                shouldRun = true;
                break;
            }
        }

        if (!shouldRun) {
            return;
        }

        printHeader();

        for (String arg : args) {
            switch (arg) {
                case "--generate-key":
                    generateJwtKey();
                    break;
                case "--generate-api-token":
                    generateApiToken();
                    break;
                case "--generate-password":
                    generatePassword();
                    break;
            }
        }

        printFooter();
        
        // 如果是密钥生成模式，退出应用
        System.exit(0);
    }

    /**
     * 生成 JWT 密钥
     */
    private void generateJwtKey() {
        System.out.println(ANSI_CYAN + "\n【JWT 密钥生成】" + ANSI_RESET);
        System.out.println(ANSI_BLUE + "Base64 编码（推荐，适用于 JWT HS256）：" + ANSI_RESET);
        
        String base64Key = SecretKeyGenerator.generateBase64Key(64);
        printKeyValue(base64Key);
        
        System.out.println(ANSI_BLUE + "\n十六进制编码：" + ANSI_RESET);
        String hexKey = SecretKeyGenerator.generateHexKey(64);
        printKeyValue(hexKey);
        
        System.out.println(ANSI_YELLOW + "\n使用建议：" + ANSI_RESET);
        System.out.println("  export JWT_SECRET=\"" + base64Key + "\"");
        
        // 验证生成的密钥强度
        var result = SecretKeyValidator.validateJwtSecret(base64Key);
        System.out.println(ANSI_GREEN + "  密钥强度：" + result.getStrengthLevel().getDisplayName() + ANSI_RESET);
    }

    /**
     * 生成 API Token
     */
    private void generateApiToken() {
        System.out.println(ANSI_CYAN + "\n【API Token 生成】" + ANSI_RESET);
        
        String token = SecretKeyGenerator.generateApiToken();
        printKeyValue(token);
        
        System.out.println(ANSI_YELLOW + "\n使用示例：" + ANSI_RESET);
        System.out.println("  curl -H \"X-API-Key: " + token + "\" http://localhost:8080/api/...");
    }

    /**
     * 生成随机密码
     */
    private void generatePassword() {
        System.out.println(ANSI_CYAN + "\n【随机密码生成】" + ANSI_RESET);
        
        // 生成 3 个不同长度的密码
        int[] lengths = {16, 20, 24};
        
        for (int length : lengths) {
            String password = SecretKeyGenerator.generateAlphanumericKey(length);
            System.out.println(ANSI_BLUE + length + " 字符密码：" + ANSI_RESET);
            printKeyValue(password);
            
            var result = SecretKeyValidator.validatePassword(password);
            System.out.println(ANSI_GREEN + "  密码强度：" + result.getStrengthLevel().getDisplayName() + ANSI_RESET);
            System.out.println();
        }
        
        System.out.println(ANSI_YELLOW + "使用建议：" + ANSI_RESET);
        System.out.println("  export INITIAL_ADMIN_PASSWORD=\"<选择的密码>\"");
    }

    /**
     * 打印键值对
     */
    private void printKeyValue(final String value) {
        System.out.println(ANSI_GREEN + "  " + value + ANSI_RESET);
    }

    /**
     * 打印头部
     */
    private void printHeader() {
        System.out.println();
        System.out.println("╔══════════════════════════════════════════════════════════════════════════════╗");
        System.out.println("║                                                                              ║");
        System.out.println("║                         JAiRouter 密钥生成工具                               ║");
        System.out.println("║                                                                              ║");
        System.out.println("╚══════════════════════════════════════════════════════════════════════════════╝");
        System.out.println();
    }

    /**
     * 打印尾部
     */
    private void printFooter() {
        System.out.println(ANSI_YELLOW + "\n【环境变量设置】" + ANSI_RESET);
        System.out.println("将生成的密钥添加到环境变量：");
        System.out.println("  Linux/Mac: export VARIABLE_NAME=\"value\"");
        System.out.println("  Windows:   set VARIABLE_NAME=value");
        System.out.println();
        System.out.println(ANSI_YELLOW + "【Docker 使用】" + ANSI_RESET);
        System.out.println("  docker run -e VARIABLE_NAME=\"value\" ...");
        System.out.println();
        System.out.println(ANSI_YELLOW + "【Docker Compose 使用】" + ANSI_RESET);
        System.out.println("  environment:");
        System.out.println("    - VARIABLE_NAME=value");
        System.out.println();
    }
}
