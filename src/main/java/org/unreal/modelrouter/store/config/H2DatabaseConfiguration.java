package org.unreal.modelrouter.store.config;

import io.r2dbc.h2.H2ConnectionConfiguration;
import io.r2dbc.h2.H2ConnectionFactory;
import io.r2dbc.spi.ConnectionFactory;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.r2dbc.config.AbstractR2dbcConfiguration;
import org.springframework.data.r2dbc.repository.config.EnableR2dbcRepositories;
import org.springframework.r2dbc.connection.init.ConnectionFactoryInitializer;
import org.springframework.r2dbc.connection.init.ResourceDatabasePopulator;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;

/**
 * H2 数据库配置
 * 使用绝对路径解决 Windows 平台路径解析问题
 * 支持数据目录持久化配置，确保重启后使用相同的数据目录
 */
@Slf4j
@Configuration
@ConditionalOnProperty(name = "store.type", havingValue = "h2")
@EnableR2dbcRepositories(basePackages = "org.unreal.modelrouter.store.repository")
public class H2DatabaseConfiguration extends AbstractR2dbcConfiguration {

    @Value("${spring.r2dbc.url}")
    private String r2dbcUrl;

    // 配置文件名
    private static final String DATA_DIR_CONFIG_FILE = ".jairouter-data-dir.properties";
    // 配置键名
    private static final String DATA_DIR_KEY = "data.directory";
    // 默认数据子目录名
    private static final String DEFAULT_DATA_SUBDIR = "data";

    @Bean
    @Override
    public ConnectionFactory connectionFactory() {
        // 获取数据目录（使用绝对路径）
        File dataDirectory = getDataDirectory();

        // 确保 data 目录存在
        ensureDataDirectoryExists(dataDirectory);
        
        // 保存数据目录配置（确保每次启动都记录使用的目录）
        saveDataDirectoryConfig(dataDirectory);

        // 构建数据库文件路径
        String dbFileName = extractDbNameFromUrl(r2dbcUrl);
        File dbFile = new File(dataDirectory, dbFileName);
        String absolutePath = dbFile.getAbsolutePath();

        // 将 Windows 路径分隔符转换为正斜杠
        String normalizedPath = absolutePath.replace("\\", "/");

        // 构建 H2 file URL（注意：这里是 H2 URL，不是 R2DBC URL）
        String h2FileUrl = "file:///" + normalizedPath;

        // 构建 R2DBC 连接配置
        H2ConnectionConfiguration.Builder configBuilder = H2ConnectionConfiguration.builder()
                .username("sa")
                .url(h2FileUrl);

        // 设置 H2 特定参数
        configBuilder.property("DB_CLOSE_DELAY", "-1");
        configBuilder.property("MODE", "MySQL");
        configBuilder.property("DATABASE_TO_UPPER", "FALSE");

        H2ConnectionConfiguration config = configBuilder.build();

        log.info("Creating H2 connection factory");
        log.info("  Original R2DBC URL: {}", r2dbcUrl);
        log.info("  Data directory: {}", dataDirectory.getAbsolutePath());
        log.info("  Database file: {}", dbFile.getAbsolutePath());
        log.info("  H2 file URL: {}", h2FileUrl);
        log.info("  Properties: DB_CLOSE_DELAY=-1, MODE=MySQL, DATABASE_TO_UPPER=FALSE");

        return new H2ConnectionFactory(config);
    }

    /**
     * 获取数据目录
     * 优先使用记录的目录，如果没有记录则使用当前运行目录下的 data 子目录
     * @return 数据目录
     */
    private File getDataDirectory() {
        // 1. 尝试从配置文件中读取记录的目录
        File configFile = findConfigFile();
        if (configFile != null && configFile.exists()) {
            try {
                Properties props = new Properties();
                try (FileInputStream fis = new FileInputStream(configFile)) {
                    props.load(fis);
                }
                
                String recordedPath = props.getProperty(DATA_DIR_KEY);
                if (recordedPath != null && !recordedPath.trim().isEmpty()) {
                    File recordedDir = new File(recordedPath);
                    if (recordedDir.exists() && recordedDir.isDirectory()) {
                        log.info("Using recorded data directory: {}", recordedDir.getAbsolutePath());
                        return recordedDir;
                    } else {
                        log.warn("Recorded data directory does not exist: {}, will use current directory", recordedPath);
                    }
                }
            } catch (IOException e) {
                log.warn("Failed to load data directory config, will use current directory: {}", e.getMessage());
            }
        }

        // 2. 使用当前运行目录下的 data 子目录
        String basePath = System.getProperty("user.dir");
        File dataDir = new File(basePath, DEFAULT_DATA_SUBDIR);
        log.info("Using default data directory: {}", dataDir.getAbsolutePath());
        return dataDir;
    }

