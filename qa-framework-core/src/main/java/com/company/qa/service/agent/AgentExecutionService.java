package com.company.qa.service.agent;

import com.company.qa.model.agent.*;

import com.company.qa.model.agent.entity.AgentActionHistory;
import com.company.qa.model.agent.entity.AgentExecution;
import com.company.qa.model.enums.AgentActionType;
import com.company.qa.model.enums.AgentStatus;
import com.company.qa.model.enums.AgentType;
import com.company.qa.repository.AgentActionHistoryRepository;
import com.company.qa.repository.AgentExecutionRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Service for persisting agent executions to database.
 *
 * Bridges between:
 * - AgentContext (in-memory state)
 * - AgentExecution (database entity)
 *
 * Used by:
 * - AgentOrchestrator to track executions
 * - Analytics services for reporting
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class AgentExecutionService {

    private final AgentExecutionRepository executionRepository;
    private final AgentActionHistoryRepository actionHistoryRepository;
    private final ObjectMapper objectMapper;

    /**
     * Create new agent execution record.
     *
     * Called when agent starts.
     */
    @Transactional
    public AgentExecution createExecution(
            AgentType agentType,
            AgentGoal goal,
            AgentConfig config,  // ✅ ADD THIS PARAMETER
            UUID triggeredBy,
            String triggeredByName) {

        Map<String, Object> goalMap = objectMapper.convertValue(goal, new TypeReference<>() {});

        AgentExecution execution = AgentExecution.builder()
                .agentType(agentType)
                .status(AgentStatus.RUNNING)
                .goal(goalMap)
                .currentIteration(0)
                .maxIterations(config.getMaxIterations())  // ✅ USE CONFIG
                .startedAt(Instant.now())
                .triggeredBy(triggeredBy)
                .triggeredByName(triggeredByName)
                .totalAICost(BigDecimal.ZERO)
                .totalActions(0)
                .build();

        execution = executionRepository.save(execution);

        log.info("Created agent execution: {} - {} (triggered by: {})",
                execution.getId(), agentType, triggeredByName);

        return execution;
    }

    /**
     * Update execution with current context state.
     *
     * Called periodically during execution and at completion.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void updateExecution(UUID executionId, AgentContext context, AgentStatus status) {
        AgentExecution execution = executionRepository.findById(executionId)
                .orElseThrow(() -> new IllegalArgumentException("Execution not found: " + executionId));

        execution.setStatus(status);

        // context may be null when called from stopAgent (future cancelled before context available)
        if (context != null) {
            execution.setCurrentIteration(context.getCurrentIteration());
            execution.setTotalAICost(BigDecimal.valueOf(context.getTotalAICost()));

            if (context.getWorkProducts() != null && !context.getWorkProducts().isEmpty()) {
                execution.setOutputs(context.getWorkProducts());
            }
        }

        // Set completion time if terminal status
        if (status == AgentStatus.SUCCEEDED || status == AgentStatus.FAILED ||
                status == AgentStatus.STOPPED || status == AgentStatus.TIMEOUT ||
                status == AgentStatus.BUDGET_EXCEEDED) {
            execution.setCompletedAt(Instant.now());
        }

        executionRepository.save(execution);

        log.debug("Updated execution: {} - status: {}, iteration: {}",
                executionId, status, context != null ? context.getCurrentIteration() : "N/A");
    }

    /**
     * Mark execution as stopped without requiring an AgentContext.
     * Used by AgentOrchestrator.stopAgent() where the context
     * is no longer accessible after future.cancel().
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void stopExecution(UUID executionId) {
        executionRepository.findById(executionId).ifPresent(execution -> {
            execution.setStatus(AgentStatus.STOPPED);
            execution.setCompletedAt(Instant.now());
            executionRepository.save(execution);
            log.info("Marked execution as STOPPED: {}", executionId);
        });
    }

    /**
     * Record final result when agent completes.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordResult(UUID executionId, AgentResult result) {
        AgentExecution execution = executionRepository.findById(executionId)
                .orElseThrow(() -> new IllegalArgumentException("Execution not found: " + executionId));

        Map<String, Object> resultMap = objectMapper.convertValue(result, new TypeReference<>() {});
        execution.setResult(resultMap);

        executionRepository.save(execution);

        log.info("Recorded result for execution: {} - status: {}", executionId, result.getStatus());
    }

    /**
     * Record error when agent fails.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordError(UUID executionId, String errorMessage) {
        AgentExecution execution = executionRepository.findById(executionId)
                .orElseThrow(() -> new IllegalArgumentException("Execution not found: " + executionId));

        execution.setStatus(AgentStatus.FAILED);
        execution.setErrorMessage(errorMessage);
        execution.setCompletedAt(Instant.now());

        executionRepository.save(execution);

        log.error("Recorded error for execution: {} - {}", executionId, errorMessage);
    }

    /**
     * Save action to history table.
     *
     * Called after each agent action.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void saveAction(UUID executionId, int iteration, AgentHistoryEntry historyEntry) {
        Map<String, Object> input = historyEntry.getActionInput();
        Map<String, Object> output = historyEntry.getActionOutput();

        AgentActionHistory action = AgentActionHistory.builder()
                .agentExecutionId(executionId)
                .iteration(iteration)
                .actionType(historyEntry.getActionType())
                .actionInput(input)
                .actionOutput(output)
                .success(historyEntry.isSuccess())
                .errorMessage(historyEntry.getErrorMessage())
                .durationMs(historyEntry.getDurationMs())
                .aiCost(historyEntry.getAiCost() != null ? BigDecimal.valueOf(historyEntry.getAiCost()) : null)
                .requiredApproval(historyEntry.isRequiredApproval())
                .approvalRequestId(historyEntry.getApprovalRequestId())
                .timestamp(Instant.now())
                .build();

        actionHistoryRepository.save(action);

        // Update execution total actions count
        executionRepository.findById(executionId).ifPresent(execution -> {
            execution.setTotalActions((execution.getTotalActions() != null ? execution.getTotalActions() : 0) + 1);
            executionRepository.save(execution);
        });

        log.debug("Saved action: {} - iteration: {}, type: {}, success: {}",
                executionId, iteration, historyEntry.getActionType(), historyEntry.isSuccess());
    }

    /**
     * Get execution by ID.
     */
    public AgentExecution getExecution(UUID executionId) {
        return executionRepository.findById(executionId)
                .orElseThrow(() -> new IllegalArgumentException("Execution not found: " + executionId));
    }

    /**
     * Get all actions for an execution.
     */
    public List<AgentActionHistory> getActions(UUID executionId) {
        return actionHistoryRepository.findByAgentExecutionIdOrderByIterationAsc(executionId);
    }

    /**
     * Get all executions, most recent first, up to limit.
     */
    public List<AgentExecution> getAllExecutions(int limit) {
        return executionRepository.findByOrderByStartedAtDesc(
                PageRequest.of(0, limit)
        ).getContent();
    }

    /**
     * Get running executions.
     */
    public List<AgentExecution> getRunningExecutions() {
        return executionRepository.findRunningAgents();
    }

    /**
     * Get executions by type.
     */
    public List<AgentExecution> getExecutionsByType(AgentType agentType) {
        return executionRepository.findByAgentType(agentType);
    }

    /**
     * Get executions by status.
     */
    public List<AgentExecution> getExecutionsByStatus(AgentStatus status) {
        return executionRepository.findByStatus(status);
    }
}