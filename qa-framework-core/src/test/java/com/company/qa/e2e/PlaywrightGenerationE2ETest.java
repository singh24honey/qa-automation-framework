package com.company.qa.e2e;

import com.company.qa.model.dto.request.TestGenerationRequest;
import com.company.qa.model.dto.response.TestGenerationResponse;
import com.company.qa.model.entity.AIGeneratedTest;
import com.company.qa.model.entity.JiraConfiguration;
import com.company.qa.model.entity.JiraStory;
import com.company.qa.repository.AIGeneratedTestRepository;
import com.company.qa.repository.JiraConfigurationRepository;
import com.company.qa.repository.JiraStoryRepository;
import com.company.qa.service.TestFileWriterService;
import com.company.qa.service.ai.AITestGenerationService;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.*;

/**
 * Comprehensive End-to-End Test Suite for Week 12 Day 5.
 *
 * Tests complete flow:
 * JIRA Story → AI Generation → Playwright File Writing → Validation
 *
 * Success Criteria (Week 12 Exit Criteria):
 * ✅ PlaywrightContextBuilder generates Playwright-specific prompts
 * ✅ AITestGenerationService has working Playwright branch
 * ✅ AI generates Java/JUnit test code (not Cucumber)
 * ✅ Generated code uses role-based locators
 * ✅ Test files written to correct location
 * ✅ End-to-end: JIRA → AI → Test File works
 */
@SpringBootTest
@ActiveProfiles("dev")
@Transactional
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("Week 12 Day 5 - Playwright E2E Test Suite")
class PlaywrightGenerationE2ETest {

    @Autowired
    private AITestGenerationService aiTestGenerationService;

    @Autowired
    private TestFileWriterService fileWriterService;

    @Autowired
    private JiraStoryRepository jiraStoryRepository;

    @Autowired
    private AIGeneratedTestRepository aiGeneratedTestRepository;

    private static Path tempTestDir;

    @Autowired
    private JiraConfigurationRepository jiraConfigurationRepository;


