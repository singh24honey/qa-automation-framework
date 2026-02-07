package com.company.qa.model.agent.entity;

import com.company.qa.model.enums.AgentActionType;
import io.hypersistence.utils.hibernate.type.json.JsonBinaryType;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Type;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Entity for agent action audit trail.
 *
 * Maps to agent_action_history table.
 * Records every action taken by an agent.
 *
 * Used by:
 * - Audit logging and compliance
 * - Debugging failed agent executions
 * - Cost tracking and analytics
 */
@Entity
@Table(name = "agent_action_history")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentActionHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "agent_execution_id", nullable = false)
    private UUID agentExecutionId;

    @Column(name = "iteration", nullable = false)
    private Integer iteration;

    @Enumerated(EnumType.STRING)
    @Column(name = "action_type", nullable = false, length = 100)
    private AgentActionType actionType;

    /**
     * Action input parameters (stored as JSONB).
     * Example: {"jiraKey": "PROJ-123", "framework": "PLAYWRIGHT"}
     */
    @Type(JsonBinaryType.class)
    @Column(name = "action_input", columnDefinition = "jsonb")
    private Map<String, Object> actionInput;

    /**
     * Action output/results (stored as JSONB).
     * Example: {"testCode": "...", "testClassName": "..."}
     */
    @Type(JsonBinaryType.class)
    @Column(name = "action_output", columnDefinition = "jsonb")
    private Map<String, Object> actionOutput;

    @Column(name = "success", nullable = false)
    private Boolean success;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "duration_ms")
    private Integer durationMs;

    @Column(name = "required_approval")
    private Boolean requiredApproval;

    @Column(name = "approval_request_id")
    private UUID approvalRequestId;

    @Column(name = "ai_cost", precision = 10, scale = 6)
    private BigDecimal aiCost;

    @Column(name = "timestamp")
    private Instant timestamp;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
        if (timestamp == null) {
            timestamp = Instant.now();
        }
        if (requiredApproval == null) {
            requiredApproval = false;
        }
    }
}