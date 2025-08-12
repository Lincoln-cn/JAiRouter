package org.unreal.modelrouter.util;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * 路径安全处理工具类
 * 用于防止路径遍历攻击，确保文件操作的安全性
 */
public final class PathSanitizer {

    private PathSanitizer() {
        // 工具类，禁止实例化
    }

    /**
     * 安全地构建文件路径，防止路径遍历攻击
     * 
     * @param basePath 基础路径
     * @param fileName 文件名
     * @return 安全的文件对象
     * @throws SecurityException 如果检测到路径遍历攻击
     */
    public static File createSafeFile(String basePath, String fileName) {
        if (basePath == null || fileName == null) {
            throw new IllegalArgumentException("Base path and file name cannot be null");
        }
        
        // 清理文件名，移除危险字符
        String sanitizedFileName = sanitizeFileName(fileName);
        
        // 创建基础路径
        File baseDir = new File(basePath).getAbsoluteFile();
        File targetFile = new File(baseDir, sanitizedFileName).getAbsoluteFile();
        
        // 检查目标文件是否在基础目录内
        if (!isWithinDirectory(baseDir, targetFile)) {
            throw new SecurityException("Path traversal attempt detected: " + fileName);
        }
        
        return targetFile;
    }

    /**
     * 安全地构建路径，防止路径遍历攻击
     * 
     * @param basePath 基础路径
     * @param subPath 子路径
     * @return 安全的路径对象
     * @throws SecurityException 如果检测到路径遍历攻击
     */
    public static Path createSafePath(String basePath, String subPath) {
        if (basePath == null || subPath == null) {
            throw new IllegalArgumentException("Base path and sub path cannot be null");
        }
        
        // 清理子路径
        String sanitizedSubPath = sanitizeFileName(subPath);
        
        // 创建路径并规范化
        Path base = Paths.get(basePath).toAbsolutePath().normalize();
        Path target = base.resolve(sanitizedSubPath).normalize();
        
        // 检查目标路径是否在基础路径内
        if (!target.startsWith(base)) {
            throw new SecurityException("Path traversal attempt detected: " + subPath);
        }
        
        return target;
    }

    /**
     * 清理文件名，移除危险字符
     * 
     * @param fileName 原始文件名
     * @return 清理后的文件名
     */
    public static String sanitizeFileName(String fileName) {
        if (fileName == null) {
            return null;
        }
        
        // 移除路径遍历字符和其他危险字符
        return fileName.replaceAll("[.]{2,}", ".")  // 移除多个连续的点
                      .replaceAll("[/\\\\]", "_")    // 替换路径分隔符
                      .replaceAll("[<>:\"|?*]", "_") // 替换Windows不允许的字符
                      .trim();
    }

    /**
     * 检查目标文件是否在指定目录内
     * 
     * @param directory 目录
     * @param file 目标文件
     * @return 是否在目录内
     */
    public static boolean isWithinDirectory(File directory, File file) {
        try {
            String dirPath = directory.getCanonicalPath();
            String filePath = file.getCanonicalPath();
            return filePath.startsWith(dirPath + File.separator) || filePath.equals(dirPath);
        } catch (Exception e) {
            // 如果无法获取规范路径，则认为不安全
            return false;
        }
    }

    /**
     * 清理和规范化路径
     *
     * @param path 要处理的路径
     * @return 规范化的路径对象
     */
    public static Path sanitizePath(String path) {
        if (path == null) {
            throw new IllegalArgumentException("Path cannot be null");
        }

        return Paths.get(path).toAbsolutePath().normalize();
    }
}