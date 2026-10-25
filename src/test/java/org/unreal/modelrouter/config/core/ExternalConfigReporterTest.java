package org.unreal.modelrouter.config.core;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExternalConfigReporterTest {

    @Test
    @DisplayName("目录不存在时不报错、不编造文件")
    void missingRootYieldsEmptyList(@TempDir final Path tmp) {
        ExternalConfigReporter reporter = new ExternalConfigReporter(tmp.resolve("not-there").toString());
        assertTrue(reporter.describePresentFiles().isEmpty());
    }

    @Test
    @DisplayName("目录存在但没有任何已知文件时返回空列表（启动日志会 warn）")
    void emptyRootYieldsEmptyList(@TempDir final Path tmp) throws IOException {
        Files.createDirectories(tmp.resolve("config"));
        ExternalConfigReporter reporter = new ExternalConfigReporter(tmp.resolve("config").toString());
        assertTrue(reporter.describePresentFiles().isEmpty());
    }

    @Test
    @DisplayName("列出实际存在的挂载文件及其大小")
    void listsPresentFilesWithSize(@TempDir final Path tmp) throws IOException {
        Path root = tmp.resolve("app-config");
        Files.createDirectories(root.resolve("auth"));
        Files.createDirectories(root.resolve("router"));
        Files.writeString(root.resolve("application.yml"), "model:\n  adapter: ollama\n");
        Files.writeString(root.resolve("auth/jwt.yml"), "jairouter:\n  security:\n    jwt:\n      enabled: true\n");

        ExternalConfigReporter reporter = new ExternalConfigReporter(root.toString());
        List<String> present = reporter.describePresentFiles();

        assertEquals(2, present.size(), "只有真实存在的两个文件应被列出");
        assertTrue(present.get(0).contains("application.yml"), present.get(0));
        assertTrue(present.get(1).contains("jwt.yml"), present.get(1));
        assertTrue(present.get(0).contains("bytes"), "描述里应带文件大小: " + present.get(0));
        assertFalse(present.stream().anyMatch(line -> line.contains("services.yml")),
                "不存在的 router/services.yml 不应出现在清单里: " + present);
        assertEquals(root, reporter.getExternalRoot());
    }
}
