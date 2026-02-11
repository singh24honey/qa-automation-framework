package com.company.qa.service.agent.tool;

import com.company.qa.model.enums.AgentActionType;
import com.company.qa.service.agent.resilience.AgentCircuitBreaker;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Registry of all tools available to agents.
 *
 * Provides:
 * - Tool registration (auto at startup)
 * - Tool discovery by action type
 * - Tool execution with validation
 * - Tool catalog for AI prompts
 *
 * Tools self-register via @PostConstruct in their implementations.
 */
@Service
@Slf4j
public class AgentToolRegistry {

    private final Map<AgentActionType, AgentTool> tools = new ConcurrentHashMap<>();
    private final AgentCircuitBreaker circuitBreaker; // ✅ INJECTED

    public AgentToolRegistry(AgentCircuitBreaker circuitBreaker) {
        this.circuitBreaker = circuitBreaker;
    }


    /**
     * Register a tool.
     *
     * Called by tool implementations during @PostConstruct.
     *
     * @param tool Tool to register
     */
    public void registerTool(AgentTool tool) {
        AgentActionType actionType = tool.getActionType();

        if (tools.containsKey(actionType)) {
            log.warn("⚠️  Tool for action {} already registered, overwriting", actionType);
        }

        tools.put(actionType, tool);
        log.info("🔧 Registered tool: {} → {}", actionType, tool.getName());
    }

    /**
     * Get tool by action type.
     *
     * @param actionType Action type
     * @return Optional containing tool if found
     */
    public Optional<AgentTool> getTool(AgentActionType actionType) {
        return Optional.ofNullable(tools.get(actionType));
    }

    /**
     * Execute a tool.
     *
     * Validates parameters before execution.
     *
     * @param actionType Action type to execute
     * @param parameters Tool parameters
     * @return Tool execution result
     * @throws IllegalArgumentException if tool not found or parameters invalid
     */
    public Map<String, Object> executeTool(
            AgentActionType actionType,
            Map<String, Object> parameters) {

        AgentTool tool = tools.get(actionType);
        if (tool == null) {
            return Map.of("success", false, "error", "No tool registered");
        }

        String toolName = tool.getClass().getSimpleName();

        // 1. ✅ CHECK CIRCUIT BREAKER BEFORE EXECUTION
        if (!circuitBreaker.allowRequest(toolName)) {
            log.warn("🔴 Circuit OPEN for {}, rejecting request", toolName);
            return Map.of(
                    "success", false,
                    "error", "Circuit breaker OPEN for " + toolName,
                    "circuitBreakerOpen", true
            );
        }

        // 2. Validate parameters
        if (!tool.validateParameters(parameters)) {
            return Map.of("success", false, "error", "Invalid parameters");
        }

        // 3. Execute tool
        try {
            Map<String, Object> result = tool.execute(parameters);

            // 4. ✅ RECORD SUCCESS/FAILURE TO CIRCUIT BREAKER
            if (result.get("success") == Boolean.TRUE) {
                circuitBreaker.recordSuccess(toolName);
            } else {
                circuitBreaker.recordFailure(toolName);
            }

            return result;

        } catch (Exception e) {
            // 5. ✅ RECORD EXCEPTION TO CIRCUIT BREAKER
            circuitBreaker.recordFailure(toolName);
            log.error("Tool {} threw exception", toolName, e);

            return Map.of(
                    "success", false,
                    "error", e.getMessage()
            );
        }
    }

