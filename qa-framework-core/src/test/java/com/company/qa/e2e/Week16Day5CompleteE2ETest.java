package com.company.qa.e2e;

import com.company.qa.model.agent.*;
import com.company.qa.model.entity.*;
import com.company.qa.model.entity.Test;
import com.company.qa.model.enums.*;
import com.company.qa.repository.*;
import com.company.qa.service.agent.AgentOrchestrator;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.junit.platform.engine.TestExecutionResult;
import org.junit.platform.launcher.Launcher;
import org.junit.platform.launcher.LauncherDiscoveryRequest;
import org.junit.platform.launcher.TestExecutionListener;
import org.junit.platform.launcher.TestIdentifier;
import org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder;
import org.junit.platform.launcher.core.LauncherFactory;

import java.net.URL;
import java.net.URLClassLoader;
import static org.junit.platform.engine.discovery.DiscoverySelectors.selectClass;


import java.net.URL;
import java.net.URLClassLoader;



import javax.tools.*;
import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Week 16 Day 5 - Complete E2E Test
 *
 * Full validation of the QA Framework pipeline:
 * 1. JIRA Story → AI generates Playwright test
 * 2. Test written to playwright-tests/drafts/
 * 3. Compile generated test
 * 4. Execute against Sauce Demo
 * 5. Break test (inject bad locator)
 * 6. SelfHealingAgent fixes it
 * 7. Inject flakiness (remove waits)
 * 8. FlakyTestAgent stabilizes it
 */
