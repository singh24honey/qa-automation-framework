package com.company.qa.model.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "quality_trend_analysis")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QualityTrendAnalysis {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "analysis_date", nullable = false)
    private LocalDate analysisDate;

    @Column(name = "suite_id")
    private Long suiteId;

    // Trend metrics
    @Column(name = "pass_rate_7d_avg", precision = 5, scale = 2)
    private BigDecimal passRate7dAvg;

    @Column(name = "pass_rate_30d_avg", precision = 5, scale = 2)
    private BigDecimal passRate30dAvg;

    @Column(name = "pass_rate_trend", length = 20)
    private String passRateTrend;

    // Volatility metrics
    @Column(name = "pass_rate_volatility", precision = 5, scale = 2)
    private BigDecimal passRateVolatility;

    @Column(name = "flakiness_score", precision = 5, scale = 2)
    private BigDecimal flakinessScore;

    // Failure analysis
    @Column(name = "new_failures_count")
    private Integer newFailuresCount;

    @Column(name = "recurring_failures_count")
    private Integer recurringFailuresCount;

    @Column(name = "resolved_failures_count")
    private Integer resolvedFailuresCount;

    // AI contribution
    @Column(name = "ai_fixed_failures")
    private Integer aiFixedFailures;

    @Column(name = "ai_prevented_failures")
    private Integer aiPreventedFailures;

    // Predictions
    @Column(name = "predicted_next_7d_pass_rate", precision = 5, scale = 2)
    private BigDecimal predictedNext7dPassRate;

    @Column(name = "confidence_score", precision = 5, scale = 2)
    private BigDecimal confidenceScore;

    @Column(name = "created_at")
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
    }
}