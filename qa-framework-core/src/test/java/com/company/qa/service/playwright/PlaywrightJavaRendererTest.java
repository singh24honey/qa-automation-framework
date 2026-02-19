package com.company.qa.service.playwright;

import com.company.qa.model.intent.IntentActionType;
import com.company.qa.model.intent.IntentTestStep;
import com.company.qa.model.intent.TestIntent;
import com.company.qa.model.intent.TestScenario;
import com.company.qa.testsupport.PostgresIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PlaywrightJavaRendererTest  {

    private PlaywrightJavaRenderer renderer;

    @BeforeEach
    void setUp() {
        renderer = new PlaywrightJavaRenderer();
        // Set @Value fields that affect rendered output
        ReflectionTestUtils.setField(renderer, "baseClass",
                "com.company.qa.playwright.BasePlaywrightTest");
        ReflectionTestUtils.setField(renderer, "basePackage",
                "com.company.qa.playwright.generated");
        ReflectionTestUtils.setField(renderer, "addDisplayName", true);
        ReflectionTestUtils.setField(renderer, "addStepComments", true);
    }

    // ============================================================
    // Locator Rendering — all 9 strategies
    // ============================================================

    @Nested
    @DisplayName("renderLocator — all 9 strategies")
    class LocatorRenderingTest {

        @Test
        void testidStrategy() {
            assertThat(renderer.renderLocator("testid=username"))
                    .isEqualTo("page.getByTestId(\"username\")");
        }

        @Test
        void roleStrategyWithName() {
            assertThat(renderer.renderLocator("role=button[name='Login']"))
                    .isEqualTo("page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(\"Login\"))");
        }

        @Test
        void roleStrategyWithoutOptions() {
            assertThat(renderer.renderLocator("role=button"))
                    .isEqualTo("page.getByRole(AriaRole.BUTTON)");
        }

        @Test
        void roleStrategyWithExact() {
            assertThat(renderer.renderLocator("role=button[name='OK'][exact=true]"))
                    .contains("setName(\"OK\")")
                    .contains("setExact(true)");
        }

        @Test
        void labelStrategy() {
            assertThat(renderer.renderLocator("label=Email Address"))
                    .isEqualTo("page.getByLabel(\"Email Address\")");
        }

        @Test
        void textStrategy() {
            assertThat(renderer.renderLocator("text=Products"))
                    .isEqualTo("page.getByText(\"Products\")");
        }

        @Test
        void cssStrategy() {
            assertThat(renderer.renderLocator("css=[data-test='login-button']"))
                    .isEqualTo("page.locator(\"[data-test='login-button']\")");
        }

        @Test
        void xpathStrategy() {
            assertThat(renderer.renderLocator("xpath=//button[@id='submit']"))
                    .isEqualTo("page.locator(\"xpath=//button[@id='submit']\")");
        }

        @Test
        void idStrategy() {
            assertThat(renderer.renderLocator("id=submitBtn"))
                    .isEqualTo("page.locator(\"#submitBtn\")");
        }

        @Test
        void nameStrategy() {
            assertThat(renderer.renderLocator("name=username"))
                    .isEqualTo("page.locator(\"[name=\\\"username\\\"]\")");
        }

        @Test
        void classStrategy() {
            assertThat(renderer.renderLocator("class=btn-primary"))
                    .isEqualTo("page.locator(\".btn-primary\")");
        }

        @Test
        void rawCssSelectorWithoutPrefix() {
            assertThat(renderer.renderLocator("#user-name"))
                    .isEqualTo("page.locator(\"#user-name\")");
        }

        @Test
        void rawAttributeSelectorWithoutPrefix() {
            assertThat(renderer.renderLocator("[data-test=\"login-button\"]"))
                    .isEqualTo("page.locator(\"[data-test=\\\"login-button\\\"]\")");
        }

        @Test
        void shouldThrowForNullLocator() {
            assertThatThrownBy(() -> renderer.renderLocator(null))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void shouldThrowForBlankLocator() {
            assertThatThrownBy(() -> renderer.renderLocator("  "))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    // ============================================================
    // Action Statement Rendering — all 24 action types
    // ============================================================

    @Nested
    @DisplayName("renderActionStatement — all 24 actions")
    class ActionRenderingTest {

        // ---- Navigation ----

        @Test
        void NAVIGATE() {
            assertStatement(IntentActionType.NAVIGATE, null, "https://www.saucedemo.com",
                    "page.navigate(\"https://www.saucedemo.com\");");
        }

        @Test
        void RELOAD() {
            assertStatement(IntentActionType.RELOAD, null, null,
                    "page.reload();");
        }

        @Test
        void GO_BACK() {
            assertStatement(IntentActionType.GO_BACK, null, null,
                    "page.goBack();");
        }

        // ---- Input ----

        @Test
        void FILL() {
            assertStatement(IntentActionType.FILL, "testid=username", "standard_user",
                    "page.getByTestId(\"username\").fill(\"standard_user\");");
        }

        @Test
        void CLEAR() {
            assertStatement(IntentActionType.CLEAR, "testid=username", null,
                    "page.getByTestId(\"username\").clear();");
        }

        @Test
        void SELECT_OPTION() {
            assertStatement(IntentActionType.SELECT_OPTION, "testid=dropdown", "Option A",
                    "page.getByTestId(\"dropdown\").selectOption(\"Option A\");");
        }

        @Test
        void CHECK() {
            assertStatement(IntentActionType.CHECK, "testid=terms", null,
                    "page.getByTestId(\"terms\").check();");
        }

        @Test
        void UNCHECK() {
            assertStatement(IntentActionType.UNCHECK, "testid=terms", null,
                    "page.getByTestId(\"terms\").uncheck();");
        }

        @Test
        void PRESS_KEY() {
            assertStatement(IntentActionType.PRESS_KEY, null, "Enter",
                    "page.keyboard().press(\"Enter\");");
        }

        // ---- Interaction ----

        @Test
        void CLICK() {
            assertStatement(IntentActionType.CLICK, "testid=login-button", null,
                    "page.getByTestId(\"login-button\").click();");
        }

        @Test
        void CLICK_ROLE() {
            String statement = renderer.renderActionStatement(IntentTestStep.builder()
                    .action(IntentActionType.CLICK_ROLE)
                    .value("button[name='Login']")
                    .build());
            assertThat(statement).contains("getByRole(AriaRole.BUTTON")
                    .contains("setName(\"Login\")")
                    .endsWith(".click();");
        }

        @Test
        void HOVER() {
            assertStatement(IntentActionType.HOVER, "testid=menu-item", null,
                    "page.getByTestId(\"menu-item\").hover();");
        }

        // ---- Wait ----

        @Test
        void WAIT_FOR_LOAD() {
            assertStatement(IntentActionType.WAIT_FOR_LOAD, null, null,
                    "page.waitForLoadState();");
        }

        @Test
        void WAIT_FOR_SELECTOR() {
            assertStatement(IntentActionType.WAIT_FOR_SELECTOR, "testid=spinner", null,
                    "page.getByTestId(\"spinner\").waitFor();");
        }

        @Test
        void WAIT_FOR_URL() {
            assertStatement(IntentActionType.WAIT_FOR_URL, null, ".*inventory.*",
                    "page.waitForURL(Pattern.compile(\".*inventory.*\"));");
        }

        // ---- Assertions ----

        @Test
        void ASSERT_TEXT() {
            assertStatement(IntentActionType.ASSERT_TEXT, "testid=title", "Products",
                    "assertThat(page.getByTestId(\"title\")).hasText(\"Products\");");
        }

        @Test
        void ASSERT_VISIBLE() {
            assertStatement(IntentActionType.ASSERT_VISIBLE, "testid=cart", null,
                    "assertThat(page.getByTestId(\"cart\")).isVisible();");
        }

        @Test
        void ASSERT_HIDDEN() {
            assertStatement(IntentActionType.ASSERT_HIDDEN, "testid=spinner", null,
                    "assertThat(page.getByTestId(\"spinner\")).isHidden();");
        }

        @Test
        void ASSERT_URL() {
            assertStatement(IntentActionType.ASSERT_URL, null, ".*inventory.*",
                    "assertThat(page).hasURL(Pattern.compile(\".*inventory.*\"));");
        }

        @Test
        void ASSERT_TITLE() {
            assertStatement(IntentActionType.ASSERT_TITLE, null, "Swag Labs",
                    "assertThat(page).hasTitle(\"Swag Labs\");");
        }

        @Test
        void ASSERT_COUNT() {
            assertStatement(IntentActionType.ASSERT_COUNT, "css=.inventory_item", "6",
                    "assertThat(page.locator(\".inventory_item\")).hasCount(6);");
        }

        @Test
        void ASSERT_VALUE() {
            assertStatement(IntentActionType.ASSERT_VALUE, "testid=username", "standard_user",
                    "assertThat(page.getByTestId(\"username\")).hasValue(\"standard_user\");");
        }

        @Test
        void ASSERT_ENABLED() {
            assertStatement(IntentActionType.ASSERT_ENABLED, "testid=login-button", null,
                    "assertThat(page.getByTestId(\"login-button\")).isEnabled();");
        }

        @Test
        void ASSERT_DISABLED() {
            assertStatement(IntentActionType.ASSERT_DISABLED, "testid=login-button", null,
                    "assertThat(page.getByTestId(\"login-button\")).isDisabled();");
        }

        // ---- Helper ----

        private void assertStatement(IntentActionType action, String locator, String value,
                                     String expected) {
            IntentTestStep step = IntentTestStep.builder()
                    .action(action)
                    .locator(locator)
                    .value(value)
                    .build();
            assertThat(renderer.renderActionStatement(step)).isEqualTo(expected);
        }
    }

    // ============================================================
    // Timeout Handling
    // ============================================================

    @Nested
    @DisplayName("Timeout handling")
    class TimeoutTest {

        @Test
        void shouldInsertWaitForBeforeStepWhenTimeoutIsSet() {
            TestScenario scenario = TestScenario.builder()
                    .name("testWithTimeout")
                    .steps(List.of(
                            IntentTestStep.builder()
                                    .action(IntentActionType.FILL)
                                    .locator("testid=username")
                                    .value("user")
                                    .timeout(5000)
                                    .build(),
                            IntentTestStep.builder()
                                    .action(IntentActionType.ASSERT_URL)
                                    .value(".*")
                                    .build()
                    ))
                    .build();
            TestIntent intent = TestIntent.builder()
                    .testClassName("TimeoutTest")
                    .scenarios(List.of(scenario))
                    .build();

            String java = renderer.render(intent);

            // waitFor must appear BEFORE fill
            int waitForIdx = java.indexOf("waitFor(new Locator.WaitForOptions().setTimeout(5000))");
            int fillIdx = java.indexOf(".fill(\"user\")");

            assertThat(waitForIdx).isGreaterThan(0);
            assertThat(fillIdx).isGreaterThan(waitForIdx);
        }

        @Test
        void shouldNotInsertWaitForWhenTimeoutIsNull() {
            TestIntent intent = buildSauceLoginIntent();
            String java = renderer.render(intent);
            assertThat(java).doesNotContain("WaitForOptions");
        }
    }

    // ============================================================
    // Import Management
    // ============================================================

    @Nested
    @DisplayName("Import management")
    class ImportTest {

        @Test
        void shouldAlwaysIncludeTestAndDisplayNameImports() {
            String java = renderer.render(buildSauceLoginIntent());
            assertThat(java).contains("import org.junit.jupiter.api.Test;");
            assertThat(java).contains("import org.junit.jupiter.api.DisplayName;");
        }

        @Test
        void shouldIncludeAssertThatImportWhenAssertionStepPresent() {
            String java = renderer.render(buildSauceLoginIntent()); // has ASSERT_URL
            assertThat(java).contains(
                    "import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;");
        }

        @Test
        void shouldNotIncludeAssertThatImportWhenNoAssertions() {
            // Can't actually render this — validator catches it.
            // But we test the ImportSet directly via collectImports:
            TestIntent intentNoAsserts = TestIntent.builder()
                    .testClassName("NoAssertTest")
                    .scenarios(List.of(
                            TestScenario.builder()
                                    .name("t")
                                    .steps(List.of(
                                            IntentTestStep.builder()
                                                    .action(IntentActionType.NAVIGATE)
                                                    .value("https://example.com")
                                                    .build()
                                    ))
                                    .build()
                    ))
                    .build();

            PlaywrightJavaRenderer.ImportSet imports = renderer.collectImports(intentNoAsserts);
            assertThat(imports.getStaticImports())
                    .noneMatch(i -> i.contains("assertThat"));
        }

        @Test
        void shouldIncludeAriaRoleImportForRoleLocator() {
            TestIntent intent = TestIntent.builder()
                    .testClassName("RoleTest")
                    .scenarios(List.of(
                            TestScenario.builder()
                                    .name("testRole")
                                    .steps(List.of(
                                            IntentTestStep.builder()
                                                    .action(IntentActionType.CLICK)
                                                    .locator("role=button[name='Login']")
                                                    .build(),
                                            IntentTestStep.builder()
                                                    .action(IntentActionType.ASSERT_URL)
                                                    .value(".*")
                                                    .build()
                                    ))
                                    .build()
                    ))
                    .build();

            String java = renderer.render(intent);
            assertThat(java).contains("import com.microsoft.playwright.options.AriaRole;");
        }

        @Test
        void shouldIncludePatternImportForAssertUrlStep() {
            String java = renderer.render(buildSauceLoginIntent()); // has ASSERT_URL
            assertThat(java).contains("import java.util.regex.Pattern;");
        }

        @Test
        void shouldNeverImportPlaywrightOrBrowserClasses() {
            String java = renderer.render(buildSauceLoginIntent());
            assertThat(java).doesNotContain("import com.microsoft.playwright.Playwright;");
            assertThat(java).doesNotContain("import com.microsoft.playwright.Browser;");
            assertThat(java).doesNotContain("import com.microsoft.playwright.BrowserType;");
        }
    }

    // ============================================================
    // Full Class Structure
    // ============================================================

    @Nested
    @DisplayName("Class structure")
    class ClassStructureTest {

        @Test
        void shouldContainPackageDeclaration() {
            String java = renderer.render(buildSauceLoginIntent());
            assertThat(java).startsWith("package com.company.qa.playwright.generated;");
        }

        @Test
        void shouldExtendBasePlaywrightTest() {
            String java = renderer.render(buildSauceLoginIntent());
            assertThat(java).contains("extends BasePlaywrightTest");
        }

        @Test
        void shouldUseCorrectTestClassName() {
            String java = renderer.render(buildSauceLoginIntent());
            assertThat(java).contains("public class LoginTest extends");
        }

        @Test
        void shouldContainAutoGeneratedComment() {
            String java = renderer.render(buildSauceLoginIntent());
            assertThat(java).contains("Auto-generated by Zero-Hallucination Pipeline");
        }

        @Test
        void shouldContainAtTestAnnotation() {
            String java = renderer.render(buildSauceLoginIntent());
            assertThat(java).contains("@Test");
        }

        @Test
        void shouldContainDisplayNameAnnotation() {
            String java = renderer.render(buildSauceLoginIntent());
            assertThat(java).contains("@DisplayName(");
        }

        @Test
        void shouldContainStepCommentFromDescription() {
            TestScenario scenario = TestScenario.builder()
                    .name("testWithComments")
                    .steps(List.of(
                            IntentTestStep.builder()
                                    .action(IntentActionType.NAVIGATE)
                                    .value("https://example.com")
                                    .description("Open the home page")
                                    .build(),
                            IntentTestStep.builder()
                                    .action(IntentActionType.ASSERT_URL)
                                    .value(".*")
                                    .build()
                    ))
                    .build();
            TestIntent intent = TestIntent.builder()
                    .testClassName("CommentTest")
                    .scenarios(List.of(scenario))
                    .build();

            String java = renderer.render(intent);
            assertThat(java).contains("// Open the home page");
        }

        @Test
        void shouldRenderMultipleScenariosAsMultipleMethods() {
            TestIntent intent = buildMultiScenarioIntent();
            String java = renderer.render(intent);
            assertThat(java).contains("testScenarioOne");
            assertThat(java).contains("testScenarioTwo");
        }

        @Test
        void shouldCloseClassWithBrace() {
            String java = renderer.render(buildSauceLoginIntent());
            assertThat(java.stripTrailing()).endsWith("}");
        }

        @Test
        void shouldBeIdempotent() {
            TestIntent intent = buildSauceLoginIntent();
            String first = renderer.render(intent);
            String second = renderer.render(intent);
            assertThat(first).isEqualTo(second);
        }
    }

    // ============================================================
    // sanitizeMethodName
    // ============================================================

    @Nested
    @DisplayName("sanitizeMethodName")
    class SanitizeMethodNameTest {

        @Test
        void shouldPreserveAlreadyValidMethodName() {
            assertThat(renderer.sanitizeMethodName("testSuccessfulLogin"))
                    .isEqualTo("testSuccessfulLogin");
        }

        @Test
        void shouldConvertSpacesToCamelCase() {
            assertThat(renderer.sanitizeMethodName("Test Successful Login"))
                    .isEqualTo("testSuccessfulLogin");
        }

        @Test
        void shouldConvertHyphensToUpperCase() {
            assertThat(renderer.sanitizeMethodName("test-login-flow"))
                    .isEqualTo("testLoginFlow");
        }

        @Test
        void shouldPrefixUnderscoreWhenStartsWithDigit() {
            assertThat(renderer.sanitizeMethodName("123invalid"))
                    .startsWith("_");
        }

        @Test
        void shouldReturnFallbackForNullInput() {
            assertThat(renderer.sanitizeMethodName(null)).isEqualTo("testScenario");
        }

        @Test
        void shouldReturnFallbackForBlankInput() {
            assertThat(renderer.sanitizeMethodName("  ")).isEqualTo("testScenario");
        }
    }

    // ============================================================
    // escapeJavaString
    // ============================================================

    @Nested
    @DisplayName("escapeJavaString")
    class EscapeJavaStringTest {

        @Test
        void shouldEscapeDoubleQuotes() {
            assertThat(renderer.escapeJavaString("say \"hello\""))
                    .isEqualTo("say \\\"hello\\\"");
        }

        @Test
        void shouldEscapeBackslash() {
            assertThat(renderer.escapeJavaString("a\\b"))
                    .isEqualTo("a\\\\b");
        }

        @Test
        void shouldEscapeNewline() {
            assertThat(renderer.escapeJavaString("line1\nline2"))
                    .isEqualTo("line1\\nline2");
        }

        @Test
        void shouldReturnEmptyStringForNull() {
            assertThat(renderer.escapeJavaString(null)).isEqualTo("");
        }

        @Test
        void shouldNotModifyCleanString() {
            assertThat(renderer.escapeJavaString("https://www.saucedemo.com"))
                    .isEqualTo("https://www.saucedemo.com");
        }
    }

    // ============================================================
    // Sauce Demo full render — golden test
    // ============================================================

    @Test
    @DisplayName("Sauce Demo full login scenario — golden output check")
    void shouldRenderSauceDemoLoginTestCorrectly() {
        String java = renderer.render(buildSauceLoginIntent());

        // Package + class
        assertThat(java).contains("package com.company.qa.playwright.generated;");
        assertThat(java).contains("public class LoginTest extends BasePlaywrightTest");

        // Correct Playwright API calls
        assertThat(java).contains("page.navigate(\"https://www.saucedemo.com\")");
        assertThat(java).contains("page.getByTestId(\"username\").fill(\"standard_user\")");
        assertThat(java).contains("page.getByTestId(\"password\").fill(\"secret_sauce\")");
        assertThat(java).contains("page.getByTestId(\"login-button\").click()");
        assertThat(java).contains("assertThat(page).hasURL(Pattern.compile(\".*inventory.*\"))");

        // No hallucinated Java
        assertThat(java).doesNotContain("Playwright.create()");
        assertThat(java).doesNotContain("Thread.sleep");
        assertThat(java).doesNotContain("BrowserType");
    }

    // ============================================================
    // Helpers
    // ============================================================

    private TestIntent buildSauceLoginIntent() {
        return TestIntent.builder()
                .testClassName("LoginTest")
                .scenarios(List.of(
                        TestScenario.builder()
                                .name("testSuccessfulLogin")
                                .description("Standard user logs in and sees products page")
                                .steps(List.of(
                                        IntentTestStep.builder()
                                                .action(IntentActionType.NAVIGATE)
                                                .value("https://www.saucedemo.com")
                                                .description("Open Sauce Demo login page")
                                                .build(),
                                        IntentTestStep.builder()
                                                .action(IntentActionType.FILL)
                                                .locator("testid=username")
                                                .value("standard_user")
                                                .description("Enter username")
                                                .build(),
                                        IntentTestStep.builder()
                                                .action(IntentActionType.FILL)
                                                .locator("testid=password")
                                                .value("secret_sauce")
                                                .description("Enter password")
                                                .build(),
                                        IntentTestStep.builder()
                                                .action(IntentActionType.CLICK)
                                                .locator("testid=login-button")
                                                .description("Click login button")
                                                .build(),
                                        IntentTestStep.builder()
                                                .action(IntentActionType.ASSERT_URL)
                                                .value(".*inventory.*")
                                                .description("Verify redirect to inventory page")
                                                .build()
                                ))
                                .build()
                ))
                .build();
    }

    private TestIntent buildMultiScenarioIntent() {
        return TestIntent.builder()
                .testClassName("MultiTest")
                .scenarios(List.of(
                        TestScenario.builder()
                                .name("testScenarioOne")
                                .steps(List.of(
                                        IntentTestStep.builder()
                                                .action(IntentActionType.NAVIGATE)
                                                .value("https://example.com")
                                                .build(),
                                        IntentTestStep.builder()
                                                .action(IntentActionType.ASSERT_URL)
                                                .value(".*example.*")
                                                .build()
                                ))
                                .build(),
                        TestScenario.builder()
                                .name("testScenarioTwo")
                                .steps(List.of(
                                        IntentTestStep.builder()
                                                .action(IntentActionType.NAVIGATE)
                                                .value("https://example.com/about")
                                                .build(),
                                        IntentTestStep.builder()
                                                .action(IntentActionType.ASSERT_TITLE)
                                                .value("About Us")
                                                .build()
                                ))
                                .build()
                ))
                .build();
    }
}