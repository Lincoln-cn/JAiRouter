package org.unreal.modelrouter.persistence.store.persistence;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.unreal.modelrouter.util.JacksonHelper;
import reactor.test.StepVerifier;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 文件状态持久化服务测试
 *
 * v2.4.7: 测试文件存储状态持久化实现
 *
 * 注意：使用临时目录进行实际文件测试
 *
 * @author JAiRouter Team
 * @since 2.4.7
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("FileStatePersistenceService Tests")
public class FileStatePersistenceServiceImplTest {

    @InjectMocks
    private FileStatePersistenceServiceImpl fileService;

    private StatePersistenceService.StateType stateType = StatePersistenceService.StateType.CIRCUIT_BREAKER;

    private Path tempDir;

    @BeforeEach
    void setUp() throws IOException {
        // 创建临时目录
        tempDir = Files.createTempDirectory("state-persistence-test");
        ReflectionTestUtils.setField(fileService, "storagePath", tempDir.toString());
    }

    /* ===================== 基础属性测试 ===================== */

    @Test
    @DisplayName("获取层名称 - file")
    void testGetTierName() {
        assertEquals("file", fileService.getTierName());
    }

    @Test
    @DisplayName("获取层优先级 - 3")
    void testGetTierPriority() {
        assertEquals(3, fileService.getTierPriority());
    }

    /* ===================== CRUD 操作测试 ===================== */

    @Test
    @DisplayName("保存操作 - 成功")
    void testSaveSuccess() {
        Map<String, Object> stateData = createTestState("test-key");

        StepVerifier.create(fileService.save(stateType, "test-key", stateData))
                .expectNext(true)
                .verifyComplete();

        // 验证文件已创建
        Path expectedFile = tempDir.resolve("circuit_breaker").resolve("test-key.json");
        assertTrue(Files.exists(expectedFile));
    }

    @Test
    @DisplayName("加载操作 - 成功")
    void testLoadSuccess() throws IOException {
        // 先保存数据
        Map<String, Object> stateData = createTestState("test-key");
        fileService.save(stateType, "test-key", stateData).block();

        // 然后加载
        StepVerifier.create(fileService.load(stateType, "test-key"))
                .expectNextMatches(data -> data.containsKey("stateId"))
                .verifyComplete();
    }

    @Test
    @DisplayName("加载操作 - 文件不存在返回空 Map")
    void testLoadEmpty() {
        StepVerifier.create(fileService.load(stateType, "nonexistent-key"))
                .expectNextMatches(data -> data.isEmpty())
                .verifyComplete();
    }

    @Test
    @DisplayName("删除操作 - 成功")
    void testDeleteSuccess() throws IOException {
        // 先保存数据
        Map<String, Object> stateData = createTestState("test-key");
        fileService.save(stateType, "test-key", stateData).block();

        // 然后删除
        StepVerifier.create(fileService.delete(stateType, "test-key"))
                .expectNext(true)
                .verifyComplete();

        // 验证文件已删除
        Path expectedFile = tempDir.resolve("circuit_breaker").resolve("test-key.json");
        assertFalse(Files.exists(expectedFile));
    }

    @Test
    @DisplayName("删除操作 - 文件不存在也返回成功")
    void testDeleteNonexistent() {
        StepVerifier.create(fileService.delete(stateType, "nonexistent-key"))
                .expectNext(true)
                .verifyComplete();
    }

    @Test
    @DisplayName("存在检查 - 存在")
    void testExistsTrue() throws IOException {
        // 先保存数据
        Map<String, Object> stateData = createTestState("test-key");
        fileService.save(stateType, "test-key", stateData).block();

        StepVerifier.create(fileService.exists(stateType, "test-key"))
                .expectNext(true)
                .verifyComplete();
    }

    @Test
    @DisplayName("存在检查 - 不存在")
    void testExistsFalse() {
        StepVerifier.create(fileService.exists(stateType, "nonexistent-key"))
                .expectNext(false)
                .verifyComplete();
    }

    /* ===================== 批量操作测试 ===================== */

    @Test
    @DisplayName("批量保存 - 成功")
    void testSaveBatchSuccess() {
        Map<String, Map<String, Object>> batchData = new HashMap<>();
        batchData.put("key-1", createTestState("key-1"));
        batchData.put("key-2", createTestState("key-2"));

        StepVerifier.create(fileService.saveBatch(stateType, batchData))
                .expectNext(2)
                .verifyComplete();
    }

    @Test
    @DisplayName("批量加载 - 成功")
    void testLoadBatchSuccess() throws IOException {
        // 先保存数据
        fileService.save(stateType, "key-1", createTestState("key-1")).block();
        fileService.save(stateType, "key-2", createTestState("key-2")).block();

        List<String> keys = List.of("key-1", "key-2");

        StepVerifier.create(fileService.loadBatch(stateType, keys))
                .expectNextMatches(result -> result.size() == 2)
                .verifyComplete();
    }

    @Test
    @DisplayName("获取所有键 - 成功")
    void testGetAllKeysSuccess() throws IOException {
        // 先保存数据
        fileService.save(stateType, "key-1", createTestState("key-1")).block();
        fileService.save(stateType, "key-2", createTestState("key-2")).block();

        StepVerifier.create(fileService.getAllKeys(stateType))
                .expectNextMatches(keys -> {
                    int count = 0;
                    for (String key : keys) {
                        count++;
                    }
                    return count == 2;
                })
                .verifyComplete();
    }

    /* ===================== 健康检查测试 ===================== */

    @Test
    @DisplayName("健康检查 - 正常")
    void testIsHealthyTrue() {
        StepVerifier.create(fileService.isHealthy())
                .expectNext(true)
                .verifyComplete();
    }

    /* ===================== 清除操作测试 ===================== */

    @Test
    @DisplayName("清除所有 - 成功")
    void testClearAllSuccess() throws IOException {
        // 先保存数据
        fileService.save(stateType, "key-1", createTestState("key-1")).block();
        fileService.save(stateType, "key-2", createTestState("key-2")).block();

        StepVerifier.create(fileService.clearAll(stateType))
                .expectNext(true)
                .verifyComplete();

        // 验证目录已清空
        Path typeDir = tempDir.resolve("circuit_breaker");
        assertFalse(Files.exists(typeDir));
    }

    /* ===================== 辅助方法 ===================== */

    private Map<String, Object> createTestState(String stateId) {
        Map<String, Object> state = new HashMap<>();
        state.put("stateId", stateId);
        state.put("timestamp", System.currentTimeMillis());
        state.put("status", "ACTIVE");
        return state;
    }
}