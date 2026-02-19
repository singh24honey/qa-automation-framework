package com.company.qa.service.playwright;

import com.company.qa.model.intent.IntentActionType;
import com.company.qa.model.intent.IntentTestStep;
import com.company.qa.model.intent.TestIntent;
import com.company.qa.model.intent.TestScenario;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Core of the Zero-Hallucination pipeline.
 *
 * Converts a validated {@link TestIntent} into a compilable Java test class string.
 * This is a pure function: same input always produces the same output.
 * No AI calls, no external I/O, no randomness.
 *
 * Split across two days per implementation plan:
 *   Day 3 (this file): class structure, import management, locator rendering
 *   Day 4 (continuation): all 24 action renderers + timeout handling
 *
 * Usage:
 * <pre>
 *   String javaCode = renderer.render(validatedIntent);
 *   // javaCode is ready to write to disk as a .java file
 * </pre>
 *
 * @author QA Framework
 * @since Zero-Hallucination Pipeline
 */
@Slf4j
@Service
public class PlaywrightJavaRenderer {

    // ========== Config ==========

    @Value("${playwright.renderer.base-class:com.company.qa.playwright.BasePlaywrightTest}")
    private String baseClass;

    @Value("${playwright.renderer.base-package:com.company.qa.playwright.generated}")
    private String basePackage;

    @Value("${playwright.renderer.add-display-name:true}")
    private boolean addDisplayName;

    @Value("${playwright.renderer.add-step-comments:true}")
    private boolean addStepComments;

    // ========== Constants ==========

    private static final String INDENT = "    ";
    private static final String DOUBLE_INDENT = INDENT + INDENT;
    private static final String NEWLINE = "\n";

    // ========== Public Entry Point ==========

    /**
     * Render a validated TestIntent into a complete Java test class source string.
     *
     * Precondition: the intent has already been validated by {@link TestIntentValidator}.
     * If an action type is encountered that has no renderer, an
     * {@link IllegalArgumentException} is thrown — this is a programming error,
     * not a user error (it means a new ActionType was added without a renderer).
     *
     * @param intent validated TestIntent to render
     * @return compilable Java source code string
     * @throws IllegalArgumentException if an action type has no renderer implementation
     */
    public String render(TestIntent intent) {
        log.debug("Rendering TestIntent: className={}, scenarios={}",
                intent.getTestClassName(), intent.getScenarioCount());

        // Step 1: Analyze which imports are needed
        ImportSet imports = collectImports(intent);

        // Step 2: Build the class string
        StringBuilder sb = new StringBuilder();

        // Package + imports
        renderPackageAndImports(sb, imports, intent);

        // Class declaration
        renderClassDeclaration(sb, intent);

        // Test methods (one per scenario)
        for (TestScenario scenario : intent.getScenarios()) {
            renderScenario(sb, scenario, intent);
        }

        // Close class
        sb.append("}").append(NEWLINE);

        String result = sb.toString();
        log.debug("Rendered {} lines of Java for class {}",
                result.lines().count(), intent.getTestClassName());
        return result;
    }

    // ========== Import Collection ==========

    /**
     * Analyze the TestIntent and determine which imports are required.
     * Only imports that are actually used are included — no blanket imports.
     */
    ImportSet collectImports(TestIntent intent) {
        ImportSet imports = new ImportSet();

        // Always needed
        imports.addStatic(false, "org.junit.jupiter.api.Test");
        imports.addStatic(false, "org.junit.jupiter.api.DisplayName");

        // Scan all steps
        for (TestScenario scenario : intent.getScenarios()) {
            for (IntentTestStep step : scenario.getSteps()) {
                applyImportsForStep(step, imports);
            }
        }

        return imports;
    }

