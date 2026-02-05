package com.company.qa.integration.jira;

import com.company.qa.model.entity.JiraConfiguration;
import com.company.qa.model.enums.HealthStatus;
import com.company.qa.repository.JiraConfigurationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Spring Boot Actuator health indicator for JIRA connectivity.
 * Accessible at /actuator/health/jira
 */
@Component("jira")
@RequiredArgsConstructor
public class JiraHealthIndicator implements HealthIndicator {

    private final JiraConfigurationRepository configRepository;
    private final JiraHealthCheckService healthCheckService;

    @Override
    public Health health() {
        List<JiraConfiguration> configs = configRepository.findByEnabledTrue();

        if (configs.isEmpty()) {
            return Health.up()
                    .withDetail("message", "No JIRA configurations enabled")
                    .build();
        }

        Map<String, Object> details = new HashMap<>();
        boolean allUp = true;

        for (JiraConfiguration config : configs) {
            HealthStatus status = healthCheckService.getLatestStatus(config.getId());

            Map<String, Object> configDetails = new HashMap<>();
            configDetails.put("status", status);
            configDetails.put("url", config.getJiraUrl());
            configDetails.put("projectKey", config.getProjectKey());

            details.put(config.getConfigName(), configDetails);

            if (status != HealthStatus.UP) {
                allUp = false;
            }
        }

        details.put("totalConfigs", configs.size());

        return allUp
                ? Health.up().withDetails(details).build()
                : Health.down().withDetails(details).build();
    }
}