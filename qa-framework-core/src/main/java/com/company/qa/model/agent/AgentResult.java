package com.company.qa.model.agent;

import com.company.qa.model.enums.AgentStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Final result of agent execution.
 *
 * Returned when agent completes (success or failure).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentResult {

    /**
     * Agent execution ID.
     */
    private UUID executionId;

    /**
     * Final status.
     */
    private AgentStatus status;

    /**
     * The goal that was attempted.
     */
    private AgentGoal goal;

    /**
     * Number of iterations completed.
     */
    private int iterationsCompleted;

    /**
     * Final outputs produced.
     *
     * For PlaywrightAgent:
     * - testFilePath: String
     * - pullRequestUrl: String
     * - approvalRequestId: UUID
     *
     * For FlakyAgent:
     * - fixedTestPath: String
     * - stabilityScore: Double
     * - pullRequestUrl: String
     */
    private Map<String, Object> outputs;

    /**
     * Error message if failed.
     */
    private String errorMessage;

    /**
     * Total AI cost for this execution.
     */
    private Double totalAICost;

    /**
     * Total execution time.
     */
    private Duration totalDuration;

    /**
     * When execution started.
     */
    private Instant startedAt;

    /**
     * When execution completed.
     */
    private Instant completedAt;

    /**
     * Summary of what happened.
     */
    private String summary;

    /**
     * Get output value by key.
     */
    @SuppressWarnings("unchecked")
    public <T> T getOutput(String key, Class<T> type) {
        if (outputs == null) {
            return null;
        }
        Object value = outputs.get(key);
        if (value == null) {
            return null;
        }
        return (T) value;
    }
}