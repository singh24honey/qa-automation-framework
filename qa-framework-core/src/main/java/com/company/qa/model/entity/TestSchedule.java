package com.company.qa.model.entity;

import com.company.qa.model.enums.ScheduleStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "test_schedules")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TestSchedule extends BaseEntity {

    @Column(nullable = false)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "test_id", nullable = false)
    private UUID testId;

    @Column(name = "cron_expression", nullable = false, length = 100)
    private String cronExpression;

    @Column(length = 50)
    @Builder.Default
    private String timezone = "UTC";

    @Column(length = 50)
    @Builder.Default
    private String browser = "CHROME";

    @Column(length = 100)
    private String environment;

    @Builder.Default
    private Boolean headless = true;

    @Column(name = "is_enabled")
    @Builder.Default
    private Boolean isEnabled = true;

    @Column(name = "is_running")
    @Builder.Default
    private Boolean isRunning = false;

    @Column(name = "last_run_time")
    private Instant lastRunTime;

    @Column(name = "last_run_status", length = 50)
    @Enumerated(EnumType.STRING)
    private ScheduleStatus lastRunStatus;

    @Column(name = "next_run_time")
    private Instant nextRunTime;

    @Column(name = "total_runs")
    @Builder.Default
    private Integer totalRuns = 0;

    @Column(name = "successful_runs")
    @Builder.Default
    private Integer successfulRuns = 0;

    @Column(name = "failed_runs")
    @Builder.Default
    private Integer failedRuns = 0;

    @Column(name = "created_by", length = 100)
    private String createdBy;

    // Relationship to Test (lazy load)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "test_id", insertable = false, updatable = false)
    private Test test;

    // Helper methods
    public void incrementTotalRuns() {
        this.totalRuns = (this.totalRuns == null ? 0 : this.totalRuns) + 1;
    }

    public void incrementSuccessfulRuns() {
        this.successfulRuns = (this.successfulRuns == null ? 0 : this.successfulRuns) + 1;
    }

    public void incrementFailedRuns() {
        this.failedRuns = (this.failedRuns == null ? 0 : this.failedRuns) + 1;
    }

    public double getSuccessRate() {
        if (totalRuns == null || totalRuns == 0) {
            return 0.0;
        }
        return (successfulRuns != null ? successfulRuns : 0) * 100.0 / totalRuns;
    }
}