package com.company.qa.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AnalyticsDashboardDTO {

    private TestSuiteHealthDTO suiteHealth;
    private List<FlakyTestDTO> flakyTests;
    private List<PerformanceMetricsDTO> slowTests;
    private List<FailurePatternDTO> commonFailures;
    private TrendAnalysisDTO trends;
    private Instant generatedAt;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TrendAnalysisDTO {
        private String period; // DAILY, WEEKLY, MONTHLY
        private Double passRateTrend;
        private Double executionTimeTrend;
        private Integer totalExecutions;
        private List<DailyMetric> dailyMetrics;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DailyMetric {
        private String date;
        private Integer executions;
        private Double passRate;
        private Double avgDuration;
    }
}