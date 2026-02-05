package com.company.qa.model.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "executive_kpi_cache")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExecutiveKPICache {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Time period
    @Column(name = "period_start", nullable = false)
    private Instant periodStart;

    @Column(name = "period_end", nullable = false)
    private Instant periodEnd;

    @Column(name = "period_type", nullable = false, length = 20)
    private String periodType; // DAILY, WEEKLY, MONTHLY, QUARTERLY

    // Quality KPIs
    @Column(name = "overall_pass_rate", precision = 5, scale = 2)
    private BigDecimal overallPassRate;

    @Column(name = "trend_direction", length = 10)
    private String trendDirection;

    @Column(name = "quality_score", precision = 5, scale = 2)
    private BigDecimal qualityScore;

    // Execution KPIs
    @Column(name = "total_executions")
    private Long totalExecutions;

    @Column(name = "avg_execution_time_ms")
    private Long avgExecutionTimeMs;

    @Column(name = "flaky_test_count")
    private Integer flakyTestCount;

    // AI KPIs
    @Column(name = "ai_accuracy_rate", precision = 5, scale = 2)
    private BigDecimal aiAccuracyRate;

    @Column(name = "ai_cost_total", precision = 15, scale = 2)
    private BigDecimal aiCostTotal;

    @Column(name = "ai_cost_per_fix", precision = 10, scale = 2)
    private BigDecimal aiCostPerFix;

    @Column(name = "ai_suggestions_accepted")
    private Integer aiSuggestionsAccepted;

    @Column(name = "ai_suggestions_rejected")
    private Integer aiSuggestionsRejected;

    // Resource KPIs
    @Column(name = "peak_concurrent_executions")
    private Integer peakConcurrentExecutions;

    @Column(name = "resource_utilization_pct", precision = 5, scale = 2)
    private BigDecimal resourceUtilizationPct;

    // Business Impact
    @Column(name = "estimated_time_saved_hours", precision = 10, scale = 2)
    private BigDecimal estimatedTimeSavedHours;

    @Column(name = "estimated_cost_saved_usd", precision = 15, scale = 2)
    private BigDecimal estimatedCostSavedUsd;

    // Metadata
    @Column(name = "cache_generated_at", nullable = false)
    private Instant cacheGeneratedAt;

    @Column(name = "is_final")
    private Boolean isFinal;

    @Column(name = "created_at")
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
        updatedAt = Instant.now();
        if (cacheGeneratedAt == null) {
            cacheGeneratedAt = Instant.now();
        }
        if (isFinal == null) {
            isFinal = false;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }
}