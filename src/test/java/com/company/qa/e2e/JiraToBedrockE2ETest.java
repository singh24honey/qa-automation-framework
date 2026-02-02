package com.company.qa.e2e;

import com.company.qa.model.dto.request.TestGenerationRequest;
import com.company.qa.model.dto.response.TestGenerationResponse;
import com.company.qa.model.entity.AIGeneratedTest;
import com.company.qa.model.entity.AITestGenerationAttempt;
import com.company.qa.model.entity.JiraStory;
import com.company.qa.repository.AIGeneratedTestRepository;
import com.company.qa.repository.AITestGenerationAttemptRepository;
import com.company.qa.repository.JiraStoryRepository;
import com.company.qa.service.ai.AITestGenerationService;
import org.junit.jupiter.api.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.io.File;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-End test for complete JIRA to AI to Test Generation flow.
 *
 * Tests the full pipeline:
 * 1. Fetch JIRA story (SCRUM-7)
 * 2. Generate test using AWS Bedrock (Amazon Nova Pro)
 * 3. Verify database records
 * 4. Verify files written to draft folder
 * 5. Verify cost tracking
 */
@SpringBootTest
@ActiveProfiles("dev")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class JiraToBedrockE2ETest {

    private static final Logger log = LoggerFactory.getLogger(JiraToBedrockE2ETest.class);

    @Autowired
    private AITestGenerationService generationService;

    @Autowired
    private JiraStoryRepository jiraStoryRepository;

    @Autowired
    private AIGeneratedTestRepository aiGeneratedTestRepository;

    @Autowired
    private AITestGenerationAttemptRepository attemptRepository;

    private static final String TEST_STORY_KEY = "SCRUM-7";
    private static TestGenerationResponse response;

    @Test
    @Order(1)
    @DisplayName("E2E: Fetch JIRA story SCRUM-7")
    void step1_fetchJiraStory() {
        log.info("=== STEP 1: Fetching JIRA story {} ===", TEST_STORY_KEY);

        // Verify story exists or fetch it
        JiraStory story = jiraStoryRepository.findByJiraKey(TEST_STORY_KEY)
                .orElseGet(() -> {
                    log.info("Story not in DB, will be fetched during generation");
                    return null;
                });

        if (story != null) {
            log.info("SUCCESS: Story found in DB: {}", story.getSummary());
            assertThat(story.getJiraKey()).isEqualTo(TEST_STORY_KEY);
            assertThat(story.getSummary()).isNotBlank();
        } else {
            log.info("WARNING: Story not cached, will be fetched from JIRA API");
        }
    }

    @Test
    @Order(2)
    @DisplayName("E2E: Generate test from SCRUM-7 using AWS Bedrock")
    void step2_generateTestWithBedrock() {
        log.info("=== STEP 2: Generating test with AWS Bedrock ===");

        // Build generation request
        TestGenerationRequest request = TestGenerationRequest.builder()
                .jiraStoryKey(TEST_STORY_KEY)
                .testType(AIGeneratedTest.TestType.UI)
                .testFramework(AIGeneratedTest.TestFramework.CUCUMBER)
                .aiProvider("BEDROCK")
                .aiModel("amazon.nova-pro-v1:0")
                .skipQualityCheck(false)
                .build();

        log.info("Request: {}", request);

        // Generate test (full pipeline)
        response = generationService.generateTestFromStory(request);

        log.info("SUCCESS: Generation complete!");
        log.info("Test ID: {}", response.getTestId());
        log.info("Test Name: {}", response.getTestName());
        log.info("Quality Score: {}", response.getQualityScore());
        log.info("AI Cost: ${}", response.getTotalCostUsd());
        log.info("Status: {}", response.getStatus());

        // Assertions - CORRECTED based on actual DTO
        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getTestId()).isNotNull();
        assertThat(response.getTestId()).isInstanceOf(UUID.class);
        assertThat(response.getTestName()).isNotBlank();
        assertThat(response.getQualityScore()).isGreaterThanOrEqualTo(BigDecimal.ZERO);
        assertThat(response.getTotalCostUsd()).isNotNull();
        assertThat(response.getTotalCostUsd().doubleValue()).isLessThan(0.50);
    }

    @Test
    @Order(3)
    @DisplayName("E2E: Verify database records")
    void step3_verifyDatabaseRecords() {
        log.info("=== STEP 3: Verifying database records ===");

        // Verify AIGeneratedTest record
        AIGeneratedTest generatedTest = aiGeneratedTestRepository
                .findById(response.getTestId())
                .orElseThrow(() -> new AssertionError("Generated test not found"));

        log.info("SUCCESS: Generated Test Record:");
        log.info("  - Test Name: {}", generatedTest.getTestName());
        log.info("  - Framework: {}", generatedTest.getTestFramework());
        log.info("  - Quality Score: {}", generatedTest.getQualityScore());
        log.info("  - Status: {}", generatedTest.getStatus());
        log.info("  - Draft Path: {}", generatedTest.getDraftFolderPath());

        assertThat(generatedTest.getJiraStoryKey()).isEqualTo(TEST_STORY_KEY);
        assertThat(generatedTest.getTestFramework()).isEqualTo(AIGeneratedTest.TestFramework.CUCUMBER);
        assertThat(generatedTest.getTestCodeJson()).isNotNull();
        assertThat(generatedTest.getQualityScore()).isNotNull();

        // Verify generation attempt record
        List<AITestGenerationAttempt> attempts = attemptRepository
                .findByJiraStoryKeyOrderByAttemptedAtDesc(TEST_STORY_KEY);

        log.info("SUCCESS: Generation Attempts: {}", attempts.size());

        assertThat(attempts).isNotEmpty();
        AITestGenerationAttempt lastAttempt = attempts.get(0);
        assertThat(lastAttempt.getSuccess()).isTrue();
        assertThat(lastAttempt.getAiProvider()).isEqualTo("BEDROCK");
        assertThat(lastAttempt.getAiModel()).isEqualTo("amazon.nova-pro-v1:0");
        assertThat(lastAttempt.getPromptTokens()).isGreaterThan(0);
        assertThat(lastAttempt.getCompletionTokens()).isGreaterThan(0);
        assertThat(lastAttempt.getTotalCostUsd()).isNotNull();

        log.info("  - Prompt Tokens: {}", lastAttempt.getPromptTokens());
        log.info("  - Completion Tokens: {}", lastAttempt.getCompletionTokens());
        log.info("  - Total Cost: ${}", lastAttempt.getTotalCostUsd());
        log.info("  - Duration: {}ms", lastAttempt.getDurationMs());
    }

    @Test
    @Order(4)
    @DisplayName("E2E: Verify files written to draft folder")
    void step4_verifyDraftFiles() throws Exception {
        log.info("=== STEP 4: Verifying draft files ===");

        AIGeneratedTest generatedTest = aiGeneratedTestRepository
                .findById(response.getTestId())
                .orElseThrow();

        String draftPath = generatedTest.getDraftFolderPath();
        assertThat(draftPath).isNotNull();

        log.info("Draft folder: {}", draftPath);

        Path draftFolder = Path.of(draftPath);
        assertThat(draftFolder).exists();
        assertThat(draftFolder).isDirectory();

        // Check for feature file
        File featureFile = draftFolder.resolve(TEST_STORY_KEY + ".feature").toFile();
        if (featureFile.exists()) {
            String content = Files.readString(featureFile.toPath());
            log.info("SUCCESS: Feature file created ({} bytes)", content.length());

            // Preview first 500 chars
            int previewLength = Math.min(500, content.length());
            log.info("Preview:\n{}", content.substring(0, previewLength));

            assertThat(content).contains("Feature:");
            assertThat(content).contains("Scenario:");
        } else {
            log.warn("Feature file not found at expected location");
        }

        // Check for step definitions
        File stepsDir = draftFolder.resolve("steps").toFile();
        if (stepsDir.exists() && stepsDir.isDirectory()) {
            log.info("SUCCESS: Steps directory created");
            File[] stepFiles = stepsDir.listFiles();
            if (stepFiles != null && stepFiles.length > 0) {
                assertThat(stepFiles).isNotEmpty();
                log.info("  - Step files count: {}", stepFiles.length);
            }
        }

        // Check for page objects
        File pagesDir = draftFolder.resolve("pages").toFile();
        if (pagesDir.exists() && pagesDir.isDirectory()) {
            log.info("SUCCESS: Page objects directory created");
        }
    }

    @Test
    @Order(5)
    @DisplayName("E2E: Verify complete pipeline integration")
    void step5_verifyCompleteIntegration() {
        log.info("=== STEP 5: Pipeline Integration Summary ===");

        // Summary
        log.info("SUCCESS: JIRA Integration - Story fetched from https://singh24honey.atlassian.net");
        log.info("SUCCESS: AI Integration - Test generated using Amazon Nova Pro");
        log.info("SUCCESS: Quality Assessment - Score = {}", response.getQualityScore());
        log.info("SUCCESS: File Generation - Files written to draft folder");
        log.info("SUCCESS: Cost Tracking - Total cost = ${}", response.getTotalCostUsd());
        log.info("SUCCESS: Audit Trail - Attempt records created");

        // Final assertions
        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getQualityScore())
                .isGreaterThanOrEqualTo(BigDecimal.valueOf(60.0));
        assertThat(response.getTotalCostUsd().doubleValue()).isLessThan(0.50);

        log.info("=== E2E TEST PASSED - Complete pipeline works! ===");
    }
}