package org.unreal.modelrouter.util;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;

/**
 * 安全文件操作工具类
 * 提供安全的文件读写操作，防止路径遍历攻击
 */
public final class SafeFileOperations {

    private SafeFileOperations() {
        // 工具类，禁止实例化
    }

    /**
     * 安全地创建文件，确保文件在指定的基础目录内
     * 
     * @param baseDirectory 基础目录
     * @param fileName 文件名
     * @return 安全的文件对象
     * @throws SecurityException 如果检测到路径遍历攻击
     * @throws IOException 如果文件操作失败
     */
    public static File createSafeFile(String baseDirectory, String fileName) throws IOException {
        if (baseDirectory == null || fileName == null) {
            throw new IllegalArgumentException("Base directory and file name cannot be null");
        }
        
        // 规范化基础目录路径
        Path basePath = Paths.get(baseDirectory).toAbsolutePath().normalize();

        // 清理文件名
        String sanitizedFileName = sanitizeFileName(fileName);

        // 创建目标文件路径
        Path targetPath = basePath.resolve(sanitizedFileName).normalize();

        // 检查目标路径是否在基础目录内
        if (!targetPath.startsWith(basePath)) {
            throw new SecurityException("Path traversal attempt detected: " + fileName);
        }
        
        // 确保基础目录存在
        Files.createDirectories(basePath);

        return targetPath.toFile();
    }

    /**
     * 安全地创建目录路径
     *
     * @param baseDirectory 基础目录
     * @param subDirectory 子目录名
     * @return 安全的路径对象
     * @throws SecurityException 如果检测到路径遍历攻击
     * @throws IOException 如果目录创建失败
     */
    public static Path createSafeDirectory(String baseDirectory, String subDirectory) throws IOException {
        if (baseDirectory == null || subDirectory == null) {
            throw new IllegalArgumentException("Base directory and sub directory cannot be null");
        }

        // 规范化基础目录路径
        Path basePath = Paths.get(baseDirectory).toAbsolutePath().normalize();

        // 清理子目录名
        String sanitizedSubDir = sanitizeFileName(subDirectory);

        // 创建目标目录路径
        Path targetPath = basePath.resolve(sanitizedSubDir).normalize();

        // 检查目标路径是否在基础目录内
        if (!targetPath.startsWith(basePath)) {
            throw new SecurityException("Path traversal attempt detected: " + subDirectory);
        }

        // 创建目录
        Files.createDirectories(targetPath);

        return targetPath;
    }

    /**
     * 验证文件路径是否安全
     *
     * @param baseDirectory 基础目录
     * @param filePath 文件路径
     * @return 是否安全
     */
    public static boolean isPathSafe(String baseDirectory, String filePath) {
        try {
            Path basePath = Paths.get(baseDirectory).toAbsolutePath().normalize();
            Path targetPath = Paths.get(filePath).toAbsolutePath().normalize();
            return targetPath.startsWith(basePath);
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 清理文件名，移除危险字符
     *
     * @param fileName 原始文件名
     * @return 清理后的文件名
     */
    public static String sanitizeFileName(String fileName) {
        if (fileName == null || fileName.trim().isEmpty()) {
            throw new IllegalArgumentException("File name cannot be null or empty");
        }

        // 移除路径遍历字符和其他危险字符
        String sanitized = fileName.replaceAll("[.]{2,}", ".")     // 移除多个连续的点
                                  .replaceAll("[/\\\\]", "_")       // 替换路径分隔符
                                  .replaceAll("[<>:\"|?*]", "_")    // 替换Windows不允许的字符
                                  .replaceAll("[\u0000-\u001f]", "_") // 替换控制字符
                                  .trim();

        // 确保文件名不为空且不是特殊名称
        if (sanitized.isEmpty() || ".".equals(sanitized) || "..".equals(sanitized)) {
            throw new IllegalArgumentException("Invalid file name: " + fileName);
        }

        return sanitized;
    }

    /**
     * 将数据写入JSON文件
     *
     * @param basePath 基础路径
     * @param data 要写入的数据
     * @param objectMapper JSON对象映射器
     * @return 文件的绝对路径
     * @throws IOException 如果文件操作失败
     */
    public static String writeJsonFile(Path basePath, Map<String, Object> data, ObjectMapper objectMapper)
            throws IOException {
        if (basePath == null || data == null || objectMapper == null) {
            throw new IllegalArgumentException("Base path, data and object mapper cannot be null");
        }

        // 确保基础目录存在并规范化路径
        Path safeBasePath = basePath.toAbsolutePath().normalize();
        if (safeBasePath.getParent() != null) {
            Files.createDirectories(safeBasePath.getParent());
        }

        // 检查路径安全性
        if (safeBasePath.getParent() != null && !isPathSafe(safeBasePath.getParent().toString(),
                safeBasePath.toString())) {
            throw new SecurityException("Path traversal attempt detected: " + basePath.toString());
        }

        // 将数据写入JSON文件
        objectMapper.writeValue(safeBasePath.toFile(), data);

        return safeBasePath.toString();
    }

    /**
     * 从JSON文件读取数据
     *
     * @param basePath 基础路径
     * @param objectMapper JSON对象映射器
     * @param typeReference 类型引用
     * @return 从文件读取的数据
     * @throws IOException 如果文件操作失败
     */
    public static <T> T readJsonFile(Path basePath, ObjectMapper objectMapper, TypeReference<T> typeReference)
            throws IOException {
        if (basePath == null || objectMapper == null || typeReference == null) {
            throw new IllegalArgumentException("Base path, object mapper and type reference cannot be null");
        }

        // 规范化路径
        Path safeBasePath = basePath.toAbsolutePath().normalize();

        // 检查路径安全性
        if (safeBasePath.getParent() != null && !isPathSafe(safeBasePath.getParent().toString(),
                safeBasePath.toString())) {
            throw new SecurityException("Path traversal attempt detected: " + basePath.toString());
        }

        // 检查文件是否存在
        if (!Files.exists(safeBasePath)) {
            throw new IOException("File does not exist: " + safeBasePath.toString());
        }

        // 从JSON文件读取数据
        return objectMapper.readValue(safeBasePath.toFile(), typeReference);
    }

    /**
     * 安全地删除文件
     *
     * @param basePath 基础路径
     * @throws IOException 如果文件操作失败
     */
    public static void deleteFile(Path basePath) throws IOException {
        if (basePath == null) {
            throw new IllegalArgumentException("Base path cannot be null");
        }

        // 规范化路径
        Path safeBasePath = basePath.toAbsolutePath().normalize();

        // 检查路径安全性
        if (safeBasePath.getParent() != null && !isPathSafe(safeBasePath.getParent().toString(),
                safeBasePath.toString())) {
            throw new SecurityException("Path traversal attempt detected: " + basePath.toString());
        }

        // 删除文件
        if (Files.exists(safeBasePath)) {
            Files.delete(safeBasePath);
        }
    }
}