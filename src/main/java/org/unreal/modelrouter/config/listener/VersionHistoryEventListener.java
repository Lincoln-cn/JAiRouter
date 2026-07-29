package org.unreal.modelrouter.config.listener;

import java.time.Instant;

import lombok.extern.slf4j.Slf4j;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import org.unreal.modelrouter.config.event.VersionCreatedEvent;
import org.unreal.modelrouter.persistence.jpa.entity.ConfigVersionHistoryEntity;
import org.unreal.modelrouter.persistence.jpa.repository.ConfigVersionHistoryRepository;

/**
 * Version history event listener.
 *
 * Handles version creation events, persists version history to database.
 *
 * @since v2.12.0
 * @since v2.6.12 Added database persistence
 */
@Component
@Slf4j
public class VersionHistoryEventListener {

    private final ConfigVersionHistoryRepository versionHistoryRepository;

    @Autowired(required = false)
    public VersionHistoryEventListener(ConfigVersionHistoryRepository versionHistoryRepository) {
        this.versionHistoryRepository = versionHistoryRepository;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Async
    public void onVersionCreated(VersionCreatedEvent event) {
        log.info("VersionCreated: version={}, description={}, userId={}, timestamp={}",
            event.versionNumber(),
            event.description(),
            event.userId(),
            event.timestamp());

        persistVersionHistory(event);
    }

    private void persistVersionHistory(VersionCreatedEvent event) {
        if (versionHistoryRepository == null) {
            log.warn("ConfigVersionHistoryRepository not available, skipping persistence");
            return;
        }

        try {
            ConfigVersionHistoryEntity entity = new ConfigVersionHistoryEntity();
            entity.setVersionNumber(String.valueOf(event.versionNumber()));
            entity.setDescription(event.description());
            entity.setUserId(event.userId());
            entity.setTimestamp(event.timestamp() != null ? event.timestamp() : Instant.now());
            entity.setStatus("ACTIVE");

            versionHistoryRepository.save(entity);
            log.debug("Version history persisted: version={}", event.versionNumber());
        } catch (Exception e) {
            log.error("Failed to persist version history: {}", e.getMessage(), e);
        }
    }
}
