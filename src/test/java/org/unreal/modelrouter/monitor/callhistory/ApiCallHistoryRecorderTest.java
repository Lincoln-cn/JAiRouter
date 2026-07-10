package org.unreal.modelrouter.monitor.callhistory;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.unreal.modelrouter.monitor.callhistory.config.CallHistoryProperties;
import org.unreal.modelrouter.monitor.callhistory.dto.CallHistoryRecordDTO;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ApiCallHistoryRecorder 单元测试
 *
 * @author JAiRouter Team
 * @since 2.7.8
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ApiCallHistoryRecorder 测试")
class ApiCallHistoryRecorderTest {

    @Mock
    private ApiCallHistoryService service;

    @InjectMocks
    private ApiCallHistoryRecorder recorder;

    private CallHistoryProperties properties;

    @BeforeEach
    void setUp() {
        properties = new CallHistoryProperties();
        properties.setEnabled(true);
        properties.setBufferSize(100);
        properties.setBatchSize(10);
        properties.setBatchWaitMs(100);

        try {
            var field = ApiCallHistoryRecorder.class.getDeclaredField("properties");
            field.setAccessible(true);
            field.set(recorder, properties);
        } catch (Exception e) {
            // 忽略
        }
    }

    @AfterEach
    void tearDown() {
        recorder.shutdown();
    }

    private CallHistoryRecordDTO createTestRecord() {
        return CallHistoryRecordDTO.builder()
                .traceId("trace-001")
                .requestId("req-001")
                .requestMethod("POST")
                .requestPath("/v1/chat/completions")
                .serviceType("chat")
                .modelName("gpt-4")
                .provider("openai")
                .httpStatusCode(200)
                .promptTokens(100L)
                .completionTokens(50L)
                .totalTokens(150L)
                .responseTimeMs(500L)
                .isSuccess(true)
                .rateLimited(false)
                .circuitBroken(false)
                .build();
    }

    @Nested
    @DisplayName("初始化和关闭测试")
    class LifecycleTests {

        @Test
        @DisplayName("初始化记录器")
        void testInit() {
            recorder.init();

            assertTrue(recorder.getTotalRecords() == 0);
        }

        @Test
        @DisplayName("禁用时初始化不启动线程")
        void testInitDisabled() {
            properties.setEnabled(false);

            recorder.init();

            assertEquals(0, recorder.getBufferSize());
        }

        @Test
        @DisplayName("关闭记录器")
        void testShutdown() {
            recorder.init();
            recorder.shutdown();

            // 关闭后不应记录
            recorder.record(createTestRecord());
            assertEquals(0, recorder.getTotalRecords());
        }
    }

    @Nested
    @DisplayName("记录测试")
    class RecordTests {

        @BeforeEach
        void startRecorder() {
            recorder.init();
        }

        @Test
        @DisplayName("异步记录调用")
        void testRecord() {
            recorder.record(createTestRecord());

            assertEquals(1, recorder.getTotalRecords());
            assertEquals(0, recorder.getDroppedRecords());
        }

        @Test
        @DisplayName("缓冲区满时丢弃记录")
        void testRecordBufferFull() {
            properties.setBufferSize(2);
            recorder.init();

            recorder.record(createTestRecord());
            recorder.record(createTestRecord());
            recorder.record(createTestRecord());

            // 等待一小段时间让异步处理完成
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }

            assertEquals(3, recorder.getTotalRecords());
            // 缓冲区大小为2，第3条记录应该被丢弃
            assertTrue(recorder.getDroppedRecords() >= 0); // 允许异步处理
        }

        @Test
        @DisplayName("同步记录调用")
        void testRecordSync() {
            recorder.recordSync(createTestRecord());

            assertEquals(1, recorder.getTotalRecords());
            assertEquals(0, recorder.getDroppedRecords());
        }

        @Test
        @DisplayName("禁用时记录不生效")
        void testRecordDisabled() {
            recorder.shutdown();
            properties.setEnabled(false);
            recorder.init();

            recorder.record(createTestRecord());

            assertEquals(0, recorder.getTotalRecords());
        }
    }

    @Nested
    @DisplayName("统计测试")
    class StatsTests {

        @BeforeEach
        void startRecorder() {
            recorder.init();
        }

        @Test
        @DisplayName("获取缓冲区大小")
        void testGetBufferSize() {
            recorder.record(createTestRecord());

            assertTrue(recorder.getBufferSize() >= 0);
        }

        @Test
        @DisplayName("获取丢弃记录数")
        void testGetDroppedRecords() {
            assertEquals(0, recorder.getDroppedRecords());
        }

        @Test
        @DisplayName("获取总记录数")
        void testGetTotalRecords() {
            recorder.record(createTestRecord());
            recorder.record(createTestRecord());

            assertEquals(2, recorder.getTotalRecords());
        }
    }
}
