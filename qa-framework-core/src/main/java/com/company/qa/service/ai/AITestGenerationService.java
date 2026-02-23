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
import com.company.qa.service.context.PlaywrightContextBuilder;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.company.qa.model.intent.TestIntent;
import com.company.qa.service.playwright.FrameworkCapabilityService;
import com.company.qa.service.playwright.PlaywrightJavaRenderer;
import com.company.qa.service.playwright.TestIntentParser;
import com.company.qa.service.playwright.TestIntentParser.ParseResult;
import com.company.qa.service.playwright.ValidationResult;
import org.springframework.beans.factory.annotation.Value;

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
    /** @deprecated System 1 file writer — no longer called. See DraftFileService. */
    @Deprecated(since = "Phase 2", forRemoval = false)
    private final TestFileWriterService fileWriterService;

    private final JiraStoryService jiraStoryService;
    private final PlaywrightContextBuilder playwrightContextBuilder;

    // ─── Zero-Hallucination Pipeline (Week 17+) ───────────────────────
    private final TestIntentParser testIntentParser;
    private final PlaywrightJavaRenderer playwrightJavaRenderer;
    // FrameworkCapabilityService is already injected via PlaywrightContextBuilder;
    // no need to inject it here again.

    /**
     * Feature flag — mirrors playwright.intent.enabled.
     * Controls whether parseAIResponse() tries the intent pipeline first.
     */
    @Value("${playwright.intent.enabled:false}")
    private boolean intentEnabled;


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
            Map<String, Object> testCode = parseAIResponse(aiResponse.getContent(), request.getTestType(), request.getTestFramework());

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
            //String draftPath = fileWriterService.writeToDraftFolder(generatedTest, testCode);
            //generatedTest.setDraftFolderPath(draftPath);
            //aiGeneratedTestRepository.save(generatedTest);

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
    /*private String buildGenerationPrompt(JiraStory story, TestGenerationRequest request) {
        log.debug("Building generation prompt for {}", story.getJiraKey());

        // Use JiraContextBuilder to create base context
        String baseContext = jiraContextBuilder.buildMinimalPrompt(story);

        String basePrompt;

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
            case PLAYWRIGHT:
                // NEW: Use PlaywrightContextBuilder for Playwright tests
                basePrompt = playwrightContextBuilder.buildPlaywrightTestPrompt(
                        story,
                        baseContext,
                        request.getTestType()
                );
                log.debug("Using PlaywrightContextBuilder for Playwright framework");
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
    }*/


    // ═══════════════════════════════════════════════════════════════════
    // WEEK 12 DAY 3: ENHANCED PROMPT BUILDING WITH FRAMEWORK SUPPORT
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Build AI prompt using appropriate context builder based on framework.
     *
     * Week 12 Day 3 Enhancement:
     * - CUCUMBER → JiraContextBuilder (existing)
     * - PLAYWRIGHT → PlaywrightContextBuilder (new)
     * - Enhanced scenarios if enabled
     *
     * @param story JIRA story entity
     * @param request Test generation request
     * @return Framework-optimized AI prompt
     */
    private String buildGenerationPrompt(JiraStory story, TestGenerationRequest request) {
        log.debug("Building generation prompt for {} with framework {}",
                story.getJiraKey(),
                request.getTestFramework());

        String basePrompt;

        // Framework decision - use appropriate context builder
        switch (request.getTestFramework()) {
            case PLAYWRIGHT:
                // NEW: Use PlaywrightContextBuilder for Playwright tests
                basePrompt = playwrightContextBuilder.buildContext(
                        story,
                        request.getUserPrompt()
                );
                log.debug("Using PlaywrightContextBuilder for Playwright framework");
                break;

            case CUCUMBER:
                // EXISTING: Use JiraContextBuilder for Cucumber tests
                basePrompt = jiraContextBuilder.buildStoryTestPrompt(
                        story,
                        request.getUserPrompt()
                );
                log.debug("Using JiraContextBuilder for Cucumber framework");
                break;

            case TESTNG:
                // Use JiraContextBuilder as base, add TestNG-specific instructions
                basePrompt = buildTestNGPrompt(story, request);
                log.debug("Using custom builder for TestNG framework");
                break;

            default:
                throw new TestGenerationException(
                        "Unsupported test framework: " + request.getTestFramework());
        }

        // Add enhanced scenario guidance if enabled (Week 12 Day 3 enhancement)
        if (shouldEnhanceScenarios(story, request)) {
            basePrompt = buildEnhancedScenarioPrompt(basePrompt, story, request);
            log.debug("Enhanced prompt with additional scenario coverage guidance");
        }

        return basePrompt;
    }


    /**
     * Build TestNG-specific prompt.
     * Uses JiraContextBuilder as base and adds TestNG instructions.
     */
    private String buildTestNGPrompt(JiraStory story, TestGenerationRequest request) {
        String baseContext = jiraContextBuilder.buildMinimalPrompt(story);

        StringBuilder prompt = new StringBuilder(baseContext);
        prompt.append("\n\n");
        prompt.append("=== TEST GENERATION INSTRUCTIONS ===\n");
        prompt.append("Generate a ").append(request.getTestType())
                .append(" test using TestNG framework.\n\n");
        prompt.append(getTestNGInstructions(request.getTestType()));

        // Output format
        prompt.append("\n\n=== OUTPUT FORMAT ===\n");
        prompt.append("Return JSON: {\"testClass\": \"...\", \"pageObjects\": {...}}\n");

        return prompt.toString();
    }

    /**
     * Determine if enhanced scenario guidance should be added.
     *
     * Enhanced scenarios are beneficial for:
     * - Stories without detailed acceptance criteria
     * - Security-sensitive features (login, auth, payments)
     * - Complex user flows
     */
    private boolean shouldEnhanceScenarios(JiraStory story, TestGenerationRequest request) {
        // Always enhance for UI tests
        if (request.getTestType() == AIGeneratedTest.TestType.UI) {
            return true;
        }

        // Enhance if AC is missing or minimal
        if (!story.hasAcceptanceCriteria() ||
                story.getAcceptanceCriteria().length() < 100) {
            return true;
        }

        // Enhance for security-sensitive stories
        String summary = story.getSummary().toLowerCase();
        if (summary.contains("login") || summary.contains("auth") ||
                summary.contains("payment") || summary.contains("security")) {
            return true;
        }

        return false;
    }

    /**
     * Build enhanced prompt with additional scenario coverage guidance.
     *
     * Week 12 Day 3 Enhancement:
     * Adds explicit instructions for edge cases, negative scenarios, and security.
     * This improves AI scenario thinking from ~70% to ~75-85% coverage.
     *
     * @param basePrompt Original prompt from context builder
     * @param story JIRA story
     * @param request Generation request
     * @return Enhanced prompt with scenario guidance
     */
    private String buildEnhancedScenarioPrompt(
            String basePrompt,
            JiraStory story,
            TestGenerationRequest request) {

        StringBuilder enhanced = new StringBuilder(basePrompt);
        enhanced.append("\n\n");
        enhanced.append("═══════════════════════════════════════════════════════════\n");
        enhanced.append("ENHANCED SCENARIO COVERAGE REQUIREMENTS\n");
        enhanced.append("═══════════════════════════════════════════════════════════\n\n");

        enhanced.append("⚠️  CRITICAL: Generate tests for ALL of these scenario categories:\n\n");

        // 1. Happy Path (from AC)
        enhanced.append("1️⃣  HAPPY PATH SCENARIOS (from Acceptance Criteria):\n");
        enhanced.append("   ✅ Each acceptance criterion as written\n");
        enhanced.append("   ✅ Primary user flow with valid inputs\n");
        enhanced.append("   ✅ Successful completion scenarios\n\n");

        // 2. Edge Cases
        enhanced.append("2️⃣  EDGE CASE SCENARIOS:\n");
        enhanced.append("   🔸 Empty/null inputs for all fields\n");
        enhanced.append("   🔸 Boundary values (min/max length, min/max number)\n");
        enhanced.append("   🔸 Special characters (!@#$%^&*)\n");
        enhanced.append("   🔸 Invalid data types (string where number expected)\n");
        enhanced.append("   🔸 Whitespace-only inputs\n");
        enhanced.append("   🔸 Very long inputs (1000+ characters)\n\n");

        // 3. Negative Scenarios
        enhanced.append("3️⃣  NEGATIVE SCENARIOS:\n");
        enhanced.append("   ❌ Invalid states (performing action when not allowed)\n");
        enhanced.append("   ❌ Unauthorized access attempts\n");
        enhanced.append("   ❌ Error conditions and error messages\n");
        enhanced.append("   ❌ Missing required fields\n");
        enhanced.append("   ❌ Concurrent action conflicts\n\n");

        // 4. Security Scenarios (if relevant)
        /*if (isSecurityRelevant(story)) {
            enhanced.append("4️⃣  SECURITY SCENARIOS:\n");
            enhanced.append("   🔒 SQL injection attempts ('; DROP TABLE--)\n");
            enhanced.append("   🔒 XSS attempts (<script>alert('xss')</script>)\n");
            enhanced.append("   🔒 Authentication bypass attempts\n");
            enhanced.append("   🔒 Session hijacking scenarios\n");
            enhanced.append("   🔒 Brute force protection\n\n");
        }*/

        // 5. UX/Behavioral Scenarios
        enhanced.append("5️⃣  UX/BEHAVIORAL SCENARIOS:\n");
        enhanced.append("   🎨 Browser back button behavior\n");
        enhanced.append("   🎨 Page refresh handling\n");
        enhanced.append("   🎨 Multiple browser tabs\n");
        enhanced.append("   🎨 Session timeout\n");
        enhanced.append("   🎨 Network interruption recovery\n\n");

        enhanced.append("💡 GOAL: Comprehensive test coverage beyond just acceptance criteria\n");
        enhanced.append("═══════════════════════════════════════════════════════════\n\n");

        return enhanced.toString();
    }

    /**
     * Determine if story is security-relevant.
     */
    private boolean isSecurityRelevant(JiraStory story) {
        String text = (story.getSummary() + " " +
                (story.getDescription() != null ? story.getDescription() : ""))
                .toLowerCase();

        return text.contains("login") || text.contains("auth") ||
                text.contains("password") || text.contains("security") ||
                text.contains("payment") || text.contains("sensitive") ||
                text.contains("credential") || text.contains("token");
    }

    /**
     * Get context builder name for logging.
     */
    private String getContextBuilderName(AIGeneratedTest.TestFramework framework) {
        switch (framework) {
            case PLAYWRIGHT:
                return "PlaywrightContextBuilder";
            case CUCUMBER:
                return "JiraContextBuilder";
            case TESTNG:
                return "TestNGBuilder";
            default:
                return "UnknownBuilder";
        }
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
     * Sanitize AI response before JSON extraction.
     * Handles cases where AI generates invalid constructs inside JSON string values:
     * - "a".repeat(1000)  → replaced with a long literal string
     * - "aaa..."          → ellipsis stripped to valid string
     * - ...               → bare ellipsis removed
     */
    private String sanitizeAIResponse(String response) {
        if (response == null) return null;

        String sanitized = response;

        // Fix: "value": "someStr".repeat(N)  → "value": "aaaaaa..."
        sanitized = sanitized.replaceAll(
                "\"([^\"]{0,50})\"\\.repeat\\(\\d+\\)",
                "\"$1$1$1$1$1\""
        );

        // Fix: "value": "aaa..."  → "value": "aaa"   (ellipsis inside string)
        sanitized = sanitized.replaceAll(
                "(\"[^\"]*)\\.{2,}(\")",
                "$1$2"
        );

        // Fix: bare ... outside quotes (e.g., between fields)
        sanitized = sanitized.replaceAll(
                "(?<!\")\\.\\.\\+(?!\")",
                ""
        );

        if (!sanitized.equals(response)) {
            log.info("Sanitized AI response: removed invalid constructs (ellipsis/repeat)");
        }

        return sanitized;
    }
    // ═══════════════════════════════════════════════════════════════════
    // WEEK 12 DAY 3: ENHANCED JSON PARSING WITH FRAMEWORK SUPPORT
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Parse AI response into structured test code.
     *
     * Week 12 Day 3 Enhancement:
     * Now supports multiple JSON structures based on framework:
     *
     * CUCUMBER:
     * {
     *   "featureFile": "...",
     *   "stepDefinitions": [...],
     *   "pageObjects": [...]
     * }
     *
     * PLAYWRIGHT:
     * {
     *   "testClassName": "LoginTest",
     *   "testClass": "public class LoginTest...",
     *   "pageObjects": {"LoginPage": "...", ...},
     *   "usesExistingPages": false,
     *   "newPagesNeeded": [...]
     * }
     *
     * @param aiResponse Raw AI response
     * @param testType Type of test (UI, API, etc.)
     * @param framework Test framework (CUCUMBER, PLAYWRIGHT, etc.)
     * @return Parsed test code structure
     */
    private Map<String, Object> parseAIResponse(
            String aiResponse,
            AIGeneratedTest.TestType testType,
            AIGeneratedTest.TestFramework framework) {

        log.debug("Parsing AI response for {} test with {} framework", testType, framework);

        // PRE-SANITIZE: Remove invalid constructs AI generates for edge-case scenarios
        // e.g., "a".repeat(1000), "aaa...", bare ellipsis between fields
        aiResponse = sanitizeAIResponse(aiResponse);   // ← ADD THIS LINE

        ObjectMapper mapper = new ObjectMapper();

        // Two-pass extraction strategy:
        // Pass 1: extractAndCleanJson (handles markdown blocks, basic truncation)
        // Pass 2: extractJsonFromResponse (conservative — trims to last valid '}')
        // This handles AI outputs with ellipsis, trailing comments, or mid-JSON invalid chars.
        String[] extractionAttempts = new String[2];
        try {
            extractionAttempts[0] = extractAndCleanJson(aiResponse);
        } catch (Exception e) {
            log.debug("extractAndCleanJson failed: {}", e.getMessage());
        }
        try {
            extractionAttempts[1] = extractJsonFromResponse(aiResponse);
        } catch (Exception e) {
            log.debug("extractJsonFromResponse failed: {}", e.getMessage());
        }

        Map<String, Object> testCode = null;
        Exception lastParseException = null;

        for (int pass = 0; pass < extractionAttempts.length; pass++) {
            String candidate = extractionAttempts[pass];
            if (candidate == null || candidate.isBlank()) continue;
            try {
                @SuppressWarnings("unchecked")
                Map<String, Object> parsed = mapper.readValue(candidate, Map.class);
                testCode = parsed;
                if (pass > 0) {
                    log.info("JSON extracted successfully on pass {} (fallback extractor)", pass + 1);
                }
                break;
            } catch (Exception e) {
                lastParseException = e;
                log.debug("JSON parse pass {} failed: {}", pass + 1, e.getMessage());
            }
        }

        if (testCode == null) {
            throw new TestGenerationException(
                    "Failed to parse AI response for " + framework +
                            " (likely truncated or invalid JSON)",
                    lastParseException
            );
        }

        try {
            // Framework-aware validation
            validateTestCodeStructure(testCode, testType, framework);

            log.debug("Successfully parsed {} JSON structure with {} keys",
                    framework, testCode.keySet().size());

            return testCode;

        } catch (TestGenerationException e) {
            throw e; // keep original message
        } catch (Exception e) {
            throw new TestGenerationException(
                    "Failed to validate parsed AI response for " + framework,
                    e
            );
        }
    }


    /**
     * Extract and clean JSON from AI response.
     * Handles markdown code blocks and truncated responses.
     */
    private String extractAndCleanJson(String response) {
        String cleaned = response.trim();

        // Remove language identifier before JSON (e.g., "java", "json")
        if (!cleaned.startsWith("{") && !cleaned.startsWith("[")) {
            int jsonStart = cleaned.indexOf('{');
            if (jsonStart > 0) {
                cleaned = cleaned.substring(jsonStart);
            }
        }

        // Remove markdown code blocks
        if (cleaned.startsWith("```json")) {
            cleaned = cleaned.substring(7);
        }
        if (cleaned.startsWith("```")) {
            cleaned = cleaned.substring(3);
        }
        if (cleaned.endsWith("```")) {
            cleaned = cleaned.substring(0, cleaned.length() - 3);
        }

        cleaned = cleaned.trim();

        // If truncated, try to close JSON properly
        if (!cleaned.endsWith("}")) {
            int lastComma = cleaned.lastIndexOf(',');
            int lastQuote = cleaned.lastIndexOf('"');
            int lastBrace = cleaned.lastIndexOf('}');

            int cutPoint = Math.max(Math.max(lastComma, lastQuote), lastBrace);

            if (cutPoint > 0) {
                cleaned = cleaned.substring(0, cutPoint);
                if (!cleaned.endsWith("\"") && !cleaned.endsWith("}")) {
                    cleaned += "\"";
                }
                cleaned += "\n}";
            }
        }

        return cleaned;
    }
    /**
     * Extract JSON from AI response (handles markdown code blocks).
     * Same as before - works for all frameworks.
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
     * Validate JSON structure is well-formed.
     */
   /* private void validateJsonStructure(String json) {
        if (!json.startsWith("{") || !json.endsWith("}")) {
            throw new TestGenerationException("Invalid JSON boundaries");
        }
    }*/

    /**
     * Validate test code structure based on framework.
     *
     * Week 12 Day 3 Enhancement:
     * Framework-aware validation for different JSON structures.
     */
    private void validateTestCodeStructure(
            Map<String, Object> testCode,
            AIGeneratedTest.TestType testType,
            AIGeneratedTest.TestFramework framework) {

        log.debug("Validating {} test code structure for {} framework", testType, framework);

        if (testType == AIGeneratedTest.TestType.UI) {
            switch (framework) {
                case PLAYWRIGHT:
                    validatePlaywrightStructure(testCode);
                    break;

                case CUCUMBER:
                    validateCucumberStructure(testCode);
                    break;

                case TESTNG:
                    validateTestNGStructure(testCode);
                    break;

                default:
                    throw new TestGenerationException(
                            "Unsupported framework for validation: " + framework);
            }
        }
        // Add validation for API, E2E test types as needed
    }

    /**
     * Validate Playwright-specific JSON structure.
     *
     * Expected structure:
     * {
     *   "testClassName": "LoginTest",
     *   "testClass": "<Java code>",
     *   "pageObjects": {...},      // Optional
     *   "usesExistingPages": false, // Optional
     *   "newPagesNeeded": [...]     // Optional
     * }
     */
    /**
     * Validate Playwright JSON structure and — when intent mode is enabled —
     * run the Zero-Hallucination pipeline: parse → validate → render.
     *
     * Two paths:
     *
     * PATH A (intent mode, response contains "scenarios" key):
     *   TestIntentParser.parse() → ValidationResult → PlaywrightJavaRenderer.render()
     *   On success: injects "renderedJava" + "format":"INTENT_V1" into testCode map.
     *   On failure: throws TestGenerationException with validation errors.
     *
     * PATH B (legacy mode OR intent mode but response is legacy format):
     *   Original validation: requires "testClassName" + "testClass" with Java code.
     *   Falls through to existing behaviour without any change.
     *
     * The testCode map is mutated in-place for PATH A so that downstream code
     * (createGeneratedTestEntity, assessQuality, fileWriterService) sees the
     * rendered Java in the map without needing modification.
     *
     * @param testCode Mutable map from parseAIResponse()
     * @since Zero-Hallucination Pipeline
     */
    private void validatePlaywrightStructure(Map<String, Object> testCode) {

        // ── PATH A: Intent format detected ──────────────────────────────
        if (intentEnabled && testIntentParser.isIntentFormat(toJsonString(testCode))) {
            log.info("Intent format detected — running Zero-Hallucination pipeline");
            runIntentPipeline(testCode);
            return;
        }

        // ── PATH B: Legacy format — original validation unchanged ────────
        log.debug("Legacy Playwright format — using original validation");

        if (!testCode.containsKey("testClassName")) {
            throw new TestGenerationException(
                    "Missing 'testClassName' in Playwright test response");
        }

        if (!testCode.containsKey("testClass")) {
            throw new TestGenerationException(
                    "Missing 'testClass' in Playwright test response");
        }

        Object testClass = testCode.get("testClass");
        if (testClass == null || testClass.toString().trim().isEmpty()) {
            throw new TestGenerationException(
                    "Empty 'testClass' in Playwright test response");
        }

        String testClassCode = testClass.toString();
        if (!testClassCode.contains("class") || !testClassCode.contains("@Test")) {
            throw new TestGenerationException(
                    "Invalid testClass — does not contain Java test class structure");
        }

        if (testClassCode.contains("Feature:") || testClassCode.contains("Scenario:")) {
            log.warn("⚠️  Playwright test contains Gherkin syntax — AI may be confused");
        }

        log.debug("✅ Legacy Playwright structure validated: testClassName={}, testClass={} chars",
                testCode.get("testClassName"),
                testClassCode.length());
    }

    /**
     * Zero-Hallucination pipeline: parse JSON map → validate → render Java.
     *
     * Mutates the testCode map to inject pipeline results under "INTENT_V1" format.
     * After this method returns the map contains:
     *   - "format"              → "INTENT_V1"
     *   - "testClassName"       → from TestIntent (already in map)
     *   - "scenarios"           → from TestIntent (already in map)
     *   - "testClass"           → rendered Java string (NEW — added by this method)
     *   - "renderedJava"        → same rendered Java string (duplicate for clarity)
     *   - "validationWarnings"  → List<String> of warnings (empty if none)
     *
     * The "testClass" key is injected specifically so that downstream code that
     * reads testCode.get("testClass") (quality assessment, file writer) continues
     * to work without modification.
     *
     * @param testCode Mutable map containing the AI's TestIntent JSON
     * @throws TestGenerationException if parse or validation fails
     * @since Zero-Hallucination Pipeline
     */
    private void runIntentPipeline(Map<String, Object> testCode) {
        // Re-serialize the map to JSON so TestIntentParser can deserialize it cleanly
        String intentJson = toJsonString(testCode);

        // Step 1: Parse JSON → TestIntent (includes validation internally)
        ParseResult parseResult = testIntentParser.parse(intentJson);

        if (parseResult.isParseError()) {
            throw new TestGenerationException(
                    "Intent pipeline: JSON parse error — " + parseResult.getErrorMessage());
        }

        if (parseResult.isValidationFailure()) {
            ValidationResult validation = parseResult.getValidation();
            String errors = String.join("; ", validation.getErrors());
            throw new TestGenerationException(
                    "Intent pipeline: validation failed — " + errors);
        }

        if (!parseResult.isSuccess()) {
            // NOT_INTENT should not reach here (isIntentFormat guard above)
            throw new TestGenerationException(
                    "Intent pipeline: unexpected parse status — " + parseResult.getSummary());
        }

        TestIntent intent = parseResult.getIntent();
        log.info("Intent parsed: {} scenarios, {} total steps",
                intent.getScenarioCount(), intent.getTotalStepCount());

        // Step 2: Render TestIntent → Java source string
        String renderedJava;
        try {
            renderedJava = playwrightJavaRenderer.render(intent);
        } catch (Exception e) {
            throw new TestGenerationException(
                    "Intent pipeline: rendering failed — " + e.getMessage(), e);
        }

        log.info("Intent rendered: {} chars of Java for class {}",
                renderedJava.length(), intent.getTestClassName());

        // Step 3: Mutate testCode map — inject rendered Java + metadata
        testCode.put("format", "INTENT_V1");
        testCode.put("testClassName", intent.getTestClassName());

        // "testClass" — the key all downstream code expects for the Java source
        testCode.put("testClass", renderedJava);

        // "renderedJava" — explicit alias so it's clear this was renderer-produced
        testCode.put("renderedJava", renderedJava);

        // Preserve validation warnings for storage (non-blocking)
        ValidationResult validation = parseResult.getValidation();
        if (validation != null && validation.hasWarnings()) {
            testCode.put("validationWarnings", validation.getWarnings());
            log.warn("Intent validation warnings for {}: {}",
                    intent.getTestClassName(), validation.getWarnings());
        } else {
            testCode.put("validationWarnings", java.util.Collections.emptyList());
        }

        log.info("✅ Intent pipeline complete: INTENT_V1 envelope ready for {}",
                intent.getTestClassName());
    }

    /**
     * Re-serialize a Map back to a JSON string.
     * Used to pass the already-parsed testCode map back to TestIntentParser,
     * which expects a raw JSON string.
     *
     * Throws TestGenerationException (not IOException) so callers don't need
     * checked exception handling.
     */
    private String toJsonString(Map<String, Object> map) {
        try {
            // ObjectMapper is already available as a field in this class
            // (used in parseAIResponse). Use it directly.
            return new com.fasterxml.jackson.databind.ObjectMapper()
                    .writeValueAsString(map);
        } catch (Exception e) {
            throw new TestGenerationException(
                    "Failed to re-serialize testCode map to JSON", e);
        }
    }

    /**
     * Validate Cucumber-specific JSON structure.
     *
     * Expected structure:
     * {
     *   "featureFile": "...",
     *   "stepDefinitions": [...],
     *   "pageObjects": [...]
     * }
     */
    private void validateCucumberStructure(Map<String, Object> testCode) {
        if (!testCode.containsKey("featureFile")) {
            throw new TestGenerationException(
                    "Missing 'featureFile' in Cucumber test response");
        }

        if (!testCode.containsKey("stepDefinitions")) {
            throw new TestGenerationException(
                    "Missing 'stepDefinitions' in Cucumber test response");
        }

        if (!testCode.containsKey("pageObjects")) {
            throw new TestGenerationException(
                    "Missing 'pageObjects' in Cucumber test response");
        }

        log.debug("✅ Cucumber structure validated");
    }

    /**
     * Validate TestNG-specific JSON structure.
     */
    private void validateTestNGStructure(Map<String, Object> testCode) {
        if (!testCode.containsKey("testClass")) {
            throw new TestGenerationException(
                    "Missing 'testClass' in TestNG test response");
        }

        log.debug("✅ TestNG structure validated");
    }

    /**
     * Extract JSON from AI response (handles markdown code blocks).
     */
   /* private String extractJsonFromResponse(String response) {
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
    }*/

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
        boolean isIntentV1 = "INTENT_V1".equals(testCode.get("format"));
        log.debug("Assessing test quality for {} (format: {})",
                generatedTest.getTestName(),
                isIntentV1 ? "INTENT_V1" : "LEGACY");
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

    private String getPlaywrightInstructions(AIGeneratedTest.TestType testType) {
        return """
        Generate Playwright-based automated tests with:
        1. Test classes using Playwright Test (Java) APIs
        2. Proper browser, context, and page lifecycle management
        3. Page Object Model (POM) classes for UI interactions
        4. Reusable utilities for common actions and waits
        
        Requirements:
        - Use Playwright Java APIs (Browser, BrowserContext, Page)
        - Follow Page Object Model pattern strictly
        - Use reliable locators (role, text, test-id; avoid brittle XPath)
        - Include explicit assertions using Playwright assertions
        - Handle waits using Playwright auto-waiting (avoid Thread.sleep)
        - Add setup and teardown logic for browser lifecycle
        - Include comments for non-trivial logic and selectors
        - Follow existing coding and naming standards
        - Ensure tests are deterministic and environment-independent
        """;
    }
}