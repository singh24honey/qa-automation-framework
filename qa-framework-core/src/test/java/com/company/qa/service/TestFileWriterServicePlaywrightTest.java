package com.company.qa.service;

import com.company.qa.exception.StorageException;
import com.company.qa.model.entity.AIGeneratedTest;
import com.company.qa.model.entity.JiraStory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.*;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for Playwright file writing in TestFileWriterService.
 *
 * Tests Week 12 Day 4 implementation:
 * - Writing Playwright test class files
 * - Writing new Page Object files
 * - Handling existing Page Objects (logging only)
 * - Proper file structure creation
 * - Error handling for missing required fields
 */
class TestFileWriterServicePlaywrightTest {

    @TempDir
    Path tempDir;

    private TestFileWriterService fileWriterService;

    @BeforeEach
    void setUp() {
        fileWriterService = new TestFileWriterService();

        // Set draft and committed folder paths using reflection
        ReflectionTestUtils.setField(fileWriterService, "draftFolderPath", tempDir.toString());
        ReflectionTestUtils.setField(fileWriterService, "committedFolderPath", tempDir.toString());
    }

    // ============================================================
    // HAPPY PATH TESTS
    // ============================================================

    @Test
    @DisplayName("Should write Playwright test with test class only")
    void writePlaywrightFiles_TestClassOnly_Success() throws IOException {
        // Given
        AIGeneratedTest test = createPlaywrightTest();
        Map<String, Object> testCode = createMinimalPlaywrightTestCode();

        // When
        String draftPath = fileWriterService.writeToDraftFolder(test, testCode);

        // Then
        assertThat(draftPath).isNotNull();
        Path draftFolder = Path.of(draftPath);
        assertThat(Files.exists(draftFolder)).isTrue();

        // Verify test file exists
        Path testFile = draftFolder.resolve("PROJ123_LoginTest.java");
        assertThat(Files.exists(testFile)).isTrue();

        String content = Files.readString(testFile);
        assertThat(content).contains("public class PROJ123_LoginTest");
        assertThat(content).contains("@Test");
        assertThat(content).contains("Playwright");
    }

    @Test
    @DisplayName("Should write Playwright test with new Page Objects")
    void writePlaywrightFiles_WithNewPageObjects_Success() throws IOException {
        // Given
        AIGeneratedTest test = createPlaywrightTest();
        Map<String, Object> testCode = createPlaywrightTestCodeWithNewPages();

        // When
        String draftPath = fileWriterService.writeToDraftFolder(test, testCode);

        // Then
        Path draftFolder = Path.of(draftPath);

        // Verify test file
        Path testFile = draftFolder.resolve("PROJ123_LoginTest.java");
        assertThat(Files.exists(testFile)).isTrue();

        // Verify pages folder and files
        Path pagesFolder = draftFolder.resolve("pages");
        assertThat(Files.exists(pagesFolder)).isTrue();
        assertThat(Files.isDirectory(pagesFolder)).isTrue();

        Path loginPage = pagesFolder.resolve("LoginPage.java");
        assertThat(Files.exists(loginPage)).isTrue();
        String loginPageContent = Files.readString(loginPage);
        assertThat(loginPageContent).contains("public class LoginPage");

        Path dashboardPage = pagesFolder.resolve("DashboardPage.java");
        assertThat(Files.exists(dashboardPage)).isTrue();
        String dashboardPageContent = Files.readString(dashboardPage);
        assertThat(dashboardPageContent).contains("public class DashboardPage");
    }

    @Test
    @DisplayName("Should write Playwright test that uses existing Page Objects")
    void writePlaywrightFiles_WithExistingPages_Success() throws IOException {
        // Given
        AIGeneratedTest test = createPlaywrightTest();
        Map<String, Object> testCode = new HashMap<>();
        testCode.put("testClassName", "PROJ123_LoginTest");
        testCode.put("testClass", createSampleTestClass());
        testCode.put("usesExistingPages", Arrays.asList("LoginPage", "DashboardPage"));

        // When
        String draftPath = fileWriterService.writeToDraftFolder(test, testCode);

        // Then
        Path draftFolder = Path.of(draftPath);

        // Verify test file exists
        Path testFile = draftFolder.resolve("PROJ123_LoginTest.java");
        assertThat(Files.exists(testFile)).isTrue();

        // Verify NO pages folder created (using existing pages)
        Path pagesFolder = draftFolder.resolve("pages");
        assertThat(Files.exists(pagesFolder)).isFalse();
    }

    @Test
    @DisplayName("Should write Playwright test with README")
    void writePlaywrightFiles_WithReadme_Success() throws IOException {
        // Given
        AIGeneratedTest test = createPlaywrightTest();
        Map<String, Object> testCode = createMinimalPlaywrightTestCode();
        testCode.put("readme", "# PROJ-123 Login Test\n\nThis test verifies login functionality.");

        // When
        String draftPath = fileWriterService.writeToDraftFolder(test, testCode);

        // Then
        Path draftFolder = Path.of(draftPath);
        Path readmeFile = draftFolder.resolve("README.md");

        assertThat(Files.exists(readmeFile)).isTrue();
        String readmeContent = Files.readString(readmeFile);
        assertThat(readmeContent).contains("# PROJ-123 Login Test");
        assertThat(readmeContent).contains("login functionality");
    }

