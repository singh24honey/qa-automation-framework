package com.company.qa.model.entity;

import com.company.qa.model.enums.AIProvider;
import com.company.qa.model.enums.AITaskType;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Tracks AI API usage including tokens and costs.
 */
@Entity
@Table(name = "ai_usage_logs")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AIUsageLog {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "request_id", nullable = false, length = 100)
    private String requestId;

    @Column(name = "user_id")
    private UUID userId;

    @Column(name = "user_name", length = 100)
    private String userName;

    @Column(name = "user_role", length = 50)
    private String userRole;

    // AI Provider
    @Enumerated(EnumType.STRING)
    @Column(name = "provider", nullable = false, length = 50)
    private AIProvider provider;

    @Column(name = "model_name", nullable = false, length = 100)
    private String modelName;

    @Enumerated(EnumType.STRING)
    @Column(name = "task_type", nullable = false, length = 50)
    private AITaskType taskType;

    // Token Usage
    @Column(name = "prompt_tokens", nullable = false)
    private Integer promptTokens;

    @Column(name = "completion_tokens", nullable = false)
    private Integer completionTokens;

    @Column(name = "total_tokens", nullable = false)
    private Integer totalTokens;

    // Cost Calculation
    @Column(name = "prompt_cost_per_token", precision = 12, scale = 8)
    private BigDecimal promptCostPerToken;

    @Column(name = "completion_cost_per_token", precision = 12, scale = 8)
    private BigDecimal completionCostPerToken;

    @Column(name = "total_cost", precision = 10, scale = 4)
    private BigDecimal totalCost;

    @Column(name = "currency", length = 3)
    private String currency;

    // Request Details
    @Column(name = "request_content_length")
    private Integer requestContentLength;

    @Column(name = "response_content_length")
    private Integer responseContentLength;

    @Column(name = "processing_time_ms")
    private Long processingTimeMs;

    // Success/Failure
    @Column(name = "success", nullable = false)
    private Boolean success;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    // Timestamps
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    // Related Entities
    @Column(name = "approval_request_id")
    private UUID approvalRequestId;

    @Column(name = "test_id")
    private UUID testId;

    @Column(name = "execution_id")
    private UUID executionId;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
        if (currency == null) {
            currency = "USD";
        }
        if (success == null) {
            success = true;
        }
        if (promptTokens == null) {
            promptTokens = 0;
        }
        if (completionTokens == null) {
            completionTokens = 0;
        }
        if (totalTokens == null) {
            totalTokens = promptTokens + completionTokens;
        }
    }

    /**
     * Calculate total cost based on token usage and pricing.
     */
    public void calculateCost() {
        if (promptCostPerToken != null && completionCostPerToken != null) {
            BigDecimal promptCost = promptCostPerToken.multiply(
                    BigDecimal.valueOf(promptTokens));
            BigDecimal completionCost = completionCostPerToken.multiply(
                    BigDecimal.valueOf(completionTokens));
            totalCost = promptCost.add(completionCost);
        }
    }
}