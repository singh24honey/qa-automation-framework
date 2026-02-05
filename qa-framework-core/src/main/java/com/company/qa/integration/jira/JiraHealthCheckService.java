package com.company.qa.integration.jira;

import com.company.qa.config.JiraProperties;
import com.company.qa.model.entity.JiraConfiguration;
import com.company.qa.model.entity.JiraHealthSnapshot;
import com.company.qa.model.enums.HealthStatus;
import com.company.qa.repository.JiraConfigurationRepository;
import com.company.qa.repository.JiraHealthSnapshotRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Service for monitoring JIRA API health.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class JiraHealthCheckService {

    private final JiraRestClient jiraRestClient;
    private final JiraConfigurationRepository configRepository;
    private final JiraHealthSnapshotRepository healthSnapshotRepository;
    private final JiraProperties jiraProperties;

    /**
     * Periodic health check for all enabled JIRA configurations.
     */
    @Scheduled(fixedDelayString = "${jira.health.interval-seconds:300}000")
    public void performHealthChecks() {
        if (!jiraProperties.getHealth().isEnabled()) {
            log.debug("JIRA health checks disabled");
            return;
        }

        List<JiraConfiguration> configs = configRepository.findByEnabledTrue();
        log.info("Running JIRA health checks for {} configurations", configs.size());

        for (JiraConfiguration config : configs) {
            performHealthCheck(config);
        }
    }

    /**
     * Performs health check for a single JIRA configuration.
     */
    public HealthStatus performHealthCheck(JiraConfiguration config) {
        Instant startTime = Instant.now();
        HealthStatus status;
        String errorMessage = null;

        try {
            boolean isHealthy = jiraRestClient.checkHealth(config);
            status = isHealthy ? HealthStatus.UP : HealthStatus.DOWN;

            if (isHealthy) {
                log.debug("JIRA health check PASSED: {}", config.getConfigName());
            } else {
                log.warn("JIRA health check FAILED: {}", config.getConfigName());
            }

        } catch (Exception e) {
            status = HealthStatus.DOWN;
            errorMessage = e.getMessage();
            log.error("JIRA health check ERROR: {}", config.getConfigName(), e);
        }

        int responseTimeMs = (int) Duration.between(startTime, Instant.now()).toMillis();

        JiraHealthSnapshot snapshot = JiraHealthSnapshot.builder()
                .config(config)
                .status(status)
                .responseTimeMs(responseTimeMs)
                .errorMessage(errorMessage)
                .build();

        healthSnapshotRepository.save(snapshot);

        return status;
    }

    /**
     * Get latest health status for a configuration.
     */
    public HealthStatus getLatestStatus(UUID configId) {
        return healthSnapshotRepository
                .findTopByConfigIdOrderByCheckedAtDesc(configId)
                .map(JiraHealthSnapshot::getStatus)
                .orElse(HealthStatus.DOWN);
    }
}