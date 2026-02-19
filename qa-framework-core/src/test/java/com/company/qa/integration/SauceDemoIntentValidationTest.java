package com.company.qa.integration;

import com.company.qa.model.intent.IntentActionType;
import com.company.qa.model.intent.TestIntent;
import com.company.qa.service.playwright.PlaywrightJavaRenderer;
import com.company.qa.service.playwright.TestIntentParser;
import com.company.qa.service.playwright.TestIntentParser.ParseResult;
import com.company.qa.service.playwright.TestIntentValidator;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Week 2, Day 4 — Sauce Demo Intent Validation Test.
 *
 * Proves the Zero-Hallucination pipeline produces production-quality Playwright Java
 * for all Sauce Demo test scenarios defined in the implementation plan.
 *
 * Strategy:
 *   - Uses REAL service instances wired manually (no Spring, no mocks, fast startup)
 *   - Canned TestIntent JSON mirrors exactly what a real AI returns with
 *     playwright.intent.enabled=true and the Sauce Demo element registry in the prompt
 *   - Each @Test asserts structural correctness of rendered Java AND logs the full
 *     rendered source to test output so the developer can visually compare quality
 *
 * Hallucination checks (applied to every rendered class):
 *   - No "Playwright.create()"  — base class owns browser lifecycle
 *   - No "Thread.sleep("       — Playwright auto-waits
 *   - No "BrowserType"         — never in generated tests
 *   - No "Browser.newContext"  — handled by base class
 *   - No "new Page()"          — not how Playwright works
 *
 * @author QA Framework
 * @since Zero-Hallucination Pipeline — Week 2 Day 4
 */
@DisplayName("Sauce Demo Intent Validation — Zero-Hallucination Pipeline")
class SauceDemoIntentValidationTest {

    // ─── Services wired manually (no Spring context needed) ───────────────────

    private ObjectMapper objectMapper;
    private TestIntentValidator validator;
    private TestIntentParser parser;
    private PlaywrightJavaRenderer renderer;

    // ─── Hallucination guard — strings that must NEVER appear in rendered Java ─

    private static final String[] FORBIDDEN_IN_RENDERED_JAVA = {
            "Playwright.create()",
            "Thread.sleep(",
            "BrowserType",
            "Browser.newContext",
            "new Page()",
            "import com.microsoft.playwright.Playwright"
    };

    // ─── Setup ────────────────────────────────────────────────────────────────

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        validator    = new TestIntentValidator();
        parser       = new TestIntentParser(objectMapper, validator);
        renderer     = new PlaywrightJavaRenderer();

