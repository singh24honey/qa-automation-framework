package com.company.qa.service.playwright;

import com.company.qa.model.intent.IntentActionType;
import com.company.qa.model.intent.IntentTestStep;
import com.company.qa.model.intent.TestIntent;
import com.company.qa.model.intent.TestScenario;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Validates TestIntent before rendering.
 * Catches AI mistakes early with clear error messages.
 *
 * @since Zero-Hallucination Pipeline
 */
@Slf4j
@Service
public class TestIntentValidator {

    private static final Pattern JAVA_IDENTIFIER = Pattern.compile("^[A-Za-z_$][A-Za-z0-9_$]*$");
    private static final int MAX_STEPS_WARNING = 20;

    // Patterns that indicate AI leaked Java code into locators
    private static final List<String> FORBIDDEN_LOCATOR_PATTERNS = List.of(
            "page.locator", "page.getByRole", "page.getByText",
            "page.getByLabel", "page.getByTestId",
            "Playwright.create", "Thread.sleep",
            "BrowserType", "Browser.newContext"
    );

    /**
     * Validate a TestIntent. Returns errors and warnings.
     */
    public ValidationResult validate(TestIntent intent) {
        ValidationResult result = ValidationResult.builder().build();

        if (intent == null) {
            result.addError("TestIntent is null");
            return result;
        }

        validateClassName(intent, result);
        validateScenarios(intent, result);

        if (result.isValid()) {
            log.debug("TestIntent validation passed: {} scenarios, {} total steps",
                    intent.getScenarioCount(), intent.getTotalStepCount());
        } else {
            log.warn("TestIntent validation failed with {} errors: {}",
                    result.getErrors().size(), result.getErrors());
        }

        return result;
    }

    // R1: Valid class name
    private void validateClassName(TestIntent intent, ValidationResult result) {
        String name = intent.getTestClassName();
        if (name == null || name.isBlank()) {
            result.addError("testClassName is required");
            return;
        }
        if (!JAVA_IDENTIFIER.matcher(name).matches()) {
            result.addError("Invalid testClassName '" + name + "': must be a valid Java identifier");
            return;
        }
        if (!name.endsWith("Test")) {
            result.addWarning("testClassName '" + name + "' should end with 'Test' by convention");
        }
    }

    // R2-R4, R10-R11: Scenario-level validation
    private void validateScenarios(TestIntent intent, ValidationResult result) {
        List<TestScenario> scenarios = intent.getScenarios();

        // R2
        if (scenarios == null || scenarios.isEmpty()) {
            result.addError("TestIntent must have at least one scenario");
            return;
        }

        // R3: Unique names
        Set<String> names = new HashSet<>();
        for (TestScenario scenario : scenarios) {
            if (scenario.getName() != null && !names.add(scenario.getName())) {
                result.addError("Duplicate scenario name: '" + scenario.getName() + "'");
            }
        }

        // Validate each scenario
        for (int i = 0; i < scenarios.size(); i++) {
            TestScenario scenario = scenarios.get(i);
            String label = "Scenario[" + i + "] '" + scenario.getName() + "'";
            validateScenario(scenario, label, result);
        }
    }

    private void validateScenario(TestScenario scenario, String label, ValidationResult result) {
        // R4
        if (scenario.getSteps() == null || scenario.getSteps().isEmpty()) {
            result.addError(label + " has no steps");
            return;
        }

        // R10
        if (!scenario.hasAssertions()) {
            result.addError(label + " has no assertions — every test must assert something");
        }

        // R11
        if (scenario.getStepCount() > MAX_STEPS_WARNING) {
            result.addWarning(label + " has " + scenario.getStepCount() + " steps — consider splitting");
        }

        // Validate each step
        for (int j = 0; j < scenario.getSteps().size(); j++) {
            IntentTestStep step = scenario.getSteps().get(j);
            String stepLabel = label + " → Step[" + j + "]";
            validateStep(step, stepLabel, result);
        }
    }

