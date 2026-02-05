package com.company.qa.scheduler;

import com.company.qa.service.quality.TestQualityHistoryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Scheduler for quality history maintenance
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class QualityHistoryScheduler {

    private final TestQualityHistoryService historyService;

    /**
     * Create daily quality snapshot
     * Runs every day at 1:00 AM
     */
    @Scheduled(cron = "0 0 1 * * *")
    public void createDailySnapshot() {
        log.info("Starting scheduled daily quality snapshot creation");

        try {
            historyService.createDailySnapshot();
            log.info("Daily quality snapshot created successfully");
        } catch (Exception e) {
            log.error("Failed to create daily quality snapshot", e);
        }
    }

    /**
     * Cleanup old execution history
     * Runs every Sunday at 2:00 AM
     * Keeps last 90 days
     */
    @Scheduled(cron = "0 0 2 * * SUN")
    public void cleanupOldHistory() {
        log.info("Starting scheduled cleanup of old execution history");

        try {
            historyService.cleanupOldHistory(90);
            log.info("Old execution history cleaned up successfully");
        } catch (Exception e) {
            log.error("Failed to cleanup old execution history", e);
        }
    }

    /**
     * Cleanup old snapshots
     * Runs on 1st of every month at 3:00 AM
     * Keeps last 365 days
     */
    @Scheduled(cron = "0 0 3 1 * *")
    public void cleanupOldSnapshots() {
        log.info("Starting scheduled cleanup of old snapshots");

        try {
            historyService.cleanupOldSnapshots(365);
            log.info("Old snapshots cleaned up successfully");
        } catch (Exception e) {
            log.error("Failed to cleanup old snapshots", e);
        }
    }

    /**
     * Cleanup resolved patterns
     * Runs every Sunday at 4:00 AM
     * Keeps last 180 days
     */
    @Scheduled(cron = "0 0 4 * * SUN")
    public void cleanupResolvedPatterns() {
        log.info("Starting scheduled cleanup of resolved patterns");

        try {
            historyService.cleanupResolvedPatterns(180);
            log.info("Resolved patterns cleaned up successfully");
        } catch (Exception e) {
            log.error("Failed to cleanup resolved patterns", e);
        }
    }
}