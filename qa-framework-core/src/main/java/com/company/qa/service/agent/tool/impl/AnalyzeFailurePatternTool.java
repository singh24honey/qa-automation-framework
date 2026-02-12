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
 * Agent tool to analyze failure patterns using AI.
 *
 * Input parameters:
 * - stabilityResult: StabilityAnalysisResult JSON from previous tool
 * - testCode: The actual test code (optional)
 *
 * Output:
 * - success: true/false
 * - stabilityResult: Updated StabilityAnalysisResult with root cause
 * - rootCause: FlakyRootCause enum value
 * - explanation: AI explanation
 * - error: error message if failed
 *
 * @author QA Framework
 * @since Week 16 Day 1
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AnalyzeFailurePatternTool implements AgentTool {

    private final AIGatewayService aiGateway;
    private final ObjectMapper objectMapper;
    private final AgentToolRegistry toolRegistry;

    @PostConstruct
    public void register() {
        toolRegistry.registerTool(this);
    }

    @Override
    public AgentActionType getActionType() {
        return AgentActionType.ANALYZE_FAILURE;
    }

    @Override
    public String getName() {
        return "Failure Pattern Analyzer";
    }

    @Override
    public String getDescription() {
        return "Uses AI to analyze failure patterns and identify root cause category " +
                "(timing, data, environment, locator). Returns categorization and fix recommendation.";
    }

    @Override
    public Map<String, Object> execute(Map<String, Object> parameters) {
        log.info("🤖 Analyzing failure pattern with AI: {}", parameters);

        try {
            // Extract parameters
            String stabilityResultJson = (String) parameters.get("stabilityResult");
            StabilityAnalysisResult stabilityResult = objectMapper.readValue(
                    stabilityResultJson, StabilityAnalysisResult.class
            );

            String testCode = (String) parameters.getOrDefault("testCode", "Not provided");

            // Build AI prompt
            String prompt = buildAnalysisPrompt(stabilityResult, testCode);

            SecureAIRequest aiRequest = SecureAIRequest.builder()
                    .content(prompt)                          // ✅ CHANGE: .prompt → .content
                    .operationType(SecureAIRequest.OperationType.FAILURE_ANALYSIS)  // ✅ ADD THIS
                    .userId(UUID.randomUUID())                // ✅ ADD THIS (or get from context)
                    .userRole(UserRole.QA_ENGINEER)           // ✅ ADD THIS (or get from context)
                    .testName(stabilityResult.getTestName())           // ✅ ADD THIS
                    .build();

            SecureAIResponse aiResponse = aiGateway.analyzeFailure(aiRequest);  // ✅ CHANGE: processRequest → analyzeFailure

            if (!aiResponse.isSuccess()) {
                Map<String, Object> result = new HashMap<>();
                result.put("success", false);
                result.put("error", "AI analysis failed: " + aiResponse.getErrorMessage());
                return result;
            }

            // Parse AI response
            Map<String, Object> analysis = parseAIResponse(aiResponse.getContent());  // ✅ CHANGE: getSanitizedResponse → getContent

            // Update stability result with AI findings
            stabilityResult.setRootCause(
                    FlakyRootCause.valueOf((String) analysis.get("rootCause"))
            );
            stabilityResult.setRootCauseExplanation((String) analysis.get("explanation"));
            stabilityResult.setRecommendedFix((String) analysis.get("recommendedFix"));

            log.info("✅ Failure pattern analyzed: {} - {}",
                    stabilityResult.getTestName(),
                    stabilityResult.getRootCause());

            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("stabilityResult", objectMapper.writeValueAsString(stabilityResult));
            result.put("rootCause", stabilityResult.getRootCause().name());
            result.put("explanation", stabilityResult.getRootCauseExplanation());
            result.put("recommendedFix", stabilityResult.getRecommendedFix());

            return result;

        } catch (Exception e) {
            log.error("❌ Failure pattern analysis failed: {}", e.getMessage(), e);

            Map<String, Object> result = new HashMap<>();
            result.put("success", false);
            result.put("error", e.getMessage());
            return result;
        }
    }

    @Override
    public boolean validateParameters(Map<String, Object> parameters) {
        if (parameters == null || !parameters.containsKey("stabilityResult")) {
            return false;
        }

        Object stabilityResult = parameters.get("stabilityResult");
        return stabilityResult instanceof String && !((String) stabilityResult).isBlank();
    }

    @Override
    public Map<String, String> getParameterSchema() {
        Map<String, String> schema = new HashMap<>();
        schema.put("stabilityResult", "string (required) - JSON string of StabilityAnalysisResult");
        schema.put("testCode", "string (optional) - The test code content for better analysis");
        return schema;
    }

    /**
     * Build prompt for AI analysis.
     */
    private String buildAnalysisPrompt(StabilityAnalysisResult result, String testCode) {
        return String.format("""
            You are a QA automation expert analyzing a flaky test.
            
            TEST INFORMATION:
            - Test Name: %s
            - Total Runs: %d
            - Passed: %d
            - Failed: %d
            - Pattern: %s (P=Pass, F=Fail)
            
            FAILURE MESSAGES:
            %s
            
            TEST CODE:
            %s
            
            TASK:
            Analyze this flaky test and identify the root cause category.
            
            ROOT CAUSE CATEGORIES:
            1. TIMING_ISSUE - Race conditions, async operations, insufficient waits
            2. DATA_DEPENDENCY - Test data conflicts, cleanup issues, shared state
            3. ENVIRONMENT_DEPENDENCY - External services, network issues, environment-specific failures
            4. LOCATOR_BRITTLENESS - Unreliable element locators, dynamic IDs, DOM changes
            5. UNKNOWN - Multiple causes or unable to determine
            
            RESPONSE FORMAT (JSON only, no markdown):
            {
              "rootCause": "TIMING_ISSUE",
              "explanation": "Brief explanation of why you categorized it this way",
              "recommendedFix": "Specific fix strategy for this root cause"
            }
            
            Return ONLY the JSON object, no additional text.
            """,
                result.getTestName(),
                result.getTotalRuns(),
                result.getPassedRuns(),
                result.getFailedRuns(),
                result.getPattern(),
                String.join("\n", result.getErrorMessages()),
                testCode
        );
    }

    /**
     * Parse AI response JSON.
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> parseAIResponse(String response) throws Exception {
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