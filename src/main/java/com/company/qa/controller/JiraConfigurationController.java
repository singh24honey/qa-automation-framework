package com.company.qa.controller;

import com.company.qa.integration.jira.JiraHealthCheckService;
import com.company.qa.model.dto.ApiResponse;
import com.company.qa.model.entity.JiraConfiguration;
import com.company.qa.model.enums.HealthStatus;
import com.company.qa.repository.JiraConfigurationRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * REST API for managing JIRA configurations.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/jira/config")
@RequiredArgsConstructor
@Tag(name = "JIRA Configuration", description = "Manage JIRA Cloud connections")
public class JiraConfigurationController {

    private final JiraConfigurationRepository configRepository;
    private final JiraHealthCheckService healthCheckService;

    @GetMapping
    @Operation(summary = "List all JIRA configurations")
    public ResponseEntity<List<JiraConfiguration>> listConfigurations() {
        List<JiraConfiguration> configs = configRepository.findAll();
        return ResponseEntity.ok(configs);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get JIRA configuration by ID")
    public ResponseEntity<JiraConfiguration> getConfiguration(@PathVariable UUID id) {
        return configRepository.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/{id}/health-check")
    @Operation(summary = "Trigger manual health check")
    public ResponseEntity<ApiResponse<HealthStatus>> checkHealth(@PathVariable UUID id) {
        return configRepository.findById(id)
                .map(config -> {
                    log.info("Manual health check: {}", config.getConfigName());
                    HealthStatus status = healthCheckService.performHealthCheck(config);

                    return ResponseEntity.ok(ApiResponse.<HealthStatus>builder()
                            .success(status == HealthStatus.UP)
                            .data(status)
                            .message("Health check completed: " + status)
                            .build());
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @PatchMapping("/{id}/enabled")
    @Operation(summary = "Enable or disable JIRA configuration")
    public ResponseEntity<ApiResponse<JiraConfiguration>> updateEnabled(
            @PathVariable UUID id,
            @RequestParam boolean enabled
    ) {
        return configRepository.findById(id)
                .map(config -> {
                    config.setEnabled(enabled);
                    JiraConfiguration saved = configRepository.save(config);

                    log.info("JIRA config '{}' {}",
                            config.getConfigName(),
                            enabled ? "enabled" : "disabled");

                    return ResponseEntity.ok(ApiResponse.<JiraConfiguration>builder()
                            .success(true)
                            .data(saved)
                            .message("Configuration " + (enabled ? "enabled" : "disabled"))
                            .build());
                })
                .orElse(ResponseEntity.notFound().build());
    }
}