    private void validateStep(IntentTestStep step, String label, ValidationResult result) {
        // R5: Valid action
        if (step.getAction() == null) {
            result.addError(label + ": action is required");
            return;
        }

        IntentActionType action = step.getAction();

        // R6: Locator required
        if (action.requiresLocator() && isBlank(step.getLocator())) {
            result.addError(label + ": " + action.name() + " requires a locator");
        }

        // R7: Value required
        // NOTE: We use isNull() not isBlank() here intentionally.
        // A null value means the AI omitted the field entirely — that's an error.
        // An empty string ("") is VALID test data for actions like FILL used to test
        // empty-field validation scenarios (e.g., testEmptyUsername submits "" to the field).
        // isBlank() would incorrectly reject these legitimate edge-case test scenarios.
        if (action.requiresValue() && isNull(step.getValue())) {
            result.addError(label + ": " + action.name() + " requires a value (set to empty string \"\" if intentionally blank)");
        }

        // R8 + R9: Locator format (only if locator is present)
        if (!isBlank(step.getLocator())) {
            validateLocatorFormat(step.getLocator(), label, result);
        }
    }

    // R8 + R9: No Java code in locators
    private void validateLocatorFormat(String locator, String label, ValidationResult result) {
        // Existing: check forbidden Java patterns
        for (String forbidden : FORBIDDEN_LOCATOR_PATTERNS) {
            if (locator.contains(forbidden)) {
                result.addError(label + ": locator contains Java code '" + forbidden + "'");
                return;
            }
        }

        // Existing: check strategy prefix
        if (locator.contains("=")) {
            String strategy = locator.substring(0, locator.indexOf('=')).trim().toLowerCase();
            Set<String> validStrategies = Set.of(
                    "role", "label", "text", "testid", "css", "xpath", "id", "name", "class");
            if (!validStrategies.contains(strategy)) {
                result.addError(label + ": unknown locator strategy '" + strategy + "'");
                return;
            }

            // ✅ NEW: validate CSS selector value for common mistakes
            if ("css".equals(strategy)) {
                String cssValue = locator.substring(locator.indexOf('=') + 1).trim();
                validateCssSelector(cssValue, label, result);
            }
        }
    }

    /**
     * Detects common AI-generated CSS mistakes:
     *
     * 1. Compound multi-class selectors without descendant space
     *    e.g. ".parent-class.child-class" — means same element has both classes,
     *    but the DOM usually has them on parent/child elements.
     *    Correct: ".parent-class .child-class" (with space)
     *
     * 2. nth-child + class without space
     *    e.g. ".inventory_item:nth-child(1).btn_inventory"
     *    Correct: ".inventory_item:nth-child(1) .btn_inventory"
     */
    private void validateCssSelector(String cssValue, String label, ValidationResult result) {
        // Pattern: ":nth-child(N).someClass" or ":nth-of-type(N).someClass"
        // nth-child pseudo selects the element — .class after it means SAME element has that class
        // Usually this is a mistake: the button is a CHILD, not the same element
        if (cssValue.matches(".*:nth-child\\(\\d+\\)\\.\\w.*") ||
                cssValue.matches(".*:nth-of-type\\(\\d+\\)\\.\\w.*")) {
            result.addWarning(label + ": CSS selector '" + cssValue +
                    "' uses ':nth-child(N).class' which targets an element that must have BOTH the " +
                    "nth-child position AND the class simultaneously. " +
                    "If the class is on a child element, add a space: ':nth-child(N) .class'. " +
                    "Prefer testid= locators for stability.");
        }

        // Pattern: ".classA.classB" where classB looks like a button/action class (not a modifier)
        // e.g. ".inventory_item.btn_inventory" — btn_ prefix on a child element is suspicious
        if (cssValue.matches(".*\\.[a-z_]+\\.[a-z]*btn[a-z_]*.*") ||
                cssValue.matches(".*\\.[a-z_]+\\.[a-z]*button[a-z_]*.*")) {
            result.addWarning(label + ": CSS selector '" + cssValue +
                    "' chains a container class with a button class. " +
                    "Buttons are usually child elements — use a descendant space: '.container .btn'. " +
                    "Prefer testid= locators for stability.");
        }
    }
    private boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    /**
     * Checks if a value is strictly null (absent from JSON).
     * Unlike isBlank(), this allows empty strings as valid test data.
     * Used for R7 validation where "" is a legitimate test value (e.g., empty field submission tests).
     */
    private boolean isNull(String s) {
        return s == null;
    }
}