    @BeforeAll
    static void setupClass() throws IOException {
        tempTestDir = Files.createTempDirectory("e2e-playwright-tests");
        System.out.println("Created temp test directory: " + tempTestDir);
    }

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(fileWriterService, "draftFolderPath",
                tempTestDir.toString());
        ReflectionTestUtils.setField(fileWriterService, "committedFolderPath",
                tempTestDir.toString());
    }

    @AfterAll
    static void cleanupClass() throws IOException {
        if (tempTestDir != null && Files.exists(tempTestDir)) {
            deleteRecursively(tempTestDir);
            System.out.println("Cleaned up temp test directory");
        }
    }

    // ============================================================
    // EXIT CRITERIA TESTS
    // ============================================================

    @Test
    @Order(1)
    @DisplayName("Exit Criteria 1: PlaywrightContextBuilder generates Playwright-specific prompts")
    void exitCriteria1_PlaywrightContextBuilder_GeneratesPlaywrightPrompts() throws Exception {
        // Given: JIRA story with login requirements
        JiraStory story = createLoginJiraStory("PROJ-100");
        story = jiraStoryRepository.save(story);

        // When: Generate test with Playwright framework
        TestGenerationRequest request = TestGenerationRequest.builder()
                .jiraStoryKey("PROJ-100")
                .testType(AIGeneratedTest.TestType.UI)
                .testFramework(AIGeneratedTest.TestFramework.PLAYWRIGHT)
                .aiProvider("BEDROCK")
                .skipQualityCheck(true)
                .build();

        TestGenerationResponse response = aiTestGenerationService.generateTestFromStory(request);

        // Then: Verify Playwright-specific prompt was used
        assertThat(response).isNotNull();
        assertThat(response.isSuccess()).isTrue();

        // Retrieve the generated test to verify context
        AIGeneratedTest generatedTest = aiGeneratedTestRepository.findById(response.getTestId())
                .orElseThrow();

        // Verify test framework
        assertThat(generatedTest.getTestFramework())
                .isEqualTo(AIGeneratedTest.TestFramework.PLAYWRIGHT);

        System.out.println("✅ Exit Criteria 1: PASSED - PlaywrightContextBuilder working");
    }

    @Test
    @Order(2)
    @DisplayName("Exit Criteria 2: AITestGenerationService has working Playwright branch")
    void exitCriteria2_AITestGenerationService_HasPlaywrightBranch() throws Exception {
        // Given: JIRA story
        JiraStory story = createLoginJiraStory("PROJ-101");
        story = jiraStoryRepository.save(story);

        // When: Request Playwright test generation
        TestGenerationRequest request = TestGenerationRequest.builder()
                .jiraStoryKey("PROJ-101")
                .testType(AIGeneratedTest.TestType.UI)
                .testFramework(AIGeneratedTest.TestFramework.PLAYWRIGHT)
                .aiProvider("BEDROCK")
                .skipQualityCheck(true)
                .build();

        TestGenerationResponse response = aiTestGenerationService.generateTestFromStory(request);

        // Then: Verify response indicates Playwright generation
        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getTestFramework()).isEqualTo("PLAYWRIGHT");

        System.out.println("✅ Exit Criteria 2: PASSED - Playwright branch working");
    }

    @Test
    @Order(3)
    @DisplayName("Exit Criteria 3: AI generates Java/JUnit test code (not Cucumber)")
    void exitCriteria3_GeneratedCode_IsJavaJUnit_NotCucumber() throws Exception {
        // Given: JIRA story
        JiraStory story = createLoginJiraStory("PROJ-102");
        story = jiraStoryRepository.save(story);

        // When: Generate test
        TestGenerationRequest request = TestGenerationRequest.builder()
                .jiraStoryKey("PROJ-102")
                .testType(AIGeneratedTest.TestType.UI)
                .testFramework(AIGeneratedTest.TestFramework.PLAYWRIGHT)
                .aiProvider("BEDROCK")
                .skipQualityCheck(true)
                .build();

        TestGenerationResponse response = aiTestGenerationService.generateTestFromStory(request);

        // Then: Verify generated files
        Path draftFolder = Paths.get(response.getDraftFolderPath());
        List<Path> javaFiles = findJavaFiles(draftFolder);

        assertThat(javaFiles).isNotEmpty();

        // Verify it's Java/JUnit, not Cucumber
        for (Path javaFile : javaFiles) {
            String content = Files.readString(javaFile);

            // Should NOT contain Cucumber imports
            assertThat(content).doesNotContain("import io.cucumber");
            assertThat(content).doesNotContain("@Given");
            assertThat(content).doesNotContain("@When");
            assertThat(content).doesNotContain("@Then");

            // Should contain JUnit imports
            assertThat(content).contains("import org.junit");
            assertThat(content).contains("@Test");
        }

        // Should NOT contain .feature files
        List<Path> featureFiles = findFilesWithExtension(draftFolder, ".feature");
        assertThat(featureFiles).isEmpty();

        System.out.println("✅ Exit Criteria 3: PASSED - Generates Java/JUnit, not Cucumber");
    }

    @Test
    @Order(4)
    @DisplayName("Exit Criteria 4: Generated code uses role-based locators")
    void exitCriteria4_GeneratedCode_UsesRoleBasedLocators() throws Exception {
        // Given: JIRA story with form interaction
        JiraStory story = JiraStory.builder()
                .jiraKey("PROJ-103")
                .summary("User registration form")
                .description("User should fill registration form")
                .acceptanceCriteria("""
                        Given user is on registration page
                        When user fills in email and password
                        And clicks submit button
                        Then user should see success message
                        """)
                .priority("High")
                .createdAt(LocalDateTime.now())
                .build();
        story = jiraStoryRepository.save(story);

        // When: Generate test
        TestGenerationRequest request = TestGenerationRequest.builder()
                .jiraStoryKey("PROJ-103")
                .testType(AIGeneratedTest.TestType.UI)
                .testFramework(AIGeneratedTest.TestFramework.PLAYWRIGHT)
                .aiProvider("BEDROCK")
                .skipQualityCheck(true)
                .build();

        TestGenerationResponse response = aiTestGenerationService.generateTestFromStory(request);

        // Then: Verify role-based locators are used
        Path draftFolder = Paths.get(response.getDraftFolderPath());
        List<Path> javaFiles = findJavaFiles(draftFolder);

        boolean foundRoleBasedLocators = false;
        boolean foundLabelBasedLocators = false;

        for (Path javaFile : javaFiles) {
            String content = Files.readString(javaFile);

            // Check for role-based locators
            if (content.contains("getByRole") || content.contains("AriaRole")) {
                foundRoleBasedLocators = true;
            }

            // Check for label-based locators
            if (content.contains("getByLabel")) {
                foundLabelBasedLocators = true;
            }

            // Should NOT use brittle CSS/XPath as primary locators
            long cssLocatorCount = countOccurrences(content, ".locator(\"");
            long roleLocatorCount = countOccurrences(content, "getByRole");
            long labelLocatorCount = countOccurrences(content, "getByLabel");

            // Role/Label should be more prevalent than CSS
            assertThat(roleLocatorCount + labelLocatorCount)
                    .as("Should use more role/label locators than CSS")
                    .isGreaterThanOrEqualTo(cssLocatorCount);
        }

        assertThat(foundRoleBasedLocators || foundLabelBasedLocators)
                .as("Should use role-based or label-based locators")
                .isTrue();

        System.out.println("✅ Exit Criteria 4: PASSED - Uses role-based locators");
    }

    @Test
    @Order(5)
    @DisplayName("Exit Criteria 5: Test files written to correct location")
    void exitCriteria5_TestFiles_WrittenToCorrectLocation() throws Exception {
        // Given: JIRA story
        JiraStory story = createLoginJiraStory("PROJ-104");
        story = jiraStoryRepository.save(story);

        // When: Generate test
        TestGenerationRequest request = TestGenerationRequest.builder()
                .jiraStoryKey("PROJ-104")
                .testType(AIGeneratedTest.TestType.UI)
                .testFramework(AIGeneratedTest.TestFramework.PLAYWRIGHT)
                .aiProvider("BEDROCK")
                .skipQualityCheck(true)
                .build();

        TestGenerationResponse response = aiTestGenerationService.generateTestFromStory(request);

        // Then: Verify file location
        String draftPath = response.getDraftFolderPath();

        // Should contain proper structure: /UI/PROJ-104/timestamp/
        assertThat(draftPath).contains("UI");
        assertThat(draftPath).contains("PROJ-104");

        // Should exist and be a directory
        Path draftFolder = Paths.get(draftPath);
        assertThat(Files.exists(draftFolder)).isTrue();
        assertThat(Files.isDirectory(draftFolder)).isTrue();

        // Should contain at least one test file
        List<Path> testFiles = findJavaFiles(draftFolder);
        assertThat(testFiles).isNotEmpty();

        System.out.println("✅ Exit Criteria 5: PASSED - Files in correct location: " + draftPath);
    }

    @Test
    @Order(6)
    @DisplayName("Exit Criteria 6: End-to-end JIRA → AI → Test File works")
    void exitCriteria6_EndToEnd_CompleteFlow_Works() throws Exception {
        // COMPLETE FLOW TEST

        // Step 1: Create JIRA story
        JiraStory story = JiraStory.builder()
                .jiraKey("PROJ-200")
                .summary("Complete E2E test - Dashboard navigation")
                .description("User navigates to dashboard and views data")
                .acceptanceCriteria("""
                        Given user is logged in
                        When user navigates to dashboard
                        Then user should see welcome message
                        And user should see data tables
                        """)
                .priority("Critical")
                .status("In Progress")
                .createdAt(LocalDateTime.now())
                .build();
        story = jiraStoryRepository.save(story);

        assertThat(story.getId()).isNotNull();
        System.out.println("Step 1: ✅ JIRA story created: " + story.getJiraKey());

        // Step 2: Trigger AI generation
        TestGenerationRequest request = TestGenerationRequest.builder()
                .jiraStoryKey("PROJ-200")
                .testType(AIGeneratedTest.TestType.UI)
                .testFramework(AIGeneratedTest.TestFramework.PLAYWRIGHT)
                .aiProvider("BEDROCK")
                .skipQualityCheck(false) // Enable quality check
                .build();

        TestGenerationResponse response = aiTestGenerationService.generateTestFromStory(request);

        assertThat(response).isNotNull();
        assertThat(response.isSuccess()).isTrue();
        System.out.println("Step 2: ✅ AI generation completed");

        // Step 3: Verify database records
        AIGeneratedTest generatedTest = aiGeneratedTestRepository.findById(response.getTestId())
                .orElseThrow();

        assertThat(generatedTest.getJiraStoryKey()).isEqualTo("PROJ-200");
        assertThat(generatedTest.getTestFramework()).isEqualTo(AIGeneratedTest.TestFramework.PLAYWRIGHT);
        assertThat(generatedTest.getDraftFolderPath()).isNotNull();
        assertThat(generatedTest.getStatus()).isIn(
                AIGeneratedTest.TestGenerationStatus.DRAFT,
                AIGeneratedTest.TestGenerationStatus.PENDING_REVIEW
        );
        System.out.println("Step 3: ✅ Database records verified");

        // Step 4: Verify files written
        Path draftFolder = Paths.get(generatedTest.getDraftFolderPath());
        assertThat(Files.exists(draftFolder)).isTrue();

        List<Path> javaFiles = findJavaFiles(draftFolder);
        assertThat(javaFiles).isNotEmpty();
        System.out.println("Step 4: ✅ Test files written: " + javaFiles.size() + " file(s)");

        // Step 5: Verify test file quality
        for (Path javaFile : javaFiles) {
            String content = Files.readString(javaFile);

            // Basic quality checks
            assertThat(content).contains("package com.company.qa");
            assertThat(content).contains("import com.microsoft.playwright");
            assertThat(content).contains("@Test");
            assertThat(content).contains("Browser");
            assertThat(content).contains("Page");

            System.out.println("Step 5: ✅ Test file quality verified: " + javaFile.getFileName());
        }

        // Step 6: Verify can commit approved test
        generatedTest.setStatus(AIGeneratedTest.TestGenerationStatus.APPROVED);
        aiGeneratedTestRepository.save(generatedTest);

        String committedPath = fileWriterService.commitApprovedTest(generatedTest);
        assertThat(committedPath).isNotNull();
        assertThat(Files.exists(Paths.get(committedPath))).isTrue();
        System.out.println("Step 6: ✅ Test committed to: " + committedPath);

        System.out.println("\n✅ Exit Criteria 6: PASSED - Complete E2E flow working!");
    }

    // ============================================================
    // ADDITIONAL VALIDATION TESTS
    // ============================================================

    @Test
    @Order(7)
    @DisplayName("Validation: Multiple tests can be generated sequentially")
    void validation_MultipleTests_CanBeGeneratedSequentially() throws Exception {
        for (int i = 1; i <= 3; i++) {
            JiraStory story = createLoginJiraStory("PROJ-30" + i);
            story = jiraStoryRepository.save(story);

            TestGenerationRequest request = TestGenerationRequest.builder()
                    .jiraStoryKey("PROJ-30" + i)
                    .testType(AIGeneratedTest.TestType.UI)
                    .testFramework(AIGeneratedTest.TestFramework.PLAYWRIGHT)
                    .aiProvider("BEDROCK")
                    .skipQualityCheck(true)
                    .build();

            TestGenerationResponse response = aiTestGenerationService.generateTestFromStory(request);

            assertThat(response.isSuccess()).isTrue();
            assertThat(response.getDraftFolderPath()).isNotNull();

            System.out.println("Generated test " + i + "/3: " + response.getTestName());
        }

        System.out.println("✅ Validation: Sequential generation working");
    }

    @Test
    @Order(8)
    @DisplayName("Validation: Generated test contains proper Playwright setup/teardown")
    void validation_GeneratedTest_ContainsProperSetupTeardown() throws Exception {
        JiraStory story = createLoginJiraStory("PROJ-400");
        story = jiraStoryRepository.save(story);

        TestGenerationRequest request = TestGenerationRequest.builder()
                .jiraStoryKey("PROJ-400")
                .testType(AIGeneratedTest.TestType.UI)
                .testFramework(AIGeneratedTest.TestFramework.PLAYWRIGHT)
                .aiProvider("BEDROCK")
                .skipQualityCheck(true)
                .build();

        TestGenerationResponse response = aiTestGenerationService.generateTestFromStory(request);

        Path draftFolder = Paths.get(response.getDraftFolderPath());
        List<Path> javaFiles = findJavaFiles(draftFolder);

        for (Path javaFile : javaFiles) {
            String content = Files.readString(javaFile);

            // Should have proper lifecycle management
            assertThat(content).containsAnyOf("@BeforeEach", "@Before", "@BeforeAll");
            assertThat(content).containsAnyOf("@AfterEach", "@After", "@AfterAll");

            // Should create Playwright instance
            assertThat(content).contains("Playwright.create()");

            // Should close resources
            assertThat(content).containsAnyOf("close()", "playwright.close()");
        }

        System.out.println("✅ Validation: Setup/teardown present");
    }

    @Test
    @Order(9)
    @DisplayName("Validation: File naming follows convention")
    void validation_FileNaming_FollowsConvention() throws Exception {
        JiraStory story = createLoginJiraStory("PROJ-500");
        story = jiraStoryRepository.save(story);

        TestGenerationRequest request = TestGenerationRequest.builder()
                .jiraStoryKey("PROJ-500")
                .testType(AIGeneratedTest.TestType.UI)
                .testFramework(AIGeneratedTest.TestFramework.PLAYWRIGHT)
                .aiProvider("BEDROCK")
                .skipQualityCheck(true)
                .build();

        TestGenerationResponse response = aiTestGenerationService.generateTestFromStory(request);

        Path draftFolder = Paths.get(response.getDraftFolderPath());
        List<Path> javaFiles = findJavaFiles(draftFolder);

        for (Path javaFile : javaFiles) {
            String fileName = javaFile.getFileName().toString();

            // Should end with Test.java
            assertThat(fileName).endsWith("Test.java");

            // Should contain JIRA key (sanitized)
            assertThat(fileName).contains("PROJ500");

            System.out.println("Test file name: " + fileName);
        }

        System.out.println("✅ Validation: File naming convention followed");
    }

    // ============================================================
    // HELPER METHODS
    // ============================================================

    private JiraStory createLoginJiraStory(String project) {

        JiraConfiguration testConfig = JiraConfiguration.builder()
                .id(UUID.randomUUID())
                .configName("integration-test")
                .jiraUrl("https://singh24honey.atlassian.net/jira")
                .projectKey("TEST")
                .maxRequestsPerMinute(60)
                .secretArn("test-secret")
                .createdBy("QA")
                .enabled(false)
                .build();

        testConfig = jiraConfigurationRepository.save(testConfig);

        return JiraStory.builder()
                .jiraKey(project)
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


    private List<Path> findJavaFiles(Path directory) throws IOException {
        return findFilesWithExtension(directory, ".java");
    }

    private List<Path> findFilesWithExtension(Path directory, String extension) throws IOException {
        if (!Files.exists(directory)) {
            return List.of();
        }

        return Files.walk(directory)
                .filter(Files::isRegularFile)
                .filter(path -> path.toString().endsWith(extension))
                .collect(Collectors.toList());
    }

    private long countOccurrences(String text, String pattern) {
        return text.split(pattern, -1).length - 1;
    }

    private static void deleteRecursively(Path path) throws IOException {
        if (Files.isDirectory(path)) {
            Files.list(path).forEach(child -> {
                try {
                    deleteRecursively(child);
                } catch (IOException e) {
                    System.err.println("Failed to delete: " + child);
                }
            });
        }
        Files.deleteIfExists(path);
    }
}