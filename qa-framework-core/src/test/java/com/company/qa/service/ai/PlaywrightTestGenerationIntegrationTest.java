package com.company.qa.service.ai;

import com.company.qa.model.dto.request.TestGenerationRequest;
import com.company.qa.model.dto.response.TestGenerationResponse;
import com.company.qa.model.entity.AIGeneratedTest;
import com.company.qa.model.entity.JiraConfiguration;
import com.company.qa.model.entity.JiraStory;
import com.company.qa.repository.AIGeneratedTestRepository;
import com.company.qa.repository.JiraConfigurationRepository;
import com.company.qa.repository.JiraStoryRepository;
import com.company.qa.service.TestFileWriterService;
import com.company.qa.testsupport.PostgresIntegrationTest;
import jakarta.persistence.PrePersist;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test for Week 12 Day 4 - Playwright File Writing.
 *
 * Tests the complete flow:
 * 1. JIRA story exists in database
 * 2. AITestGenerationService generates Playwright test
 * 3. TestFileWriterService writes files to disk
 * 4. Files are accessible and properly structured
 *
 * This test uses MOCK AI provider to avoid actual AI calls.
 */

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class PlaywrightTestGenerationIntegrationTest extends PostgresIntegrationTest {

    @Autowired
    private AITestGenerationService aiTestGenerationService;

    @Autowired
    private TestFileWriterService testFileWriterService;

    @Autowired
    private JiraStoryRepository jiraStoryRepository;

    @Autowired
    private AIGeneratedTestRepository aiGeneratedTestRepository;

    @Autowired
    private JiraConfigurationRepository jiraConfigurationRepository;

    @TempDir
    Path tempDir;


    private static final String TEST_STORY_KEY = "SCRUM-7";
    @Test
    @DisplayName("E2E: JIRA Story → AI Generation → Playwright Files Written")
    void endToEnd_PlaywrightTestGeneration_Success() throws Exception {
        // Setup: Configure temp directory for file writing
        ReflectionTestUtils.setField(testFileWriterService, "draftFolderPath", tempDir.toString());

        // Step 1: Create JIRA story in database
        JiraStory story = createTestJiraStory();
        story = jiraStoryRepository.save(story);

        // Step 2: Create test generation request for Playwright
        TestGenerationRequest request = TestGenerationRequest.builder()
                .jiraStoryKey("PROJ-789")
                .testType(AIGeneratedTest.TestType.UI)
                .testFramework(AIGeneratedTest.TestFramework.PLAYWRIGHT)
                .aiProvider("MOCK") // Use mock AI to avoid actual calls
                .skipQualityCheck(true)// Skip quality check for faster test
                .build();

        // Step 3: Generate test via service
        TestGenerationResponse response = aiTestGenerationService.generateTestFromStory(request);

        // Step 4: Verify response
        assertThat(response).isNotNull();
        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getTestName()).contains("PROJ789");
        assertThat(response.getDraftFolderPath()).isNotNull();

        // Step 5: Verify files were written to disk
        Path draftFolder = Paths.get(response.getDraftFolderPath());
        assertThat(Files.exists(draftFolder)).isTrue();
        assertThat(Files.isDirectory(draftFolder)).isTrue();

        // Step 6: Verify test file exists
        Path testFile = findTestFile(draftFolder);
        assertThat(testFile).isNotNull();
        assertThat(Files.exists(testFile)).isTrue();

        String testContent = Files.readString(testFile);
        assertThat(testContent).contains("Playwright");
        assertThat(testContent).contains("@Test");
        assertThat(testContent).contains("Browser");

        // Step 7: Verify database record
        AIGeneratedTest savedTest = aiGeneratedTestRepository.findById(response.getTestId()).orElse(null);
        assertThat(savedTest).isNotNull();
        assertThat(savedTest.getTestFramework()).isEqualTo(AIGeneratedTest.TestFramework.PLAYWRIGHT);
        assertThat(savedTest.getDraftFolderPath()).isEqualTo(draftFolder.toString());
        assertThat(savedTest.getStatus()).isEqualTo(AIGeneratedTest.TestGenerationStatus.DRAFT);
    }

    @Test
    @DisplayName("E2E: Playwright test with new Page Objects written correctly")
    void endToEnd_PlaywrightWithPageObjects_Success() throws Exception {
        // Setup
        ReflectionTestUtils.setField(testFileWriterService, "draftFolderPath", tempDir.toString());

        // Create story with Page Object requirements
        JiraStory story = JiraStory.builder()
                .jiraKey("PROJ-790")
                .summary("Complex login flow with dashboard")
                .description("User should login and see dashboard")
                .acceptanceCriteria("""
                        Given user is on login page
                        When user enters valid credentials
                        And clicks sign in
                        Then user should see dashboard
                        And welcome message should be displayed
                        """)
                .priority("High")
                .createdAt(LocalDateTime.now())
                .build();
        story = jiraStoryRepository.save(story);

        // Generate test
        TestGenerationRequest request = TestGenerationRequest.builder()
                .jiraStoryKey("PROJ-790")
                .testType(AIGeneratedTest.TestType.UI)
                .testFramework(AIGeneratedTest.TestFramework.PLAYWRIGHT)
                .aiProvider("MOCK")
                .skipQualityCheck(true)
                .build();

        TestGenerationResponse response = aiTestGenerationService.generateTestFromStory(request);

        // Verify files
        Path draftFolder = Paths.get(response.getDraftFolderPath());

        // Check for test file
        Path testFile = findTestFile(draftFolder);
        assertThat(testFile).isNotNull();

        // Check for pages folder (if AI generated new Page Objects)
        Path pagesFolder = draftFolder.resolve("pages");

        if (Files.exists(pagesFolder)) {
            // Verify it's a directory and contains .java files
            assertThat(Files.isDirectory(pagesFolder)).isTrue();

            long pageObjectCount = Files.list(pagesFolder)
                    .filter(path -> path.toString().endsWith(".java"))
                    .count();

            assertThat(pageObjectCount).isGreaterThan(0);
        }
    }

    @Test
    @DisplayName("E2E: Commit approved Playwright test to final location")
    void endToEnd_CommitApprovedPlaywrightTest_Success() throws Exception {
        // Setup
        ReflectionTestUtils.setField(testFileWriterService, "draftFolderPath", tempDir.toString());
        ReflectionTestUtils.setField(testFileWriterService, "committedFolderPath", tempDir.toString());

        // Create and generate test
        JiraStory story = createTestJiraStory();
        story = jiraStoryRepository.save(story);

        TestGenerationRequest request = TestGenerationRequest.builder()
                .jiraStoryKey("PROJ-789")
                .testType(AIGeneratedTest.TestType.UI)
                .testFramework(AIGeneratedTest.TestFramework.PLAYWRIGHT)
                .aiProvider("MOCK")
                .skipQualityCheck(true)
                .build();

        TestGenerationResponse response = aiTestGenerationService.generateTestFromStory(request);

        // Simulate approval
        AIGeneratedTest test = aiGeneratedTestRepository.findById(response.getTestId()).orElseThrow();
        test.setStatus(AIGeneratedTest.TestGenerationStatus.APPROVED);
        aiGeneratedTestRepository.save(test);

        // Commit to final location
        String committedPath = testFileWriterService.commitApprovedTest(test);

        // Verify committed folder structure
        assertThat(committedPath).contains("playwright-tests");
        assertThat(committedPath).contains("src/test/java/generated");
        assertThat(committedPath).contains("PROJ-789");

        Path committedFolder = Paths.get(committedPath);
        assertThat(Files.exists(committedFolder)).isTrue();

        // Verify test file was copied
        Path committedTestFile = findTestFile(committedFolder);
        assertThat(committedTestFile).isNotNull();
        assertThat(Files.exists(committedTestFile)).isTrue();
    }

    // ============================================================
    // HELPER METHODS
    // ============================================================

    private JiraStory createTestJiraStory() {

        JiraConfiguration testConfig = JiraConfiguration.builder()
                .id(UUID.randomUUID())
                .configName("integration-test")
                .jiraUrl("http://localhost:" + "8787")
                .projectKey("TEST")
                .maxRequestsPerMinute(60)
                .secretArn("test-secret")
                .createdBy("QA")
                .enabled(false)
                .build();

        testConfig = jiraConfigurationRepository.save(testConfig);

        return JiraStory.builder()
                .jiraKey("PROJ-789")
                .summary("User login functionality")
                .description("As a user, I want to login to the application")
                .acceptanceCriteria("""
                        Given user is on login page
                        When user enters valid credentials
                        Then user should be logged in successfully
                        """)
                .priority("High")
                .status("Open")
                .createdAt(LocalDateTime.now()).configuration(testConfig)
                .build();
    }

    /**
     * Find the test file in a directory (handles dynamic naming).
     */
    private Path findTestFile(Path directory) throws Exception {
        return Files.list(directory)
                .filter(path -> path.toString().endsWith("Test.java"))
                .findFirst()
                .orElse(null);
    }
}
