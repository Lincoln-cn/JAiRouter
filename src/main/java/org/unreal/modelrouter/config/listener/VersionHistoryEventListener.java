package org.unreal.modelrouter.config.listener;

import java.util.HashMap;
import java.util.Map;

import lombok.extern.slf4j.Slf4j;

import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import org.unreal.modelrouter.config.event.VersionCreatedEvent;

/**
 * 版本历史事件监听器
 *
 * <p>处理版本创建事件，异步记录版本历史。使用 {@code @TransactionalEventListener}
 * 确保事件处理在事务提交后执行，避免数据不一致问题。
 *
 * <p>后续可扩展：持久化版本历史到数据库，支持版本回滚和审计。
 *
 * @since v2.12.0
 */
@Component
@Slf4j
public class VersionHistoryEventListener {

    /**
     * 处理版本创建事件
     *
     * <p>在事务提交后异步处理，记录版本历史信息。
     *
     * @param event 版本创建事件
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Async
    public void onVersionCreated(VersionCreatedEvent event) {
        log.info("VersionCreated: version={}, description={}, userId={}, timestamp={}",
            event.versionNumber(),
            event.description(),
            event.userId(),
            event.timestamp());

        // TODO: 后续持久化版本历史到数据库
        // versionHistoryRepository.save(buildVersionRecord(event));
    }

    /**
     * 构建版本记录
     *
     * @param event 版本创建事件
     * @return 版本记录Map
     */
    private Map<String, Object> buildVersionRecord(VersionCreatedEvent event) {
        Map<String, Object> record = new HashMap<>();
        record.put("versionNumber", event.versionNumber());
        record.put("description", event.description());
        record.put("userId", event.userId());
        record.put("timestamp", event.timestamp().toString());
        return record;
    }
}