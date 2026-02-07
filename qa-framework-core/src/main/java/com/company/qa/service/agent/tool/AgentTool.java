package com.company.qa.service.agent.tool;

import com.company.qa.model.enums.AgentActionType;

import java.util.Map;

/**
 * Interface for tools that agents can use.
 *
 * A "tool" is any service/capability that an agent can invoke.
 * Tools wrap existing framework services to make them discoverable by agents.
 *
 * Examples:
 * - JiraService → FETCH_JIRA_STORY tool
 * - GitService → COMMIT_CHANGES tool
 * - PlaywrightExecutor → EXECUTE_TEST tool
 * - AIGatewayService → GENERATE_TEST_CODE tool
 *
 * Tools are auto-registered with AgentToolRegistry at startup.
 */
public interface AgentTool {

    /**
     * Get the action type this tool handles.
     *
     * Must be unique across all tools.
     */
    AgentActionType getActionType();

    /**
     * Get human-readable tool name.
     *
     * Used in logs and AI prompts.
     */
    String getName();

    /**
     * Get tool description for AI context.
     *
     * Explains what the tool does and when to use it.
     * This is included in AI prompts so agent knows capabilities.
     */
    String getDescription();

    /**
     * Execute the tool with given parameters.
     *
     * @param parameters Tool-specific parameters
     * @return Tool execution result as Map
     * @throws RuntimeException if execution fails
     */
    Map<String, Object> execute(Map<String, Object> parameters);

    /**
     * Validate parameters before execution.
     *
     * Checks that all required parameters are present and valid.
     *
     * @param parameters Parameters to validate
     * @return true if valid, false otherwise
     */
    boolean validateParameters(Map<String, Object> parameters);

    /**
     * Get parameter schema for AI prompts.
     *
     * Describes what parameters this tool expects.
     * Format: {"paramName": "type (required|optional) - description"}
     *
     * Example:
     * {
     *   "jiraKey": "string (required) - JIRA story key like 'PROJ-123'",
     *   "fields": "array (optional) - Fields to include in response"
     * }
     *
     * @return Parameter schema
     */
    Map<String, String> getParameterSchema();
}