package com.company.qa.controller;

import com.company.qa.model.agent.*;

import com.company.qa.model.agent.entity.AgentActionHistory;
import com.company.qa.model.agent.entity.AgentExecution;
import com.company.qa.model.dto.ApiResponse;
import com.company.qa.model.enums.AgentType;
import com.company.qa.service.agent.AgentExecutionService;
import com.company.qa.service.agent.AgentOrchestrator;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Comparator;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * REST API for agent execution.
 *
 * Endpoints:
 * - POST /api/agents/start - Start agent
 * - POST /api/agents/{id}/stop - Stop agent
 * - GET /api/agents/{id} - Get execution status
 * - GET /api/agents/{id}/actions - Get action history
 * - GET /api/agents/running - Get running agents
 * - GET /api/agents/types - Get available agent types
 */
@RestController
@RequestMapping("/api/v1/agents")
@Slf4j
@RequiredArgsConstructor
public class AgentController {

    private final AgentOrchestrator orchestrator;
    private final AgentExecutionService executionService;

    /**
     * Start agent execution.
     *
     * POST /api/v1/agents/start
     *
     * Request body:
     * {
     *   "agentType": "PLAYWRIGHT_TEST_GENERATOR",
     *   "goalType": "GENERATE_TEST",
     *   "parameters": {
     *     "jiraKey": "PROJ-123",
     *     "framework": "PLAYWRIGHT"
     *   },
     *   "maxIterations": 20,
     *   "maxAICost": 5.0
     * }
     */
    @PostMapping("/start")
    public ResponseEntity<AgentExecutionResponse> startAgent(
            @Valid @RequestBody StartAgentRequest request,
            @RequestHeader(value = "X-User-ID", required = false) String userId,
            @RequestHeader(value = "X-User-Name", required = false) String userName) {

        log.info("Starting agent: {} - Goal: {}", request.getAgentType(), request.getGoalType());

        String successCriteriaJson = null;
        ObjectMapper objectMapper   = new ObjectMapper();
        if (request.getSuccessCriteria() != null) {
            try {

                successCriteriaJson = objectMapper.writeValueAsString(request.getSuccessCriteria());
            } catch (Exception e) {
                log.warn("Failed to serialize success criteria", e);
            }
        }
        // Build goal
        AgentGoal goal = AgentGoal.builder()
                .goalType(request.getGoalType())
                .parameters(request.getParameters())
                .successCriteria(successCriteriaJson)  // ✅ USE JSON STRING
                .build();

        // Build config
        AgentConfig config = AgentConfig.builder()
                .maxIterations(request.getMaxIterations() != null ? request.getMaxIterations() : 20)
                .maxAICost(request.getMaxAICost() != null ? request.getMaxAICost() : 5.0)
                .approvalTimeoutSeconds(request.getApprovalTimeoutSeconds() != null ?
                        request.getApprovalTimeoutSeconds() : 3600)
                .build();

        // Parse user info
        UUID triggeredBy = userId != null ? UUID.fromString(userId) : null;
        String triggeredByName = userName != null ? userName : "system";

        // Create execution record synchronously, then launch agent async.
        // This avoids the race condition of querying getRunningAgents() right after start.
        AgentExecution execution = orchestrator.createAndStartAgent(
                request.getAgentType(),
                goal,
                config,
                triggeredBy,
                triggeredByName
        );

        AgentExecutionResponse response = mapToResponse(execution);

        return ResponseEntity.accepted().body(response);
    }