    private void applyImportsForStep(IntentTestStep step, ImportSet imports) {
        IntentActionType action = step.getAction();
        if (action == null) return;

        // Any assertion requires assertThat
        if (action.isAssertion()) {
            imports.addStatic(true, "com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat");
        }

        // CLICK_ROLE needs AriaRole
        if (action == IntentActionType.CLICK_ROLE) {
            imports.addStatic(false, "com.microsoft.playwright.options.AriaRole");
        }

        // ASSERT_URL uses Pattern (regex)
        if (action == IntentActionType.ASSERT_URL || action == IntentActionType.WAIT_FOR_URL) {
            imports.addStatic(false, "java.util.regex.Pattern");
        }

        // Any locator-based action requires Locator import
        if (action.requiresLocator() && step.getLocator() != null) {
            imports.addStatic(false, "com.microsoft.playwright.Locator");
        }

        // role= locator prefix also needs AriaRole
        if (step.getLocator() != null && step.getLocator().toLowerCase().startsWith("role=")) {
            imports.addStatic(false, "com.microsoft.playwright.options.AriaRole");
        }

        // Timeout option needs Locator.WaitForOptions
        if (step.getTimeout() != null && action.requiresLocator()) {
            // Locator.WaitForOptions is inner class of Locator — already covered by Locator import
            // No additional import needed since we use Locator.WaitForOptions
        }
    }

    // ========== Class Structure Rendering ==========

    private void renderPackageAndImports(StringBuilder sb, ImportSet imports, TestIntent intent) {
        // Package declaration
        sb.append("package ").append(basePackage).append(";").append(NEWLINE);
        sb.append(NEWLINE);

        // Sorted imports: static imports first, then regular, separated by blank line
        List<String> staticImports = imports.getStaticImports();
        List<String> regularImports = imports.getRegularImports();

        if (!staticImports.isEmpty()) {
            for (String imp : staticImports) {
                sb.append("import static ").append(imp).append(";").append(NEWLINE);
            }
            sb.append(NEWLINE);
        }

        if (!regularImports.isEmpty()) {
            for (String imp : regularImports) {
                sb.append("import ").append(imp).append(";").append(NEWLINE);
            }
            sb.append(NEWLINE);
        }
    }

    private void renderClassDeclaration(StringBuilder sb, TestIntent intent) {
        // Extract simple base class name for extends clause (keep fully qualified if no import)
        String simpleBaseClass = baseClass.contains(".")
                ? baseClass.substring(baseClass.lastIndexOf('.') + 1)
                : baseClass;

        sb.append("/**").append(NEWLINE);
        sb.append(" * Auto-generated by Zero-Hallucination Pipeline.").append(NEWLINE);
        sb.append(" * DO NOT EDIT — regenerate from TestIntent source.").append(NEWLINE);
        sb.append(" */").append(NEWLINE);
        sb.append("public class ").append(intent.getTestClassName())
                .append(" extends ").append(simpleBaseClass).append(" {").append(NEWLINE);
        sb.append(NEWLINE);
    }

    // ========== Scenario / Method Rendering ==========

    private void renderScenario(StringBuilder sb, TestScenario scenario, TestIntent intent) {
        String methodName = sanitizeMethodName(scenario.getName());

        // @Test
        sb.append(INDENT).append("@Test").append(NEWLINE);

        // @DisplayName (if enabled and description exists)
        if (addDisplayName) {
            String displayName = scenario.getDescription() != null
                    ? scenario.getDescription()
                    : scenario.getName();
            sb.append(INDENT).append("@DisplayName(\"")
                    .append(escapeJavaString(displayName))
                    .append("\")").append(NEWLINE);
        }

        // Method signature
        sb.append(INDENT).append("public void ").append(methodName).append("() {").append(NEWLINE);

        // Steps
        for (IntentTestStep step : scenario.getSteps()) {
            renderStep(sb, step);
        }

        // Close method
        sb.append(INDENT).append("}").append(NEWLINE);
        sb.append(NEWLINE);
    }

