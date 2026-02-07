package com.company.qa.model.agent;

import com.company.qa.model.enums.AgentType;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * Request to start an agent execution.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StartAgentRequest {

    @NotNull(message = "Agent type is required")
    private AgentType agentType;

    @NotNull(message = "Goal type is required")
    private String goalType;

    private Map<String, Object> parameters;

    private Map<String, Object> successCriteria;

    // Optional overrides for default config
    private Integer maxIterations;
    private Double maxAICost;
    private Integer approvalTimeoutSeconds;
}