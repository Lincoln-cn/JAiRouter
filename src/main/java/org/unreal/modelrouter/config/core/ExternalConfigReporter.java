package org.unreal.modelrouter.config.core;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

/**
 * 外部挂载配置体检：启动后把「真正生效的外部配置文件」打进日志。
 *
 * <p>#634：{@code spring.config.import} 的语义是「后导入者优先」，历史上外部挂载文件
 * （{@code /app/config/**}）排在 classpath 之前，于是挂载文件里的同名键被 classpath 静默覆盖，
 * 而文档与 k8s ConfigMap 注释都写着「覆盖 classpath 默认配置」—— 运维改了挂载配置却不生效，
 * 且没有任何提示。</p>
 *
 * <p>修法有两半：① {@code application.yml} 把外部导入移到列表最后（行为修复）；
 * ② 本类在启动日志里明确列出「这次启动到底加载了哪些外部文件」，让「覆盖生效了没有」
 * 变成可观测事实，而不是靠猜。</p>
 *
 * <p>目录可用 {@code jairouter.config.external-root} 覆盖（默认 {@code /app/config}），
 * 便于测试与非容器部署。</p>
 *
 * @since v3.5.2
 */
@Component
public class ExternalConfigReporter implements ApplicationListener<ApplicationReadyEvent> {

    private static final Logger log = LoggerFactory.getLogger(ExternalConfigReporter.class);

    /** 与 {@code application.yml} 里声明的外部导入保持一致。 */
    private static final List<String> EXTERNAL_FILES = List.of(
            "application.yml",
            "auth/jwt.yml",
            "router/services.yml");

    private final Path externalRoot;

    public ExternalConfigReporter(
            @Value("${jairouter.config.external-root:/app/config}") final String externalRoot) {
        this.externalRoot = Paths.get(externalRoot);
    }

    @Override
    public void onApplicationEvent(final ApplicationReadyEvent event) {
        report();
    }

    /**
     * 输出外部配置加载情况（只读、无副作用）。
     */
    public void report() {
        if (!Files.isDirectory(externalRoot)) {
            log.debug("未发现外部配置目录 {}，本次启动全部使用 classpath 内置配置", externalRoot);
            return;
        }
        final List<String> loaded = describePresentFiles();
        if (loaded.isEmpty()) {
            log.warn("外部配置目录 {} 存在，但没有任何已知配置文件（{}），挂载可能不完整",
                    externalRoot, String.join(", ", EXTERNAL_FILES));
            return;
        }
        log.info("外部配置已生效（按后导入者优先覆盖 classpath 同名键）: {}（目录 {}）",
                String.join(", ", loaded), externalRoot);
    }

    /**
     * 列出实际存在的外部配置文件（绝对路径 + 字节数），用于日志与测试断言。
     *
     * @return 存在的文件描述；目录不存在或无匹配文件时为空列表
     */
    public List<String> describePresentFiles() {
        final List<String> present = new ArrayList<>();
        for (String relative : EXTERNAL_FILES) {
            final Path candidate = externalRoot.resolve(relative);
            if (!Files.isRegularFile(candidate)) {
                continue;
            }
            final long size;
            try {
                size = Files.size(candidate);
            } catch (IOException e) {
                present.add(candidate + " (size unknown)");
                continue;
            }
            present.add(candidate + " (" + size + " bytes)");
        }
        return present;
    }

    /**
     * @return 被检查的外部配置目录（绝对路径形式）
     */
    public Path getExternalRoot() {
        return externalRoot;
    }
}
