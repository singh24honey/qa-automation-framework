package com.company.qa.service.agent.tool.impl;

import com.company.qa.model.dto.request.TestGenerationRequest;
import com.company.qa.model.dto.response.TestGenerationResponse;
import com.company.qa.model.entity.AIGeneratedTest;
import com.company.qa.model.enums.AgentActionType;
import com.company.qa.service.agent.tool.AgentTool;
import com.company.qa.service.agent.tool.AgentToolRegistry;
import com.company.qa.service.ai.AITestGenerationService;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * Tool for generating Playwright test code using AI from JIRA stories.
 *
 * Uses existing AITestGenerationService which provides complete workflow:
 * - Fetches JIRA story
 * - Uses PlaywrightContextBuilder for optimized prompts
 * - Calls AIGatewayService (with sanitization, rate limiting, validation)
 * - Saves to database (AIGeneratedTest entity)
 * - Writes files to draft folder
 * - Tracks costs and quality metrics
 * - Includes retry logic
 *
 * This is the recommended approach - reuses all existing infrastructure!
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class GenerateTestCodeTool implements AgentTool {

    private final AITestGenerationService aiTestGenerationService;
    private final AgentToolRegistry toolRegistry;

    @PostConstruct
    public void register() {
        toolRegistry.registerTool(this);
    }

    @Override
    public AgentActionType getActionType() {
        return AgentActionType.GENERATE_TEST_CODE;
    }

    @Override
    public String getName() {
        return "AI Test Code Generator";
    }

    @Override
    public String getDescription() {
        return "Generates Playwright test code from JIRA story using AI. " +
                "Uses PlaywrightContextBuilder for optimized prompts with role-based locators. " +
                "Includes automatic quality assessment, cost tracking, and file writing. " +
                "Returns test code ready for review. Use this after fetching JIRA story.";
    }

    @Override
    public Map<String, Object> execute(Map<String, Object> parameters) {
        String jiraKey = (String) parameters.get("jiraKey");
        String frameworkStr = (String) parameters.getOrDefault("framework", "PLAYWRIGHT");
        String testTypeStr = (String) parameters.getOrDefault("testType", "UI");
        String userPrompt = (String) parameters.get("userPrompt");

        try {
            // 1. Convert string parameters to enums
            AIGeneratedTest.TestFramework framework;
            try {
                framework = AIGeneratedTest.TestFramework.valueOf(frameworkStr.toUpperCase());
            } catch (IllegalArgumentException e) {
                framework = AIGeneratedTest.TestFramework.PLAYWRIGHT;
                log.warn("Invalid framework '{}', defaulting to PLAYWRIGHT", frameworkStr);
            }

            AIGeneratedTest.TestType testType;
            try {
                testType = AIGeneratedTest.TestType.valueOf(testTypeStr.toUpperCase());
            } catch (IllegalArgumentException e) {
                testType = AIGeneratedTest.TestType.UI;
                log.warn("Invalid testType '{}', defaulting to UI", testTypeStr);
            }

            // 2. Build request using existing DTO
            TestGenerationRequest request = TestGenerationRequest.builder()
                    .jiraStoryKey(jiraKey)
                    .testFramework(framework)
                    .testType(testType)
                    .UserPrompt(userPrompt)  // Note: The field has capital U (typo in original code)
                    .skipQualityCheck(false)  // Enable quality assessment
                    .build();

            log.info("Generating {} test for: {} using {}", testType, jiraKey, framework);

            // 3. Call AITestGenerationService (handles everything!)
            // This internally:
            // - Fetches JIRA story
            // - Uses PlaywrightContextBuilder.buildContext() for Playwright
            // - Calls AIGatewayService.generateTest()
            // - Does quality assessment
            // - Saves to database
            // - Writes to draft folder
            // - Tracks costs
            TestGenerationResponse response = aiTestGenerationService.generateTestFromStory(request);

            // 4. Check if generation was successful
            if (!response.isSuccess()) {
                log.error("Test generation failed for {}: {}", jiraKey, response.getMessage());

                Map<String, Object> result = new HashMap<>();
                result.put("success", false);
                result.put("error", response.getMessage());
                return result;
            }

            // 5. Extract test code from response
            // The testCode is a Map<String, Object> containing the generated files
            Map<String, Object> testCode = response.getTestCode();
            //String testClassName = response.getTestName();
            String testFilePath = response.getDraftFolderPath();

            // 6. Build success result
            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("testCode", extractMainTestCode(testCode));  // Extract main test file
            result.put("testCodeMap", testCode);  // Full map (may contain multiple files)
           // result.put("testClassName", testClassName);
            result.put("testFilePath", testFilePath);
            result.put("jiraKey", jiraKey);
            result.put("framework", framework.name());
            result.put("testType", testType.name());

            String testClassName = generateTestClassName(jiraKey);
            result.put("testClassName", testClassName);
            result.put("fileName", testClassName + ".java");

            // AI metadata
            result.put("aiCost", response.getTotalCostUsd());
            result.put("tokensUsed", response.getPromptTokens() + response.getCompletionTokens());
            result.put("promptTokens", response.getPromptTokens());
            result.put("completionTokens", response.getCompletionTokens());

            // Quality metrics
            result.put("qualityScore", response.getQualityScore());
            result.put("confidenceLevel", response.getConfidenceLevel());
            result.put("qualityConcerns", response.getQualityConcerns());

            // Generated test ID
            result.put("generatedTestId", response.getTestId());

            log.info("✅ Generated test for {}: {} (Quality: {}, Cost: ${})",
                    jiraKey,
                    testClassName,
                    response.getQualityScore(),
                    response.getTotalCostUsd());

            return result;

        } catch (Exception e) {
            log.error("❌ Failed to generate test for {}", jiraKey, e);

            Map<String, Object> result = new HashMap<>();
            result.put("success", false);
            result.put("error", e.getMessage());
            result.put("errorType", e.getClass().getSimpleName());
            return result;
        }
    }

    @Override
    public boolean validateParameters(Map<String, Object> parameters) {
        return parameters != null &&
                parameters.containsKey("jiraKey") &&
                parameters.get("jiraKey") instanceof String;
    }

    @Override
    public Map<String, String> getParameterSchema() {
        Map<String, String> schema = new HashMap<>();
        schema.put("jiraKey", "string (required) - JIRA story key (e.g., 'PROJ-123')");
        schema.put("framework", "string (optional) - Test framework ('PLAYWRIGHT', 'CUCUMBER', 'TESTNG'). Default: PLAYWRIGHT");
        schema.put("testType", "string (optional) - Test type ('UI', 'API', 'E2E'). Default: UI");
        schema.put("userPrompt", "string (optional) - Additional user instructions for AI");
        return schema;
    }

    /**
     * Extract main test code from testCode map.
     *
     * The testCode map can contain:
     * - For Playwright: { "testClass": "...", "pageObjects": {...} }
     * - For Cucumber: { "featureFile": "...", "stepDefinitions": [...], "pageObjects": [...] }
     *
     * We extract the main test file content.
     */
    private String extractMainTestCode(Map<String, Object> testCode) {
        if (testCode == null || testCode.isEmpty()) {
            return "";
        }

        // For Playwright: Extract testClass
        if (testCode.containsKey("testClass")) {
            return (String) testCode.get("testClass");
        }

        // For Cucumber: Extract featureFile
        if (testCode.containsKey("featureFile")) {
            return (String) testCode.get("featureFile");
        }

        // Fallback: Return first string value found
        return testCode.values().stream()
                .filter(v -> v instanceof String)
                .map(v -> (String) v)
                .findFirst()
                .orElse("");
    }

    /**
     * Generate test class name from JIRA key
     * SAUCE-001 → Sauce001Test
     * SAUCE-LOGIN → SauceLoginTest
     */
    private String generateTestClassName(String jiraKey) {
        // Remove hyphen and capitalize
        String[] parts = jiraKey.split("-");
        StringBuilder className = new StringBuilder();

        for (String part : parts) {
            className.append(part.substring(0, 1).toUpperCase())
                    .append(part.substring(1).toLowerCase());
        }

        className.append("Test");
        return className.toString();
    }
}