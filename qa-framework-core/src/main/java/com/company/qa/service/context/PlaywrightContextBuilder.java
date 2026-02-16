package com.company.qa.service.context;

import com.company.qa.model.dto.ParsedAcceptanceCriteria;
import com.company.qa.model.entity.AIGeneratedTest;
import com.company.qa.model.entity.JiraStory;
import com.company.qa.service.parser.AcceptanceCriteriaParser;
import com.company.qa.service.playwright.ElementRegistryService;
import com.company.qa.service.playwright.PageObjectRegistryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.io.ClassPathResource;
import java.io.InputStream;

/**
 * Week 12 Day 1-2: Playwright Context Builder
 *
 * Builds AI prompts specifically optimized for Playwright test generation.
 * This is a specialized context builder that focuses on Playwright best practices
 * and generates high-quality, framework-aware test code.
 *
 * KEY DIFFERENCES from JiraContextBuilder (Cucumber-focused):
 * ┌────────────────────────────────────────────────────────────────────────┐
 * │ Aspect              │ JiraContextBuilder      │ PlaywrightContextBuilder│
 * ├────────────────────────────────────────────────────────────────────────┤
 * │ Framework           │ Cucumber/Selenium       │ Playwright (Java)       │
 * │ Locator Strategy    │ CSS/XPath hardcoded     │ Role-based (getByRole)  │
 * │ Output Format       │ .feature + StepDefs     │ *Test.java class        │
 * │ Language            │ Gherkin + Java          │ Pure Java               │
 * │ Test Structure      │ BDD scenarios           │ JUnit/TestNG methods    │
 * │ AI Usability        │ ~10% (90% rework)       │ ~70-80% (20-30% rework) │
 * └────────────────────────────────────────────────────────────────────────┘
 *
 * INTEGRATION WITH FUTURE PHASES:
 * - Week 13: Will be enhanced with Element Registry context
 * - Week 13: Will be enhanced with Page Object Registry context
 * - Week 14: PlaywrightAgent will use this builder for prompts
 *
 * Design Philosophy:
 * 1. Provide comprehensive Playwright-specific guidance
 * 2. Emphasize role-based locators (accessibility best practices)
 * 3. Include auto-waiting patterns (no Thread.sleep)
 * 4. Guide AI toward Page Object Model
 * 5. Prepare for Element Registry integration (Week 13)
 *
 * @author QA Framework
 * @since Week 12 Day 1
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PlaywrightContextBuilder {

    private final AcceptanceCriteriaParser acParser;
    private final JiraContextBuilder jiraContextBuilder;
    private final ElementRegistryService elementRegistryService;
    private final PageObjectRegistryService pageObjectRegistryService;

    // ═══════════════════════════════════════════════════════════════════
    // MAIN CONTEXT BUILDING METHODS
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Build comprehensive AI prompt for Playwright test generation from JIRA story.
     *
     * Structure (optimized for Playwright):
     * 1. User's custom prompt (optional)
     * 2. Story context (key, type, status, priority)
     * 3. Summary
     * 4. Description (if present)
     * 5. Acceptance criteria (parsed and structured)
     * 6. Additional context (labels, components)
     * 7. Playwright-specific test generation instructions
     * 8. Locator strategy guidance (CRITICAL for usability)
     * 9. Framework context (Java, Playwright, JUnit)
     * 10. Output format instructions (JSON structure)
     *
     * @param story JIRA story entity
     * @param userPrompt User's additional instructions (optional)
     * @param testType Type of test to generate (UI, API, etc.)
     * @return Structured prompt optimized for Playwright test generation
     */
    public String buildPlaywrightTestPrompt(
            JiraStory story,
            String userPrompt,
            AIGeneratedTest.TestType testType) {

        log.debug("Building Playwright test prompt for JIRA story: {}", story.getJiraKey());

        StringBuilder prompt = new StringBuilder();

        // ───────────────────────────────────────────────────────────────
        // 1. User's Custom Request (if provided)
        // ───────────────────────────────────────────────────────────────
        if (userPrompt != null && !userPrompt.trim().isEmpty()) {
            prompt.append(userPrompt).append("\n\n");
        }

        // ───────────────────────────────────────────────────────────────
        // 2. Story Context Header
        // ───────────────────────────────────────────────────────────────
        prompt.append("=== JIRA Story Context ===\n");
        prompt.append("Story Key: ").append(story.getJiraKey()).append("\n");
        prompt.append("Type: ").append(story.getStoryType()).append("\n");
        prompt.append("Status: ").append(story.getStatus()).append("\n");
        prompt.append("Priority: ").append(story.getPriority()).append("\n");
        prompt.append("\n");

        // ───────────────────────────────────────────────────────────────
        // 3. Summary (Most Important Field)
        // ───────────────────────────────────────────────────────────────
        prompt.append("=== Story Summary ===\n");
        prompt.append(cleanJiraHtml(story.getSummary())).append("\n\n");

        // ───────────────────────────────────────────────────────────────
        // 4. Description (if present)
        // ───────────────────────────────────────────────────────────────
        if (story.getDescription() != null && !story.getDescription().trim().isEmpty()) {
            prompt.append("=== Description ===\n");
            prompt.append(cleanJiraHtml(story.getDescription())).append("\n\n");
        }

        // ───────────────────────────────────────────────────────────────
        // 5. Acceptance Criteria (Parsed and Structured)
        // ───────────────────────────────────────────────────────────────
        if (story.hasAcceptanceCriteria()) {
            ParsedAcceptanceCriteria parsedAC = acParser.parse(story.getAcceptanceCriteria());
            prompt.append(buildAcceptanceCriteriaSection(parsedAC));
        }

        // ───────────────────────────────────────────────────────────────
        // 6. Additional Context (Labels, Components, etc.)
        // ───────────────────────────────────────────────────────────────
        prompt.append(buildAdditionalContext(story));

        // ───────────────────────────────────────────────────────────────
        // 7. Playwright-Specific Test Generation Instructions
        // ───────────────────────────────────────────────────────────────
        ParsedAcceptanceCriteria.ACFormat acFormat = story.hasAcceptanceCriteria()
                ? acParser.parse(story.getAcceptanceCriteria()).getFormat()
                : ParsedAcceptanceCriteria.ACFormat.EMPTY;

        prompt.append(buildPlaywrightTestInstructions(testType, acFormat));

        // ───────────────────────────────────────────────────────────────
        // 8. Locator Strategy Guidance (CRITICAL for 70-80% usability)
        // ───────────────────────────────────────────────────────────────
        prompt.append(buildLocatorStrategyGuide());

        // ───────────────────────────────────────────────────────────────
        // 9. Framework Context
        // ───────────────────────────────────────────────────────────────
        prompt.append(buildFrameworkContext());

        // ───────────────────────────────────────────────────────────────
        // 10. Output Format Instructions
        // ───────────────────────────────────────────────────────────────
        prompt.append(buildOutputFormatInstructions());

        String result = prompt.toString();
        log.debug("Built Playwright prompt: {} characters, {} sections",
                result.length(),
                countSections(result));

        return result;
    }

    /**
     * Build complete AI context with registry integration.
     */
    public String buildContext(JiraStory story, String openApiContext) {
        log.info("Building enhanced Playwright context for story: {}", story.getJiraKey());

        StringBuilder context = new StringBuilder();

        // 1. JIRA Story Context
        context.append("=== JIRA Story Context ===\n");
        context.append(jiraContextBuilder.buildMinimalPrompt(story));
        context.append("\n\n");

        // 2. OpenAPI Context (if available)
        if (openApiContext != null && !openApiContext.trim().isEmpty()) {
            context.append("=== API Context ===\n");
            context.append(openApiContext);
            context.append("\n\n");
        }

        if (isSauceDemoStory(story)) {
            context.append("=== Sauce Demo Application Context ===\n");
            context.append("Base URL: https://www.saucedemo.com\n");
            context.append("Test Credentials: standard_user / secret_sauce\n");
            context.append("Framework: Playwright Java\n");
            context.append("Base Class: extends BasePlaywrightTest\n");
            context.append("\n");

            // Load Sauce Demo specific element registry
            context.append(getSauceDemoElementContext(story));
            context.append("\n");


        }

       if(!isSauceDemoStory(story)) { // 3. Element Registry Context (NEW - Week 13)
           List<String> relevantPages = extractRelevantPages(story);
           String elementContext = elementRegistryService.getContextForAIPrompt(relevantPages);
           context.append(elementContext);
           context.append("\n");

           // 4. Page Object Registry Context (NEW - Week 13)
           String pageObjectContext = pageObjectRegistryService.getContextForAIPrompt();
           context.append(pageObjectContext);
           context.append("\n");
       }
        // 5. Playwright Best Practices
        context.append(getPlaywrightGuidance());
        context.append("\n\n");

        // 6. Output Format Instructions
        context.append(getOutputFormatInstructions());

        log.debug("Built context: {} characters", context.length());
        return context.toString();
    }

    /**
     * Extract relevant page names from story context.
     *
     * Looks for common page keywords in summary and acceptance criteria.
     */
    private List<String> extractRelevantPages(JiraStory story) {
        List<String> pages = new ArrayList<>();

        String searchText = (story.getSummary() + " " +
                story.getAcceptanceCriteria()).toLowerCase();

        // Common page patterns
        if (searchText.contains("login") || searchText.contains("sign in")) {
            pages.add("login");
        }
        if (searchText.contains("dashboard") || searchText.contains("home")) {
            pages.add("dashboard");
        }
        if (searchText.contains("register") || searchText.contains("sign up")) {
            pages.add("registration");
        }
        if (searchText.contains("profile") || searchText.contains("account")) {
            pages.add("profile");
        }
        if (searchText.contains("checkout") || searchText.contains("cart")) {
            pages.add("checkout");
        }

        log.debug("Identified relevant pages: {}", pages);
        return pages;
    }

    /**
     * Playwright locator strategy guidance.
     */
    private String getPlaywrightGuidance() {
        return """
=== Playwright Locator Strategy (CRITICAL) ===

Use locators in this priority order:

1. Role-based (BEST for accessibility):
   page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Sign In"))
   page.getByRole(AriaRole.TEXTBOX, new Page.GetByRoleOptions().setName("Email"))

2. Label-based (for form fields):
   page.getByLabel("Email")
   page.getByLabel("Password")

3. Placeholder-based:
   page.getByPlaceholder("Enter your email")

4. Test ID (when available in Element Registry):
   page.getByTestId("login-error")

5. Text content:
   page.getByText("Welcome back")

6. CSS/XPath (LAST RESORT - avoid if possible):
   page.locator("#email-input")

=== IMPORTANT INSTRUCTIONS ===

1. **CHECK ELEMENT REGISTRY FIRST**: If an element is listed in the Element Registry above, 
   USE THE EXACT locator code provided. Do NOT create your own locator for that element.

2. **CHECK PAGE OBJECT REGISTRY**: If a Page Object exists for the page you're testing,
   USE the existing methods instead of writing raw Playwright code in the test.
   Example: Use loginPage.login(email, password) instead of page.getByLabel("Email").fill(...)

3. **Only create NEW Page Objects if**:
   - No existing Page Object handles this page
   - The test requires new methods not in existing Page Objects

4. **Test Structure**:
   - Extend BasePlaywrightTest
   - Use JUnit 5 (@Test annotation)
   - Include meaningful assertions
   - Add comments for complex logic

5. **Naming Conventions**:
   - Test class: {StoryKey}_{Feature}Test (e.g., PROJ123_LoginTest)
   - Test methods: test{Scenario}() (e.g., testUserCanLoginWithValidCredentials)
""";
    }

    /**private String getPlaywrightGuidance() {
     return """
     === Playwright Locator Strategy (CRITICAL) ===

     Use locators in this priority order:

     1. Role-based (BEST for accessibility):
     page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Sign In"))
     page.getByRole(AriaRole.TEXTBOX, new Page.GetByRoleOptions().setName("Email"))

     2. Label-based (for form fields):
     page.getByLabel("Email")
     page.getByLabel("Password")

     3. Placeholder-based:
     page.getByPlaceholder("Enter your email")

     4. Test ID (when available in Element Registry):
     page.getByTestId("login-error")

     5. Text content:
     page.getByText("Welcome back")

     6. CSS/XPath (LAST RESORT - avoid if possible):
     page.locator("#email-input")

     === IMPORTANT INSTRUCTIONS ===

     1. **CHECK ELEMENT REGISTRY FIRST**: If an element is listed in the Element Registry above,
     USE THE EXACT locator code provided. Do NOT create your own locator for that element.

     2. **CHECK PAGE OBJECT REGISTRY**: If a Page Object exists for the page you're testing,
     USE the existing methods instead of writing raw Playwright code in the test.
     Example: Use loginPage.login(email, password) instead of page.getByLabel("Email").fill(...)

     3. **Only create NEW Page Objects if**:
     - No existing Page Object handles this page
     - The test requires new methods not in existing Page Objects

     4. **Test Structure**:
     - Extend BasePlaywrightTest
     - Use JUnit 5 (@Test annotation)
     - Include meaningful assertions
     - Add comments for complex logic

     5. **Naming Conventions**:
     - Test class: {StoryKey}_{Feature}Test (e.g., PROJ123_LoginTest)
     - Test methods:
     * JSON output format instructions.
     */
    private String getOutputFormatInstructions() {
        return """
=== Output Format (STRICT) ===

Return ONLY valid JSON (no markdown, no code blocks):

{
  "testClassName": "PROJ123_LoginTest",
  "testClass": "package com.company.qa.playwright.generated;\\n\\nimport com.microsoft.playwright.*;\\nimport org.junit.jupiter.api.*;\\nimport static com.microsoft.playwright.assertions.PlaywrightAssertions.*;\\n\\npublic class PROJ123_LoginTest extends BasePlaywrightTest {\\n    \\n    @Test\\n    public void testUserCanLoginSuccessfully() {\\n        page.navigate(\\"https://example.com/login\\");\\n        page.getByLabel(\\"Email\\").fill(\\"user@example.com\\");\\n        page.getByLabel(\\"Password\\").fill(\\"password123\\");\\n        page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(\\"Sign In\\")).click();\\n        \\n        assertThat(page).hasURL(Pattern.compile(\\".*dashboard\\"));\\n    }\\n}",
  "usesExistingPages": ["LoginPage"],
  "newPagesNeeded": []
}

Field explanations:
- testClassName: Name of the test class
- testClass: Complete Java test class code as a string (escape quotes and newlines)
- usesExistingPages: List of existing Page Object class names referenced
- newPagesNeeded: Array of new Page Objects to create (only if absolutely necessary)
  Format: [{"className": "NewPage", "classContent": "full Java code"}]

CRITICAL: 
- Return ONLY the JSON object
- No ```json or ``` markers
- No explanatory text before or after
- Properly escape all quotes and newlines in the Java code
""";
    }

    /**
     * Build context without registry (fallback for backward compatibility).
     */
    public String buildBasicContext(JiraStory story, String openApiContext) {
        log.warn("Using basic context without registry integration");

        StringBuilder context = new StringBuilder();
        context.append("=== JIRA Story Context ===\n");
        context.append(jiraContextBuilder.buildMinimalPrompt(story));
        context.append("\n\n");

        if (openApiContext != null && !openApiContext.trim().isEmpty()) {
            context.append("=== API Context ===\n");
            context.append(openApiContext);
            context.append("\n\n");
        }

        context.append(getPlaywrightGuidance());
        context.append("\n\n");
        context.append(getOutputFormatInstructions());

        return context.toString();
    }


    /**
     * Build minimal prompt for quick Playwright test generation.
     * Useful for simple stories or when context size needs to be minimized.
     *
     * Contains only:
     * - Summary
     * - Acceptance criteria OR description
     * - Basic Playwright instructions
     * - Locator strategy (simplified)
     * - Output format
     */
    public String buildMinimalPlaywrightPrompt(
            JiraStory story,
            AIGeneratedTest.TestType testType) {

        StringBuilder prompt = new StringBuilder();

        prompt.append("Generate Playwright test for: ")
                .append(cleanJiraHtml(story.getSummary()))
                .append("\n\n");

        if (story.hasAcceptanceCriteria()) {
            prompt.append("Acceptance Criteria:\n");
            prompt.append(cleanJiraHtml(story.getAcceptanceCriteria())).append("\n\n");
        } else if (story.getDescription() != null) {
            prompt.append("Description:\n");
            prompt.append(cleanJiraHtml(story.getDescription())).append("\n\n");
        }

        // Essential Playwright guidance
        prompt.append("Use Playwright (Java) with role-based locators:\n");
        prompt.append("- page.getByRole() for buttons, links, textboxes\n");
        prompt.append("- page.getByLabel() for form fields\n");
        prompt.append("- page.getByTestId() when role/label not available\n\n");

        // Output format (simplified)
        prompt.append("Return JSON: {\"testClassName\": \"...\", \"testClass\": \"...\"}");

        return prompt.toString();
    }

    // ═══════════════════════════════════════════════════════════════════
    // SECTION BUILDERS (Playwright-Optimized)
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Build acceptance criteria section.
     * Reuses logic from JiraContextBuilder but adapted for Playwright.
     */
    private String buildAcceptanceCriteriaSection(ParsedAcceptanceCriteria parsedAC) {
        StringBuilder section = new StringBuilder();

        section.append("=== Acceptance Criteria ===\n");
        section.append("Format: ").append(parsedAC.getFormat()).append("\n");

        switch (parsedAC.getFormat()) {
            case GHERKIN:
                section.append("Scenarios:\n");
                parsedAC.getScenarios().forEach(scenario -> {
                    section.append(scenario.toGherkinString()).append("\n");
                });
                break;

            case NUMBERED_LIST:
            case BULLET_POINTS:
                section.append("Steps:\n");
                for (int i = 0; i < parsedAC.getSteps().size(); i++) {
                    section.append(i + 1).append(". ")
                            .append(parsedAC.getSteps().get(i)).append("\n");
                }
                section.append("\nNormalized to Gherkin:\n");
                section.append(parsedAC.getNormalizedGherkin()).append("\n");
                break;

            case UNSTRUCTURED:
            case MIXED:
                section.append("Raw Text:\n");
                section.append(parsedAC.getRawText()).append("\n");
                if (parsedAC.getNormalizedGherkin() != null) {
                    section.append("\nNormalized to Gherkin:\n");
                    section.append(parsedAC.getNormalizedGherkin()).append("\n");
                }
                break;

            default:
                section.append("No acceptance criteria provided.\n");
                section.append("Tests should be generated based on story summary and description.\n");
        }

        section.append("\n");
        return section.toString();
    }

    /**
     * Build additional context section.
     * Same as JiraContextBuilder - includes labels, components, assignee.
     */
    private String buildAdditionalContext(JiraStory story) {
        StringBuilder context = new StringBuilder();
        context.append("=== Additional Context ===\n");

        boolean hasAdditionalContext = false;

        // Labels
        if (story.getLabels() != null && story.getLabels().length > 0) {
            context.append("Labels: ")
                    .append(Arrays.stream(story.getLabels())
                            .collect(Collectors.joining(", ")))
                    .append("\n");
            hasAdditionalContext = true;
        }

        // Components
        if (story.getComponents() != null && story.getComponents().length > 0) {
            context.append("Components: ")
                    .append(Arrays.stream(story.getComponents())
                            .collect(Collectors.joining(", ")))
                    .append("\n");
            hasAdditionalContext = true;
        }

        // Assignee
        if (story.getAssignee() != null) {
            context.append("Assignee: ").append(story.getAssignee()).append("\n");
            hasAdditionalContext = true;
        }

        if (!hasAdditionalContext) {
            context.append("No additional context available.\n");
        }

        context.append("\n");
        return context.toString();
    }

    /**
     * Build Playwright-specific test generation instructions.
     *
     * This is the CRITICAL section that makes tests 70-80% usable instead of 10%.
     *
     * Instructions emphasize:
     * - Role-based locators (not CSS/XPath)
     * - Auto-waiting (no Thread.sleep)
     * - Page Object Model
     * - JUnit/TestNG patterns (not Gherkin)
     * - Proper assertions
     */
    private String buildPlaywrightTestInstructions(
            AIGeneratedTest.TestType testType,
            ParsedAcceptanceCriteria.ACFormat acFormat) {

        StringBuilder instructions = new StringBuilder();

        instructions.append("=== Playwright Test Generation Instructions ===\n");
        instructions.append("Generate a ").append(testType).append(" test using Playwright (Java) that:\n\n");

        // Core requirements
        instructions.append("✅ MUST DO:\n");
        instructions.append("1. Cover all acceptance criteria scenarios\n");
        instructions.append("2. Use role-based locators (getByRole, getByLabel, getByTestId)\n");
        instructions.append("3. Follow Page Object Model design pattern\n");
        instructions.append("4. Include proper assertions using assertThat() from Playwright\n");
        instructions.append("5. Use auto-waiting (Playwright waits automatically - NO Thread.sleep)\n");
        instructions.append("6. Create a single Java test class (NOT Gherkin/Cucumber)\n");
        instructions.append("7. Include both positive and negative test cases\n");
        instructions.append("8. Add meaningful test data examples\n\n");

        // Anti-patterns to avoid
        instructions.append("❌ DO NOT:\n");
        instructions.append("1. Use CSS selectors or XPath (use role-based locators instead)\n");
        instructions.append("2. Add Thread.sleep or explicit waits (Playwright auto-waits)\n");
        instructions.append("3. Generate Gherkin/Cucumber syntax (use Java/JUnit)\n");
        instructions.append("4. Hardcode locators without using getByRole/getByLabel\n");
        instructions.append("5. Create multi-file structures (single test class is sufficient)\n\n");

        // Format-specific guidance
        if (acFormat == ParsedAcceptanceCriteria.ACFormat.GHERKIN) {
            instructions.append("📝 Note: Acceptance criteria are in Gherkin format.\n");
            instructions.append("   Convert each scenario to a @Test method in Java.\n");
            instructions.append("   Use role-based locators instead of step definitions.\n\n");
        } else if (acFormat == ParsedAcceptanceCriteria.ACFormat.EMPTY) {
            instructions.append("📝 Note: No explicit acceptance criteria provided.\n");
            instructions.append("   Infer test scenarios from summary and description.\n");
            instructions.append("   Create comprehensive positive/negative test cases.\n\n");
        } else {
            instructions.append("📝 Note: Acceptance criteria are in ")
                    .append(acFormat.toString().toLowerCase().replace("_", " "))
                    .append(" format.\n");
            instructions.append("   Convert each criterion to a @Test method.\n\n");
        }

        return instructions.toString();
    }

    /**
     * Build locator strategy guide.
     *
     * This is the MOST CRITICAL section for achieving 70-80% AI test usability.
     *
     * Provides concrete examples of:
     * - When to use each locator type
     * - Actual Playwright Java code patterns
     * - Accessibility best practices
     * - Fallback strategies
     *
     * Week 13 Enhancement:
     * This section will be enhanced with Element Registry context,
     * providing AI with actual application-specific locators.
     */
    private String buildLocatorStrategyGuide() {
        StringBuilder guide = new StringBuilder();

        guide.append("=== Playwright Locator Strategy Guide ===\n\n");

        guide.append("🎯 PRIMARY STRATEGY: Role-based Locators (Accessibility Best Practice)\n\n");

        guide.append("1. BUTTONS & LINKS:\n");
        guide.append("   page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(\"Login\"))\n");
        guide.append("   page.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName(\"Sign Up\"))\n\n");

        guide.append("2. FORM FIELDS:\n");
        guide.append("   page.getByLabel(\"Email\")\n");
        guide.append("   page.getByLabel(\"Password\")\n");
        guide.append("   page.getByPlaceholder(\"Enter your name\")\n\n");

        guide.append("3. TEXT & HEADINGS:\n");
        guide.append("   page.getByText(\"Welcome back\")\n");
        guide.append("   page.getByRole(AriaRole.HEADING, new Page.GetByRoleOptions().setName(\"Dashboard\"))\n\n");

        guide.append("4. CUSTOM TEST IDS (when role/label not available):\n");
        guide.append("   page.getByTestId(\"submit-button\")\n");
        guide.append("   page.getByTestId(\"user-profile-card\")\n\n");

        guide.append("5. COMPLEX SELECTIONS:\n");
        guide.append("   // Checkboxes\n");
        guide.append("   page.getByRole(AriaRole.CHECKBOX, new Page.GetByRoleOptions().setName(\"Remember me\"))\n");
        guide.append("   \n");
        guide.append("   // Dropdowns\n");
        guide.append("   page.getByRole(AriaRole.COMBOBOX).selectOption(\"United States\")\n\n");

        guide.append("⚠️  FALLBACK STRATEGY (use only when role-based locators aren't possible):\n");
        guide.append("   page.locator(\"css=[data-test='submit']\")  // data-test attributes\n");
        guide.append("   page.locator(\"#login-form\")               // IDs only\n\n");

        guide.append("❌ AVOID (reduces test maintainability):\n");
        guide.append("   page.locator(\"div.class1.class2 > span:nth-child(3)\")  // Complex CSS\n");
        guide.append("   page.locator(\"//div[@class='...']/span[3]\")           // XPath\n\n");

        guide.append("💡 WEEK 13 PREVIEW:\n");
        guide.append("   Element Registry will provide application-specific locators.\n");
        guide.append("   This will further improve test generation accuracy to 80-90%.\n\n");

        return guide.toString();
    }

    /**
     * Build framework context section.
     *
     * Provides AI with technical stack information:
     * - Java version and build tool
     * - Playwright library details
     * - Test framework (JUnit/TestNG)
     * - Page Object Model patterns
     * - Assertion library
     */
    private String buildFrameworkContext() {
        StringBuilder context = new StringBuilder();

        context.append("=== Framework & Technical Context ===\n\n");

        context.append("Language & Build:\n");
        context.append("- Java 17\n");
        context.append("- Gradle 8.x\n");
        context.append("- Spring Boot 3.2 (for test infrastructure)\n\n");

        context.append("Playwright Setup:\n");
        context.append("- Library: com.microsoft.playwright:playwright:1.41.0\n");
        context.append("- Browsers: Chromium (default), Firefox, WebKit\n");
        context.append("- Headless: Configurable via application.yml\n\n");

        context.append("Test Framework:\n");
        context.append("- JUnit 5 OR TestNG (use @Test annotation)\n");
        context.append("- Assertions: Playwright's assertThat() or AssertJ\n");
        context.append("- Base class: Extend BasePlaywrightTest (handles browser setup)\n\n");

        context.append("Page Object Model:\n");
        context.append("- Base class: BasePage (common page methods)\n");
        context.append("- Locators: Defined as methods returning Locator objects\n");
        context.append("- Actions: Public methods for page interactions\n");
        context.append("- Example structure:\n");
        context.append("  public class LoginPage extends BasePage {\n");
        context.append("      public Locator emailInput() { return page.getByLabel(\"Email\"); }\n");
        context.append("      public void login(String email, String password) { ... }\n");
        context.append("  }\n\n");

        context.append("Auto-Waiting:\n");
        context.append("- Playwright automatically waits for elements to be actionable\n");
        context.append("- Default timeout: 30 seconds (configurable)\n");
        context.append("- NO explicit waits needed (Thread.sleep, WebDriverWait)\n\n");

        return context.toString();
    }

    /**
     * Build output format instructions.
     *
     * Specifies exact JSON structure AI should return.
     *
     * Structure for Playwright (different from Cucumber):
     * {
     *   "testClassName": "LoginTest",
     *   "testClass": "public class LoginTest extends BasePlaywrightTest { ... }",
     *   "pageObjects": {
     *     "LoginPage": "public class LoginPage extends BasePage { ... }",
     *     "DashboardPage": "..."
     *   },
     *   "usesExistingPages": false,
     *   "newPagesNeeded": ["LoginPage", "DashboardPage"]
     * }
     */
    private String buildOutputFormatInstructions() {
        StringBuilder format = new StringBuilder();

        format.append("=== Required Output Format ===\n\n");

        format.append("Return a JSON object with this EXACT structure:\n\n");

        format.append("{\n");
        format.append("  \"testClassName\": \"<TestName>Test\",\n");
        format.append("  \"testClass\": \"<Complete Java test class code>\",\n");
        format.append("  \"pageObjects\": {\n");
        format.append("    \"<PageName>Page\": \"<Complete page object class code>\",\n");
        format.append("    ...\n");
        format.append("  },\n");
        format.append("  \"usesExistingPages\": false,\n");
        format.append("  \"newPagesNeeded\": [\"LoginPage\", \"DashboardPage\"]\n");
        format.append("}\n\n");

        format.append("Field Descriptions:\n");
        format.append("- testClassName: Name of the test class (e.g., \"LoginTest\")\n");
        format.append("- testClass: Complete Java code for the test class\n");
        format.append("- pageObjects: Map of page object classes (name → code)\n");
        format.append("- usesExistingPages: Always false for now (Week 13: Element Registry)\n");
        format.append("- newPagesNeeded: Array of page object names that need to be created\n\n");

        format.append("Example testClass structure:\n");
        format.append("```java\n");
        format.append("package com.company.qa.playwright.generated;\n\n");
        format.append("import com.microsoft.playwright.*;\n");
        format.append("import org.junit.jupiter.api.*;\n");
        format.append("import static com.microsoft.playwright.assertions.PlaywrightAssertions.*;\n\n");
        format.append("public class LoginTest extends BasePlaywrightTest {\n");
        format.append("    \n");
        format.append("    @Test\n");
        format.append("    public void testUserCanLoginSuccessfully() {\n");
        format.append("        page.navigate(\"https://example.com/login\");\n");
        format.append("        page.getByLabel(\"Email\").fill(\"user@example.com\");\n");
        format.append("        page.getByLabel(\"Password\").fill(\"password123\");\n");
        format.append("        page.getByRole(AriaRole.BUTTON, \n");
        format.append("            new Page.GetByRoleOptions().setName(\"Sign In\")).click();\n");
        format.append("        \n");
        format.append("        assertThat(page).hasURL(\"https://example.com/dashboard\");\n");
        format.append("    }\n");
        format.append("}\n");
        format.append("```\n\n");

        format.append("⚠️  CRITICAL: Do NOT use Cucumber/Gherkin syntax.\n");
        format.append("    This is a Playwright (Java) test, not a Cucumber test.\n\n");

        return format.toString();
    }

    // ═══════════════════════════════════════════════════════════════════
    // TEXT CLEANING & HELPER METHODS
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Clean JIRA HTML markup and special characters.
     * Same as JiraContextBuilder - removes HTML, entities, JIRA markup.
     */
    public String cleanJiraHtml(String text) {
        if (text == null) return "";

        return text
                // Remove HTML tags
                .replaceAll("<[^>]+>", "")
                // Remove HTML entities
                .replaceAll("&nbsp;", " ")
                .replaceAll("&lt;", "<")
                .replaceAll("&gt;", ">")
                .replaceAll("&amp;", "&")
                .replaceAll("&quot;", "\"")
                .replaceAll("&apos;", "'")
                // Remove JIRA markup
                .replaceAll("\\{code[^}]*\\}", "")
                .replaceAll("\\{quote\\}", "")
                .replaceAll("\\{color[^}]*\\}", "")
                .replaceAll("\\{panel[^}]*\\}", "")
                .replaceAll("\\{noformat\\}", "")
                // Remove wiki-style links
                .replaceAll("\\[([^|\\]]+)\\|[^\\]]+\\]", "$1")  // [text|url] → text
                .replaceAll("\\[([^\\]]+)\\]", "$1")              // [text] → text
                // Normalize whitespace
                .replaceAll("\\r\\n", "\n")
                .replaceAll("\\r", "\n")
                .replaceAll(" +", " ")
                .replaceAll("\n\n\n+", "\n\n")
                .trim();
    }

    /**
     * Count sections in generated prompt.
     * Used for logging and metrics.
     */
    private int countSections(String prompt) {
        return (int) prompt.lines()
                .filter(line -> line.startsWith("==="))
                .count();
    }

    /**
     * Extract summary information from prompt for logging.
     * Format: "SCRUM-7 | UI Test | Playwright | 10 sections | 4,523 chars"
     */
    public String getPromptSummary(JiraStory story, AIGeneratedTest.TestType testType, String prompt) {
        return String.format("%s | %s Test | Playwright | %d sections | %,d chars",
                story.getJiraKey(),
                testType,
                countSections(prompt),
                prompt.length());
    }

    /**
     * Check if story is for Sauce Demo application
     */
    private boolean isSauceDemoStory(JiraStory story) {
        String content = (story.getJiraKey() + " " +
                story.getSummary() + " " +
                story.getDescription()).toLowerCase();
        return content.contains("sauce") ||
                content.contains("saucedemo") ;
                //story.getLabels() != null && story.getLabels().contains("sauce-demo");
    }

    /**
     * Get Sauce Demo specific element context
     */
    private String getSauceDemoElementContext(JiraStory story) {
        StringBuilder context = new StringBuilder();

        ObjectMapper objectMapper = new ObjectMapper();
        try {
            // Load Sauce Demo element registry
            ClassPathResource resource = new ClassPathResource(
                    "playwright/element-registry-saucedemo.json");

            try (InputStream is = resource.getInputStream()) {
                JsonNode registry = objectMapper.readTree(is);
                JsonNode pages = registry.get("pages");

                // Identify relevant Sauce Demo pages
                List<String> relevantPages = identifySauceDemoPages(story);

                if (!relevantPages.isEmpty()) {
                    context.append("Available Sauce Demo Page Elements:\n\n");

                    for (String pageName : relevantPages) {
                        context.append(buildSauceDemoPageContext(pages, pageName));
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Could not load Sauce Demo element registry: {}", e.getMessage());
        }

        return context.toString();
    }

    /**
     * Identify relevant Sauce Demo pages from story
     */
    private List<String> identifySauceDemoPages(JiraStory story) {
        List<String> pages = new ArrayList<>();
        String content = (story.getSummary() + " " +
                story.getDescription() + " " +
                story.getAcceptanceCriteria()).toLowerCase();

        if (content.contains("login") || content.contains("log in")) {
            pages.add("LoginPage");
        }
        if (content.contains("product") || content.contains("inventory") ||
                content.contains("add to cart")) {
            pages.add("ProductsPage");
        }
        if (content.contains("cart") || content.contains("shopping cart")) {
            pages.add("CartPage");
        }
        if (content.contains("checkout") || content.contains("shipping")) {
            pages.add("CheckoutInfoPage");
            pages.add("CheckoutOverviewPage");
        }
        if (content.contains("confirmation") || content.contains("complete")) {
            pages.add("CheckoutCompletePage");
        }

        return pages;
    }

    /**
     * Build context for a specific Sauce Demo page
     */
    private String buildSauceDemoPageContext(JsonNode pages, String pageName) {
        StringBuilder context = new StringBuilder();

        for (JsonNode page : pages) {
            if (page.get("name").asText().equals(pageName)) {
                context.append("Page: ").append(pageName).append("\n");

                JsonNode elements = page.get("elements");
                for (JsonNode element : elements) {
                    String elemName = element.get("name").asText();
                    String primarySelector = element.get("primarySelector").asText();

                    context.append("  - ").append(elemName)
                            .append(": ").append(primarySelector).append("\n");
                }

                context.append("\n");
                break;
            }
        }

        return context.toString();
    }
}