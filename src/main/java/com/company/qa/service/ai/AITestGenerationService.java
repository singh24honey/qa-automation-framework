package com.company.qa.service.ai;

import com.company.qa.exception.TestGenerationException;
import com.company.qa.integration.jira.JiraStoryService;
import com.company.qa.model.dto.SecureAIRequest;
import com.company.qa.model.dto.SecureAIResponse;
import com.company.qa.model.dto.request.TestGenerationRequest;
import com.company.qa.model.dto.response.TestGenerationResponse;
import com.company.qa.model.entity.AIGeneratedTest;
import com.company.qa.model.entity.AIGeneratedTest.TestGenerationStatus;
import com.company.qa.model.entity.AITestGenerationAttempt;
import com.company.qa.model.entity.JiraStory;
import com.company.qa.model.enums.UserRole;
import com.company.qa.repository.AIGeneratedTestRepository;
import com.company.qa.repository.AITestGenerationAttemptRepository;
import com.company.qa.repository.JiraStoryRepository;
import com.company.qa.ai.service.AiRecommendationService;
import com.company.qa.service.TestFileWriterService;
import com.company.qa.service.context.JiraContextBuilder;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Core service for AI-powered test generation from JIRA stories.
 *
 * Architecture:
 * 1. Fetch JIRA story
 * 2. Build AI prompt using JiraContextBuilder (Week 9 Day 2)
 * 3. Call AI via AIGatewayService (Week 5) with security/validation
 * 4. Parse AI response into structured test code
 * 5. Assess quality using AIRecommendationService (Week 6)
 * 6. Save to database and draft folder
 * 7. Track costs using CostTracker (Week 7)
 * 8. Log attempt history for debugging
 *
 * Integrates with:
 * - Week 5: AIGatewayService (all AI calls, security, rate limiting)
 * - Week 6: AIRecommendationService (quality scoring)
 * - Week 7: CostTracker (cost tracking via AIGatewayService)
 * - Week 9 Day 2: JiraContextBuilder (prompt generation)
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class AITestGenerationService {

    private final JiraStoryRepository jiraStoryRepository;
    private final AIGeneratedTestRepository aiGeneratedTestRepository;
    private final AITestGenerationAttemptRepository attemptRepository;

    // Week 5: AI Gateway (handles all AI interactions with security)
    private final AIGatewayService aiGatewayService;

    // Week 6: Quality Assessment
    private final QualityAssessmentService qualityAssessmentService;

    // Week 9 Day 2: Context Builder
    private final JiraContextBuilder jiraContextBuilder;

    // File writer (to be implemented in Part 3)
    private final TestFileWriterService fileWriterService;

    private final JiraStoryService jiraStoryService;


    @Value("${ai.test-generation.quality.minimum-score:60.0}")
    private BigDecimal minimumQualityScore;

    @Value("${ai.test-generation.cost.max-per-test-usd:0.50}")
    private BigDecimal maxCostPerTest;

    @Value("${ai.test-generation.retry.max-attempts:3}")
    private int maxRetryAttempts;

    /**
     * Generate test from JIRA story with retry logic.
     */
    @Transactional
    public TestGenerationResponse generateTestFromStory(TestGenerationRequest request) {
        log.info("Starting test generation for story: {}", request.getJiraStoryKey());

        // 1. Validate and fetch JIRA story
        JiraStory story = jiraStoryRepository.findByJiraKey(request.getJiraStoryKey())
                .orElseGet(() -> jiraStoryService.fetchAndSaveFromJira(request.getJiraStoryKey()));

        // 2. Attempt generation with retry
        for (int attempt = 1; attempt <= maxRetryAttempts; attempt++) {
            try {
                log.info("Generation attempt {} of {} for {}",
                        attempt, maxRetryAttempts, request.getJiraStoryKey());

                TestGenerationResponse response = attemptGeneration(story, request, attempt);

                if (response.isSuccess()) {
                    log.info("Test generation successful on attempt {} for {}",
                            attempt, request.getJiraStoryKey());
                    return response;
                }

            } catch (Exception e) {
                log.warn("Generation attempt {} failed for {}: {}",
                        attempt, request.getJiraStoryKey(), e.getMessage());

                if (attempt == maxRetryAttempts) {
                    throw new TestGenerationException(
                            "Test generation failed after " + maxRetryAttempts + " attempts", e);
                }

                // Exponential backoff
                sleepBetweenRetries(attempt);
            }
        }

        throw new TestGenerationException("Test generation failed after all retry attempts");
    }

    /**
     * Single generation attempt with full flow.
     */
    private TestGenerationResponse attemptGeneration(
            JiraStory story,
            TestGenerationRequest request,
            int attemptNumber) {

        long startTime = System.currentTimeMillis();
        AITestGenerationAttempt attempt = createAttemptRecord(story, request, attemptNumber);

        try {
            // Step 1: Build AI prompt using JiraContextBuilder (Week 9 Day 2)
            String prompt = buildGenerationPrompt(story, request);
            attempt.setPromptContext(prompt);

            // Step 2: Call AI via AIGatewayService (Week 5)
            // UPDATED: Now returns SecureAIResponse instead of String
            SecureAIResponse aiResponse = callAI(prompt, request, attempt);

            // Step 3: Check if AI call was successful
            if (!aiResponse.isSuccess()) {
                throw new TestGenerationException(
                        "AI generation failed: " + aiResponse.getErrorMessage());
            }

            // Step 4: Extract content from response
            attempt.setRawResponse(aiResponse.getContent());

            // Step 5: Parse AI response into structured test code
            Map<String, Object> testCode = parseAIResponse(aiResponse.getContent(), request.getTestType());

            // Step 6: Create AI generated test entity
            AIGeneratedTest generatedTest = createGeneratedTestEntity(
                    story, request, testCode, attempt);

            // Step 7: Assess quality (Week 6 QualityAssessmentService)
            if (!request.isSkipQualityCheck()) {
                assessQuality(generatedTest, testCode);
            }

            // Step 8: Save to database
            generatedTest = aiGeneratedTestRepository.save(generatedTest);

            // Step 9: Write to draft folder
            String draftPath = fileWriterService.writeToDraftFolder(generatedTest, testCode);
            generatedTest.setDraftFolderPath(draftPath);
            aiGeneratedTestRepository.save(generatedTest);

            // Step 10: Mark attempt as successful
            attempt.setSuccess(true);
            attempt.setGeneratedTestId(generatedTest.getId());
            attempt.setDurationMs((int)(System.currentTimeMillis() - startTime));
            attemptRepository.save(attempt);

            log.info("Test generated successfully: {} (Quality: {})",
                    generatedTest.getTestName(), generatedTest.getQualityScore());

            return TestGenerationResponse.fromEntity(generatedTest);

        } catch (Exception e) {
            // Log failed attempt
            attempt.setSuccess(false);
            attempt.setErrorMessage(e.getMessage());
            attempt.setErrorType(categorizeError(e));
            attempt.setDurationMs((int)(System.currentTimeMillis() - startTime));
            attemptRepository.save(attempt);

            throw new TestGenerationException("Generation attempt failed", e);
        }
    }

    /**
     * Build AI prompt using JiraContextBuilder (Week 9 Day 2).
     */
    private String buildGenerationPrompt(JiraStory story, TestGenerationRequest request) {
        log.debug("Building generation prompt for {}", story.getJiraKey());

        // Use JiraContextBuilder to create base context
        String baseContext = jiraContextBuilder.buildMinimalPrompt(story);

        // Add test-specific instructions
        StringBuilder prompt = new StringBuilder();
        prompt.append(baseContext);
        prompt.append("\n\n");
        prompt.append("=== TEST GENERATION INSTRUCTIONS ===\n");
        prompt.append("Generate a ").append(request.getTestType()).append(" test using ");
        prompt.append(request.getTestFramework()).append(" framework.\n\n");

        // Framework-specific templates
        switch (request.getTestFramework()) {
            case CUCUMBER:
                prompt.append(getCucumberInstructions(request.getTestType()));
                break;
            case TESTNG:
                prompt.append(getTestNGInstructions(request.getTestType()));
                break;
            default:
                throw new TestGenerationException("Unsupported framework: " + request.getTestFramework());
        }

        // Add additional context if provided
        if (!request.getAdditionalContext().isEmpty()) {
            prompt.append("\n\n=== ADDITIONAL CONTEXT ===\n");
            request.getAdditionalContext().forEach((key, value) ->
                    prompt.append(key).append(": ").append(value).append("\n"));
        }

        // Output format instructions
        prompt.append("\n\n=== OUTPUT FORMAT ===\n");
        prompt.append("Return a JSON object with the following structure:\n");
        prompt.append("{\n");
        prompt.append("  \"featureFile\": \"<Gherkin feature file content>\",\n");
        prompt.append("  \"stepDefinitions\": [\"<Step definition class 1>\", \"<Step definition class 2>\"],\n");
        prompt.append("  \"pageObjects\": [\"<Page object class 1>\", \"<Page object class 2>\"]\n");
        prompt.append("}\n");

        return prompt.toString();
    }

    /**
     * Call AI via AIGatewayService (Week 5).
     * AIGatewayService handles:
     * - Data sanitization (PII/credential detection)
     * - Rate limiting
     * - Cost controls
     * - Response validation
     * - Cost tracking via CostTracker (Week 7)
     * - Approval request creation (Week 6)
     */
    private SecureAIResponse callAI(String prompt, TestGenerationRequest request, AITestGenerationAttempt attempt) {
        log.debug("Calling AI gateway for test generation");

        // Build SecureAIRequest (required by AIGatewayService)
        SecureAIRequest aiRequest = SecureAIRequest.builder()
                .userId(getCurrentUserId())  // Get from security context or use system user
                .userRole(getCurrentUserRole())  // Get from security context or use default
                .content(prompt)
                .framework(request.getTestFramework().name())
                .language("Java")  // Default language
                .targetUrl(null)  // Not applicable for JIRA-based generation
                .strictMode(true)  // Enable strict sanitization
                .operationType(SecureAIRequest.OperationType.TEST_GENERATION)
                .build();

        // Call AIGatewayService (Week 5)
        // This handles: sanitization, rate limits, cost checks, validation, approval creation
        SecureAIResponse response = aiGatewayService.generateTest(aiRequest);

        // Update attempt record with token usage and costs
        if (response.isSuccess()) {
            attempt.setPromptTokens(response.getTokensUsed() != 0 ?
                    (int)(response.getTokensUsed() * 0.3) : null);  // Estimate 30% prompt
            attempt.setCompletionTokens(response.getTokensUsed() != 0 ?
                    (int)(response.getTokensUsed() * 0.7) : null);  // Estimate 70% completion
            attempt.setTotalCostUsd(BigDecimal.valueOf(response.getEstimatedCost()));
        }

        return response;
    }

    /**
     * Get current user ID from security context.
     * Falls back to system user if no security context.
     */
    private UUID getCurrentUserId() {
        // TODO: Get from Spring Security context when authentication is implemented
        // For now, use a system user ID
        return UUID.fromString("00000000-0000-0000-0000-000000000001"); // System user
    }

    /**
     * Get current user role from security context.
     * Falls back to QA_ENGINEER if no security context.
     */
    private UserRole getCurrentUserRole() {
        // TODO: Get from Spring Security context when authentication is implemented
        // For now, default to QA_ENGINEER
        return UserRole.QA_ENGINEER;
    }

    private void validateJsonStructure(String json) {
        if (!json.startsWith("{") || !json.endsWith("}")) {
            throw new TestGenerationException("Invalid JSON boundaries");
        }
    }
    /**
     * Parse AI response into structured test code.
     * Expected format: JSON with featureFile, stepDefinitions, pageObjects
     */
    private Map<String, Object> parseAIResponse(String aiResponse, AIGeneratedTest.TestType testType) {
        log.debug("Parsing AI response for test code extraction");

        try {
            String jsonContent = extractJsonFromResponse(aiResponse);
            validateJsonStructure(jsonContent);

            ObjectMapper mapper = new ObjectMapper();
            @SuppressWarnings("unchecked")
            Map<String, Object> testCode = mapper.readValue(jsonContent, Map.class);

            validateTestCodeStructure(testCode, testType);
            return testCode;

        } catch (TestGenerationException e) {
            throw e; // keep original message
        } catch (Exception e) {
            throw new TestGenerationException(
                    "Failed to parse AI response (likely truncated or invalid JSON)",
                    e
            );
        }
    }

    /**
     * Extract JSON from AI response (handles markdown code blocks).
     */
    private String extractJsonFromResponse(String response) {
        if (response == null || response.isBlank()) {
            throw new TestGenerationException("Empty AI response");
        }

        String cleaned = response.trim();

        // Remove opening markdown
        if (cleaned.startsWith("```")) {
            int firstNewLine = cleaned.indexOf('\n');
            if (firstNewLine > 0) {
                cleaned = cleaned.substring(firstNewLine + 1);
            }
        }

        // Remove closing markdown
        if (cleaned.endsWith("```")) {
            cleaned = cleaned.substring(0, cleaned.lastIndexOf("```"));
        }

        cleaned = cleaned.trim();

        // Basic sanity checks
        if (!cleaned.startsWith("{")) {
            throw new TestGenerationException("AI response does not start with JSON object");
        }

        // Trim after last closing brace (handles trailing garbage)
        int lastBrace = cleaned.lastIndexOf('}');
        if (lastBrace == -1) {
            throw new TestGenerationException("AI response JSON is truncated (missing closing brace)");
        }

        return cleaned.substring(0, lastBrace + 1);
    }

    /**
     * Validate test code structure.
     */
    private void validateTestCodeStructure(Map<String, Object> testCode, AIGeneratedTest.TestType testType) {
        if (testType == AIGeneratedTest.TestType.UI) {
            if (!testCode.containsKey("featureFile")) {
                throw new TestGenerationException("Missing featureFile in AI response");
            }
            if (!testCode.containsKey("stepDefinitions")) {
                throw new TestGenerationException("Missing stepDefinitions in AI response");
            }
            if (!testCode.containsKey("pageObjects")) {
                throw new TestGenerationException("Missing pageObjects in AI response");
            }
        }
        // Add validation for API, E2E test types as needed
    }

    /**
     * Create AIGeneratedTest entity from parsed response.
     */
    private AIGeneratedTest createGeneratedTestEntity(
            JiraStory story,
            TestGenerationRequest request,
            Map<String, Object> testCode,
            AITestGenerationAttempt attempt) {

        // Generate test name from story key
        String testName = generateTestName(story, request.getTestType());

        return AIGeneratedTest.builder()
                .jiraStory(story)
                .jiraStoryKey(story.getJiraKey())
                .testName(testName)
                .testType(request.getTestType())
                .testFramework(request.getTestFramework())
                .testCodeJson(testCode)
                .aiProvider(attempt.getAiProvider())
                .aiModel(attempt.getAiModel())
                .promptTokens(attempt.getPromptTokens())
                .completionTokens(attempt.getCompletionTokens())
                .totalCostUsd(attempt.getTotalCostUsd())
                .status(TestGenerationStatus.DRAFT)
                .generatedAt(LocalDateTime.now())
                .build();
    }

    /**
     * Assess quality using AIRecommendationService (Week 6).
     */
    /**
     * Assess quality using QualityAssessmentService (rule-based).
     */
    private void assessQuality(AIGeneratedTest generatedTest, Map<String, Object> testCode) {
        log.debug("Assessing test quality for {}", generatedTest.getTestName());

        // Use QualityAssessmentService instead of AIRecommendationService
        QualityAssessmentService.QualityAssessmentResult assessment =
                qualityAssessmentService.assessTestQuality(testCode);

        // Extract quality metrics
        generatedTest.setQualityScore(assessment.getQualityScore());
        generatedTest.setConfidenceLevel(
                AIGeneratedTest.ConfidenceLevel.valueOf(assessment.getConfidenceLevel()));

        // Convert concerns to entity format
        if (assessment.getConcerns() != null && !assessment.getConcerns().isEmpty()) {
            List<AIGeneratedTest.QualityConcern> qualityConcerns =
                    assessment.getConcerns().stream()
                            .map(c -> AIGeneratedTest.QualityConcern.builder()
                                    .type(c.getType())
                                    .severity(c.getSeverity())
                                    .message(c.getMessage())
                                    .suggestion(c.getSuggestion())
                                    .build())
                            .collect(java.util.stream.Collectors.toList());

            generatedTest.setQualityConcerns(qualityConcerns);
        }

        // Automatically move to PENDING_REVIEW if quality meets threshold
        if (generatedTest.getQualityScore().compareTo(minimumQualityScore) >= 0) {
            generatedTest.setStatus(TestGenerationStatus.PENDING_REVIEW);
        }

        log.info("Quality assessment complete: score={}, confidence={}",
                assessment.getQualityScore(), assessment.getConfidenceLevel());
    }
    /**
     * Create attempt record for audit trail.
     */
    private AITestGenerationAttempt createAttemptRecord(
            JiraStory story,
            TestGenerationRequest request,
            int attemptNumber) {

        return AITestGenerationAttempt.builder()
                .jiraStory(story)
                .jiraStoryKey(story.getJiraKey())
                .attemptNumber(attemptNumber)
                .aiProvider(request.getAiProvider() != null ? request.getAiProvider() : "BEDROCK")
                .aiModel(request.getAiModel() != null ? request.getAiModel() : "amazon.nova-pro-v1:0")
                .attemptedAt(Instant.now())  // ✅ Correct
                .build();
    }

    /**
     * Generate test name from story.
     */
    private String generateTestName(JiraStory story, AIGeneratedTest.TestType testType) {
        String sanitized = story.getJiraKey().replace("-", "_");
        return sanitized + "_" + testType.name() + "_Test";
    }

    /**
     * Categorize error for analytics.
     */
    private String categorizeError(Exception e) {
        String message = e.getMessage().toLowerCase();

        if (message.contains("rate limit")) return "RATE_LIMIT";
        if (message.contains("timeout")) return "TIMEOUT";
        if (message.contains("invalid response")) return "INVALID_RESPONSE";
        if (message.contains("cost exceeded")) return "COST_EXCEEDED";
        if (message.contains("authentication")) return "AUTH_ERROR";

        return "UNKNOWN";
    }

    /**
     * Exponential backoff between retries.
     */
    private void sleepBetweenRetries(int attemptNumber) {
        try {
            long sleepMs = 1000L * (long) Math.pow(2, attemptNumber - 1);
            Thread.sleep(sleepMs);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    // ============================================================
    // FRAMEWORK-SPECIFIC INSTRUCTIONS
    // ============================================================

    private String getCucumberInstructions(AIGeneratedTest.TestType testType) {
        return """
            Generate Cucumber BDD test with:
            1. Feature file with clear Gherkin scenarios
            2. Step definition classes with proper annotations
            3. Page Object Model classes (for UI tests)
            
            Requirements:
            - Follow Page Object Model pattern
            - Use meaningful variable names
            - Include proper assertions
            - Add comments for complex logic
            - Follow existing coding standards
            """;
    }

    private String getTestNGInstructions(AIGeneratedTest.TestType testType) {
        return """
            Generate TestNG test with:
            1. Test class with @Test annotations
            2. Setup and teardown methods
            3. Page Object Model classes (for UI tests)
            
            Requirements:
            - Use TestNG annotations properly
            - Include data providers if needed
            - Proper exception handling
            - Clear test documentation
            """;
    }
}