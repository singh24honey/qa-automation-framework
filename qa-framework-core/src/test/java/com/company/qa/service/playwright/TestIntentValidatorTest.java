package com.company.qa.service.playwright;

import com.company.qa.model.intent.IntentActionType;
import com.company.qa.model.intent.IntentTestStep;
import com.company.qa.model.intent.TestIntent;
import com.company.qa.model.intent.TestScenario;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TestIntentValidatorTest {

    private TestIntentValidator validator;

    @BeforeEach
    void setUp() {
        validator = new TestIntentValidator();
    }

    // ---- Null guard ----

    @Test
    @DisplayName("null intent → error 'TestIntent is null'")
    void shouldRejectNullIntent() {
        ValidationResult result = validator.validate(null);
        assertThat(result.isValid()).isFalse();
        assertThat(result.getErrors()).anyMatch(e -> e.contains("null"));
    }

    // ---- R1: Class name ----

    @Nested
    @DisplayName("R1 — Class name validation")
    class ClassNameValidation {

        @Test
        void shouldRejectNullClassName() {
            ValidationResult result = validator.validate(intentWithClassName(null));
            assertThat(result.isValid()).isFalse();
            assertThat(result.getErrors()).anyMatch(e -> e.contains("testClassName"));
        }

        @Test
        void shouldRejectBlankClassName() {
            ValidationResult result = validator.validate(intentWithClassName("  "));
            assertThat(result.isValid()).isFalse();
        }

        @Test
        void shouldRejectClassNameWithSpaces() {
            ValidationResult result = validator.validate(intentWithClassName("Login Test"));
            assertThat(result.isValid()).isFalse();
            assertThat(result.getErrors()).anyMatch(e -> e.contains("Login Test"));
        }

        @Test
        void shouldRejectClassNameStartingWithDigit() {
            ValidationResult result = validator.validate(intentWithClassName("123LoginTest"));
            assertThat(result.isValid()).isFalse();
        }

        @Test
        void shouldWarnWhenClassNameDoesNotEndWithTest() {
            ValidationResult result = validator.validate(intentWithClassName("LoginSuite"));
            // Valid but warns
            assertThat(result.isValid()).isTrue();
            assertThat(result.hasWarnings()).isTrue();
            assertThat(result.getWarnings()).anyMatch(w -> w.contains("LoginSuite"));
        }

        @Test
        void shouldAcceptValidClassNameEndingWithTest() {
            ValidationResult result = validator.validate(buildFullyValidIntent());
            assertThat(result.isValid()).isTrue();
        }

        @Test
        void shouldAcceptClassNameWithUnderscores() {
            ValidationResult result = validator.validate(intentWithClassName("PROJ_123_LoginTest"));
            assertThat(result.isValid()).isTrue();
        }
    }

    // ---- R2: At least one scenario ----

    @Test
    @DisplayName("R2 — empty scenarios list → error")
    void shouldRejectEmptyScenariosList() {
        TestIntent intent = TestIntent.builder()
                .testClassName("LoginTest")
                .scenarios(List.of())
                .build();
        ValidationResult result = validator.validate(intent);
        assertThat(result.isValid()).isFalse();
        assertThat(result.getErrors()).anyMatch(e -> e.contains("at least one scenario"));
    }

    // ---- R3: Unique scenario names ----

    @Test
    @DisplayName("R3 — duplicate scenario names → error")
    void shouldRejectDuplicateScenarioNames() {
        TestScenario s1 = buildValidScenario("testLogin");
        TestScenario s2 = buildValidScenario("testLogin"); // duplicate
        TestIntent intent = TestIntent.builder()
                .testClassName("LoginTest")
                .scenarios(List.of(s1, s2))
                .build();

        ValidationResult result = validator.validate(intent);
        assertThat(result.isValid()).isFalse();
        assertThat(result.getErrors()).anyMatch(e -> e.contains("Duplicate") && e.contains("testLogin"));
    }

    // ---- R4: At least one step per scenario ----

    @Test
    @DisplayName("R4 — scenario with no steps → error")
    void shouldRejectScenarioWithNoSteps() {
        TestScenario emptyScenario = TestScenario.builder()
                .name("emptyScenario")
                .steps(List.of())
                .build();
        TestIntent intent = TestIntent.builder()
                .testClassName("LoginTest")
                .scenarios(List.of(emptyScenario))
                .build();

        ValidationResult result = validator.validate(intent);
        assertThat(result.isValid()).isFalse();
        assertThat(result.getErrors()).anyMatch(e -> e.contains("no steps"));
    }

    // ---- R5: Valid action type ----

    @Test
    @DisplayName("R5 — step with null action → error")
    void shouldRejectStepWithNullAction() {
        IntentTestStep nullActionStep = IntentTestStep.builder()
                .action(null)
                .locator("testid=btn")
                .build();
        ValidationResult result = validator.validate(intentWithSingleStep(nullActionStep));
        assertThat(result.isValid()).isFalse();
        assertThat(result.getErrors()).anyMatch(e -> e.contains("action is required"));
    }

    // ---- R6: Locator required ----

    @Test
    @DisplayName("R6 — FILL without locator → error")
    void shouldRejectFillWithoutLocator() {
        IntentTestStep fillNoLocator = IntentTestStep.builder()
                .action(IntentActionType.FILL)
                .locator(null)
                .value("someValue")
                .build();
        ValidationResult result = validator.validate(intentWithSingleStep(fillNoLocator));
        assertThat(result.isValid()).isFalse();
        assertThat(result.getErrors()).anyMatch(e -> e.contains("FILL") && e.contains("locator"));
    }

    @Test
    @DisplayName("R6 — CLICK without locator → error")
    void shouldRejectClickWithoutLocator() {
        IntentTestStep clickNoLocator = IntentTestStep.builder()
                .action(IntentActionType.CLICK)
                .locator(null)
                .build();
        ValidationResult result = validator.validate(intentWithSingleStep(clickNoLocator));
        assertThat(result.isValid()).isFalse();
    }

    // ---- R7: Value required ----

    @Test
    @DisplayName("R7 — NAVIGATE without value → error")
    void shouldRejectNavigateWithoutValue() {
        IntentTestStep navNoValue = IntentTestStep.builder()
                .action(IntentActionType.NAVIGATE)
                .value(null)
                .build();
        ValidationResult result = validator.validate(intentWithSingleStep(navNoValue));
        assertThat(result.isValid()).isFalse();
        assertThat(result.getErrors()).anyMatch(e -> e.contains("NAVIGATE") && e.contains("value"));
    }

    @Test
    @DisplayName("R7 — FILL without value → error")
    void shouldRejectFillWithoutValue() {
        IntentTestStep fillNoValue = IntentTestStep.builder()
                .action(IntentActionType.FILL)
                .locator("testid=username")
                .value(null)
                .build();
        ValidationResult result = validator.validate(intentWithSingleStep(fillNoValue));
        assertThat(result.isValid()).isFalse();
        assertThat(result.getErrors()).anyMatch(e -> e.contains("FILL") && e.contains("value"));
    }

    // ---- R8+R9: Locator format — no Java code in locators ----

    @Test
    @DisplayName("R9 — page.locator() in locator field → error")
    void shouldRejectPageLocatorInLocatorField() {
        IntentTestStep badLocator = IntentTestStep.builder()
                .action(IntentActionType.CLICK)
                .locator("page.locator('[data-test=\"btn\"]')")
                .build();
        ValidationResult result = validator.validate(intentWithSingleStep(badLocator));
        assertThat(result.isValid()).isFalse();
        assertThat(result.getErrors()).anyMatch(e -> e.contains("Java code"));
    }

    @Test
    @DisplayName("R9 — Thread.sleep in locator field → error")
    void shouldRejectThreadSleepInLocatorField() {
        IntentTestStep badLocator = IntentTestStep.builder()
                .action(IntentActionType.CLICK)
                .locator("Thread.sleep(2000)")
                .build();
        ValidationResult result = validator.validate(intentWithSingleStep(badLocator));
        assertThat(result.isValid()).isFalse();
    }

    @Test
    @DisplayName("R9 — Playwright.create() in locator → error")
    void shouldRejectPlaywrightCreateInLocator() {
        IntentTestStep badLocator = IntentTestStep.builder()
                .action(IntentActionType.CLICK)
                .locator("Playwright.create()")
                .build();
        ValidationResult result = validator.validate(intentWithSingleStep(badLocator));
        assertThat(result.isValid()).isFalse();
    }

    @Test
    @DisplayName("R8 — Unknown locator strategy → error")
    void shouldRejectUnknownLocatorStrategy() {
        IntentTestStep unknownStrategy = IntentTestStep.builder()
                .action(IntentActionType.CLICK)
                .locator("magic=someSelector")
                .build();
        ValidationResult result = validator.validate(intentWithSingleStep(unknownStrategy));
        assertThat(result.isValid()).isFalse();
        assertThat(result.getErrors()).anyMatch(e -> e.contains("magic"));
    }

    @Test
    @DisplayName("R8 — All 9 valid locator strategies are accepted")
    void shouldAcceptAllValidLocatorStrategies() {
        List<String> validLocators = List.of(
                "testid=username",
                "role=button[name='Login']",
                "label=Email Address",
                "text=Products",
                "css=[data-test='btn']",
                "xpath=//button[@id='submit']",
                "id=submitBtn",
                "name=username",
                "class=btn-primary"
        );

        for (String locator : validLocators) {
            IntentTestStep step = IntentTestStep.builder()
                    .action(IntentActionType.ASSERT_VISIBLE)
                    .locator(locator)
                    .build();
            ValidationResult result = validator.validate(intentWithSingleStep(step));
            assertThat(result.getErrors())
                    .as("Locator '%s' should be accepted", locator)
                    .noneMatch(e -> e.contains("strategy") || e.contains("Java code"));
        }
    }

    // ---- R10+R11: Assertions ----

    @Test
    @DisplayName("R10 — scenario with no assertions → error")
    void shouldRejectScenarioWithNoAssertions() {
        TestScenario noAsserts = TestScenario.builder()
                .name("testNoAssert")
                .steps(List.of(
                        IntentTestStep.builder()
                                .action(IntentActionType.NAVIGATE)
                                .value("https://example.com")
                                .build(),
                        IntentTestStep.builder()
                                .action(IntentActionType.CLICK)
                                .locator("testid=btn")
                                .build()
                ))
                .build();
        TestIntent intent = TestIntent.builder()
                .testClassName("NoAssertTest")
                .scenarios(List.of(noAsserts))
                .build();

        ValidationResult result = validator.validate(intent);
        assertThat(result.isValid()).isFalse();
        assertThat(result.getErrors()).anyMatch(e -> e.contains("no assertions"));
    }

    @Test
    @DisplayName("R11 — scenario with >20 steps → warning (not error)")
    void shouldWarnForScenarioWithMoreThan20Steps() {
        List<IntentTestStep> manySteps = new ArrayList<>();
        // Add 21 valid click steps + 1 assertion at end
        for (int i = 0; i < 21; i++) {
            manySteps.add(IntentTestStep.builder()
                    .action(IntentActionType.CLICK)
                    .locator("testid=btn" + i)
                    .build());
        }
        manySteps.add(IntentTestStep.builder()
                .action(IntentActionType.ASSERT_URL)
                .value(".*done.*")
                .build());

        TestScenario scenario = TestScenario.builder()
                .name("testLong")
                .steps(manySteps)
                .build();
        TestIntent intent = TestIntent.builder()
                .testClassName("LongTest")
                .scenarios(List.of(scenario))
                .build();

        ValidationResult result = validator.validate(intent);
        assertThat(result.isValid()).isTrue();   // Warning only, not error
        assertThat(result.hasWarnings()).isTrue();
        assertThat(result.getWarnings()).anyMatch(w -> w.contains("consider splitting"));
    }

    // ---- Happy path ----

    @Test
    @DisplayName("Fully valid TestIntent → passes all rules")
    void shouldPassForFullyValidIntent() {
        ValidationResult result = validator.validate(buildFullyValidIntent());
        assertThat(result.isValid()).isTrue();
        assertThat(result.getErrors()).isEmpty();
    }

    // ---- Helpers ----

    private TestIntent buildFullyValidIntent() {
        return TestIntent.builder()
                .testClassName("LoginTest")
                .scenarios(List.of(buildValidScenario("testSuccessfulLogin")))
                .build();
    }

    private TestScenario buildValidScenario(String name) {
        return TestScenario.builder()
                .name(name)
                .description("Standard user logs in and sees inventory")
                .steps(List.of(
                        IntentTestStep.builder()
                                .action(IntentActionType.NAVIGATE)
                                .value("https://www.saucedemo.com")
                                .build(),
                        IntentTestStep.builder()
                                .action(IntentActionType.FILL)
                                .locator("testid=username")
                                .value("standard_user")
                                .build(),
                        IntentTestStep.builder()
                                .action(IntentActionType.FILL)
                                .locator("testid=password")
                                .value("secret_sauce")
                                .build(),
                        IntentTestStep.builder()
                                .action(IntentActionType.CLICK)
                                .locator("testid=login-button")
                                .build(),
                        IntentTestStep.builder()
                                .action(IntentActionType.ASSERT_URL)
                                .value(".*inventory.*")
                                .build()
                ))
                .build();
    }

    private TestIntent intentWithClassName(String className) {
        return TestIntent.builder()
                .testClassName(className)
                .scenarios(List.of(buildValidScenario("testScenario")))
                .build();
    }

    /**
     * Wraps a single step in a minimal valid intent.
     * Adds a trailing ASSERT_URL so R10 (assertions required) doesn't interfere
     * with the rule being tested — unless the test specifically tests R10.
     */
    private TestIntent intentWithSingleStep(IntentTestStep step) {
        List<IntentTestStep> steps = new ArrayList<>();
        steps.add(step);

        // Only add assert if step itself isn't an assertion (avoids masking the tested error)
        if (step.getAction() == null || !step.getAction().isAssertion()) {
            steps.add(IntentTestStep.builder()
                    .action(IntentActionType.ASSERT_URL)
                    .value(".*")
                    .build());
        }

        return TestIntent.builder()
                .testClassName("SingleStepTest")
                .scenarios(List.of(
                        TestScenario.builder()
                                .name("testScenario")
                                .steps(steps)
                                .build()
                ))
                .build();
    }
}