    private void renderStep(StringBuilder sb, IntentTestStep step) {
        // Optional: step description as comment
        if (addStepComments && step.getDescription() != null && !step.getDescription().isBlank()) {
            sb.append(DOUBLE_INDENT).append("// ").append(step.getDescription()).append(NEWLINE);
        }

        // Optional: explicit wait before locator-based action if timeout is set
        if (step.getTimeout() != null && step.getLocator() != null && step.getAction() != null
                && step.getAction().requiresLocator()) {
            String locatorExpr = renderLocator(step.getLocator());
            sb.append(DOUBLE_INDENT)
                    .append(locatorExpr)
                    .append(".waitFor(new Locator.WaitForOptions().setTimeout(")
                    .append(step.getTimeout())
                    .append("));")
                    .append(NEWLINE);
        }

        // Render the action statement
        String statement = renderActionStatement(step);
        sb.append(DOUBLE_INDENT).append(statement).append(NEWLINE);
    }

    // ========== Locator Rendering ==========

    /**
     * Converts a selector string (strategy=value format) into the correct Playwright Java API call.
     *
     * Mirrors the runtime logic in PlaywrightTestExecutor.resolveLocator() but produces
     * Java source code strings instead of live Locator objects.
     *
     * Locator format examples:
     *   testid=username          → page.getByTestId("username")
     *   role=button[name='OK']   → page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("OK"))
     *   label=Email              → page.getByLabel("Email")
     *   text=Products            → page.getByText("Products")
     *   css=#user-name           → page.locator("#user-name")
     *   xpath=//button           → page.locator("xpath=//button")
     *   id=username              → page.locator("#username")
     *   name=username            → page.locator("[name=\"username\"]")
     *   class=btn-primary        → page.locator(".btn-primary")
     *   #user-name               → page.locator("#user-name")      (no prefix = CSS default)
     *   [data-test="x"]          → page.locator("[data-test=\"x\"]")
     *
     * @param locator raw locator string from TestIntent
     * @return Java expression string (without trailing semicolon)
     */
    String renderLocator(String locator) {
        if (locator == null || locator.isBlank()) {
            throw new IllegalArgumentException("Cannot render null or blank locator");
        }

        String trimmed = locator.trim();

        // Detect strategy prefix: everything before the first '='
        int equalsIdx = trimmed.indexOf('=');

        if (equalsIdx > 0) {
            String prefix = trimmed.substring(0, equalsIdx).trim().toLowerCase();
            String value = trimmed.substring(equalsIdx + 1).trim();

            switch (prefix) {
                case "testid" -> {
                    return "page.getByTestId(\"" + escapeJavaString(value) + "\")";
                }
                case "role" -> {
                    return renderRoleLocator(value);
                }
                case "label" -> {
                    return "page.getByLabel(\"" + escapeJavaString(value) + "\")";
                }
                case "text" -> {
                    return "page.getByText(\"" + escapeJavaString(value) + "\")";
                }
                case "id" -> {
                    // id=username → page.locator("#username")
                    return "page.locator(\"#" + escapeJavaString(value) + "\")";
                }
                case "name" -> {
                    // name=username → page.locator("[name=\"username\"]")
                    return "page.locator(\"[name=\\\"" + escapeJavaString(value) + "\\\"]\")";
                }
                case "class" -> {
                    // class=btn-primary → page.locator(".btn-primary")
                    return "page.locator(\"." + escapeJavaString(value) + "\")";
                }
                case "css" -> {
                    // css=[data-test="x"] → page.locator("[data-test=\"x\"]")
                    return "page.locator(\"" + escapeJavaString(value) + "\")";
                }
                case "xpath" -> {
                    // xpath=//button → page.locator("xpath=//button")
                    return "page.locator(\"xpath=" + escapeJavaString(value) + "\")";
                }
                default -> {
                    // Unknown prefix — treat the entire string as a CSS selector
                    log.debug("Unknown locator prefix '{}' — treating as CSS selector", prefix);
                    return "page.locator(\"" + escapeJavaString(trimmed) + "\")";
                }
            }
        }

        // No prefix — treat as a raw CSS selector (e.g., "#username", ".btn", "[data-test='x']")
        return "page.locator(\"" + escapeJavaString(trimmed) + "\")";
    }

