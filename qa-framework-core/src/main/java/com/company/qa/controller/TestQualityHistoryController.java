package com.company.qa.controller;

import com.company.qa.model.dto.*;
import com.company.qa.model.entity.TestQualitySnapshot;
import com.company.qa.service.quality.TestQualityHistoryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * REST API for historical quality data
 * DIFFERENT from /api/v1/analytics (Week 3) - focuses on persistence/trends
 */
@RestController
@RequestMapping("/api/v1/quality/history")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Quality History", description = "Historical quality tracking and trends")
public class TestQualityHistoryController {

    private final TestQualityHistoryService historyService;

    /**
     * Get quality trends for last N days
     */
    @GetMapping("/trends")
    @Operation(summary = "Get quality trends",
            description = "Returns historical quality trends from daily snapshots")
    public ResponseEntity<ApiResponse<List<QualityTrendDTO>>> getQualityTrends(
            @Parameter(description = "Number of days to retrieve (default: 30)")
            @RequestParam(defaultValue = "30") int days) {

        log.info("GET /api/v1/quality/history/trends - days: {}", days);

        List<QualityTrendDTO> trends = historyService.getQualityTrends(days);

        return ResponseEntity.ok(ApiResponse.success(trends,
                String.format("Retrieved %d days of trend data", trends.size())));
    }

    /**
     * Get latest quality snapshot
     */
    @GetMapping("/snapshots/latest")
    @Operation(summary = "Get latest snapshot",
            description = "Returns the most recent quality snapshot")
    public ResponseEntity<ApiResponse<TestQualitySnapshot>> getLatestSnapshot() {
        log.info("GET /api/v1/quality/history/snapshots/latest");

        TestQualitySnapshot snapshot = historyService.getLatestSnapshot()
                .orElse(null);

        if (snapshot == null) {
            return ResponseEntity.ok(ApiResponse.success(null,
                    "No snapshots available yet"));
        }

        return ResponseEntity.ok(ApiResponse.success(snapshot,
                "Latest snapshot retrieved"));
    }

    /**
     * Create daily snapshot manually (for testing)
     */
    @PostMapping("/snapshots/create")
    @Operation(summary = "Create daily snapshot",
            description = "Manually trigger creation of daily quality snapshot")
    public ResponseEntity<ApiResponse<String>> createSnapshot() {
        log.info("POST /api/v1/quality/history/snapshots/create");

        try {
            historyService.createDailySnapshot();
            return ResponseEntity.ok(ApiResponse.success("Snapshot created",
                    "Daily quality snapshot created successfully"));
        } catch (Exception e) {
            log.error("Failed to create snapshot", e);
            return ResponseEntity.ok(ApiResponse.error(
                    "Failed to create snapshot: " + e.getMessage()));
        }
    }

    /**
     * Get stored failure patterns for a test
     */
    @GetMapping("/patterns/test/{testName}")
    @Operation(summary = "Get failure patterns for test",
            description = "Returns stored failure patterns for a specific test")
    public ResponseEntity<ApiResponse<List<StoredPatternDTO>>> getTestPatterns(
            @Parameter(description = "Test name")
            @PathVariable String testName,
            @Parameter(description = "Include resolved patterns")
            @RequestParam(defaultValue = "false") boolean includeResolved) {

        log.info("GET /api/v1/quality/history/patterns/test/{} - includeResolved: {}",
                testName, includeResolved);

        List<StoredPatternDTO> patterns = historyService.getStoredPatterns(
                testName, includeResolved);

        return ResponseEntity.ok(ApiResponse.success(patterns,
                String.format("Found %d patterns for test: %s", patterns.size(), testName)));
    }

    /**
     * Get all high-impact patterns
     */
    @GetMapping("/patterns/high-impact")
    @Operation(summary = "Get high-impact patterns",
            description = "Returns unresolved patterns with high impact scores")
    public ResponseEntity<ApiResponse<List<StoredPatternDTO>>> getHighImpactPatterns(
            @Parameter(description = "Minimum impact score (0-100)")
            @RequestParam(defaultValue = "50.0") double minScore) {

        log.info("GET /api/v1/quality/history/patterns/high-impact - minScore: {}", minScore);

        List<StoredPatternDTO> patterns = historyService.getHighImpactPatterns(minScore);

        return ResponseEntity.ok(ApiResponse.success(patterns,
                String.format("Found %d high-impact patterns", patterns.size())));
    }

    /**
     * Get all unresolved patterns
     */
    @GetMapping("/patterns/unresolved")
    @Operation(summary = "Get unresolved patterns",
            description = "Returns all unresolved failure patterns")
    public ResponseEntity<ApiResponse<List<StoredPatternDTO>>> getUnresolvedPatterns() {
        log.info("GET /api/v1/quality/history/patterns/unresolved");

        List<StoredPatternDTO> patterns = historyService.getAllUnresolvedPatterns();

        return ResponseEntity.ok(ApiResponse.success(patterns,
                String.format("Found %d unresolved patterns", patterns.size())));
    }

