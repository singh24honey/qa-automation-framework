package com.company.qa.service.ai;

import com.company.qa.model.dto.CostAnalyticsDTO;
import com.company.qa.model.enums.AIProvider;
import com.company.qa.model.enums.AITaskType;
import com.company.qa.repository.AIUsageLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Service for AI cost analytics and reporting.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class AICostAnalyticsService {

    private final AIUsageLogRepository usageLogRepository;
    private final AIBudgetService budgetService;

    /**
     * Get comprehensive cost analytics for a date range.
     */
    @Transactional(readOnly = true)
    public CostAnalyticsDTO getCostAnalytics(Instant startDate, Instant endDate) {
        log.info("Generating cost analytics: start={}, end={}", startDate, endDate);

        // Overall stats
        BigDecimal totalCost = usageLogRepository.sumCostByDateRange(startDate, endDate);
        Long totalRequests = usageLogRepository.countByDateRange(startDate, endDate);
        Long totalTokens = usageLogRepository.sumTokensByDateRange(startDate, endDate);

        BigDecimal averageCostPerRequest = totalRequests > 0
                ? totalCost.divide(BigDecimal.valueOf(totalRequests), 4, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

        // Cost by provider
        Map<String, CostAnalyticsDTO.ProviderCostStats> costByProvider = getCostByProvider(startDate, endDate);

        // Cost by task type
        Map<String, CostAnalyticsDTO.TaskTypeCostStats> costByTaskType = getCostByTaskType(startDate, endDate);

        // Top users by cost
        List<CostAnalyticsDTO.UserCostStats> topUsersByCost = getTopUsersByCost(startDate, endDate);

        // Daily cost trends
        List<CostAnalyticsDTO.DailyCostTrend> dailyCostTrends = getDailyCostTrends(startDate, endDate);

        // Budget status (global)
        var budgetStatus = budgetService.getBudgetStatus(null);

        return CostAnalyticsDTO.builder()
                .totalCost(totalCost)
                .totalRequests(totalRequests)
                .totalTokens(totalTokens)
                .averageCostPerRequest(averageCostPerRequest)
                .costByProvider(costByProvider)
                .costByTaskType(costByTaskType)
                .topUsersByCost(topUsersByCost)
                .dailyCostTrends(dailyCostTrends)
                .budgetStatus(budgetStatus)
                .build();
    }

    /**
     * Get cost breakdown by provider.
     */
    private Map<String, CostAnalyticsDTO.ProviderCostStats> getCostByProvider(
            Instant startDate, Instant endDate) {

        List<Object[]> results = usageLogRepository.findCostByProvider(startDate, endDate);

        return results.stream()
                .collect(Collectors.toMap(
                        row -> ((AIProvider) row[0]).name(),
                        row -> {
                            BigDecimal totalCost = (BigDecimal) row[1];
                            Long requestCount = ((Number) row[2]).longValue();
                            Long totalTokens = ((Number) row[3]).longValue();

                            BigDecimal avgCost = requestCount > 0
                                    ? totalCost.divide(BigDecimal.valueOf(requestCount), 4, RoundingMode.HALF_UP)
                                    : BigDecimal.ZERO;

                            return CostAnalyticsDTO.ProviderCostStats.builder()
                                    .provider(((AIProvider) row[0]).getDisplayName())
                                    .totalCost(totalCost)
                                    .requestCount(requestCount)
                                    .totalTokens(totalTokens)
                                    .averageCostPerRequest(avgCost)
                                    .build();
                        }
                ));
    }

    /**
     * Get cost breakdown by task type.
     */
    private Map<String, CostAnalyticsDTO.TaskTypeCostStats> getCostByTaskType(
            Instant startDate, Instant endDate) {

        List<Object[]> results = usageLogRepository.findCostByTaskType(startDate, endDate);

        return results.stream()
                .collect(Collectors.toMap(
                        row -> ((AITaskType) row[0]).name(),
                        row -> {
                            BigDecimal totalCost = (BigDecimal) row[1];
                            Long requestCount = ((Number) row[2]).longValue();
                            Double avgTokens = ((Number) row[3]).doubleValue();

                            return CostAnalyticsDTO.TaskTypeCostStats.builder()
                                    .taskType(((AITaskType) row[0]).getDescription())
                                    .totalCost(totalCost)
                                    .requestCount(requestCount)
                                    .averageTokensPerRequest(avgTokens.longValue())
                                    .build();
                        }
                ));
    }

    /**
     * Get top users by cost.
     */
    private List<CostAnalyticsDTO.UserCostStats> getTopUsersByCost(
            Instant startDate, Instant endDate) {

        List<Object[]> results = usageLogRepository.findTopUsersByCost(startDate, endDate);

        return results.stream()
                .limit(10)  // Top 10 users
                .map(row -> CostAnalyticsDTO.UserCostStats.builder()
                        .userId(row[0] != null ? row[0].toString() : "Unknown")
                        .userName(row[1] != null ? row[1].toString() : "Unknown User")
                        .totalCost((BigDecimal) row[2])
                        .requestCount(((Number) row[3]).longValue())
                        .build())
                .collect(Collectors.toList());
    }

    /**
     * Get daily cost trends.
     */
    private List<CostAnalyticsDTO.DailyCostTrend> getDailyCostTrends(
            Instant startDate, Instant endDate) {

        List<Object[]> results = usageLogRepository.findDailyCostTrends(startDate, endDate);

        return results.stream()
                .map(row -> {
                    LocalDate date = ((java.sql.Date) row[0]).toLocalDate();
                    BigDecimal cost = (BigDecimal) row[1];
                    Long requestCount = ((Number) row[2]).longValue();

                    return CostAnalyticsDTO.DailyCostTrend.builder()
                            .date(date.toString())
                            .cost(cost)
                            .requestCount(requestCount)
                            .build();
                })
                .collect(Collectors.toList());
    }

    /**
     * Get cost analytics for last 30 days.
     */
    public CostAnalyticsDTO getLast30DaysAnalytics() {
        Instant endDate = Instant.now();
        Instant startDate = endDate.minus(30, ChronoUnit.DAYS);
        return getCostAnalytics(startDate, endDate);
    }

    /**
     * Get cost analytics for current month.
     */
    public CostAnalyticsDTO getCurrentMonthAnalytics() {
        LocalDate now = LocalDate.now();
        LocalDate firstDayOfMonth = now.withDayOfMonth(1);

        Instant startDate = firstDayOfMonth.atStartOfDay(ZoneId.systemDefault()).toInstant();
        Instant endDate = Instant.now();

        return getCostAnalytics(startDate, endDate);
    }

    /**
     * Get user-specific cost analytics.
     */
    @Transactional(readOnly = true)
    public CostAnalyticsDTO getUserCostAnalytics(UUID userId, Instant startDate, Instant endDate) {
        BigDecimal totalCost = usageLogRepository.sumCostByUserAndDateRange(userId, startDate, endDate);

        var logs = usageLogRepository.findByUserIdAndCreatedAtBetweenOrderByCreatedAtDesc(
                userId, startDate, endDate);

        Long totalRequests = (long) logs.size();
        Long totalTokens = logs.stream().mapToLong(log -> log.getTotalTokens()).sum();

        BigDecimal avgCost = totalRequests > 0
                ? totalCost.divide(BigDecimal.valueOf(totalRequests), 4, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

        // Budget status for this user
        var budgetStatus = budgetService.getBudgetStatus(userId.toString());

        return CostAnalyticsDTO.builder()
                .totalCost(totalCost)
                .totalRequests(totalRequests)
                .totalTokens(totalTokens)
                .averageCostPerRequest(avgCost)
                .budgetStatus(budgetStatus)
                .build();
    }
}