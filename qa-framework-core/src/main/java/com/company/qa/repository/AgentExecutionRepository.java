package com.company.qa.repository;


import com.company.qa.model.agent.entity.AgentExecution;
import com.company.qa.model.enums.AgentStatus;
import com.company.qa.model.enums.AgentType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/**
 * Repository for AgentExecution entities.
 */
@Repository
public interface AgentExecutionRepository extends JpaRepository<AgentExecution, UUID> {

    List<AgentExecution> findByAgentType(AgentType agentType);

    List<AgentExecution> findByStatus(AgentStatus status);

    List<AgentExecution> findByTriggeredBy(UUID userId);

    List<AgentExecution> findByStartedAtBetween(Instant start, Instant end);

    List<AgentExecution> findByAgentTypeAndStatus(AgentType agentType, AgentStatus status);

    Page<AgentExecution> findByOrderByStartedAtDesc(Pageable pageable);

    long countByStatus(AgentStatus status);


    @Query("SELECT ae FROM AgentExecution ae WHERE ae.status = 'RUNNING' AND ae.startedAt < :threshold")
    List<AgentExecution> findStuckAgents(@Param("threshold") Instant threshold);

    @Query("SELECT ae FROM AgentExecution ae ORDER BY ae.createdAt DESC")
    List<AgentExecution> findLatestExecutions();


    List<AgentExecution> findByStatusIn(List<AgentStatus> statuses);

    /**
     * Find running or waiting executions.
     * ✅ Convenience method.
     */
    default List<AgentExecution> findRunningAgents() {
        return findByStatusIn(Arrays.asList(
                AgentStatus.RUNNING,
                AgentStatus.WAITING_FOR_APPROVAL
        ));
    }


    /**
     * Check if execution exists with specific status.
     */
    boolean existsByIdAndStatus(UUID id, AgentStatus status);

    List<AgentExecution> findByAgentTypeOrderByCreatedAtDesc(AgentType agentType);

}