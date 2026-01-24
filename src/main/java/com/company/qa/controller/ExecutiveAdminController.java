package com.company.qa.controller;

import com.company.qa.model.dto.ApiResponse;
import com.company.qa.scheduler.ExecutiveDataScheduler;
import com.company.qa.service.analytics.ExecutiveDataPopulationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/v1/executive/admin")
@RequiredArgsConstructor
@Tag(name = "Executive Admin", description = "Administrative operations for executive dashboard")
public class ExecutiveAdminController {

    private final ExecutiveDataScheduler dataScheduler;
    private final ExecutiveDataPopulationService populationService;

    @PostMapping("/populate/today")
    @Operation(summary = "Manually populate today's data")
    public ResponseEntity<ApiResponse<Map<String, String>>> populateToday() {
        log.info("POST /api/v1/executive/admin/populate/today - Manual population");

        try {
            // Generate today's data
            populationService.generateDailyKPIs();
            populationService.generateTrendAnalysis(LocalDate.now());
            populationService.generateAlerts();

            Map<String, String> result = new HashMap<>();
            result.put("status", "success");
            result.put("message", "Today's executive data populated successfully");
            result.put("date", LocalDate.now().toString());

            return ResponseEntity.ok(ApiResponse.success(result));
        } catch (Exception e) {
            log.error("Error during manual population", e);
            return ResponseEntity.status(500).body(ApiResponse.error(
                    "Failed to populate data: " + e.getMessage()
            ));
        }
    }

    @PostMapping("/populate/refresh-all")
    @Operation(summary = "Refresh all executive data")
    public ResponseEntity<ApiResponse<String>> refreshAll() {
        log.info("POST /api/v1/executive/admin/populate/refresh-all - Full refresh");

        try {
            dataScheduler.refreshAll();
            return ResponseEntity.ok(ApiResponse.success(
                    "Full executive data refresh initiated successfully"
            ));
        } catch (Exception e) {
            log.error("Error during full refresh", e);
            return ResponseEntity.status(500).body(ApiResponse.error(
                    "Failed to refresh data: " + e.getMessage()
            ));
        }
    }

    @GetMapping("/health")
    @Operation(summary = "Check executive dashboard health")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getHealth() {
        log.info("GET /api/v1/executive/admin/health");

        try {
            Map<String, Object> health = new HashMap<>();
            health.put("status", "healthy");
            health.put("timestamp", java.time.Instant.now());
            health.put("message", "Executive dashboard is operational");

            return ResponseEntity.ok(ApiResponse.success(health));
        } catch (Exception e) {
            log.error("Health check failed", e);
            return ResponseEntity.status(500).body(ApiResponse.error(
                    "Health check failed: " + e.getMessage()
            ));
        }
    }
}