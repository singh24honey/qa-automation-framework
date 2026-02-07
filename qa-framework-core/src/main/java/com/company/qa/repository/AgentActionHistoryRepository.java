package com.company.qa.repository;


import com.company.qa.model.agent.entity.AgentActionHistory;
import com.company.qa.model.enums.AgentActionType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Repository for AgentActionHistory entities.
 */
@Repository
public interface AgentActionHistoryRepository extends JpaRepository<AgentActionHistory, UUID> {

    List<AgentActionHistory> findByAgentExecutionIdOrderByIterationAsc(UUID executionId);

    List<AgentActionHistory> findByActionType(AgentActionType actionType);

    List<AgentActionHistory> findBySuccessFalse();

    List<AgentActionHistory> findByRequiredApprovalTrue();

    List<AgentActionHistory> findByAgentExecutionIdAndIteration(UUID executionId, Integer iteration);

    long countByAgentExecutionId(UUID executionId);

    Page<AgentActionHistory> findByOrderByTimestampDesc(Pageable pageable);

    @Query("SELECT COALESCE(SUM(aah.aiCost), 0) FROM AgentActionHistory aah WHERE aah.agentExecutionId = :executionId")
    BigDecimal getTotalCostForExecution(@Param("executionId") UUID executionId);
}