    @Test
    @DisplayName("Should extract test class name from testClassName field")
    void writePlaywrightFiles_ExplicitTestClassName_Success() throws IOException {
        // Given
        AIGeneratedTest test = createPlaywrightTest();
        Map<String, Object> testCode = new HashMap<>();
        testCode.put("testClassName", "CustomTestName");
        testCode.put("testClass", createSampleTestClass());

        // When
        String draftPath = fileWriterService.writeToDraftFolder(test, testCode);

        // Then
        Path draftFolder = Path.of(draftPath);
        Path testFile = draftFolder.resolve("CustomTestName.java");
        assertThat(Files.exists(testFile)).isTrue();
    }

    @Test
    @DisplayName("Should extract test class name from Java class content")
    void writePlaywrightFiles_ExtractFromContent_Success() throws IOException {
        // Given
        AIGeneratedTest test = createPlaywrightTest();
        Map<String, Object> testCode = new HashMap<>();
        // No testClassName field
        testCode.put("testClass", createSampleTestClass());

        // When
        String draftPath = fileWriterService.writeToDraftFolder(test, testCode);

        // Then
        Path draftFolder = Path.of(draftPath);
        Path testFile = draftFolder.resolve("PROJ123_LoginTest.java");
        assertThat(Files.exists(testFile)).isTrue();
    }

    // ============================================================
    // ERROR HANDLING TESTS
    // ============================================================

    @Test
    @DisplayName("Should throw exception when testClass is missing")
    void writePlaywrightFiles_MissingTestClass_ThrowsException() {
        // Given
        AIGeneratedTest test = createPlaywrightTest();
        Map<String, Object> testCode = new HashMap<>();
        testCode.put("testClassName", "SomeTest");
        // Missing testClass

        // When/Then
        assertThatThrownBy(() -> fileWriterService.writeToDraftFolder(test, testCode))
                .isInstanceOf(StorageException.class)
                .hasMessageContaining("Missing 'testClass' in Playwright test code");
    }

    @Test
    @DisplayName("Should skip invalid Page Object entries")
    void writePlaywrightFiles_InvalidPageObjectEntry_SkipsGracefully() throws IOException {
        // Given
        AIGeneratedTest test = createPlaywrightTest();
        Map<String, Object> testCode = new HashMap<>();
        testCode.put("testClassName", "PROJ123_Test");
        testCode.put("testClass", createSampleTestClass());

        // Invalid page object (missing classContent)
        List<Map<String, String>> newPages = new ArrayList<>();
        Map<String, String> invalidPage = new HashMap<>();
        invalidPage.put("className", "InvalidPage");
        // Missing classContent
        newPages.add(invalidPage);

        Map<String, String> validPage = new HashMap<>();
        validPage.put("className", "ValidPage");
        validPage.put("classContent", "public class ValidPage {}");
        newPages.add(validPage);

        testCode.put("newPagesNeeded", newPages);

        // When
        String draftPath = fileWriterService.writeToDraftFolder(test, testCode);

        // Then
        Path draftFolder = Path.of(draftPath);
        Path pagesFolder = draftFolder.resolve("pages");

        // Invalid page should be skipped
        Path invalidPageFile = pagesFolder.resolve("InvalidPage.java");
        assertThat(Files.exists(invalidPageFile)).isFalse();

        // Valid page should be written
        Path validPageFile = pagesFolder.resolve("ValidPage.java");
        assertThat(Files.exists(validPageFile)).isTrue();
    }

    @Test
    @DisplayName("Should handle empty newPagesNeeded list")
    void writePlaywrightFiles_EmptyNewPagesList_Success() throws IOException {
        // Given
        AIGeneratedTest test = createPlaywrightTest();
        Map<String, Object> testCode = createMinimalPlaywrightTestCode();
        testCode.put("newPagesNeeded", Collections.emptyList());

        // When
        String draftPath = fileWriterService.writeToDraftFolder(test, testCode);

        // Then
        Path draftFolder = Path.of(draftPath);
        Path testFile = draftFolder.resolve("PROJ123_LoginTest.java");
        assertThat(Files.exists(testFile)).isTrue();

        // Pages folder should not exist
        Path pagesFolder = draftFolder.resolve("pages");
        assertThat(Files.exists(pagesFolder)).isFalse();
    }

    // ============================================================
    // COMMITTED FOLDER TESTS
    // ============================================================

