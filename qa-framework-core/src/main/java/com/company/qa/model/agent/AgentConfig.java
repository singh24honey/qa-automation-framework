package com.company.qa.model.agent;

import com.company.qa.model.enums.AgentActionType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Configuration for agent execution.
 *
 * Controls:
 * - Max iterations
 * - Budget limits
 * - Approval rules
 * - Timeout settings
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentConfig {

    /**
     * Maximum iterations before timeout.
     */
    @Builder.Default
    private int maxIterations = 5;

    /**
     * Maximum AI cost allowed (USD).
     */
    @Builder.Default
    private Double maxAICost = 1.0;

    /**
     * Actions that ALWAYS require approval.
     */
    @Builder.Default
    private Set<AgentActionType> actionsRequiringApproval = Set.of(
            AgentActionType.COMMIT_CHANGES,
            AgentActionType.CREATE_PULL_REQUEST,
            AgentActionType.DELETE_FILE,
            AgentActionType.MERGE_PR
    );

    /**
     * Actions that NEVER require approval.
     */
    @Builder.Default
    private Set<AgentActionType> actionsNeverRequiringApproval = Set.of(
            AgentActionType.FETCH_JIRA_STORY,
            AgentActionType.QUERY_ELEMENT_REGISTRY,
            AgentActionType.READ_FILE
    );

    /**
     * Timeout for waiting for approval (seconds).
     */
    @Builder.Default
    private long approvalTimeoutSeconds = 3600; // 1 hour

    /**
     * Agent-specific configuration parameters.
     */
    @Builder.Default
    private Map<String, Object> customConfig = new HashMap<>();

    /**
     * Check if action requires approval.
     */
    public boolean requiresApproval(AgentActionType actionType) {
        if (actionsNeverRequiringApproval.contains(actionType)) {
            return false;
        }
        return actionsRequiringApproval.contains(actionType);
    }

    /**
     * Get custom config value.
     */
    @SuppressWarnings("unchecked")
    public <T> T getCustomConfig(String key, Class<T> type) {
        Object value = customConfig.get(key);
        if (value == null) {
            return null;
        }
        return (T) value;
    }
}