    /**
     * Resolve a failure pattern
     */
    @PutMapping("/patterns/{patternId}/resolve")
    @Operation(summary = "Resolve failure pattern",
            description = "Mark a failure pattern as resolved with notes")
    public ResponseEntity<ApiResponse<String>> resolvePattern(
            @Parameter(description = "Pattern ID")
            @PathVariable Long patternId,
            @Parameter(description = "Resolution notes")
            @RequestBody Map<String, String> request) {

        log.info("PUT /api/v1/quality/history/patterns/{}/resolve", patternId);

        String notes = request.getOrDefault("notes", "Resolved");

        try {
            historyService.resolvePattern(patternId, notes);
            return ResponseEntity.ok(ApiResponse.success("Pattern resolved",
                    "Pattern marked as resolved successfully"));
        } catch (Exception e) {
            log.error("Failed to resolve pattern", e);
            return ResponseEntity.ok(ApiResponse.error(
                    "Failed to resolve pattern: " + e.getMessage()));
        }
    }

    /**
     * Get historical statistics summary
     */
    @GetMapping("/stats")
    @Operation(summary = "Get historical statistics",
            description = "Returns summary statistics from historical data")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getHistoricalStats(
            @Parameter(description = "Number of days to analyze")
            @RequestParam(defaultValue = "30") int days) {

        log.info("GET /api/v1/quality/history/stats - days: {}", days);

        List<QualityTrendDTO> trends = historyService.getQualityTrends(days);
        List<StoredPatternDTO> unresolvedPatterns = historyService.getAllUnresolvedPatterns();

        if (trends.isEmpty()) {
            return ResponseEntity.ok(ApiResponse.success(
                    Map.of("message", "No historical data available"),
                    "No data available for the specified period"));
        }

        // Calculate statistics
        double avgHealthScore = trends.stream()
                .mapToDouble(QualityTrendDTO::getOverallHealthScore)
                .average()
                .orElse(0.0);

        double avgPassRate = trends.stream()
                .mapToDouble(QualityTrendDTO::getAvgPassRate)
                .average()
                .orElse(0.0);

        int totalExecutions = trends.stream()
                .mapToInt(QualityTrendDTO::getTotalExecutions)
                .sum();

        // Compare first and last to determine trend
        String healthTrend = "STABLE";
        if (trends.size() >= 2) {
            double firstHealth = trends.get(0).getOverallHealthScore();
            double lastHealth = trends.get(trends.size() - 1).getOverallHealthScore();
            double change = lastHealth - firstHealth;

            if (change > 5) healthTrend = "IMPROVING";
            else if (change < -5) healthTrend = "DEGRADING";
        }

        Map<String, Object> stats = Map.of(
                "period", days + " days",
                "avgHealthScore", Math.round(avgHealthScore * 100.0) / 100.0,
                "avgPassRate", Math.round(avgPassRate * 100.0) / 100.0,
                "totalExecutions", totalExecutions,
                "healthTrend", healthTrend,
                "unresolvedPatterns", unresolvedPatterns.size(),
                "snapshotsAnalyzed", trends.size()
        );

        return ResponseEntity.ok(ApiResponse.success(stats,
                "Historical statistics retrieved"));
    }

    /**
     * Trigger cleanup manually (for testing)
     */
    @PostMapping("/cleanup")
    @Operation(summary = "Trigger data cleanup",
            description = "Manually trigger cleanup of old historical data")
    public ResponseEntity<ApiResponse<Map<String, String>>> triggerCleanup(
            @Parameter(description = "History retention days")
            @RequestParam(defaultValue = "90") int historyRetentionDays,
            @Parameter(description = "Snapshot retention days")
            @RequestParam(defaultValue = "365") int snapshotRetentionDays,
            @Parameter(description = "Pattern retention days")
            @RequestParam(defaultValue = "180") int patternRetentionDays) {

        log.info("POST /api/v1/quality/history/cleanup");

        try {
            historyService.cleanupOldHistory(historyRetentionDays);
            historyService.cleanupOldSnapshots(snapshotRetentionDays);
            historyService.cleanupResolvedPatterns(patternRetentionDays);

            Map<String, String> result = Map.of(
                    "historyCleanup", "Completed (retention: " + historyRetentionDays + " days)",
                    "snapshotCleanup", "Completed (retention: " + snapshotRetentionDays + " days)",
                    "patternCleanup", "Completed (retention: " + patternRetentionDays + " days)"
            );

            return ResponseEntity.ok(ApiResponse.success(result,
                    "Cleanup completed successfully"));
        } catch (Exception e) {
            log.error("Cleanup failed", e);
            return ResponseEntity.ok(ApiResponse.error(
                    "Cleanup failed: " + e.getMessage()));
        }
    }
}