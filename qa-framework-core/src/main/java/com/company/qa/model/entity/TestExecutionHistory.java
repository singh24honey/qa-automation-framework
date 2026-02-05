package com.company.qa.model.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/**
 * Lightweight execution history record for long-term tracking
 * Complements (not duplicates) TestExecution entity
 */
@Entity
@Table(name = "test_execution_history")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TestExecutionHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "execution_id", nullable = true)
    private UUID executionId;

    @Column(name = "test_name", nullable = false, length = 500)
    private String testName;

    @Column(name = "status", nullable = false, length = 20)
    private String status;

    @Column(name = "duration_ms", nullable = false)
    private Long durationMs;

    @Column(name = "failure_reason", columnDefinition = "TEXT")
    private String failureReason;

    @Column(name = "failure_type", length = 100)
    private String failureType;

    @Column(name = "browser", length = 50)
    private String browser;

    @Column(name = "environment", length = 50)
    private String environment;

    @Column(name = "executed_by", length = 100)
    private String executedBy;

    @Column(name = "executed_at", nullable = false)
    private Instant executedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
        if (executedAt == null) {
            executedAt = Instant.now();
        }
    }
}