    /**
     * Render a role-based locator.
     *
     * Input format:  button[name='Login']  or  button  or  button[name='Login'][exact=true]
     * Output:        page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Login"))
     *
     * Supported role options parsed from bracket notation:
     *   [name='...']     → setName("...")
     *   [exact=true]     → setExact(true)
     *
     * @param roleSpec value after "role=" prefix
     * @return Java expression string
     */
    private String renderRoleLocator(String roleSpec) {
        // Split: "button[name='Login']" → roleName="button", options="[name='Login']"
        int bracketIdx = roleSpec.indexOf('[');

        String roleName;
        String optionsPart;

        if (bracketIdx >= 0) {
            roleName = roleSpec.substring(0, bracketIdx).trim();
            optionsPart = roleSpec.substring(bracketIdx);
        } else {
            roleName = roleSpec.trim();
            optionsPart = "";
        }

        String ariaRole = "AriaRole." + roleName.toUpperCase();

        if (optionsPart.isEmpty()) {
            return "page.getByRole(" + ariaRole + ")";
        }

        // Parse options from bracket notation
        StringBuilder options = new StringBuilder("new Page.GetByRoleOptions()");

        // name option: [name='Login'] or [name="Login"]
        java.util.regex.Matcher nameMatcher = java.util.regex.Pattern
                .compile("\\[name=['\"]([^'\"]+)['\"]\\]")
                .matcher(optionsPart);
        if (nameMatcher.find()) {
            options.append(".setName(\"").append(escapeJavaString(nameMatcher.group(1))).append("\")");
        }

        // exact option: [exact=true] or [exact=false]
        java.util.regex.Matcher exactMatcher = java.util.regex.Pattern
                .compile("\\[exact=(true|false)\\]")
                .matcher(optionsPart);
        if (exactMatcher.find()) {
            options.append(".setExact(").append(exactMatcher.group(1)).append(")");
        }

        // If options builder only has the new ...() prefix and no setters were added,
        // still include it for explicitness
        return "page.getByRole(" + ariaRole + ", " + options + ")";
    }

    // ========== Action Statement Rendering ==========
    // NOTE: All action renderers will be implemented in Day 4.
    // This method is the dispatch point — Day 4 fills in every case.

    /**
     * Render a single step into a Java statement string.
     * Dispatches to per-action renderer.
     *
     * @param step the step to render
     * @return Java statement string (without trailing newline, WITH trailing semicolon)
     */
    String renderActionStatement(IntentTestStep step) {
        IntentActionType action = step.getAction();

        return switch (action) {
            // ---- Navigation ----
            case NAVIGATE -> "page.navigate(\"" + escapeJavaString(step.getValue()) + "\");";
            case RELOAD -> "page.reload();";
            case GO_BACK -> "page.goBack();";

            // ---- Input ----
            case FILL -> renderLocator(step.getLocator()) + ".fill(\"" + escapeJavaString(step.getValue()) + "\");";
            case CLEAR -> renderLocator(step.getLocator()) + ".clear();";
            case SELECT_OPTION -> renderLocator(step.getLocator()) + ".selectOption(\"" + escapeJavaString(step.getValue()) + "\");";
            case CHECK -> renderLocator(step.getLocator()) + ".check();";
            case UNCHECK -> renderLocator(step.getLocator()) + ".uncheck();";
            case PRESS_KEY -> "page.keyboard().press(\"" + escapeJavaString(step.getValue()) + "\");";

            // ---- Interaction ----
            case CLICK -> renderLocator(step.getLocator()) + ".click();";
            case CLICK_ROLE -> renderLocator("role=" + step.getValue()) + ".click();";
            case HOVER -> renderLocator(step.getLocator()) + ".hover();";

            // ---- Wait ----
            case WAIT_FOR_LOAD -> "page.waitForLoadState();";
            case WAIT_FOR_SELECTOR -> renderLocator(step.getLocator()) + ".waitFor();";
            case WAIT_FOR_URL -> "page.waitForURL(Pattern.compile(\"" + escapeJavaString(step.getValue()) + "\"));";

            // ---- Assertions ----
            case ASSERT_TEXT -> "assertThat(" + renderLocator(step.getLocator()) + ").hasText(\"" + escapeJavaString(step.getValue()) + "\");";
            case ASSERT_VISIBLE -> "assertThat(" + renderLocator(step.getLocator()) + ").isVisible();";
            case ASSERT_HIDDEN -> "assertThat(" + renderLocator(step.getLocator()) + ").isHidden();";
            case ASSERT_URL -> "assertThat(page).hasURL(Pattern.compile(\"" + escapeJavaString(step.getValue()) + "\"));";
            case ASSERT_TITLE -> "assertThat(page).hasTitle(\"" + escapeJavaString(step.getValue()) + "\");";
            case ASSERT_COUNT -> "assertThat(" + renderLocator(step.getLocator()) + ").hasCount(" + step.getValue() + ");";
            case ASSERT_VALUE -> "assertThat(" + renderLocator(step.getLocator()) + ").hasValue(\"" + escapeJavaString(step.getValue()) + "\");";
            case ASSERT_ENABLED -> "assertThat(" + renderLocator(step.getLocator()) + ").isEnabled();";
            case ASSERT_DISABLED -> "assertThat(" + renderLocator(step.getLocator()) + ").isDisabled();";
        };
    }

