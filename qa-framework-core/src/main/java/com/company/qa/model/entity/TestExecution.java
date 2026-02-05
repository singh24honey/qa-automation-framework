package com.company.qa.model.entity;

import com.company.qa.execution.decision.ExecutionMode;
import com.company.qa.model.StringListConverter;
import com.company.qa.model.enums.TestStatus;
import com.company.qa.quality.model.QualityVerdict;
import io.hypersistence.utils.hibernate.type.json.JsonType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Type;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "test_executions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TestExecution extends BaseEntity {

    @Column(name = "test_id")
    private UUID testId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TestStatus status;

    @Column(length = 100)
    private String environment;

    @Column(length = 50)
    private String browser;

    @Column(length = 50)
    private String platform;

    @Column(name = "start_time")
    private Instant startTime;

    @Column(name = "end_time")
    private Instant endTime;

    @Column
    private Integer duration;

    @Column(name = "retry_count")
    private Integer retryCount = 0;

    @Column(name = "triggered_by", length = 100)
    private String triggeredBy;

    @Column(name = "error_details", columnDefinition = "TEXT")
    private String errorDetails;

    @Type(JsonType.class)
    @Column(name = "screenshot_urls", columnDefinition = "json")
    @Builder.Default
    private String[] screenshotUrls = new String[0];

    @Column(name = "log_url", length = 500)
    private String logUrl;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "execution_mode")
    private ExecutionMode executionMode;

    @Column(name = "external_execution_ref")
    private String externalExecutionRef;

    @Enumerated(EnumType.STRING)
    @Column(name = "quality_verdict")
    private QualityVerdict qualityVerdict;

    @Column(name = "quality_reasons", length = 1000)
    private String qualityReasons;

    @Column(name = "ai_recommendations", length = 1000)
    private String aiRecommendations;

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
        updatedAt = Instant.now();
        if (screenshotUrls == null) {
            screenshotUrls = new String[0];
        }
    }



}