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

import java.util.*;

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
                                    
                                    CRITICAL - SUPPORTED ACTIONS ONLY (use EXACTLY these names):
                                    - navigate: {"action": "navigate", "value": "https://url"}
                                    - type: {"action": "type", "locator": "css=[data-test='field']", "value": "text"}
                                    - click: {"action": "click", "locator": "css=button#submit"}
                                    - wait: {"action": "wait", "locator": "css=.element", "timeout": 5} OR {"action": "wait", "value": "3000"} for sleep
                                    - waitForLoadState: {"action": "waitForLoadState"} waits for page load
                                    - assertvisible: {"action": "assertvisible", "locator": "css=.element"}
                                    - asserttext: {"action": "asserttext", "locator": "css=.el", "value": "expected"}
                                    - select: {"action": "select", "locator": "css=select", "value": "option"}
                                    - check / uncheck: for checkboxes
                                    
                                    DO NOT use: waitForPageLoad, waitForNetworkIdle, login, checkNotNull, or any other action names.
                                    For locators, use CSS format: css=[data-test='x'], css=#id, css=.class, or role=button[name='Submit']
                                    
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
            ATTEMPT 1 — Add a wait ONLY before the step that failed:
            - Read FAILURE MESSAGES above to find WHICH step failed
              (e.g. "Run 2: Element not found: css=.inventory_list")
            - Insert ONE wait immediately before that specific failing step ONLY
            - Format: {"action": "wait", "locator": "css=.element", "timeout": 5000}
            - Do NOT add waits before steps that already have a wait before them
            - Do NOT pair a wait AND assertvisible on the same locator — use assertvisible only
            - Do NOT change any other steps
            """;
            case 2 -> """
            ATTEMPT 2 — Add waitForLoadState after every navigate step:
            - Insert {"action": "waitForLoadState"} immediately after each navigate step
            - Also increase the timeout from Attempt 1 from 5000 to 10000
            - No other changes
            """;
            default -> """
            ATTEMPT 3 — Maximum stability, still targeted:
            - Add {"action": "wait", "value": "2000"} (2s sleep) before the failing step
            - Set all existing timeout values to 15000
            - Add waitForLoadState after every navigate step if not already present
            - Keep all other steps unchanged
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

        Map<String, Object> parsed = objectMapper.readValue(cleaned.trim(), Map.class);

        // Normalize: AI sometimes returns fixedTestCode nested inside recommendedFixes
        // Expected: {"fixedTestCode": "...", "fixStrategy": "...", "confidence": 0.85}
        // Actual AI variants:
        //   {"recommendedFixes": {"fixedTestCode": {...}}}
        //   {"fixedTestCode": {...}}  (object instead of string)
        if (!parsed.containsKey("fixedTestCode") && parsed.containsKey("recommendedFixes")) {
            Object recommendedFixes = parsed.get("recommendedFixes");
            if (recommendedFixes instanceof Map) {
                Map<String, Object> fixes = (Map<String, Object>) recommendedFixes;
                if (fixes.containsKey("fixedTestCode")) {
                    parsed.put("fixedTestCode", fixes.get("fixedTestCode"));
                }
            }
        }

        // In GenerateFixTool.java — parseFixResponse() method

// Deduplicate: remove wait steps immediately before assertvisible on same locator
        Object fixedTestCodeRaw = parsed.get("fixedTestCode");
        if (fixedTestCodeRaw instanceof String fixedJson) {
            try {
                Map<String, Object> testContent = objectMapper.readValue(fixedJson, Map.class);
                List<Map<String, Object>> steps = (List<Map<String, Object>>) testContent.get("steps");

                if (steps != null && steps.size() > 1) {
                    List<Map<String, Object>> deduped = new ArrayList<>();
                    for (int i = 0; i < steps.size(); i++) {
                        Map<String, Object> current = steps.get(i);
                        Map<String, Object> next = (i + 1 < steps.size()) ? steps.get(i + 1) : null;

                        // Drop wait if the next step is assertvisible on the same locator
                        if ("wait".equals(current.get("action"))
                                && next != null
                                && "assertvisible".equals(next.get("action"))
                                && Objects.equals(current.get("locator"), next.get("locator"))) {
                            log.debug("🧹 Removed redundant wait before assertvisible: {}", current.get("locator"));
                            continue;
                        }

                        // Drop consecutive exact duplicates (same action + locator + value)
                        if (!deduped.isEmpty()) {
                            Map<String, Object> prev = deduped.get(deduped.size() - 1);
                            if (Objects.equals(prev.get("action"), current.get("action"))
                                    && Objects.equals(prev.get("locator"), current.get("locator"))
                                    && Objects.equals(prev.get("value"), current.get("value"))) {
                                log.debug("🧹 Removed duplicate step: {}", current.get("action"));
                                continue;
                            }
                        }
                        deduped.add(current);
                    }
                    if (deduped.size() != steps.size()) {
                        testContent.put("steps", deduped);
                        parsed.put("fixedTestCode", objectMapper.writeValueAsString(testContent));
                        log.info("🧹 Deduplication: {} → {} steps", steps.size(), deduped.size());
                    }
                }
            } catch (Exception e) {
                log.warn("⚠️ Could not deduplicate steps: {}", e.getMessage());
            }
        }

        return parsed;
    }
}