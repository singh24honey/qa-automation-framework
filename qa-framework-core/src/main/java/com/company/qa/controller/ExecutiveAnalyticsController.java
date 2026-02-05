package com.company.qa.controller;

import com.company.qa.model.dto.*;
import com.company.qa.service.analytics.ExecutiveAnalyticsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/v1/executive")
@RequiredArgsConstructor
@Tag(name = "Executive Analytics", description = "Executive dashboard and analytics APIs")
public class ExecutiveAnalyticsController {

    private final ExecutiveAnalyticsService executiveAnalyticsService;

    @GetMapping("/dashboard")
    @Operation(summary = "Get executive dashboard")
    public ResponseEntity<ApiResponse<ExecutiveDashboardDTO>> getDashboard() {
        log.info("GET /api/v1/executive/dashboard");

        ExecutiveDashboardDTO dashboard = executiveAnalyticsService.getExecutiveDashboard();

        return ResponseEntity.ok(ApiResponse.success(dashboard));
    }

    @GetMapping("/trends")
    @Operation(summary = "Get quality trends")
    public ResponseEntity<ApiResponse<List<QualityTrendDTO2>>> getQualityTrends(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {

        log.info("GET /api/v1/executive/trends?startDate={}&endDate={}", startDate, endDate);

        List<QualityTrendDTO2> trends = executiveAnalyticsService.getQualityTrends(startDate, endDate);

        return ResponseEntity.ok(ApiResponse.success(trends));
    }

    @GetMapping("/trends/suite/{suiteId}")
    @Operation(summary = "Get quality trends for suite")
    public ResponseEntity<ApiResponse<List<QualityTrendDTO2>>> getQualityTrendsForSuite(
            @PathVariable Long suiteId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {

        log.info("GET /api/v1/executive/trends/suite/{}?startDate={}&endDate={}",
                suiteId, startDate, endDate);

        List<QualityTrendDTO2> trends = executiveAnalyticsService
                .getQualityTrendsForSuite(suiteId, startDate, endDate);

        return ResponseEntity.ok(ApiResponse.success(trends));
    }

    @GetMapping("/alerts")
    @Operation(summary = "Get active alerts")
    public ResponseEntity<ApiResponse<List<ExecutiveAlertDTO>>> getActiveAlerts() {
        log.info("GET /api/v1/executive/alerts");

        List<ExecutiveAlertDTO> alerts = executiveAnalyticsService.getActiveAlerts();

        return ResponseEntity.ok(ApiResponse.success(alerts));
    }

    @GetMapping("/alerts/severity/{severity}")
    @Operation(summary = "Get alerts by severity")
    public ResponseEntity<ApiResponse<List<ExecutiveAlertDTO>>> getAlertsBySeverity(
            @PathVariable String severity) {

        log.info("GET /api/v1/executive/alerts/severity/{}", severity);

        List<ExecutiveAlertDTO> alerts = executiveAnalyticsService.getAlertsBySeverity(severity);

        return ResponseEntity.ok(ApiResponse.success(alerts));
    }

    @PutMapping("/alerts/{alertId}/acknowledge")
    @Operation(summary = "Acknowledge alert")
    public ResponseEntity<ApiResponse<ExecutiveAlertDTO>> acknowledgeAlert(
            @PathVariable Long alertId,
            @RequestParam String acknowledgedBy) {

        log.info("PUT /api/v1/executive/alerts/{}/acknowledge by {}", alertId, acknowledgedBy);

        ExecutiveAlertDTO alert = executiveAnalyticsService.acknowledgeAlert(alertId, acknowledgedBy);

        return ResponseEntity.ok(ApiResponse.success(alert));
    }

    @PutMapping("/alerts/{alertId}/resolve")
    @Operation(summary = "Resolve alert")
    public ResponseEntity<ApiResponse<ExecutiveAlertDTO>> resolveAlert(@PathVariable Long alertId) {
        log.info("PUT /api/v1/executive/alerts/{}/resolve", alertId);

        ExecutiveAlertDTO alert = executiveAnalyticsService.resolveAlert(alertId);

        return ResponseEntity.ok(ApiResponse.success(alert));
    }

    @PostMapping("/kpi/refresh")
    @Operation(summary = "Refresh KPI cache")
    public ResponseEntity<ApiResponse<String>> refreshKPICache() {
        log.info("POST /api/v1/executive/kpi/refresh");

        executiveAnalyticsService.refreshKPICache();

        return ResponseEntity.ok(ApiResponse.success("KPI cache refresh initiated"));
    }
}