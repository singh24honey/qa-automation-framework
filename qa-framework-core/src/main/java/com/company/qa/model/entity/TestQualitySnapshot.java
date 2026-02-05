package com.company.qa.model.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/**
 * Daily quality snapshot for long-term trend analysis
 */
@Entity
@Table(name = "test_quality_snapshots")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TestQualitySnapshot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "snapshot_date", nullable = false, unique = true)
    private LocalDate snapshotDate;

    @Column(name = "total_tests", nullable = false)
    private Integer totalTests;

    @Column(name = "active_tests", nullable = false)
    private Integer activeTests;

    @Column(name = "stable_tests", nullable = false)
    private Integer stableTests;

    @Column(name = "flaky_tests", nullable = false)
    private Integer flakyTests;

    @Column(name = "failing_tests", nullable = false)
    private Integer failingTests;

    @Column(name = "avg_pass_rate", nullable = false, precision = 5, scale = 2)
    private BigDecimal avgPassRate;

    @Column(name = "avg_flakiness_score", nullable = false, precision = 5, scale = 2)
    private BigDecimal avgFlakinessScore;

    @Column(name = "overall_health_score", nullable = false, precision = 5, scale = 2)
    private BigDecimal overallHealthScore;

    @Column(name = "total_executions", nullable = false)
    private Integer totalExecutions;

    @Column(name = "avg_execution_time_ms", nullable = false)
    private Long avgExecutionTimeMs;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
    }

    /**
     * Calculate quality percentage (stable / active)
     */
    public double getQualityPercentage() {
        if (activeTests == 0) return 0.0;
        return (stableTests.doubleValue() / activeTests.doubleValue()) * 100.0;
    }
}