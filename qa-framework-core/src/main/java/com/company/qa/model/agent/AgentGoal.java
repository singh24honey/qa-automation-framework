package com.company.qa.model.agent;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;
import java.util.UUID;

/**
 * Represents what an agent is trying to achieve.
 *
 * Example for PlaywrightAgent:
 * - goalType: "GENERATE_PLAYWRIGHT_TEST"
 * - parameters: { "jiraKey": "PROJ-123", "framework": "PLAYWRIGHT" }
 *
 * Example for FlakyAgent:
 * - goalType: "FIX_FLAKY_TEST"
 * - parameters: { "testId": "uuid-123", "maxRetries": 5 }
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentGoal {

    /**
     * Unique identifier for this goal.
     */
    private UUID goalId;

    /**
     * Type of goal (e.g., "GENERATE_TEST", "FIX_FLAKY_TEST").
     */
    private String goalType;

    /**
     * Goal-specific parameters.
     *
     * For GENERATE_TEST:
     * - jiraKey: String
     * - framework: String
     * - priority: String
     *
     * For FIX_FLAKY_TEST:
     * - testId: UUID
     * - failureThreshold: Integer
     * - maxRetries: Integer
     */
    private Map<String, Object> parameters;

    /**
     * Success criteria - how to know when goal is achieved.
     *
     * Example:
     * - "Test generated AND executed successfully"
     * - "Flaky test passes 5 times in a row"
     */
    private String successCriteria;

    /**
     * User who triggered this goal.
     */
    private UUID triggeredByUserId;

    /**
     * Get parameter by key with type casting.
     */
    @SuppressWarnings("unchecked")
    public <T> T getParameter(String key, Class<T> type) {
        Object value = parameters.get(key);
        if (value == null) {
            return null;
        }
        return (T) value;
    }

    /**
     * Check if all required parameters are present.
     */
    public boolean hasRequiredParameters(String... requiredKeys) {
        for (String key : requiredKeys) {
            if (!parameters.containsKey(key)) {
                return false;
            }
        }
        return true;
    }
}