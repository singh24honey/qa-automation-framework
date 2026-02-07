package com.company.qa.model.agent;

import com.company.qa.model.enums.AgentStatus;
import com.company.qa.model.enums.AgentType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Response containing agent execution details.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentExecutionResponse {

    private UUID executionId;
    private AgentType agentType;
    private AgentStatus status;

    private Map<String, Object> goal;

    private Integer currentIteration;
    private Integer maxIterations;

    private Instant startedAt;
    private Instant completedAt;

    private String triggeredBy;

    private Map<String, Object> result;
    private Map<String, Object> outputs;

    private String errorMessage;

    private Double totalAICost;
    private Integer totalActions;

    private Long durationSeconds;
}