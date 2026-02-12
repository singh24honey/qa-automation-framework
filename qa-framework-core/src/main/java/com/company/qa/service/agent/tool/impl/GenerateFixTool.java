package com.company.qa.service.agent.tool.impl;

import com.company.qa.model.agent.StabilityAnalysisResult;
import com.company.qa.model.dto.SecureAIRequest;
import com.company.qa.model.dto.SecureAIResponse;
import com.company.qa.model.enums.AgentActionType;
import com.company.qa.model.enums.FlakyRootCause;
import com.company.qa.model.enums.UserRole;
import com.company.qa.service.agent.tool.AgentTool;
import com.company.qa.service.agent.tool.AgentToolRegistry;
import com.company.qa.service.ai.AIGatewayService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Agent tool to generate fixes for flaky tests based on root cause.
 *
 * Input parameters:
 * - stabilityResult: StabilityAnalysisResult JSON with root cause
 * - testCode: Current test code content
 * - attemptNumber: Which fix attempt this is (1-3)
 *
 * Output:
 * - success: true/false
 * - fixedTestCode: Modified test code with fix applied
 * - fixStrategy: Description of changes made
 * - confidence: AI confidence in fix (0.0-1.0)
 * - error: Error message if failed
 *
 * @author QA Framework
 * @since Week 16 Day 2
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GenerateFixTool implements AgentTool {

    private final AIGatewayService aiGateway;
    private final ObjectMapper objectMapper;
    private final AgentToolRegistry toolRegistry;

    @PostConstruct
    public void register() {
        toolRegistry.registerTool(this);
    }

    @Override
    public AgentActionType getActionType() {
        return AgentActionType.SUGGEST_FIX;
    }

    @Override
    public String getName() {
        return "Flaky Test Fix Generator";
    }

    @Override
    public String getDescription() {
        return "Generates a code fix for a flaky test based on the identified root cause. " +
                "Returns modified test code with explanation of changes. " +
                "Supports multiple fix attempts with different strategies.";
    }

    @Override
    public Map<String, Object> execute(Map<String, Object> parameters) {
        log.info("🔧 Generating fix for flaky test: {}", parameters.keySet());

        try {
            // Extract parameters
            String stabilityResultJson = (String) parameters.get("stabilityResult");
            String testCode = (String) parameters.get("testCode");
            int attemptNumber = parameters.containsKey("attemptNumber")
                    ? ((Number) parameters.get("attemptNumber")).intValue()
                    : 1;

            StabilityAnalysisResult result = objectMapper.readValue(
                    stabilityResultJson, StabilityAnalysisResult.class
            );

            // Build fix prompt based on root cause and attempt number
            String prompt = buildFixPrompt(result, testCode, attemptNumber);

            log.info("Generating fix (attempt {}) for {} - Root cause: {}",
                    attemptNumber, result.getTestName(), result.getRootCause());

            // Call AI to generate fix
            SecureAIRequest aiRequest = SecureAIRequest.builder()
                    .content(prompt)
                    .operationType(SecureAIRequest.OperationType.FIX_SUGGESTION)
                    .userId(UUID.randomUUID())
                    .userRole(UserRole.QA_ENGINEER)
                    .testName(result.getTestName())
                    .build();

            SecureAIResponse aiResponse = aiGateway.analyzeFailure(aiRequest);

            if (!aiResponse.isSuccess()) {
                Map<String, Object> failResult = new HashMap<>();
                failResult.put("success", false);
                failResult.put("error", "AI fix generation failed: " + aiResponse.getErrorMessage());
                return failResult;
            }

            // Parse AI response
            Map<String, Object> fixResult = parseFixResponse(aiResponse.getContent());

            log.info("✅ Fix generated successfully for: {} (Strategy: {})",
                    result.getTestName(), fixResult.get("fixStrategy"));

            Map<String, Object> successResult = new HashMap<>();
            successResult.put("success", true);
            successResult.put("fixedTestCode", fixResult.get("fixedTestCode"));
            successResult.put("fixStrategy", fixResult.get("fixStrategy"));
            successResult.put("confidence", fixResult.get("confidence"));
            successResult.put("attemptNumber", attemptNumber);

            return successResult;

        } catch (Exception e) {
            log.error("❌ Fix generation failed: {}", e.getMessage(), e);

            Map<String, Object> result = new HashMap<>();
            result.put("success", false);
            result.put("error", e.getMessage());
            return result;
        }
    }

    @Override
    public boolean validateParameters(Map<String, Object> parameters) {
        return parameters != null &&
                parameters.containsKey("stabilityResult") &&
                parameters.containsKey("testCode");
    }

    @Override
    public Map<String, String> getParameterSchema() {
        Map<String, String> schema = new HashMap<>();
        schema.put("stabilityResult", "string (required) - JSON of StabilityAnalysisResult");
        schema.put("testCode", "string (required) - Current test code content");
        schema.put("attemptNumber", "integer (optional) - Fix attempt number (default: 1)");
        return schema;
    }

    /**
     * Build fix generation prompt based on root cause.
     */
    private String buildFixPrompt(StabilityAnalysisResult result, String testCode, int attemptNumber) {
        FlakyRootCause rootCause = result.getRootCause();

        String strategyGuidance = getStrategyGuidance(rootCause, attemptNumber);

        return String.format("""
            You are a QA automation expert fixing a flaky test.
            
            TEST INFORMATION:
            - Test Name: %s
            - Root Cause: %s
            - Flakiness Pattern: %s
            - Failed Runs: %d/%d
            - This is fix attempt #%d
            
            CURRENT TEST CODE:
```json
            %s
```
            
            FAILURE MESSAGES:
            %s
            
            FIX STRATEGY GUIDANCE:
            %s
            
            TASK:
            Generate a fixed version of the test code that addresses the %s issue.
            
            REQUIREMENTS:
            1. Maintain the same JSON structure: {"steps": [...]}
            2. Each step must have: action, locator (if applicable), value (if applicable)
            3. Add appropriate waits, retries, or improved locators based on root cause
            4. If attempt #%d, try a DIFFERENT strategy than previous attempts
            5. Keep changes minimal - only fix what's needed
            
            RESPONSE FORMAT (JSON only, no markdown):
            {
              "fixedTestCode": "...complete JSON with {"steps": [...]}...",
              "fixStrategy": "Brief description of what was changed and why",
              "confidence": 0.85
            }
            
            Return ONLY the JSON object, no additional text or code blocks.
            """,
                result.getTestName(),
                rootCause.name(),
                result.getPattern(),
                result.getFailedRuns(),
                result.getTotalRuns(),
                attemptNumber,
                testCode,
                String.join("\n", result.getErrorMessages()),
                strategyGuidance,
                rootCause.getDisplayName(),
                attemptNumber
        );
    }

    /**
     * Get strategy guidance based on root cause and attempt number.
     */
    private String getStrategyGuidance(FlakyRootCause rootCause, int attemptNumber) {
        return switch (rootCause) {
            case TIMING_ISSUE -> getTimingFixStrategies(attemptNumber);
            case DATA_DEPENDENCY -> getDataFixStrategies(attemptNumber);
            case ENVIRONMENT_DEPENDENCY -> getEnvironmentFixStrategies(attemptNumber);
            case LOCATOR_BRITTLENESS -> getLocatorFixStrategies(attemptNumber);
            case UNKNOWN -> getGenericFixStrategies(attemptNumber);
        };
    }

    private String getTimingFixStrategies(int attempt) {
        return switch (attempt) {
            case 1 -> """
                ATTEMPT 1 - Add explicit waits:
                - Add wait steps before flaky actions: {"action": "wait", "locator": "css=.element", "timeout": 5000}
                - Use waitForLoadState after navigation
                - Increase timeout values on existing wait steps
                """;
            case 2 -> """
                ATTEMPT 2 - Add retry logic:
                - Wrap flaky steps in retry loops (not directly supported in JSON, but add multiple wait attempts)
                - Add wait for network idle: {"action": "wait", "locator": "networkidle", "timeout": 10000}
                - Double all timeout values
                """;
            default -> """
                ATTEMPT 3 - Maximum stability:
                - Add wait before AND after flaky steps
                - Use longest reasonable timeouts (10-15 seconds)
                - Add sleep/delay steps if needed: {"action": "wait", "value": 3000}
                """;
        };
    }

    private String getDataFixStrategies(int attempt) {
        return switch (attempt) {
            case 1 -> """
                ATTEMPT 1 - Add unique identifiers:
                - Append timestamp or random numbers to test data values
                - Example: Instead of "value": "testuser", use "value": "testuser_12345"
                """;
            case 2 -> """
                ATTEMPT 2 - Add cleanup steps:
                - Add steps at the end to clean up test data
                - Clear form fields after use
                - Add logout/reset steps
                """;
            default -> """
                ATTEMPT 3 - Isolate test data:
                - Use completely unique values for all data fields
                - Add clear/reset steps before critical actions
                - Ensure no data dependencies between steps
                """;
        };
    }

    private String getEnvironmentFixStrategies(int attempt) {
        return switch (attempt) {
            case 1 -> """
                ATTEMPT 1 - Add health checks:
                - Add wait for page load: {"action": "wait", "locator": "load", "timeout": 5000}
                - Wait for network idle before critical actions
                """;
            case 2 -> """
                ATTEMPT 2 - Add retries:
                - Add extra wait steps with longer timeouts
                - Add page reload before flaky sections
                """;
            default -> """
                ATTEMPT 3 - Maximum resilience:
                - Add waits everywhere
                - Add page reload steps strategically
                - Use longest timeouts
                """;
        };
    }

    private String getLocatorFixStrategies(int attempt) {
        return switch (attempt) {
            case 1 -> """
                ATTEMPT 1 - Use stable locators:
                - Replace CSS/XPath with role-based locators
                - Example: Change "css=#dynamic-id" to "role=button[name='Submit']"
                - Use testId attributes if available: "[data-testid='button']"
                """;
            case 2 -> """
                ATTEMPT 2 - Add fallback locators:
                - If role doesn't work, try text-based: "text=Submit"
                - Try multiple locator strategies in sequence
                """;
            default -> """
                ATTEMPT 3 - Most generic locators:
                - Use text content: "text=Button Text"
                - Use partial matches if needed
                - Add waits before all click/type actions
                """;
        };
    }

    private String getGenericFixStrategies(int attempt) {
        return """
            UNKNOWN ROOT CAUSE - Apply general stability improvements:
            - Add waits before all actions
            - Increase all timeout values
            - Add wait for page load and network idle
            - Use more stable locators where possible
            """;
    }

    /**
     * Parse AI response JSON.
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> parseFixResponse(String response) throws Exception {
        // Remove markdown code blocks if present
        String cleaned = response.trim();
        if (cleaned.startsWith("```json")) {
            cleaned = cleaned.substring(7);
        }
        if (cleaned.startsWith("```")) {
            cleaned = cleaned.substring(3);
        }
        if (cleaned.endsWith("```")) {
            cleaned = cleaned.substring(0, cleaned.length() - 3);
        }

        return objectMapper.readValue(cleaned.trim(), Map.class);
    }
}