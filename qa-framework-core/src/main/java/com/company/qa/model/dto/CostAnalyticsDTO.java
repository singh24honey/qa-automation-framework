package com.company.qa.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CostAnalyticsDTO {

    // Overall Stats
    private BigDecimal totalCost;
    private Long totalRequests;
    private Long totalTokens;
    private BigDecimal averageCostPerRequest;

    // By Provider
    private Map<String, ProviderCostStats> costByProvider;

    // By Task Type
    private Map<String, TaskTypeCostStats> costByTaskType;

    // By User
    private List<UserCostStats> topUsersByCost;

    // Trends
    private List<DailyCostTrend> dailyCostTrends;

    // Budget Status
    private BudgetStatusDTO budgetStatus;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ProviderCostStats {
        private String provider;
        private BigDecimal totalCost;
        private Long requestCount;
        private Long totalTokens;
        private BigDecimal averageCostPerRequest;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TaskTypeCostStats {
        private String taskType;
        private BigDecimal totalCost;
        private Long requestCount;
        private Long averageTokensPerRequest;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UserCostStats {
        private String userId;
        private String userName;
        private BigDecimal totalCost;
        private Long requestCount;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DailyCostTrend {
        private String date;
        private BigDecimal cost;
        private Long requestCount;
    }
}