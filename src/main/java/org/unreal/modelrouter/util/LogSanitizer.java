package org.unreal.modelrouter.util;

/**
 * 日志清理工具类
 * 用于防止日志注入攻击，清理日志参数中的危险字符
 */
public final class LogSanitizer {

    private LogSanitizer() {
        // 工具类，禁止实例化
    }

    /**
     * 清理日志参数，移除可能导致日志注入的字符
     * 
     * @param input 输入字符串
     * @return 清理后的字符串
     */
    public static String sanitize(Object input) {
        if (input == null) {
            return "null";
        }
        
        String str = input.toString();
        if (str.isEmpty()) {
            return str;
        }
        
        // 移除回车换行符和其他控制字符
        return str.replaceAll("[\r\n\t]", "_")
                  .replaceAll("[\u0000-\u001f\u007f-\u009f]", "_");
    }

    /**
     * 清理多个日志参数
     * 
     * @param inputs 输入参数数组
     * @return 清理后的参数数组
     */
    public static Object[] sanitize(Object... inputs) {
        if (inputs == null) {
            return new Object[0];
        }
        
        Object[] sanitized = new Object[inputs.length];
        for (int i = 0; i < inputs.length; i++) {
            sanitized[i] = sanitize(inputs[i]);
        }
        return sanitized;
    }

    /**
     * 安全的字符串格式化，用于日志输出
     * 
     * @param format 格式字符串
     * @param args 参数
     * @return 格式化后的安全字符串
     */
    public static String format(String format, Object... args) {
        if (format == null) {
            return "null";
        }
        
        try {
            Object[] sanitizedArgs = sanitize(args);
            return String.format(format, sanitizedArgs);
        } catch (Exception e) {
            // 如果格式化失败，返回安全的错误信息
            return "Log formatting error: " + sanitize(e.getMessage());
        }
    }
}