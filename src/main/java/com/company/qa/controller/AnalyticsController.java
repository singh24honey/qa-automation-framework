package com.company.qa.controller;

import com.company.qa.model.dto.*;
import com.company.qa.service.analytics.AnalyticsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/analytics")
@RequiredArgsConstructor
@Slf4j
public class AnalyticsController {

    private final AnalyticsService analyticsService;

    @GetMapping("/flaky-tests")
    public ResponseEntity<ApiResponse<List<FlakyTestDTO>>> getFlakyTests(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {

        log.info("Getting flaky tests");

        Instant start = startDate != null ?
                startDate.atStartOfDay(ZoneId.systemDefault()).toInstant() :
                Instant.now().minus(30, ChronoUnit.DAYS);

        Instant end = endDate != null ?
                endDate.atStartOfDay(ZoneId.systemDefault()).plus(1, ChronoUnit.DAYS).toInstant() :
                Instant.now();

        List<FlakyTestDTO> flakyTests = analyticsService.detectFlakyTests(start, end);

        return ResponseEntity.ok(ApiResponse.success(
                flakyTests,
                String.format("Found %d flaky tests", flakyTests.size())
        ));
    }

    @GetMapping("/flaky-tests/{testId}")
    public ResponseEntity<ApiResponse<FlakyTestDTO>> analyzeSingleTest(
            @PathVariable UUID testId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {

        log.info("Analyzing test: {}", testId);

        Instant start = startDate != null ?
                startDate.atStartOfDay(ZoneId.systemDefault()).toInstant() :
                Instant.now().minus(30, ChronoUnit.DAYS);

        Instant end = endDate != null ?
                endDate.atStartOfDay(ZoneId.systemDefault()).plus(1, ChronoUnit.DAYS).toInstant() :
                Instant.now();

        FlakyTestDTO analysis = analyticsService.analyzeSingleTest(testId, start, end);

        if (analysis == null) {
            return ResponseEntity.ok(ApiResponse.error(
                    "Test not found or no execution data available"
            ));
        }

        return ResponseEntity.ok(ApiResponse.success(
                analysis,
                "Test analysis completed"
        ));
    }

    @GetMapping("/performance")
    public ResponseEntity<ApiResponse<List<PerformanceMetricsDTO>>> getPerformanceMetrics(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {

        log.info("Getting performance metrics");

        Instant start = startDate != null ?
                startDate.atStartOfDay(ZoneId.systemDefault()).toInstant() :
                Instant.now().minus(30, ChronoUnit.DAYS);

        Instant end = endDate != null ?
                endDate.atStartOfDay(ZoneId.systemDefault()).plus(1, ChronoUnit.DAYS).toInstant() :
                Instant.now();

        List<PerformanceMetricsDTO> metrics = analyticsService.analyzePerformance(start, end);

        return ResponseEntity.ok(ApiResponse.success(
                metrics,
                "Performance metrics retrieved"
        ));
    }

    @GetMapping("/performance/{testId}")
    public ResponseEntity<ApiResponse<PerformanceMetricsDTO>> getTestPerformance(
            @PathVariable UUID testId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {

        log.info("Getting performance for test: {}", testId);

        Instant start = startDate != null ?
                startDate.atStartOfDay(ZoneId.systemDefault()).toInstant() :
                Instant.now().minus(30, ChronoUnit.DAYS);

        Instant end = endDate != null ?
                endDate.atStartOfDay(ZoneId.systemDefault()).plus(1, ChronoUnit.DAYS).toInstant() :
                Instant.now();

        PerformanceMetricsDTO metrics = analyticsService.getTestPerformance(testId, start, end);

        if (metrics == null) {
            return ResponseEntity.ok(ApiResponse.error("Test not found or no performance data available"));
        }

        return ResponseEntity.ok(ApiResponse.success(
                metrics,
                "Test performance metrics retrieved"
        ));
    }

    // ADD THESE METHODS TO THE END OF AnalyticsController class
// (Keep all Part 1 methods, add these below)

    @GetMapping("/failure-patterns")
    public ResponseEntity<ApiResponse<List<FailurePatternDTO>>> getFailurePatterns(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {

        log.info("Getting failure patterns");

        Instant start = startDate != null ?
                startDate.atStartOfDay(ZoneId.systemDefault()).toInstant() :
                Instant.now().minus(30, ChronoUnit.DAYS);

        Instant end = endDate != null ?
                endDate.atStartOfDay(ZoneId.systemDefault()).plus(1, ChronoUnit.DAYS).toInstant() :
                Instant.now();

        List<FailurePatternDTO> patterns = analyticsService.analyzeFailurePatterns(start, end);

        return ResponseEntity.ok(ApiResponse.success(
                patterns,
                "Failure patterns analyzed"
        ));
    }

    @GetMapping("/suite-health")
    public ResponseEntity<ApiResponse<TestSuiteHealthDTO>> getSuiteHealth(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {

        log.info("Calculating suite health");

        Instant start = startDate != null ?
                startDate.atStartOfDay(ZoneId.systemDefault()).toInstant() :
                Instant.now().minus(30, ChronoUnit.DAYS);

        Instant end = endDate != null ?
                endDate.atStartOfDay(ZoneId.systemDefault()).plus(1, ChronoUnit.DAYS).toInstant() :
                Instant.now();

        TestSuiteHealthDTO health = analyticsService.calculateSuiteHealth(start, end);

        return ResponseEntity.ok(ApiResponse.success(
                health,
                "Suite health calculated"
        ));
    }

    @GetMapping("/dashboard")
    public ResponseEntity<ApiResponse<AnalyticsDashboardDTO>> getAnalyticsDashboard(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {

        log.info("Getting analytics dashboard");

        Instant start = startDate != null ?
                startDate.atStartOfDay(ZoneId.systemDefault()).toInstant() :
                Instant.now().minus(30, ChronoUnit.DAYS);

        Instant end = endDate != null ?
                endDate.atStartOfDay(ZoneId.systemDefault()).plus(1, ChronoUnit.DAYS).toInstant() :
                Instant.now();

        AnalyticsDashboardDTO dashboard = analyticsService.getAnalyticsDashboard(start, end);

        return ResponseEntity.ok(ApiResponse.success(
                dashboard,
                "Analytics dashboard generated successfully"
        ));
    }
}