    /**
     * Stop agent execution.
     *
     * POST /api/v1/agents/{executionId}/stop
     */
    @PostMapping("/{executionId}/stop")
    public ResponseEntity<Void> stopAgent(@PathVariable UUID executionId) {
        log.info("Stopping agent: {}", executionId);

        boolean stopped = orchestrator.stopAgent(executionId);

        if (stopped) {
            return ResponseEntity.ok().build();
        } else {
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * Get agent execution status.
     *
     * GET /api/v1/agents/{executionId}
     */
    @GetMapping("/{executionId}")
    public ResponseEntity<AgentExecutionResponse> getExecution(@PathVariable UUID executionId) {
        AgentExecution execution = executionService.getExecution(executionId);
        AgentExecutionResponse response = mapToResponse(execution);
        return ResponseEntity.ok(response);
    }

    /**
     * Get agent action history.
     *
     * GET /api/v1/agents/{executionId}/actions
     */
    @GetMapping("/{executionId}/actions")
    public ResponseEntity<List<AgentActionResponse>> getActions(@PathVariable UUID executionId) {
        List<AgentActionHistory> actions = executionService.getActions(executionId);

        List<AgentActionResponse> responses = actions.stream()
                .map(this::mapActionToResponse)
                .collect(Collectors.toList());

        return ResponseEntity.ok(responses);
    }

    /**
     * Get running agents, sorted by startedAt DESC (most recent first).
     *
     * GET /api/v1/agents/running
     */
    @GetMapping("/running")
    public ResponseEntity<List<AgentExecutionResponse>> getRunningAgents() {
        List<AgentExecution> executions = orchestrator.getRunningAgents();

        List<AgentExecutionResponse> responses = executions.stream()
                .sorted(Comparator.comparing(
                        AgentExecution::getStartedAt,
                        Comparator.nullsLast(Comparator.reverseOrder())
                ))
                .map(this::mapToResponse)
                .collect(Collectors.toList());

        return ResponseEntity.ok(responses);
    }

    /**
     * Get all agent execution history, sorted by startedAt DESC.
     *
     * GET /api/v1/agents/history?status=RUNNING&limit=50
     */
    @GetMapping("/history")
    public ResponseEntity<List<AgentExecutionResponse>> getExecutionHistory(
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "50") int limit) {

        List<AgentExecution> executions;

        if (status != null && !status.isBlank()) {
            try {
                com.company.qa.model.enums.AgentStatus agentStatus =
                        com.company.qa.model.enums.AgentStatus.valueOf(status.toUpperCase());
                executions = executionService.getExecutionsByStatus(agentStatus);
            } catch (IllegalArgumentException e) {
                return ResponseEntity.badRequest().build();
            }
        } else {
            executions = executionService.getAllExecutions(limit);
        }

        List<AgentExecutionResponse> responses = executions.stream()
                .sorted(Comparator.comparing(
                        AgentExecution::getStartedAt,
                        Comparator.nullsLast(Comparator.reverseOrder())
                ))
                .limit(limit)
                .map(this::mapToResponse)
                .collect(Collectors.toList());

        return ResponseEntity.ok(responses);
    }

    /**
     * Get available agent types.
     *
     * GET /api/v1/agents/types
     */
    @GetMapping("/types")
    public ResponseEntity<List<AgentType>> getAgentTypes() {
        List<AgentType> types = orchestrator.getAvailableAgentTypes();
        return ResponseEntity.ok(types);
    }

    // ==================== Mapping Methods ====================

    private AgentExecutionResponse mapToResponse(AgentExecution execution) {
        return AgentExecutionResponse.builder()
                .executionId(execution.getId())
                .agentType(execution.getAgentType())
                .status(execution.getStatus())
                .goal(execution.getGoal())
                .currentIteration(execution.getCurrentIteration())
                .maxIterations(execution.getMaxIterations())
                .startedAt(execution.getStartedAt())
                .completedAt(execution.getCompletedAt())
                .triggeredBy(execution.getTriggeredByName())
                .result(execution.getResult())
                .outputs(execution.getOutputs())
                .errorMessage(execution.getErrorMessage())
                .totalAICost(execution.getTotalAICost() != null ?
                        execution.getTotalAICost().doubleValue() : null)
                .totalActions(execution.getTotalActions())
                .durationSeconds(execution.getDurationSeconds())
                .build();
    }

    private AgentActionResponse mapActionToResponse(AgentActionHistory action) {
        return AgentActionResponse.builder()
                .id(action.getId())
                .iteration(action.getIteration())
                .actionType(action.getActionType())
                .actionInput(action.getActionInput())
                .actionOutput(action.getActionOutput())
                .success(action.getSuccess())
                .errorMessage(action.getErrorMessage())
                .durationMs(action.getDurationMs())
                .aiCost(action.getAiCost() != null ? action.getAiCost().doubleValue() : null)
                .requiredApproval(action.getRequiredApproval())
                .approvalRequestId(action.getApprovalRequestId())
                .timestamp(action.getTimestamp())
                .build();
    }


}