        // Mirror defaults from application.yml
        ReflectionTestUtils.setField(renderer, "baseClass",
                "com.company.qa.playwright.BasePlaywrightTest");
        ReflectionTestUtils.setField(renderer, "basePackage",
                "com.company.qa.playwright.generated");
        ReflectionTestUtils.setField(renderer, "addDisplayName",  true);
        ReflectionTestUtils.setField(renderer, "addStepComments", true);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Test 1: Successful Login
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Login — standard_user logs in and sees Products page")
    void testSuccessfulLoginScenario() {
        // ── GIVEN: TestIntent JSON that a real AI would return for a login story ──
        String intentJson = """
            {
              "testClassName": "PROJ101_LoginTest",
              "scenarios": [
                {
                  "name": "testSuccessfulLogin",
                  "description": "Standard user logs in and is redirected to the Products page",
                  "steps": [
                    {"action": "NAVIGATE",      "value": "https://www.saucedemo.com",       "description": "Open Sauce Demo login page"},
                    {"action": "FILL",          "locator": "testid=username",               "value": "standard_user",  "description": "Enter valid username"},
                    {"action": "FILL",          "locator": "testid=password",               "value": "secret_sauce",   "description": "Enter valid password"},
                    {"action": "CLICK",         "locator": "testid=login-button",           "description": "Submit login form"},
                    {"action": "ASSERT_URL",    "value": ".*inventory.*",                  "description": "Verify redirected to products"},
                    {"action": "ASSERT_TEXT",   "locator": "css=.title",                   "value": "Products",       "description": "Verify Products page heading"}
                  ]
                }
              ]
            }
            """;

        // ── WHEN: full pipeline runs ──────────────────────────────────────────
        ParseResult result = parser.parse(intentJson);
        assertThat(result.isSuccess())
                .as("Parse+validate must succeed for valid login intent")
                .isTrue();

        String renderedJava = renderer.render(result.getIntent());
        logRenderedJava("testSuccessfulLogin", renderedJava);

        // ── THEN: correct Playwright API calls present ────────────────────────
        assertNoHallucinations(renderedJava);

        assertThat(renderedJava).contains("page.navigate(\"https://www.saucedemo.com\")");
        assertThat(renderedJava).contains("page.getByTestId(\"username\").fill(\"standard_user\")");
        assertThat(renderedJava).contains("page.getByTestId(\"password\").fill(\"secret_sauce\")");
        assertThat(renderedJava).contains("page.getByTestId(\"login-button\").click()");
        assertThat(renderedJava).contains("assertThat(page).hasURL(Pattern.compile(\".*inventory.*\"))");
        assertThat(renderedJava).contains("assertThat(page.locator(\".title\")).hasText(\"Products\")");

        assertThat(renderedJava).contains("class PROJ101_LoginTest extends BasePlaywrightTest");
        assertThat(renderedJava).contains("@Test");
        assertThat(renderedJava).contains("@DisplayName");
        assertThat(renderedJava).contains("public void testSuccessfulLogin()");

        // Imports: Pattern needed for ASSERT_URL, assertThat static for assertions
        assertThat(renderedJava).contains("import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat");
        assertThat(renderedJava).contains("import java.util.regex.Pattern");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Test 2: Locked-Out User
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Login — locked_out_user sees error message")
    void testLockedOutUserScenario() {
        String intentJson = """
            {
              "testClassName": "PROJ102_LockedOutTest",
              "scenarios": [
                {
                  "name": "testLockedOutUser",
                  "description": "Locked-out user attempts login and sees error banner",
                  "steps": [
                    {"action": "NAVIGATE",       "value": "https://www.saucedemo.com"},
                    {"action": "FILL",           "locator": "testid=username",         "value": "locked_out_user"},
                    {"action": "FILL",           "locator": "testid=password",         "value": "secret_sauce"},
                    {"action": "CLICK",          "locator": "testid=login-button"},
                    {"action": "ASSERT_VISIBLE", "locator": "css=[data-test='error']", "description": "Error banner appears"},
                    {"action": "ASSERT_TEXT",    "locator": "css=[data-test='error']", "value": "Sorry, this user has been locked out"}
                  ]
                }
              ]
            }
            """;

        ParseResult result = parser.parse(intentJson);
        assertThat(result.isSuccess()).as("locked-out intent must parse+validate").isTrue();

        String renderedJava = renderer.render(result.getIntent());
        logRenderedJava("testLockedOutUser", renderedJava);

        assertNoHallucinations(renderedJava);

        assertThat(renderedJava).contains("page.getByTestId(\"username\").fill(\"locked_out_user\")");
        assertThat(renderedJava).contains("assertThat(page.locator(\"[data-test='error']\")).isVisible()");
        assertThat(renderedJava).contains(
                "assertThat(page.locator(\"[data-test='error']\")).hasText(\"Sorry, this user has been locked out\")");

        // No URL assertion in this scenario, so Pattern import should NOT be present
        assertThat(renderedJava).doesNotContain("import java.util.regex.Pattern");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Test 3: Add To Cart
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Cart — user adds Backpack and cart badge shows 1")
    void testAddToCartScenario() {
        String intentJson = """
            {
              "testClassName": "PROJ103_CartTest",
              "scenarios": [
                {
                  "name": "testAddBackpackToCart",
                  "description": "User logs in, adds Backpack to cart, verifies badge count",
                  "steps": [
                    {"action": "NAVIGATE",       "value": "https://www.saucedemo.com"},
                    {"action": "FILL",           "locator": "testid=username",                             "value": "standard_user"},
                    {"action": "FILL",           "locator": "testid=password",                             "value": "secret_sauce"},
                    {"action": "CLICK",          "locator": "testid=login-button"},
                    {"action": "WAIT_FOR_LOAD",  "description": "Wait for products page"},
                    {"action": "CLICK",          "locator": "testid=add-to-cart-sauce-labs-backpack",      "description": "Add Backpack to cart"},
                    {"action": "ASSERT_TEXT",    "locator": "css=.shopping_cart_badge",                   "value": "1",                  "description": "Badge shows 1 item"},
                    {"action": "ASSERT_VISIBLE", "locator": "css=.shopping_cart_badge",                   "description": "Badge is visible"}
                  ]
                }
              ]
            }
            """;

        ParseResult result = parser.parse(intentJson);
        assertThat(result.isSuccess()).as("add-to-cart intent must parse+validate").isTrue();

        String renderedJava = renderer.render(result.getIntent());
        logRenderedJava("testAddToCart", renderedJava);

        assertNoHallucinations(renderedJava);

        assertThat(renderedJava).contains("page.waitForLoadState()");
        assertThat(renderedJava).contains(
                "page.getByTestId(\"add-to-cart-sauce-labs-backpack\").click()");
        assertThat(renderedJava).contains(
                "assertThat(page.locator(\".shopping_cart_badge\")).hasText(\"1\")");
        assertThat(renderedJava).contains(
                "assertThat(page.locator(\".shopping_cart_badge\")).isVisible()");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Test 4: Checkout Form
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Checkout — user fills shipping form and sees order summary")
    void testCheckoutFlowScenario() {
        String intentJson = """
            {
              "testClassName": "PROJ104_CheckoutTest",
              "scenarios": [
                {
                  "name": "testCheckoutFormAndSummary",
                  "description": "User proceeds through checkout and verifies order summary",
                  "steps": [
                    {"action": "NAVIGATE",       "value": "https://www.saucedemo.com"},
                    {"action": "FILL",           "locator": "testid=username",          "value": "standard_user"},
                    {"action": "FILL",           "locator": "testid=password",          "value": "secret_sauce"},
                    {"action": "CLICK",          "locator": "testid=login-button"},
                    {"action": "CLICK",          "locator": "testid=add-to-cart-sauce-labs-backpack"},
                    {"action": "CLICK",          "locator": "css=.shopping_cart_link",  "description": "Open cart"},
                    {"action": "CLICK",          "locator": "testid=checkout",          "description": "Proceed to checkout"},
                    {"action": "FILL",           "locator": "testid=firstName",         "value": "Test"},
                    {"action": "FILL",           "locator": "testid=lastName",          "value": "User"},
                    {"action": "FILL",           "locator": "testid=postalCode",        "value": "12345"},
                    {"action": "CLICK",          "locator": "testid=continue"},
                    {"action": "ASSERT_VISIBLE", "locator": "css=.summary_info",        "description": "Order summary is visible"},
                    {"action": "ASSERT_URL",     "value": ".*checkout-step-two.*",     "description": "On checkout summary page"}
                  ]
                }
              ]
            }
            """;

        ParseResult result = parser.parse(intentJson);
        assertThat(result.isSuccess()).as("checkout intent must parse+validate").isTrue();

        String renderedJava = renderer.render(result.getIntent());
        logRenderedJava("testCheckoutFlow", renderedJava);

        assertNoHallucinations(renderedJava);

        assertThat(renderedJava).contains("page.getByTestId(\"firstName\").fill(\"Test\")");
        assertThat(renderedJava).contains("page.getByTestId(\"lastName\").fill(\"User\")");
        assertThat(renderedJava).contains("page.getByTestId(\"postalCode\").fill(\"12345\")");
        assertThat(renderedJava).contains("page.getByTestId(\"continue\").click()");
        assertThat(renderedJava).contains("assertThat(page.locator(\".summary_info\")).isVisible()");
        assertThat(renderedJava).contains(
                "assertThat(page).hasURL(Pattern.compile(\".*checkout-step-two.*\"))");

        // Pattern needed because of ASSERT_URL
        assertThat(renderedJava).contains("import java.util.regex.Pattern");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Test 5: Full E2E — Login → Cart → Checkout → Order Complete
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("E2E — Login → add item → checkout → order complete")
    void testFullE2EScenario() {
        String intentJson = """
            {
              "testClassName": "PROJ105_FullE2ETest",
              "scenarios": [
                {
                  "name": "testFullPurchaseFlow",
                  "description": "Complete purchase from login through order confirmation",
                  "steps": [
                    {"action": "NAVIGATE",       "value": "https://www.saucedemo.com"},
                    {"action": "FILL",           "locator": "testid=username",                             "value": "standard_user"},
                    {"action": "FILL",           "locator": "testid=password",                             "value": "secret_sauce"},
                    {"action": "CLICK",          "locator": "testid=login-button"},
                    {"action": "ASSERT_URL",     "value": ".*inventory.*"},
                    {"action": "CLICK",          "locator": "testid=add-to-cart-sauce-labs-backpack"},
                    {"action": "ASSERT_TEXT",    "locator": "css=.shopping_cart_badge",                   "value": "1"},
                    {"action": "CLICK",          "locator": "css=.shopping_cart_link"},
                    {"action": "CLICK",          "locator": "testid=checkout"},
                    {"action": "FILL",           "locator": "testid=firstName",                           "value": "QA"},
                    {"action": "FILL",           "locator": "testid=lastName",                            "value": "Tester"},
                    {"action": "FILL",           "locator": "testid=postalCode",                          "value": "10001"},
                    {"action": "CLICK",          "locator": "testid=continue"},
                    {"action": "CLICK",          "locator": "testid=finish",                              "description": "Complete the order"},
                    {"action": "ASSERT_URL",     "value": ".*checkout-complete.*",                       "description": "On order complete page"},
                    {"action": "ASSERT_VISIBLE", "locator": "css=.complete-header",                      "description": "Success header visible"},
                    {"action": "ASSERT_TEXT",    "locator": "css=.complete-header",                      "value": "Thank you for your order!"}
                  ]
                }
              ]
            }
            """;

        ParseResult result = parser.parse(intentJson);
        assertThat(result.isSuccess()).as("full E2E intent must parse+validate").isTrue();

        TestIntent intent = result.getIntent();
        assertThat(intent.getScenarioCount()).isEqualTo(1);
        assertThat(intent.getTotalStepCount()).isEqualTo(17);
        assertThat(intent.getTotalAssertionCount()).isGreaterThanOrEqualTo(4);

        String renderedJava = renderer.render(intent);
        logRenderedJava("testFullE2E", renderedJava);

        assertNoHallucinations(renderedJava);

        // Navigation
        assertThat(renderedJava).contains("page.navigate(\"https://www.saucedemo.com\")");

        // Login
        assertThat(renderedJava).contains("page.getByTestId(\"username\").fill(\"standard_user\")");

        // Cart
        assertThat(renderedJava).contains("page.getByTestId(\"add-to-cart-sauce-labs-backpack\").click()");
        assertThat(renderedJava).contains("assertThat(page.locator(\".shopping_cart_badge\")).hasText(\"1\")");

        // Checkout form
        assertThat(renderedJava).contains("page.getByTestId(\"firstName\").fill(\"QA\")");
        assertThat(renderedJava).contains("page.getByTestId(\"finish\").click()");

        // Completion assertions
        assertThat(renderedJava).contains(
                "assertThat(page).hasURL(Pattern.compile(\".*checkout-complete.*\"))");
        assertThat(renderedJava).contains(
                "assertThat(page.locator(\".complete-header\")).hasText(\"Thank you for your order!\")");

        // Class structure
        assertThat(renderedJava).contains("class PROJ105_FullE2ETest extends BasePlaywrightTest");
        assertThat(renderedJava).contains("public void testFullPurchaseFlow()");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Test 6: Multi-Scenario Class (AI generates 2 test methods in 1 class)
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Multi-scenario class — 2 login scenarios in one test class")
    void testMultiScenarioClass() {
        String intentJson = """
            {
              "testClassName": "PROJ101_LoginSuiteTest",
              "scenarios": [
                {
                  "name": "testValidLogin",
                  "description": "Valid credentials redirect to inventory",
                  "steps": [
                    {"action": "NAVIGATE",    "value": "https://www.saucedemo.com"},
                    {"action": "FILL",        "locator": "testid=username",     "value": "standard_user"},
                    {"action": "FILL",        "locator": "testid=password",     "value": "secret_sauce"},
                    {"action": "CLICK",       "locator": "testid=login-button"},
                    {"action": "ASSERT_URL",  "value": ".*inventory.*"}
                  ]
                },
                {
                  "name": "testInvalidLogin",
                  "description": "Wrong password shows error",
                  "steps": [
                    {"action": "NAVIGATE",       "value": "https://www.saucedemo.com"},
                    {"action": "FILL",           "locator": "testid=username",     "value": "standard_user"},
                    {"action": "FILL",           "locator": "testid=password",     "value": "wrong_password"},
                    {"action": "CLICK",          "locator": "testid=login-button"},
                    {"action": "ASSERT_VISIBLE", "locator": "css=[data-test='error']"}
                  ]
                }
              ]
            }
            """;

        ParseResult result = parser.parse(intentJson);
        assertThat(result.isSuccess()).isTrue();

        String renderedJava = renderer.render(result.getIntent());
        logRenderedJava("testMultiScenarioClass", renderedJava);

        assertNoHallucinations(renderedJava);

        // Both @Test methods present
        assertThat(renderedJava).contains("public void testValidLogin()");
        assertThat(renderedJava).contains("public void testInvalidLogin()");

        // Pattern import only once, not duplicated
        long patternImportCount = renderedJava.lines()
                .filter(l -> l.contains("import java.util.regex.Pattern"))
                .count();
        assertThat(patternImportCount)
                .as("Pattern import must appear exactly once regardless of how many scenarios use ASSERT_URL")
                .isEqualTo(1);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Test 7: Timeout Handling
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Timeout field — renders explicit waitFor before action")
    void testTimeoutRendering() {
        String intentJson = """
            {
              "testClassName": "PROJ106_TimeoutTest",
              "scenarios": [
                {
                  "name": "testSlowElementHandling",
                  "description": "Handles slow-loading elements with explicit timeout",
                  "steps": [
                    {"action": "NAVIGATE",      "value": "https://www.saucedemo.com"},
                    {"action": "FILL",          "locator": "testid=username", "value": "standard_user"},
                    {"action": "FILL",          "locator": "testid=password", "value": "secret_sauce"},
                    {"action": "CLICK",         "locator": "testid=login-button"},
                    {"action": "CLICK",         "locator": "testid=add-to-cart-sauce-labs-backpack", "timeout": 10000, "description": "Wait up to 10s for add-to-cart button"},
                    {"action": "ASSERT_VISIBLE","locator": "css=.shopping_cart_badge"}
                  ]
                }
              ]
            }
            """;

        ParseResult result = parser.parse(intentJson);
        assertThat(result.isSuccess()).isTrue();

        String renderedJava = renderer.render(result.getIntent());
        logRenderedJava("testTimeoutRendering", renderedJava);

        assertNoHallucinations(renderedJava);

        // Explicit waitFor rendered before the click
        assertThat(renderedJava).contains(".waitFor(new Locator.WaitForOptions().setTimeout(10000))");
        // The click itself still renders
        assertThat(renderedJava).contains(
                "page.getByTestId(\"add-to-cart-sauce-labs-backpack\").click()");
        // No Thread.sleep anywhere
        assertThat(renderedJava).doesNotContain("Thread.sleep");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Test 8: Idempotency — same intent always produces identical Java
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Idempotency — same TestIntent always renders identical Java")
    void testRendererIdempotency() {
        String intentJson = """
            {
              "testClassName": "PROJ107_IdempotencyTest",
              "scenarios": [
                {
                  "name": "testIdempotentRender",
                  "description": "Renderer must be a pure function",
                  "steps": [
                    {"action": "NAVIGATE",    "value": "https://www.saucedemo.com"},
                    {"action": "FILL",        "locator": "testid=username", "value": "standard_user"},
                    {"action": "CLICK",       "locator": "testid=login-button"},
                    {"action": "ASSERT_URL",  "value": ".*inventory.*"}
                  ]
                }
              ]
            }
            """;

        ParseResult result = parser.parse(intentJson);
        assertThat(result.isSuccess()).isTrue();

        TestIntent intent = result.getIntent();
        String firstRender  = renderer.render(intent);
        String secondRender = renderer.render(intent);
        String thirdRender  = renderer.render(intent);

        assertThat(firstRender).isEqualTo(secondRender);
        assertThat(secondRender).isEqualTo(thirdRender);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Test 9: Parametrized locator strategy rendering
    // ─────────────────────────────────────────────────────────────────────────

    @ParameterizedTest(name = "[{index}] {0}")
    @MethodSource("sauceDemoLocatorStrategies")
    @DisplayName("Locator strategies render correctly for Sauce Demo elements")
    void testLocatorStrategyRendering(String label, String locator, String expectedJava) {
        // Build a minimal valid intent using each locator strategy
        String intentJson = String.format("""
            {
              "testClassName": "LocatorStrategyTest",
              "scenarios": [
                {
                  "name": "testLocator_%s",
                  "steps": [
                    {"action": "NAVIGATE",       "value": "https://www.saucedemo.com"},
                    {"action": "ASSERT_VISIBLE", "locator": "%s"}
                  ]
                }
              ]
            }
            """, label.replace(" ", "_"), locator.replace("\"", "\\\""));

        ParseResult result = parser.parse(intentJson);
        assertThat(result.isSuccess())
                .as("Locator strategy '%s' must parse+validate cleanly", label)
                .isTrue();

        String renderedJava = renderer.render(result.getIntent());
        assertThat(renderedJava)
                .as("Rendered Java for locator strategy '%s'", label)
                .contains(expectedJava);
    }

    static Stream<Arguments> sauceDemoLocatorStrategies() {
        return Stream.of(
                Arguments.of("testid",  "testid=username",                   "page.getByTestId(\"username\")"),
                Arguments.of("css",     "css=[data-test='error']",           "page.locator(\"[data-test='error']\")"),
                Arguments.of("css_id",  "css=.shopping_cart_badge",          "page.locator(\".shopping_cart_badge\")"),
                Arguments.of("label",   "label=Username",                    "page.getByLabel(\"Username\")"),
                Arguments.of("text",    "text=Products",                     "page.getByText(\"Products\")"),
                Arguments.of("id",      "id=user-name",                      "page.locator(\"#user-name\")"),
                Arguments.of("xpath",   "xpath=//button[@id='login-button']","page.locator(\"xpath=//button[@id='login-button']\")"),
                Arguments.of("raw_css", "#user-name",                        "page.locator(\"#user-name\")")
        );
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Test 10: Pipeline rejects intent with Java code in locator (hallucination)
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Hallucination guard — AI putting Java code in locator is rejected")
    void testHallucinatedLocatorRejected() {
        String hallucinatedJson = """
            {
              "testClassName": "HallucinationTest",
              "scenarios": [
                {
                  "name": "testBadLocator",
                  "steps": [
                    {"action": "NAVIGATE",    "value": "https://www.saucedemo.com"},
                    {"action": "CLICK",       "locator": "page.locator(\\\"#login-button\\\")"},
                    {"action": "ASSERT_URL",  "value": ".*inventory.*"}
                  ]
                }
              ]
            }
            """;

        ParseResult result = parser.parse(hallucinatedJson);

        assertThat(result.isSuccess())
                .as("Intent with Java code in locator must be REJECTED by validator")
                .isFalse();
        assertThat(result.isValidationFailure()).isTrue();
        assertThat(result.getValidation().getErrors())
                .anyMatch(e -> e.contains("locator contains Java code"));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Test 11: Pipeline rejects intent with no assertions
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Hallucination guard — scenario with no assertions is rejected")
    void testNoAssertionsRejected() {
        String noAssertionJson = """
            {
              "testClassName": "NoAssertTest",
              "scenarios": [
                {
                  "name": "testNoAssert",
                  "steps": [
                    {"action": "NAVIGATE", "value": "https://www.saucedemo.com"},
                    {"action": "CLICK",    "locator": "testid=login-button"}
                  ]
                }
              ]
            }
            """;

        ParseResult result = parser.parse(noAssertionJson);

        assertThat(result.isSuccess())
                .as("Intent with no assertions must be REJECTED by validator")
                .isFalse();
        assertThat(result.isValidationFailure()).isTrue();
        assertThat(result.getValidation().getErrors())
                .anyMatch(e -> e.contains("no assertions"));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Test 12: Quality metrics on the full E2E render
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Quality metrics — rendered Java line count is within expected bounds")
    void testRenderedJavaQualityMetrics() {
        // A 2-scenario login test class
        String intentJson = """
            {
              "testClassName": "PROJ108_QualityMetricsTest",
              "scenarios": [
                {
                  "name": "testScenarioOne",
                  "description": "First scenario",
                  "steps": [
                    {"action": "NAVIGATE",    "value": "https://www.saucedemo.com"},
                    {"action": "FILL",        "locator": "testid=username", "value": "standard_user"},
                    {"action": "CLICK",       "locator": "testid=login-button"},
                    {"action": "ASSERT_URL",  "value": ".*inventory.*"}
                  ]
                },
                {
                  "name": "testScenarioTwo",
                  "description": "Second scenario",
                  "steps": [
                    {"action": "NAVIGATE",       "value": "https://www.saucedemo.com"},
                    {"action": "FILL",           "locator": "testid=username", "value": "locked_out_user"},
                    {"action": "CLICK",          "locator": "testid=login-button"},
                    {"action": "ASSERT_VISIBLE", "locator": "css=[data-test='error']"}
                  ]
                }
              ]
            }
            """;

        ParseResult result = parser.parse(intentJson);
        assertThat(result.isSuccess()).isTrue();

        TestIntent intent = result.getIntent();
        String renderedJava = renderer.render(intent);
        logRenderedJava("testQualityMetrics", renderedJava);

        long lineCount = renderedJava.lines().count();
        System.out.printf("%n=== Quality Metrics ===%n");
        System.out.printf("Scenarios    : %d%n", intent.getScenarioCount());
        System.out.printf("Total steps  : %d%n", intent.getTotalStepCount());
        System.out.printf("Assertions   : %d%n", intent.getTotalAssertionCount());
        System.out.printf("Rendered     : %d lines, %d chars%n", lineCount, renderedJava.length());

        // Bounds: a 2-scenario Playwright test should be between 20 and 80 lines
        assertThat(lineCount)
                .as("Rendered Java line count is within expected bounds (not too sparse, not bloated)")
                .isBetween(20L, 80L);

        // Must contain exactly 2 @Test annotations
        long testCount = renderedJava.lines().filter(l -> l.strip().equals("@Test")).count();
        assertThat(testCount).isEqualTo(2);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Shared helpers
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Assert none of the known hallucination patterns are present in rendered Java.
     * Applied to every rendered class in this test suite.
     */
    private void assertNoHallucinations(String renderedJava) {
        for (String forbidden : FORBIDDEN_IN_RENDERED_JAVA) {
            assertThat(renderedJava)
                    .as("Rendered Java must NOT contain hallucinated call: '%s'", forbidden)
                    .doesNotContain(forbidden);
        }
    }

    /**
     * Log the full rendered Java source to test output.
     * This is the "test report" — developer visually inspects quality.
     * In CI, captured in the Surefire/test logs.
     */
    private void logRenderedJava(String testName, String renderedJava) {
        System.out.printf("%n%n");
        System.out.printf("╔══════════════════════════════════════════════════════════╗%n");
        System.out.printf("║  RENDERED JAVA — %s%n", testName);
        System.out.printf("╠══════════════════════════════════════════════════════════╣%n");
        System.out.println(renderedJava);
        System.out.printf("╚══════════════════════════════════════════════════════════╝%n");
    }
}