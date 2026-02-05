package com.company.qa.controller;

import com.company.qa.model.dto.ApiResponse;
import com.company.qa.model.dto.BudgetStatusDTO;
import com.company.qa.model.dto.CostAnalyticsDTO;
import com.company.qa.model.entity.AIBudget;
import com.company.qa.service.ai.AIBudgetService;
import com.company.qa.service.ai.AICostAnalyticsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
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

/**
 * REST API for AI cost analytics and budget management.
 */
@RestController
@RequestMapping("/api/v1/ai-costs")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "AI Cost Analytics", description = "AI usage cost tracking and analytics")
public class CostAnalyticsController {

    private final AICostAnalyticsService costAnalyticsService;
    private final AIBudgetService budgetService;

    /**
     * Get cost analytics for a date range.
     */
    @GetMapping("/analytics")
    @Operation(summary = "Get cost analytics", description = "Get comprehensive AI cost analytics for a date range")
    public ResponseEntity<ApiResponse<CostAnalyticsDTO>> getCostAnalytics(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {

        log.info("GET /api/v1/ai-costs/analytics - startDate={}, endDate={}", startDate, endDate);

        // Default to last 30 days if not specified
        Instant start = startDate != null
                ? startDate.atStartOfDay(ZoneId.systemDefault()).toInstant()
                : Instant.now().minus(30, ChronoUnit.DAYS);

        Instant end = endDate != null
                ? endDate.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant()
                : Instant.now();

        CostAnalyticsDTO analytics = costAnalyticsService.getCostAnalytics(start, end);

        return ResponseEntity.ok(ApiResponse.success(analytics));
    }

    /**
     * Get cost analytics for last 30 days.
     */
    @GetMapping("/analytics/last-30-days")
    @Operation(summary = "Get last 30 days analytics", description = "Get cost analytics for last 30 days")
    public ResponseEntity<ApiResponse<CostAnalyticsDTO>> getLast30DaysAnalytics() {
        log.info("GET /api/v1/ai-costs/analytics/last-30-days");

        CostAnalyticsDTO analytics = costAnalyticsService.getLast30DaysAnalytics();

        return ResponseEntity.ok(ApiResponse.success(analytics));
    }

    /**
     * Get cost analytics for current month.
     */
    @GetMapping("/analytics/current-month")
    @Operation(summary = "Get current month analytics", description = "Get cost analytics for current month")
    public ResponseEntity<ApiResponse<CostAnalyticsDTO>> getCurrentMonthAnalytics() {
        log.info("GET /api/v1/ai-costs/analytics/current-month");

        CostAnalyticsDTO analytics = costAnalyticsService.getCurrentMonthAnalytics();

        return ResponseEntity.ok(ApiResponse.success(analytics));
    }

    /**
     * Get user-specific cost analytics.
     */
    @GetMapping("/analytics/user/{userId}")
    @Operation(summary = "Get user cost analytics", description = "Get cost analytics for a specific user")
    public ResponseEntity<ApiResponse<CostAnalyticsDTO>> getUserCostAnalytics(
            @PathVariable UUID userId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {

        log.info("GET /api/v1/ai-costs/analytics/user/{} - startDate={}, endDate={}", userId, startDate, endDate);

        Instant start = startDate != null
                ? startDate.atStartOfDay(ZoneId.systemDefault()).toInstant()
                : Instant.now().minus(30, ChronoUnit.DAYS);

        Instant end = endDate != null
                ? endDate.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant()
                : Instant.now();

        CostAnalyticsDTO analytics = costAnalyticsService.getUserCostAnalytics(userId, start, end);

        return ResponseEntity.ok(ApiResponse.success(analytics));
    }

    /**
     * Get budget status.
     */
    @GetMapping("/budget/status")
    @Operation(summary = "Get budget status", description = "Get global budget status")
    public ResponseEntity<ApiResponse<BudgetStatusDTO>> getBudgetStatus() {
        log.info("GET /api/v1/ai-costs/budget/status");

        BudgetStatusDTO status = budgetService.getBudgetStatus(null);

        return ResponseEntity.ok(ApiResponse.success(status));
    }

    /**
     * Get user budget status.
     */
    @GetMapping("/budget/status/user/{userId}")
    @Operation(summary = "Get user budget status", description = "Get budget status for a specific user")
    public ResponseEntity<ApiResponse<BudgetStatusDTO>> getUserBudgetStatus(@PathVariable String userId) {
        log.info("GET /api/v1/ai-costs/budget/status/user/{}", userId);

        BudgetStatusDTO status = budgetService.getBudgetStatus(userId);

        return ResponseEntity.ok(ApiResponse.success(status));
    }

    /**
     * Create a budget.
     */
    @PostMapping("/budget")
    @Operation(summary = "Create budget", description = "Create a new AI spending budget")
    public ResponseEntity<ApiResponse<AIBudget>> createBudget(@RequestBody AIBudget budget) {
        log.info("POST /api/v1/ai-costs/budget - scopeType={}, scopeId={}",
                budget.getScopeType(), budget.getScopeId());

        AIBudget created = budgetService.createBudget(budget);

        return ResponseEntity.ok(ApiResponse.success(created, "Budget created successfully"));
    }

    /**
     * Get all active budgets.
     */
    @GetMapping("/budget")
    @Operation(summary = "Get all budgets", description = "Get all active budgets")
    public ResponseEntity<ApiResponse<List<AIBudget>>> getAllBudgets() {
        log.info("GET /api/v1/ai-costs/budget");

        List<AIBudget> budgets = budgetService.getAllActiveBudgets();

        return ResponseEntity.ok(ApiResponse.success(budgets));
    }
}