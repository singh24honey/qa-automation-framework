package com.company.qa.service.storage;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(value = "storage.cleanup.enabled", havingValue = "true", matchIfMissing = true)
public class StorageCleanupService {

    private final FileStorageService fileStorageService;

    @Scheduled(cron = "${storage.cleanup.schedule:0 0 2 * * *}")
    public void cleanupOldFiles() {
        log.info("Starting scheduled storage cleanup");

        try {
            int deletedCount = fileStorageService.cleanupOldFiles();
            log.info("Scheduled cleanup completed. Deleted {} directories", deletedCount);

        } catch (Exception e) {
            log.error("Scheduled cleanup failed: {}", e.getMessage(), e);
        }
    }
}