    @Test
    @DisplayName("Should commit Playwright test to correct folder structure")
    void commitApprovedTest_Playwright_CorrectStructure() throws IOException {
        // Given
        AIGeneratedTest test = createPlaywrightTest();
        Map<String, Object> testCode = createPlaywrightTestCodeWithNewPages();

        // Write to draft first
        String draftPath = fileWriterService.writeToDraftFolder(test, testCode);
        test.setDraftFolderPath(draftPath);
        test.setStatus(AIGeneratedTest.TestGenerationStatus.APPROVED);

        // When
        String committedPath = fileWriterService.commitApprovedTest(test);

        // Then
        assertThat(committedPath).contains("playwright-tests");
        assertThat(committedPath).contains("src/test/java/generated");
        assertThat(committedPath).contains("PROJ-123");

        Path committedFolder = Path.of(committedPath);
        assertThat(Files.exists(committedFolder)).isTrue();

        // Verify files copied
        Path testFile = committedFolder.resolve("PROJ123_LoginTest.java");
        assertThat(Files.exists(testFile)).isTrue();

        Path pagesFolder = committedFolder.resolve("pages");
        assertThat(Files.exists(pagesFolder)).isTrue();
    }

    // ============================================================
    // FOLDER STRUCTURE TESTS
    // ============================================================

    @Test
    @DisplayName("Should create proper draft folder structure")
    void writeToDraftFolder_Playwright_CorrectFolderStructure() throws IOException {
        // Given
        AIGeneratedTest test = createPlaywrightTest();
        Map<String, Object> testCode = createMinimalPlaywrightTestCode();

        // When
        String draftPath = fileWriterService.writeToDraftFolder(test, testCode);

        // Then
        // Structure: draftFolder/UI/PROJ-123/yyyyMMdd_HHmmss/
        assertThat(draftPath).contains("UI");
        assertThat(draftPath).contains("PROJ-123");

        Path draftFolder = Path.of(draftPath);
        assertThat(Files.exists(draftFolder)).isTrue();
        assertThat(Files.isDirectory(draftFolder)).isTrue();
    }

    // ============================================================
    // HELPER METHODS
    // ============================================================

    private AIGeneratedTest createPlaywrightTest() {
        JiraStory story = JiraStory.builder()
                .jiraKey("PROJ-123")
                .summary("User login functionality")
                .build();

        return AIGeneratedTest.builder()
                .jiraStory(story)
                .jiraStoryKey("PROJ-123")
                .testName("PROJ123_LoginTest")
                .testType(AIGeneratedTest.TestType.UI)
                .testFramework(AIGeneratedTest.TestFramework.PLAYWRIGHT)
                .status(AIGeneratedTest.TestGenerationStatus.DRAFT)
                .generatedAt(LocalDateTime.now())
                .build();
    }

    private Map<String, Object> createMinimalPlaywrightTestCode() {
        Map<String, Object> testCode = new HashMap<>();
        testCode.put("testClassName", "PROJ123_LoginTest");
        testCode.put("testClass", createSampleTestClass());
        return testCode;
    }

    private Map<String, Object> createPlaywrightTestCodeWithNewPages() {
        Map<String, Object> testCode = new HashMap<>();
        testCode.put("testClassName", "PROJ123_LoginTest");
        testCode.put("testClass", createSampleTestClass());

        // Add new Page Objects
        List<Map<String, String>> newPages = new ArrayList<>();

        Map<String, String> loginPage = new HashMap<>();
        loginPage.put("className", "LoginPage");
        loginPage.put("classContent", createSamplePageObject("LoginPage"));
        newPages.add(loginPage);

        Map<String, String> dashboardPage = new HashMap<>();
        dashboardPage.put("className", "DashboardPage");
        dashboardPage.put("classContent", createSamplePageObject("DashboardPage"));
        newPages.add(dashboardPage);

        testCode.put("newPagesNeeded", newPages);

        return testCode;
    }

    private String createSampleTestClass() {
        return """
                package com.company.qa.playwright.generated;
                
                import com.microsoft.playwright.*;
                import org.junit.jupiter.api.*;
                
                public class PROJ123_LoginTest {
                    private Playwright playwright;
                    private Browser browser;
                    private BrowserContext context;
                    private Page page;
                    
                    @BeforeEach
                    void setUp() {
                        playwright = Playwright.create();
                        browser = playwright.chromium().launch();
                        context = browser.newContext();
                        page = context.newPage();
                    }
                    
                    @Test
                    @DisplayName("Should login successfully with valid credentials")
                    void testLoginSuccess() {
                        page.navigate("https://example.com/login");
                        page.getByLabel("Email").fill("user@example.com");
                        page.getByLabel("Password").fill("password");
                        page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Sign In")).click();
                        
                        assertThat(page.getByTestId("welcome-msg")).isVisible();
                    }
                    
                    @AfterEach
                    void tearDown() {
                        context.close();
                        browser.close();
                        playwright.close();
                    }
                }
                """;
    }

    private String createSamplePageObject(String className) {
        return String.format("""
                package com.company.qa.playwright.pages;
                
                import com.microsoft.playwright.*;
                
                public class %s {
                    private final Page page;
                    
                    public %s(Page page) {
                        this.page = page;
                    }
                    
                    public void navigate() {
                        page.navigate("https://example.com");
                    }
                    
                    public boolean isDisplayed() {
                        return page.isVisible("[data-testid='page-container']");
                    }
                }
                """, className, className);
    }
}