    /**
     * 保存数据目录配置
     * @param dataDirectory 数据目录
     */
    private void saveDataDirectoryConfig(File dataDirectory) {
        try {
            File configFile = findConfigFile();
            Properties props = new Properties();
            
            // 如果配置文件已存在，先加载现有配置
            if (configFile.exists()) {
                try (FileInputStream fis = new FileInputStream(configFile)) {
                    props.load(fis);
                }
            }
            
            // 更新数据目录配置
            props.setProperty(DATA_DIR_KEY, dataDirectory.getAbsolutePath());
            
            // 保存到应用目录
            try (FileOutputStream fos = new FileOutputStream(configFile)) {
                props.store(fos, "JAI Router Data Directory Configuration");
            }
            
            log.info("Saved data directory config to: {}", configFile.getAbsolutePath());
        } catch (IOException e) {
            log.warn("Failed to save data directory config: {}", e.getMessage());
        }
    }

    /**
     * 查找配置文件
     * 按优先级查找：应用目录 > 用户主目录
     * @return 配置文件
     */
    private File findConfigFile() {
        // 1. 应用目录
        String basePath = System.getProperty("user.dir");
        File appConfigFile = new File(basePath, DATA_DIR_CONFIG_FILE);
        if (appConfigFile.exists()) {
            return appConfigFile;
        }

        // 2. 用户主目录
        String userHome = System.getProperty("user.home");
        File userConfigFile = new File(userHome, DATA_DIR_CONFIG_FILE);
        if (userConfigFile.exists()) {
            return userConfigFile;
        }

        // 返回应用目录的配置文件（即使不存在）
        return appConfigFile;
    }

    /**
     * 从 R2DBC URL 中提取数据库名称
     * @param url R2DBC URL
     * @return 数据库名称
     */
    private String extractDbNameFromUrl(String url) {
        // 示例：r2dbc:h2:file///./data/jairouter-dev?... -> jairouter-dev
        try {
            if (!url.contains("file///")) {
                return "jairouter-db";
            }

            String filePath = url.substring(url.indexOf("file///") + 7);
            if (filePath.contains("?")) {
                filePath = filePath.substring(0, filePath.indexOf("?"));
            }
            
            // 移除多余的斜杠
            filePath = filePath.replaceAll("^/+", "");
            
            // 获取文件名
            Path path = Paths.get(filePath.replace("\\", "/"));
            String fileName = path.getFileName().toString();
            
            // 如果文件名包含路径分隔符，取最后一部分
            if (fileName.contains("/") || fileName.contains("\\")) {
                fileName = fileName.substring(Math.max(fileName.lastIndexOf('/'), fileName.lastIndexOf('\\')) + 1);
            }
            
            return fileName != null && !fileName.isEmpty() ? fileName : "jairouter-db";
        } catch (Exception e) {
            log.warn("Failed to extract database name from URL, using default", e);
            return "jairouter-db";
        }
    }

    /**
     * 确保数据目录存在
     * @param dataDirectory 数据目录
     */
    private void ensureDataDirectoryExists(File dataDirectory) {
        if (!dataDirectory.exists()) {
            boolean created = dataDirectory.mkdirs();
            if (created) {
                log.info("Created data directory: {}", dataDirectory.getAbsolutePath());
            } else {
                log.warn("Failed to create data directory: {}", dataDirectory.getAbsolutePath());
            }
        } else if (dataDirectory.isDirectory()) {
            log.debug("Data directory already exists: {}", dataDirectory.getAbsolutePath());
        } else {
            log.error("Data directory path exists but is not a directory: {}", dataDirectory.getAbsolutePath());
            throw new IllegalStateException("Data directory path exists but is not a directory: " + dataDirectory.getAbsolutePath());
        }
    }

    /**
     * 初始化数据库表结构
     * 使用 R2DBC 的 ConnectionFactoryInitializer 确保使用相同的连接
     * 注意：这个 Bean 必须在其他使用数据库的 Bean 之前初始化
     */
    @Bean(name = "h2DatabaseInitializer")
    public ConnectionFactoryInitializer h2DatabaseInitializer(ConnectionFactory connectionFactory) {
        ConnectionFactoryInitializer initializer = new ConnectionFactoryInitializer();
        initializer.setConnectionFactory(connectionFactory);

        ResourceDatabasePopulator populator = new ResourceDatabasePopulator();
        populator.addScript(new ClassPathResource("schema.sql"));
        populator.setContinueOnError(true); // 继续执行，忽略已存在的表
        populator.setSeparator(";");

        initializer.setDatabasePopulator(populator);
        initializer.setEnabled(true); // 确保初始化器启用

        log.info("H2 database initializer configured with schema.sql");

        // 立即初始化数据库
        try {
            initializer.afterPropertiesSet();
            log.info("H2 database schema initialized successfully");
        } catch (Exception e) {
            log.error("Failed to initialize H2 database schema", e);
            throw new RuntimeException("Failed to initialize H2 database schema", e);
        }

        return initializer;
    }
}
