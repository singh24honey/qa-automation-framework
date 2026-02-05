package com.company.qa.scheduler;

import com.company.qa.service.analytics.ExecutiveDataPopulationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

/**
 * Scheduled jobs for executive dashboard data population
 *
 * Schedule:
 * - Daily KPI generation: Every day at 1:00 AM
 * - Trend analysis: Every day at 1:30 AM
 * - Alert generation: Every hour
 */
@Slf4j
@Component
@Profile("!test") // Don't run in tests
@RequiredArgsConstructor
public class ExecutiveDataScheduler {

    private final ExecutiveDataPopulationService populationService;

    /**
     * Generate daily KPIs at 1:00 AM every day
     * Runs AFTER daily snapshot creation (midnight)
     */
    @Scheduled(cron = "0 0 1 * * *")
    public void generateDailyKPIs() {
        log.info("Scheduled job: Generating daily KPIs");

        try {
            populationService.generateDailyKPIs();
            log.info("Daily KPI generation completed successfully");
        } catch (Exception e) {
            log.error("Error generating daily KPIs", e);
        }
    }

    /**
     * Generate trend analysis at 1:30 AM every day
     */
    @Scheduled(cron = "0 30 1 * * *")
    public void generateTrendAnalysis() {
        log.info("Scheduled job: Generating trend analysis");

        try {
            LocalDate today = LocalDate.now();
            populationService.generateTrendAnalysis(today);
            log.info("Trend analysis generation completed successfully");
        } catch (Exception e) {
            log.error("Error generating trend analysis", e);
        }
    }

    /**
     * Generate alerts every hour
     */
    @Scheduled(cron = "0 0 * * * *")
    public void generateAlerts() {
        log.info("Scheduled job: Generating alerts");

        try {
            populationService.generateAlerts();
            log.info("Alert generation completed successfully");
        } catch (Exception e) {
            log.error("Error generating alerts", e);
        }
    }

    /**
     * Manual refresh trigger - can be called via API
     */
    public void refreshAll() {
        log.info("Manual refresh: Regenerating all executive data");

        generateDailyKPIs();
        generateTrendAnalysis();
        generateAlerts();

        log.info("Manual refresh completed");
    }
}