    // ========== Utility Methods ==========

    /**
     * Sanitize a scenario name to a valid Java method name.
     *
     * Rules:
     * - Replace spaces and hyphens with camelCase transition
     * - Remove any character that is not alphanumeric or underscore
     * - If starts with a digit, prefix with underscore
     * - If empty after sanitization, fall back to "testScenario"
     *
     * Examples:
     *   "testSuccessfulLogin" → "testSuccessfulLogin"  (no change)
     *   "Test Successful Login" → "testSuccessfulLogin"
     *   "test-login-flow" → "testLoginFlow"
     *   "123invalid" → "_123invalid"
     */
    String sanitizeMethodName(String name) {
        if (name == null || name.isBlank()) {
            return "testScenario";
        }

        // Split on spaces, hyphens, underscores — then camelCase join
        String[] words = name.split("[\\s\\-_]+");
        StringBuilder sb = new StringBuilder();

        for (int i = 0; i < words.length; i++) {
            String word = words[i].replaceAll("[^A-Za-z0-9]", "");
            if (word.isEmpty()) continue;

            if (i == 0) {
                // First word: lowercase first letter (Java method convention)
                sb.append(Character.toLowerCase(word.charAt(0)));
                if (word.length() > 1) sb.append(word.substring(1));
            } else {
                // Subsequent words: capitalize first letter
                sb.append(Character.toUpperCase(word.charAt(0)));
                if (word.length() > 1) sb.append(word.substring(1));
            }
        }

        String result = sb.toString();

        if (result.isEmpty()) return "testScenario";

        // Prefix with underscore if starts with digit
        if (Character.isDigit(result.charAt(0))) {
            result = "_" + result;
        }

        return result;
    }

    /**
     * Escape a string value for safe inclusion in a Java string literal.
     * Handles: backslash, double-quote, newline, tab, carriage return.
     *
     * @param value raw string
     * @return escaped string safe for Java string literals
     */
    String escapeJavaString(String value) {
        if (value == null) return "";
        return value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    // ========== Import Set ==========

    /**
     * Tracks required imports, deduplicating and separating static from regular.
     */
    static class ImportSet {
        private final Set<String> staticImports = new LinkedHashSet<>();
        private final Set<String> regularImports = new LinkedHashSet<>();

        void addStatic(boolean isStatic, String fullyQualified) {
            if (isStatic) {
                staticImports.add(fullyQualified);
            } else {
                regularImports.add(fullyQualified);
            }
        }

        List<String> getStaticImports() {
            return staticImports.stream().sorted().toList();
        }

        List<String> getRegularImports() {
            return regularImports.stream().sorted().toList();
        }
    }
}