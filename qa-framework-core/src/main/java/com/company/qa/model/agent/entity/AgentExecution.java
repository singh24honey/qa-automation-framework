package com.company.qa.model.agent.entity;

import com.company.qa.model.entity.BaseEntity;
import com.company.qa.model.enums.AgentStatus;
import com.company.qa.model.enums.AgentType;
import io.hypersistence.utils.hibernate.type.json.JsonBinaryType;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;
import org.hibernate.annotations.Type;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "agent_executions")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class AgentExecution extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(name = "agent_type", nullable = false, length = 50)
    private AgentType agentType;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 50)
    private AgentStatus status;

    @Type(JsonBinaryType.class)
    @Column(name = "goal", nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> goal;

    @Column(name = "current_iteration")
    private Integer currentIteration;

    @Column(name = "max_iterations")
    private Integer maxIterations;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "triggered_by")
    private UUID triggeredBy;

    @Column(name = "triggered_by_name", length = 255)
    private String triggeredByName;

    @Type(JsonBinaryType.class)
    @Column(name = "result", columnDefinition = "jsonb")
    private Map<String, Object> result;

    @Type(JsonBinaryType.class)
    @Column(name = "outputs", columnDefinition = "jsonb")
    private Map<String, Object> outputs;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "total_ai_cost", precision = 10, scale = 6)
    private BigDecimal totalAICost;

    @Column(name = "total_actions")
    private Integer totalActions;

    public Long getDurationSeconds() {
        if (startedAt == null) {
            return null;
        }
        Instant end = completedAt != null ? completedAt : Instant.now();
        return java.time.Duration.between(startedAt, end).getSeconds();
    }

    public boolean isComplete() {
        return status == AgentStatus.SUCCEEDED
                || status == AgentStatus.FAILED
                || status == AgentStatus.STOPPED
                || status == AgentStatus.TIMEOUT
                || status == AgentStatus.BUDGET_EXCEEDED;
    }
}