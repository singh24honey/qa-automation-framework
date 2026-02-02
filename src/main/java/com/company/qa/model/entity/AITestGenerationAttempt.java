package com.company.qa.model.entity;

import com.company.qa.model.entity.JiraStory;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Audit trail entity for AI test generation attempts.
 * Tracks both successful and failed attempts for:
 * - Debugging generation failures
 * - Cost analysis and optimization
 * - Performance monitoring
 * - Rate limit tracking
 */
@Entity
@Table(name = "ai_test_generation_attempts")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AITestGenerationAttempt extends BaseEntity {  // ✅ Extends BaseEntity


    // Source reference
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "jira_story_id", nullable = true)
    private JiraStory jiraStory;  // ✅ UUID FK

    @Column(name = "jira_story_key", nullable = false, length = 50)
    private String jiraStoryKey;

    // Attempt metadata
    @Column(name = "attempt_number", nullable = false)
    @Builder.Default
    private Integer attemptNumber = 1;

    @Column(name = "ai_provider", nullable = false, length = 50)
    private String aiProvider;

    @Column(name = "ai_model", nullable = false, length = 100)
    private String aiModel;

    // Request/response
    @Column(name = "prompt_context", nullable = false, columnDefinition = "TEXT")
    private String promptContext;

    @Column(name = "raw_response", columnDefinition = "TEXT")
    private String rawResponse;

    // Outcome
    @Column(name = "success", nullable = false)
    @Builder.Default
    private Boolean success = false;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "error_type", length = 100)
    private String errorType; // RATE_LIMIT, INVALID_RESPONSE, TIMEOUT, etc.

    // Costs
    @Column(name = "prompt_tokens")
    private Integer promptTokens;

    @Column(name = "completion_tokens")
    private Integer completionTokens;

    @Column(name = "total_cost_usd", precision = 10, scale = 6)
    private BigDecimal totalCostUsd;

    // Timing
    @Column(name = "duration_ms")
    private Integer durationMs;

    // Use attemptedAt as business timestamp
    @Column(name = "attempted_at", nullable = false)
    private Instant attemptedAt = Instant.now();

    // Link to successful generation
    @Column(name = "generated_test_id")
    private UUID generatedTestId;  // ✅ UUID not Long

}