    /**
     * Execute with retry logic.
     */
    public Map<String, Object> executeToolWithRetry(
            AgentActionType actionType,
            Map<String, Object> parameters,
            int maxRetries) {

        int attempt = 0;
        Map<String, Object> lastResult = null;

        while (attempt < maxRetries) {
            attempt++;
            lastResult = executeTool(actionType, parameters);

            // Success - return
            if (lastResult.get("success") == Boolean.TRUE) {
                if (attempt > 1) {
                    log.info("✅ {} succeeded after {} attempts", actionType, attempt);
                }
                return lastResult;
            }

            // Circuit breaker open - stop retrying
            if (lastResult.containsKey("circuitBreakerOpen")) {
                log.warn("Circuit open, stopping retries for {}", actionType);
                return lastResult;
            }

            // Retry with exponential backoff
            if (attempt < maxRetries) {
                long backoffMs = (long) (Math.pow(2, attempt) * 1000);
                log.warn("⚠️ {} failed (attempt {}/{}), retry in {}ms",
                        actionType, attempt, maxRetries, backoffMs);
                try {
                    Thread.sleep(backoffMs);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }

        return lastResult;
    }

    public List<AgentTool> getAllTools() {
        return List.copyOf(tools.values());
    }

    public AgentCircuitBreaker.State getCircuitState(AgentActionType actionType) {
        AgentTool tool = tools.get(actionType);
        if (tool == null) return null;
        return circuitBreaker.getState(tool.getClass().getSimpleName());
    }

    /**
     * Get all registered tools.
     *
     * @return Unmodifiable list of all tools
     */
  /*  public List<AgentTool> getAllTools() {
        return new ArrayList<>(tools.values());
    }*/

    /**
     * Check if tool is registered.
     *
     * @param actionType Action type
     * @return true if tool exists
     */
    public boolean hasToolFor(AgentActionType actionType) {
        return tools.containsKey(actionType);
    }

    /**
     * Get count of registered tools.
     *
     * @return Number of tools
     */
    public int getToolCount() {
        return tools.size();
    }

    /**
     * Get tool catalog for AI context.
     *
     * Returns formatted string describing all available tools.
     * This is included in AI prompts so agent knows what it can do.
     *
     * @return Tool catalog as formatted string
     */
    public String getToolCatalog() {
        StringBuilder catalog = new StringBuilder();
        catalog.append("=== AVAILABLE TOOLS ===\n\n");

        tools.values().stream()
                .sorted(Comparator.comparing(AgentTool::getName))
                .forEach(tool -> {
                    catalog.append("Tool: ").append(tool.getName()).append("\n");
                    catalog.append("  Action: ").append(tool.getActionType()).append("\n");
                    catalog.append("  Description: ").append(tool.getDescription()).append("\n");
                    catalog.append("  Parameters:\n");

                    tool.getParameterSchema().forEach((param, desc) -> {
                        catalog.append("    - ").append(param).append(": ").append(desc).append("\n");
                    });

                    catalog.append("\n");
                });

        return catalog.toString();
    }

    /**
     * Get tools grouped by category.
     *
     * Useful for organizing tool display in UI.
     *
     * @return Map of category to tools
     */
    public Map<String, List<AgentTool>> getToolsByCategory() {
        Map<String, List<AgentTool>> byCategory = new HashMap<>();

        tools.values().forEach(tool -> {
            String category = categorizeAction(tool.getActionType());
            byCategory.computeIfAbsent(category, k -> new ArrayList<>()).add(tool);
        });

        return byCategory;
    }

    /**
     * Get available action types.
     *
     * @return Set of all action types with registered tools
     */
    public Set<AgentActionType> getAvailableActionTypes() {
        return new HashSet<>(tools.keySet());
    }

    /**
     * Categorize action type for organization.
     */
    private String categorizeAction(AgentActionType actionType) {
        String name = actionType.name();

        if (name.startsWith("FETCH_") || name.startsWith("QUERY_")) {
            return "Data Retrieval";
        } else if (name.startsWith("GENERATE_") || name.startsWith("ANALYZE_")) {
            return "AI Operations";
        } else if (name.contains("GIT") || name.startsWith("CREATE_BRANCH") ||
                name.startsWith("COMMIT_") || name.startsWith("MERGE_")) {
            return "Git Operations";
        } else if (name.startsWith("EXECUTE_") || name.startsWith("VALIDATE_")) {
            return "Test Execution";
        } else if (name.contains("FILE")) {
            return "File Operations";
        } else if (name.contains("APPROVAL")) {
            return "Approval Workflow";
        } else if (name.contains("JIRA")) {
            return "JIRA Integration";
        } else {
            return "Other";
        }
    }
}