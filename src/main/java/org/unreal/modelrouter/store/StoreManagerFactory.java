package org.unreal.modelrouter.store;

/**
 * StoreManager工厂类
 * 用于创建不同类型的StoreManager实例
 */
public final class StoreManagerFactory {

    private StoreManagerFactory() {
        // 工具类不应实例化
    }

    /**
     * 创建基于文件的存储管理器
     * @param storagePath 文件存储路径
     * @return FileStoreManager实例
     */
    public static StoreManager createFileStoreManager(final String storagePath) {
        return new FileStoreManager(storagePath);
    }

    /**
     * 创建基于内存的存储管理器
     * @return MemoryStoreManager实例
     */
    public static StoreManager createMemoryStoreManager() {
        return new MemoryStoreManager();
    }

    /**
     * 根据类型创建存储管理器
     * @param type 存储类型 (file, memory)
     * @param storagePath 存储路径（仅对文件存储有效）
     * @return StoreManager实例
     */
    public static StoreManager createStoreManager(final String type, final String storagePath) {
        return switch (type.toLowerCase()) {
            case "file" -> new FileStoreManager(storagePath);
            case "memory" -> new MemoryStoreManager();
            default -> throw new IllegalArgumentException("Unsupported store type: " + type);
        };
    }
}
