package com.company.qa.model.agent;

import com.company.qa.model.enums.AgentActionType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Map;

/**
 * Single entry in agent's action history.
 *
 * Records what action was taken, inputs, outputs, and outcome.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentHistoryEntry {

    /**
     * Iteration number when this action occurred.
     */
    private int iteration;

    /**
     * Type of action taken.
     */
    private AgentActionType actionType;

    /**
     * Action parameters/inputs.
     */
    private Map<String, Object> actionInput;

    /**
     * Action output/result.
     */
    private Map<String, Object> actionOutput;

    /**
     * Was the action successful?
     */
    private boolean success;

    /**
     * Error message if failed.
     */
    private String errorMessage;

    /**
     * How long the action took (milliseconds).
     */
    private Integer durationMs;

    /**
     * AI cost for this action (if applicable).
     */
    private Double aiCost;

    /**
     * When action was executed.
     */
    private Instant timestamp;

    /**
     * Did this action require approval?
     */
    private boolean requiredApproval;

    /**
     * Approval request ID if approval was needed.
     */
    private java.util.UUID approvalRequestId;
}