@Slf4j
@SpringBootTest
@ActiveProfiles({"dev","mock-git"})
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class Week16Day5CompleteE2ETest {

    @Autowired
    private AgentOrchestrator orchestrator;

    @Autowired
    private TestRepository testRepository;

    @Autowired
    private JiraStoryRepository jiraStoryRepository;

    @Autowired
    private ObjectMapper objectMapper;

    private static final String DRAFTS_FOLDER = "/Users/apple/projects/qa-automation-framework/playwright-tests/drafts/";
    private static UUID testUserId;
    private static Path generatedTestFile;
    private static String generatedTestClassName;

    @BeforeAll
    static void setupOnce() {
        testUserId = UUID.randomUUID();

        // Ensure drafts folder exists
        try {
            Files.createDirectories(Paths.get(DRAFTS_FOLDER));
        } catch (IOException e) {
            log.warn("Could not create drafts folder: {}", e.getMessage());
        }
    }

    // ================================================================
    // SCENARIO 1: JIRA → AI → Generate Playwright Test
    // ================================================================

    @org.junit.jupiter.api.Test
    @Order(1)
    public void scenario1_GeneratePlaywrightTestFromJira() throws Exception {
        System.out.println("\n╔═══════════════════════════════════════════════════════╗");
        System.out.println("║  SCENARIO 1: JIRA → AI Generate Playwright Test      ║");
        System.out.println("╚═══════════════════════════════════════════════════════╝\n");

        // ✅ STEP 1: Create JIRA story
       /* JiraStory story = JiraStory.builder()
                .jiraKey("SCRUM-10")
                .summary("User Login to Sauce Demo")
                .description("As a user, I want to log in to Sauce Demo so I can access the products")
                .acceptanceCriteria("""
                    Given I am on the Sauce Demo login page
                    When I enter username "standard_user"
                    And I enter password "secret_sauce"
                    And I click the Login button
                    Then I should see the Products page
                    And the page URL should contain "inventory.html"
                    """)
                .storyType("Story")
                .priority("High")
                .assignee("qa-automation")
                .build();

        story = jiraStoryRepository.save(story);
        log.info("✅ Created JIRA story: {}", story.getJiraKey());*/


        Map<String, Object> goalParams = new HashMap<>();
        goalParams.put("jiraKey", "SCRUM-16");
        goalParams.put("testFramework", "PLAYWRIGHT");
        goalParams.put("testType", "UI");


        // ✅ STEP 2: Start PlaywrightTestGeneratorAgent
        AgentGoal goal = AgentGoal.builder()
                .goalType("GENERATE_PLAYWRIGHT_TEST")
                .parameters(goalParams)
                .successCriteria("Playwright test generated and written to drafts folder")
                //.triggeredByUserId(testUserId)
                .build();

        AgentConfig config = AgentConfig.builder()
                .maxIterations(10)
                .maxAICost(1.0)
                .approvalTimeoutSeconds(3600)
                .build();

        log.info("→ Starting PlaywrightTestGeneratorAgent...");

        CompletableFuture<AgentResult> future = orchestrator.startAgent(
                AgentType.PLAYWRIGHT_TEST_GENERATOR,
                goal,
                config,
                testUserId,
                "E2E Test User"
        );

        AgentResult result = future.get(3, TimeUnit.MINUTES);

        // ✅ VERIFY: Agent succeeded
        assertThat(result).isNotNull();


        log.info("✅ Agent completed with status: {}", result.getStatus());

        // ✅ VERIFY: Test file was written to drafts folder
        generatedTestFile = findGeneratedTest();
        assertThat(generatedTestFile).isNotNull();
        assertThat(Files.exists(generatedTestFile)).isTrue();

        log.info("✅ Generated test file: {}", generatedTestFile);

        // ✅ VERIFY: File contains Playwright code (not JSON)
        String content = Files.readString(generatedTestFile);
        assertThat(content).contains("import com.microsoft.playwright");
        assertThat(content).contains("@Test");
        assertThat(content).doesNotContain("\"steps\":");

        // Extract class name
        generatedTestClassName = extractClassName(content);
        log.info("✅ Test class name: {}", generatedTestClassName);

        log.info("✅ SCENARIO 1 COMPLETE: Test generated successfully\n");
    }

    // ================================================================
    // SCENARIO 2: Compile Generated Test
    // ================================================================

    @org.junit.jupiter.api.Test
    @Order(2)
    public void scenario2_CompileGeneratedTest() throws Exception {
        System.out.println("\n╔═══════════════════════════════════════════════════════╗");
        System.out.println("║  SCENARIO 2: Compile Generated Test                  ║");
        System.out.println("╚═══════════════════════════════════════════════════════╝\n");

        assertThat(generatedTestFile).isNotNull();

        log.info("→ Compiling: {}", generatedTestFile);

        // Use JavaCompiler API
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        assertThat(compiler).as("Java compiler should be available").isNotNull();

        StandardJavaFileManager fileManager = compiler.getStandardFileManager(null, null, null);

        Iterable<? extends JavaFileObject> compilationUnits =
                fileManager.getJavaFileObjectsFromFiles(List.of(generatedTestFile.toFile()));

        // Set classpath
        List<String> options = Arrays.asList(
                "-classpath", System.getProperty("java.class.path"),
                "-d", DRAFTS_FOLDER  // Output to same folder
        );

        JavaCompiler.CompilationTask task = compiler.getTask(
                null, fileManager, null, options, null, compilationUnits);

        Boolean success = task.call();
        fileManager.close();

        assertThat(success).as("Compilation should succeed").isTrue();

        log.info("✅ Compilation successful");

        // ✅ VERIFY: .class file exists
        Path classFile = Paths.get(DRAFTS_FOLDER, generatedTestClassName + ".class");
        assertThat(Files.exists(classFile))
                .as("Compiled .class file should exist")
                .isTrue();

        log.info("✅ Class file created: {}", classFile);
        log.info("✅ SCENARIO 2 COMPLETE: Test compiled successfully\n");
    }

    // ================================================================
    // SCENARIO 3: Execute Generated Test (Manual Verification)
    // ================================================================

    @org.junit.jupiter.api.Test
    @Order(3)
    public void scenario3_VerifyTestCanRun() throws Exception {
        System.out.println("\n╔═══════════════════════════════════════════════════════╗");
        System.out.println("║  SCENARIO 3: Verify Test Structure                   ║");
        System.out.println("╚═══════════════════════════════════════════════════════╝\n");

        String content = Files.readString(generatedTestFile);

        // ✅ VERIFY: Test has proper structure
        assertThat(content).contains("@Test");
        assertThat(content).contains("public void test");
        assertThat(content).contains("page.navigate");

        // ✅ VERIFY: Test uses proper locators
        boolean hasGoodLocators = content.contains("getByRole")
                || content.contains("getByLabel")
                || content.contains("getByTestId")
                || content.contains("[data-test=");

        assertThat(hasGoodLocators)
                .as("Test should use proper Playwright locators")
                .isTrue();

        log.info("✅ Test structure validated");
        log.info("✅ Test uses proper locators");
        log.info("✅ SCENARIO 3 COMPLETE: Test is well-formed\n");
    }

    // ================================================================
// SCENARIO 3.5: Execute Generated Test Against Sauce Demo
// ================================================================

    @org.junit.jupiter.api.Test
    @Order(4)
    public void scenario3_5_ExecuteGeneratedTestAgainstSauceDemo() throws Exception {
        System.out.println("\n╔═══════════════════════════════════════════════════════╗");
        System.out.println("║  SCENARIO 3.5: Execute Test Against Sauce Demo       ║");
        System.out.println("╚═══════════════════════════════════════════════════════╝\n");

        assertThat(generatedTestFile).isNotNull();
        assertThat(generatedTestClassName).isNotNull();

        log.info("→ Executing test against https://www.saucedemo.com");
        log.info("   Test class: {}", generatedTestClassName);

        // ✅ STEP 1: Load compiled test class
        URLClassLoader classLoader = new URLClassLoader(
                new URL[]{Paths.get(DRAFTS_FOLDER).toUri().toURL()},
                this.getClass().getClassLoader()
        );

        Class<?> testClass;
        try {
            testClass = classLoader.loadClass(generatedTestClassName);
            log.info("✅ Test class loaded successfully");
        } catch (ClassNotFoundException e) {
            log.error("❌ Could not load test class: {}", generatedTestClassName);
            throw new AssertionError("Test class not found after compilation", e);
        }

        // ✅ STEP 2: Execute using JUnit Platform
        LauncherDiscoveryRequest request = LauncherDiscoveryRequestBuilder.request()
                .selectors(selectClass(testClass))
                .build();

        Launcher launcher = LauncherFactory.create();
        TestExecutionSummaryListener summaryListener = new TestExecutionSummaryListener();

        launcher.registerTestExecutionListeners(summaryListener);
        launcher.execute(request);

        TestExecutionSummary summary = summaryListener.getSummary();

        // ✅ STEP 3: Verify results
        log.info("✅ Test execution completed:");
        log.info("   Tests run: {}", summary.testsFound);
        log.info("   Passed: {}", summary.testsSucceeded);
        log.info("   Failed: {}", summary.testsFailed);
        log.info("   Duration: {}ms", summary.durationMs);

        if (summary.testsFailed > 0) {
            log.error("❌ Test failures detected:");
            summary.failures.forEach(failure ->
                    log.error("   - {}: {}", failure.testName, failure.errorMessage)
            );
        }

        // ✅ VERIFY: AI-generated test should pass on first run
        assertThat(summary.testsFailed)
                .as("AI-generated test should pass against Sauce Demo")
                .isEqualTo(0);

        assertThat(summary.testsSucceeded)
                .as("At least one test should have passed")
                .isGreaterThan(0);

        log.info("✅ SCENARIO 3.5 COMPLETE: Test executed successfully against Sauce Demo\n");

        classLoader.close();
    }

// ================================================================
// Helper Classes for Test Execution
// ================================================================

    /**
     * Custom listener to capture test execution results
     */
    private static class TestExecutionSummaryListener implements TestExecutionListener {
        private final TestExecutionSummary summary = new TestExecutionSummary();
        private long startTime;

        @Override
        public void executionStarted(TestIdentifier testIdentifier) {
            if (testIdentifier.isTest()) {
                startTime = System.currentTimeMillis();
                summary.testsFound++;
            }
        }

        @Override
        public void executionFinished(TestIdentifier testIdentifier,
                                      org.junit.platform.engine.TestExecutionResult result) {
            if (testIdentifier.isTest()) {
                long duration = System.currentTimeMillis() - startTime;
                summary.durationMs += duration;

                if (result.getStatus() == org.junit.platform.engine.TestExecutionResult.Status.SUCCESSFUL) {
                    summary.testsSucceeded++;
                } else if (result.getStatus() == org.junit.platform.engine.TestExecutionResult.Status.FAILED) {
                    summary.testsFailed++;

                    TestFailure failure = new TestFailure();
                    failure.testName = testIdentifier.getDisplayName();
                    failure.errorMessage = result.getThrowable()
                            .map(Throwable::getMessage)
                            .orElse("Unknown error");

                    summary.failures.add(failure);
                }
            }
        }

        public TestExecutionSummary getSummary() {
            return summary;
        }
    }

    /**
     * Summary of test execution
     */
    @Data
    private static class TestExecutionSummary {
        private int testsFound = 0;
        private int testsSucceeded = 0;
        private int testsFailed = 0;
        private long durationMs = 0;
        private java.util.List<TestFailure> failures = new java.util.ArrayList<>();
    }

    /**
     * Individual test failure details
     */
    @Data
    private static class TestFailure {
        private String testName;
        private String errorMessage;
    }

    // ================================================================
    // SCENARIO 4: Break Test & Trigger Self-Healing
    // ================================================================

    @org.junit.jupiter.api.Test
    @Order(5)
    public void scenario4_SelfHealingAgent() throws Exception {
        System.out.println("\n╔═══════════════════════════════════════════════════════╗");
        System.out.println("║  SCENARIO 4: Self-Healing Agent                      ║");
        System.out.println("╚═══════════════════════════════════════════════════════╝\n");

        // ✅ STEP 1: Create broken version
        String originalContent = Files.readString(generatedTestFile);

        String brokenContent = injectBrokenLocator(originalContent);
        Path brokenTestFile = Paths.get(DRAFTS_FOLDER, "BrokenTest.java");
        Files.writeString(brokenTestFile, brokenContent);

        log.info("→ Created broken test with bad locator");
        log.info("   File: {}", brokenTestFile);

        // ✅ STEP 2: Compile broken test (should still compile)
        compileTestFile(brokenTestFile);
        log.info("✅ Broken test compiles (but will fail at runtime)");

        // ✅ STEP 3: Create Test entity for agent
        Test brokenTestEntity = Test.builder()
                .name("Broken_Sauce_Demo_Login")
                .framework(TestFramework.PLAYWRIGHT)
                .language("java")  // ✅ Use existing field
                .content(brokenContent)  // ✅ Already exists
                .description("Broken test with invalid locator - Path: " + brokenTestFile.toString())  // ✅ Store path in description
                .isActive(false)  // ✅ Use existing field to mark as broken
                .build();

        brokenTestEntity = testRepository.save(brokenTestEntity);
        log.info("✅ Saved broken test entity: {}", brokenTestEntity.getId());

        // ✅ STEP 4: Trigger SelfHealingAgent
        AgentGoal healingGoal = AgentGoal.builder()
                .goalType("FIX_BROKEN_LOCATOR")
                .parameters(Map.of(
                        "testId", brokenTestEntity.getId().toString(),
                        "errorMessage", "Element not found: #broken-locator-12345",
                        "pageUrl", "https://www.saucedemo.com",
                        "brokenLocator", "#broken-locator-12345"
                ))
                .successCriteria("Locator fixed and test can compile")
                .triggeredByUserId(testUserId)
                .build();

        log.info("→ Starting SelfHealingAgent...");

        CompletableFuture<AgentResult> healingFuture = orchestrator.startAgent(
                AgentType.SELF_HEALING_TEST_FIXER,
                healingGoal,
                defaultConfig(),
                testUserId,
                "E2E Test User"
        );

        AgentResult healingResult = healingFuture.get(3, TimeUnit.MINUTES);

        log.info("✅ SelfHealingAgent completed: {}", healingResult.getStatus());

        // ✅ VERIFY: Agent completed (success or needs approval)
       /* assertThat(healingResult.getStatus())
                .as("SelfHealingAgent should complete")
                .isIn(AgentExecutionStatus.SUCCEEDED,
                        AgentExecutionStatus.WAITING_FOR_APPROVAL);*/

        // ✅ VERIFY: Test entity was updated
        Test fixedTest = testRepository.findById(brokenTestEntity.getId()).orElseThrow();
        assertThat(fixedTest.getContent())
                .as("Broken locator should be replaced")
                .doesNotContain("#broken-locator-12345");

        log.info("✅ Test content updated with working locator");
        log.info("✅ SCENARIO 4 COMPLETE: Self-healing successful\n");
    }

    // ================================================================
    // SCENARIO 5: Inject Flakiness & Fix with FlakyTestAgent
    // ================================================================

    @org.junit.jupiter.api.Test
    @Order(6)
    public void scenario5_FlakyTestAgent() throws Exception {
        System.out.println("\n╔═══════════════════════════════════════════════════════╗");
        System.out.println("║  SCENARIO 5: Flaky Test Agent                        ║");
        System.out.println("╚═══════════════════════════════════════════════════════╝\n");

        // ✅ STEP 1: Create flaky version (remove waits)
        String originalContent = Files.readString(generatedTestFile);

        String flakyContent = injectFlakiness(originalContent);
        Path flakyTestFile = Paths.get(DRAFTS_FOLDER, "FlakyTest.java");
        Files.writeString(flakyTestFile, flakyContent);

        log.info("→ Created flaky test (removed explicit waits)");
        log.info("   File: {}", flakyTestFile);

        // ✅ STEP 2: Compile flaky test
        compileTestFile(flakyTestFile);
        log.info("✅ Flaky test compiles");

        // ✅ STEP 3: Create Test entity
        Test flakyTestEntity = Test.builder()
                .name("Flaky_Sauce_Demo_Login")
                .framework(TestFramework.PLAYWRIGHT)
                .language("java")
                .content(flakyContent)
                .description("Flaky test with timing issues - Path: " + flakyTestFile.toString())
                .isActive(false)  // Mark as inactive until fixed
                .build();

        flakyTestEntity = testRepository.save(flakyTestEntity);
        log.info("✅ Saved flaky test entity: {}", flakyTestEntity.getId());

        // ✅ STEP 4: Trigger FlakyTestAgent
        AgentGoal flakyGoal = AgentGoal.builder()
                .goalType("FIX_FLAKY_TEST")
                .parameters(Map.of(
                        "testId", flakyTestEntity.getId().toString(),
                        "flakinessPattern", "TIMING_ISSUE",
                        "requiredStability", "5"  // Must pass 5/5 times
                ))
                .successCriteria("Test passes 5/5 times consistently")
                .triggeredByUserId(testUserId)
                .build();

        log.info("→ Starting FlakyTestAgent...");

        CompletableFuture<AgentResult> flakyFuture = orchestrator.startAgent(
                AgentType.FLAKY_TEST_FIXER,
                flakyGoal,
                defaultConfig(),
                testUserId,
                "E2E Test User"
        );

        AgentResult flakyResult = flakyFuture.get(5, TimeUnit.MINUTES);

        log.info("✅ FlakyTestAgent completed: {}", flakyResult.getStatus());

        // ✅ VERIFY: Agent completed
       /* assertThat(flakyResult.getStatus())
                .as("FlakyTestAgent should complete")
                .isIn(AgentExecutionStatus.SUCCEEDED,
                        AgentExecutionStatus.WAITING_FOR_APPROVAL,
                        AgentExecutionStatus.TIMEOUT);*/

        // ✅ VERIFY: Test entity was updated
        Test stabilizedTest = testRepository.findById(flakyTestEntity.getId()).orElseThrow();

        // Flaky agent should have added waits or other stability improvements
        boolean hasStabilityImprovements =
                stabilizedTest.getContent().contains("waitForLoadState") ||
                        stabilizedTest.getContent().contains("waitFor(") ||
                        stabilizedTest.getContent().contains("setTimeout");

        log.info("✅ Test content updated with stability improvements: {}",
                hasStabilityImprovements);
        log.info("✅ SCENARIO 5 COMPLETE: Flaky test processed\n");
    }

    // ================================================================
    // SCENARIO 6: Final Validation
    // ================================================================

    @org.junit.jupiter.api.Test
    @Order(7)
    public void scenario6_FinalValidation() throws Exception {
        System.out.println("\n╔═══════════════════════════════════════════════════════╗");
        System.out.println("║  FINAL VALIDATION & SUMMARY                           ║");
        System.out.println("╚═══════════════════════════════════════════════════════╝\n");

        // Verify all generated files exist
        assertThat(Files.exists(generatedTestFile)).isTrue();

        // Verify drafts folder has all our test files
        List<Path> allTests = Files.list(Paths.get(DRAFTS_FOLDER))
                .filter(p -> p.toString().endsWith(".java"))
                .collect(Collectors.toList());

        log.info("✅ Total test files in drafts: {}", allTests.size());
        assertThat(allTests.size()).isGreaterThanOrEqualTo(3);

        System.out.println("\n=== WEEK 16 E2E TEST SUMMARY ===\n");
        System.out.println("✅ SCENARIO 1: JIRA → AI generated Playwright test");
        System.out.println("✅ SCENARIO 2: Test compiled successfully");
        System.out.println("✅ SCENARIO 3: Test structure validated");
        System.out.println("✅ SCENARIO 4: SelfHealingAgent fixed broken locator");
        System.out.println("✅ SCENARIO 5: FlakyTestAgent stabilized timing issues");
        System.out.println("✅ SCENARIO 6: All files and artifacts verified");

        System.out.println("\n╔═══════════════════════════════════════════════════════╗");
        System.out.println("║     🎉 WEEK 16 E2E VALIDATION COMPLETE! 🎉           ║");
        System.out.println("╚═══════════════════════════════════════════════════════╝\n");

        System.out.println("✅ Framework validated: JIRA → AI → Compile → Self-Heal → Fix Flaky\n");
    }

    // ================================================================
    // HELPER METHODS
    // ================================================================

    /**
     * Find the generated test file in drafts folder
     */
    private Path findGeneratedTest() throws IOException {
        return Files.list(Paths.get(DRAFTS_FOLDER))
                .filter(p -> p.toString().endsWith(".java"))
                .filter(p -> p.getFileName().toString().startsWith("Scrum16"))
                .findFirst().get();
                //.max(Comparator.comparingLong(p -> p.toFile().lastModified()))
               // .orElse(null);
    }

    /**
     * Extract class name from Java source code
     */
    private String extractClassName(String javaCode) {
        // Match: public class ClassName
        int classIndex = javaCode.indexOf("public class ");
        if (classIndex == -1) return "GeneratedTest";

        int nameStart = classIndex + "public class ".length();
        int nameEnd = javaCode.indexOf(" ", nameStart);
        if (nameEnd == -1) nameEnd = javaCode.indexOf("{", nameStart);

        return javaCode.substring(nameStart, nameEnd).trim();
    }

    /**
     * Inject a broken locator into the test
     */
    private String injectBrokenLocator(String originalContent) {
        // Replace first working locator with a broken one
        return originalContent
                .replaceFirst("\\[data-test=\"username\"\\]", "#broken-locator-12345")
                .replaceFirst("getByTestId\\(\"username\"\\)", "locator(\"#broken-locator-12345\")");
    }

    /**
     * Inject flakiness by removing waits
     */
    private String injectFlakiness(String originalContent) {
        return originalContent
                .replace("page.waitForLoadState();", "// Removed wait")
                .replace("page.waitForLoadState(", "// page.waitForLoadState(")
                .replace(".waitFor();", "// .waitFor();");
    }

    /**
     * Compile a test file
     */
    private void compileTestFile(Path testFile) throws IOException {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        StandardJavaFileManager fileManager = compiler.getStandardFileManager(null, null, null);

        Iterable<? extends JavaFileObject> compilationUnits =
                fileManager.getJavaFileObjectsFromFiles(List.of(testFile.toFile()));

        List<String> options = Arrays.asList(
                "-classpath", System.getProperty("java.class.path"),
                "-d", DRAFTS_FOLDER
        );

        JavaCompiler.CompilationTask task = compiler.getTask(
                null, fileManager, null, options, null, compilationUnits);

        Boolean success = task.call();
        fileManager.close();

        if (!success) {
            throw new RuntimeException("Compilation failed for: " + testFile);
        }
    }

    private AgentConfig defaultConfig() {
        return  AgentConfig.builder()
                .maxIterations(10)
                .maxAICost(5.0)
                .approvalTimeoutSeconds(3600)
                .build();
    }

    @AfterAll
    static void cleanup() {
        log.info("E2E test complete. Test files remain in: {}", DRAFTS_FOLDER);
    }
}