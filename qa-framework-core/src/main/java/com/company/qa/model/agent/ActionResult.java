package com.company.qa.model.agent;

import com.company.qa.model.enums.AgentActionType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * Result of executing a single agent action.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ActionResult {

    /**
     * Action that was executed.
     */
    private AgentActionType actionType;

    /**
     * Was the action successful?
     */
    private boolean success;

    /**
     * Output data from the action.
     *
     * Example for GENERATE_TEST_CODE:
     * - testCode: String
     * - testClassName: String
     * - usedPageObjects: List<String>
     *
     * Example for EXECUTE_TEST:
     * - passed: Boolean
     * - duration: Long
     * - screenshotPath: String
     */
    private Map<String, Object> output;

    /**
     * Error message if failed.
     */
    private String errorMessage;

    /**
     * How long the action took (milliseconds).
     */
    private Integer durationMs;

    /**
     * AI cost for this action.
     */
    private Double aiCost;

    /**
     * Get output value by key.
     */
    @SuppressWarnings("unchecked")
    public <T> T getOutput(String key, Class<T> type) {
        if (output == null) {
            return null;
        }
        Object value = output.get(key);
        if (value == null) {
            return null;
        }
        return (T) value;
    }

    /**
     * Create success result.
     */
    public static ActionResult success(AgentActionType actionType, Map<String, Object> output) {
        return ActionResult.builder()
                .actionType(actionType)
                .success(true)
                .output(output)
                .build();
    }

    /**
     * Create failure result.
     */
    public static ActionResult failure(AgentActionType actionType, String errorMessage) {
        return ActionResult.builder()
                .actionType(actionType)
                .success(false)
                .errorMessage(errorMessage)
                .build();
    }
}