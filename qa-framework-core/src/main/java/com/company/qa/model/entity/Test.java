package com.company.qa.model.entity;

import com.company.qa.model.enums.Priority;
import com.company.qa.model.enums.TestFramework;
import com.company.qa.model.enums.TestStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "tests")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Test extends BaseEntity {

    //have added this manually to pass compile time error in jacabo test
    @Column(nullable = false)
    private UUID id;

    @Column(nullable = false)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TestFramework framework;

    @Column(nullable = false)
    private String language;

    @Enumerated(EnumType.STRING)
    private Priority priority;

    @Column(name = "estimated_duration")
    private Integer estimatedDuration;

    @Builder.Default
    @Column(name = "is_active")
    private Boolean isActive = true;

    @Column(columnDefinition = "TEXT")
    private String content;

    @Column(name = "notify_on_failure")
    @Builder.Default
    private Boolean notifyOnFailure = true;  // Default: notify on failures

    @Column(name = "notify_on_success")
    @Builder.Default
    private Boolean notifyOnSuccess = false;  // Default: don't notify on success

    @Enumerated(EnumType.STRING)
    @Column(name = "last_execution_status")
    private TestStatus lastExecutionStatus;

    @Column(name = "last_execution_error", columnDefinition = "TEXT")
    private String lastExecutionError;   // full Playwright stack trace — used by SelfHealingAgent

    @Column(name = "last_executed_at")
    private Instant lastExecutedAt;

    @Builder.Default
    @Column(name = "consecutive_failure_count")
    private Integer consecutiveFailureCount = 0;

    @Builder.Default
    @Column(name = "total_run_count")
    private Integer totalRunCount = 0;

}