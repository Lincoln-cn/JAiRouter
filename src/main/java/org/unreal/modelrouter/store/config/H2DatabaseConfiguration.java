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

/**
 * H2 数据库配置
 */
@Slf4j
@Configuration
@ConditionalOnProperty(name = "store.type", havingValue = "h2")
@EnableR2dbcRepositories(basePackages = "org.unreal.modelrouter.store.repository")
public class H2DatabaseConfiguration extends AbstractR2dbcConfiguration {

    @Value("${spring.r2dbc.url}")
    private String r2dbcUrl;

    @Bean
    @Override
    public ConnectionFactory connectionFactory() {
        // 从 R2DBC URL 中提取文件路径
        String filePath = extractFilePath(r2dbcUrl);
        
        // 确保 data 目录存在
        ensureDataDirectoryExists(filePath);
        
        // 构建正确的 R2DBC URL（使用相对路径）
        String normalizedUrl = normalizeR2dbcUrl(filePath);
        
        // 构建 R2DBC 连接配置
        H2ConnectionConfiguration.Builder configBuilder = H2ConnectionConfiguration.builder()
                .username("sa")
                .url(normalizedUrl);
        
        // 设置 H2 特定参数
        configBuilder.property("DB_CLOSE_DELAY", "-1");
        configBuilder.property("MODE", "MySQL");
        configBuilder.property("DATABASE_TO_UPPER", "FALSE");
        
        H2ConnectionConfiguration config = configBuilder.build();
        
        log.info("Creating H2 connection factory");
        log.info("  Original URL: {}", r2dbcUrl);
        log.info("  Normalized URL: {}", normalizedUrl);
        log.info("  File path: {}", filePath);
        log.info("  Properties: DB_CLOSE_DELAY=-1, MODE=MySQL, DATABASE_TO_UPPER=FALSE");
        
        return new H2ConnectionFactory(config);
    }
    
    /**
     * 从 R2DBC URL 中提取文件路径
     * 输入: r2dbc:h2:file///./data/jairouter-dev?DB_CLOSE_DELAY=-1;MODE=MySQL;DATABASE_TO_UPPER=FALSE
     * 输出: ./data/jairouter-dev
     */
    private String extractFilePath(String url) {
        try {
            if (!url.contains("file///")) {
                throw new IllegalArgumentException("Not a file-based H2 URL: " + url);
            }
            
            // 提取 file/// 后面的部分
            String filePath = url.substring(url.indexOf("file///") + 7);
            
            // 移除参数部分
            if (filePath.contains("?")) {
                filePath = filePath.substring(0, filePath.indexOf("?"));
            }
            
            // 移除多余的斜杠: ///./data -> ./data
            filePath = filePath.replaceAll("^/+", "");
            
            log.debug("Extracted file path: {} from URL: {}", filePath, url);
            return filePath;
        } catch (Exception e) {
            log.error("Failed to extract file path from URL: {}", url, e);
            throw new RuntimeException("Invalid H2 R2DBC URL: " + url, e);
        }
    }
    
    /**
     * 规范化 R2DBC URL
     * 输入: ./data/jairouter-dev
     * 输出: r2dbc:h2:file///./data/jairouter-dev
     */
    private String normalizeR2dbcUrl(String filePath) {
        // 保持相对路径不变
        // 确保路径格式正确，使用显式相对路径格式
        if (!filePath.startsWith("./data/")) {
            if (filePath.startsWith("data/")) {
                filePath = "./" + filePath;
            } else {
                filePath = "./data/" + filePath;
            }
        }
        
        // 构建 R2DBC URL（不包含参数，参数通过 property 方法设置）
        // 根据 R2DBC H2 规范，相对路径需要使用三斜杠格式
        return "r2dbc:h2:file///" + filePath;
    }
    
    /**
     * 确保数据目录存在
     */
    private void ensureDataDirectoryExists(String url) {
        try {
            // 从 R2DBC URL 中提取文件路径
            // 格式: r2dbc:h2:file///./data/jairouter?params
            if (url.contains("file///")) {
                String filePath = url.substring(url.indexOf("file///") + 7);
                // 移除参数部分
                if (filePath.contains("?")) {
                    filePath = filePath.substring(0, filePath.indexOf("?"));
                }
                // 移除多余的斜杠
                filePath = filePath.replaceAll("^/+\\./", "./");
                
                java.io.File dbFile = new java.io.File(filePath);
                java.io.File dataDir = dbFile.getParentFile();
                
                if (dataDir != null && !dataDir.exists()) {
                    boolean created = dataDir.mkdirs();
                    if (created) {
                        log.info("Created data directory: {}", dataDir.getPath());
                    }
                }
                
                log.info("H2 database file location: {}", dbFile.getPath());
            }
        } catch (Exception e) {
            log.warn("Failed to ensure data directory exists: {}", e.getMessage());
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
