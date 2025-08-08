package org.unreal.modelrouter.store;

/**
 * StoreManager工厂类
 * 用于创建不同类型的StoreManager实例
 */
public class StoreManagerFactory {

    /**
     * 创建基于文件的存储管理器
     * @param storagePath 文件存储路径
     * @return FileStoreManager实例
     */
    public static StoreManager createFileStoreManager(String storagePath) {
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
    public static StoreManager createStoreManager(String type, String storagePath) {
        switch (type.toLowerCase()) {
            case "file":
                return new FileStoreManager(storagePath);
            case "memory":
                return new MemoryStoreManager();
            default:
                throw new IllegalArgumentException("Unsupported store type: " + type);
        }
    }
}
