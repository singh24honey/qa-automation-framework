package com.company.qa.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExecutiveDashboardDTO {

    private KPISummary currentKPIs;
    private List<TrendDataPoint> qualityTrends;
    private List<AlertSummary> criticalAlerts;
    private BusinessImpact businessImpact;
    private AIInsights aiInsights;
    private Instant generatedAt;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class KPISummary {
        private BigDecimal overallPassRate;
        private String trendDirection;
        private BigDecimal qualityScore;
        private Long totalExecutions;
        private Long avgExecutionTimeMs;
        private Integer flakyTestCount;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TrendDataPoint {
        private Instant timestamp;
        private BigDecimal passRate;
        private String trendIndicator;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AlertSummary {
        private Long id;
        private String alertType;
        private String severity;
        private String title;
        private String description;
        private Instant detectedAt;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BusinessImpact {
        private BigDecimal estimatedTimeSavedHours;
        private BigDecimal estimatedCostSavedUsd;
        private BigDecimal aiCostTotal;
        private BigDecimal netSavings;
        private BigDecimal roi;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AIInsights {
        private BigDecimal aiAccuracyRate;
        private BigDecimal aiCostPerFix;
        private Integer aiSuggestionsAccepted;
        private Integer aiSuggestionsRejected;
        private BigDecimal acceptanceRate;
    }
}