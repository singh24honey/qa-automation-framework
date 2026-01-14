package com.company.qa.controller;

import com.company.qa.model.dto.*;
import com.company.qa.service.report.ReportService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/v1/reports")
@RequiredArgsConstructor
public class ReportController {

    private final ReportService reportService;

    @GetMapping("/dashboard")
    public ResponseEntity<ApiResponse<DashboardReportDTO>> getDashboard(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate
    ) {
        log.info("GET /api/v1/reports/dashboard - startDate: {}, endDate: {}", startDate, endDate);

        if (startDate == null) {
            startDate = LocalDate.now().minusDays(30);
        }
        if (endDate == null) {
            endDate = LocalDate.now();
        }

        DashboardReportDTO report = reportService.getDashboardReport(startDate, endDate);

        return ResponseEntity.ok(ApiResponse.success(report, "Dashboard report generated successfully"));
    }

    @GetMapping("/stats")
    public ResponseEntity<ApiResponse<ExecutionStatsDTO>> getStats(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate
    ) {
        log.info("GET /api/v1/reports/stats - startDate: {}, endDate: {}", startDate, endDate);

        if (startDate == null) {
            startDate = LocalDate.now().minusDays(30);
        }
        if (endDate == null) {
            endDate = LocalDate.now();
        }

        ExecutionStatsDTO stats = reportService.getExecutionStats(startDate, endDate);

        return ResponseEntity.ok(ApiResponse.success(stats, "Execution stats generated successfully"));
    }

    @GetMapping("/trends")
    public ResponseEntity<ApiResponse<List<TestTrendDTO>>> getTrends(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate
    ) {
        log.info("GET /api/v1/reports/trends - startDate: {}, endDate: {}", startDate, endDate);

        if (startDate == null) {
            startDate = LocalDate.now().minusDays(7);
        }
        if (endDate == null) {
            endDate = LocalDate.now();
        }

        List<TestTrendDTO> trends = reportService.getExecutionTrends(startDate, endDate);

        return ResponseEntity.ok(ApiResponse.success(trends, "Execution trends generated successfully"));
    }

    @GetMapping("/browser-stats")
    public ResponseEntity<ApiResponse<List<BrowserStatsDTO>>> getBrowserStats(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate
    ) {
        log.info("GET /api/v1/reports/browser-stats - startDate: {}, endDate: {}", startDate, endDate);

        if (startDate == null) {
            startDate = LocalDate.now().minusDays(30);
        }
        if (endDate == null) {
            endDate = LocalDate.now();
        }

        List<BrowserStatsDTO> browserStats = reportService.getBrowserStats(startDate, endDate);

        return ResponseEntity.ok(ApiResponse.success(browserStats, "Browser stats generated successfully"));
    }
}