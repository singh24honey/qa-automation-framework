package com.company.qa.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QualityTrendDTO2 {

    private Long id;
    private LocalDate analysisDate;
    private Long suiteId;
    private String suiteName;

    // Trend metrics
    private BigDecimal passRate7dAvg;
    private BigDecimal passRate30dAvg;
    private String passRateTrend;

    // Volatility
    private BigDecimal passRateVolatility;
    private BigDecimal flakinessScore;

    // Failure counts
    private Integer newFailuresCount;
    private Integer recurringFailuresCount;
    private Integer resolvedFailuresCount;

    // AI contribution
    private Integer aiFixedFailures;
    private Integer aiPreventedFailures;

    // Predictions
    private BigDecimal predictedNext7dPassRate;
    private BigDecimal confidenceScore;

    // Calculated fields
    private String healthStatus;
    private String recommendation;
}