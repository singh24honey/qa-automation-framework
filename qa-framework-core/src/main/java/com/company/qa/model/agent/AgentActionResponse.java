package com.company.qa.model.agent;

import com.company.qa.model.enums.AgentActionType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Response containing agent action details.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentActionResponse {

    private UUID id;
    private Integer iteration;
    private AgentActionType actionType;

    private Map<String, Object> actionInput;
    private Map<String, Object> actionOutput;

    private Boolean success;
    private String errorMessage;

    private Integer durationMs;
    private Double aiCost;

    private Boolean requiredApproval;
    private UUID approvalRequestId;

    private Instant timestamp;
}