package com.company.qa.model.intent;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import java.util.EnumSet;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * All action types supported by the Zero-Hallucination TestIntent pipeline.
 *
 * AI produces these action types in TestIntent JSON.
 * PlaywrightJavaRenderer maps each to deterministic Java code.
 * PlaywrightTestExecutor handles runtime execution.
 *
 * Naming: "IntentActionType" to avoid collision with existing
 * {@link com.company.qa.model.enums.ActionType} used by PlaywrightTestExecutor.
 *
 * @author QA Framework
 * @since Zero-Hallucination Pipeline
 */
public enum IntentActionType {

    // ========== Navigation ==========

    /** Navigate to a URL. Requires: value (URL). */
    NAVIGATE("Navigate to URL", false, true),

    /** Reload the current page. */
    RELOAD("Reload page", false, false),

    /** Go back in browser history. */
    GO_BACK("Go back", false, false),

    // ========== Input ==========

    /** Fill text into an input. Clears existing content first. Requires: locator, value. */
    FILL("Fill input", true, true),

    /** Clear an input field. Requires: locator. */
    CLEAR("Clear input", true, false),

    /** Select a dropdown option. Requires: locator, value (option text or value). */
    SELECT_OPTION("Select option", true, true),

    /** Check a checkbox. Requires: locator. */
    CHECK("Check checkbox", true, false),

    /** Uncheck a checkbox. Requires: locator. */
    UNCHECK("Uncheck checkbox", true, false),

    /** Press a keyboard key. Requires: value (key name, e.g., "Enter", "Tab"). */
    PRESS_KEY("Press key", false, true),

    // ========== Interaction ==========

    /** Click an element by locator. Requires: locator. */
    CLICK("Click element", true, false),

    /**
     * Click an element by ARIA role. Requires: value (role spec).
     * Format: "button[name='Login']" or just "button".
     * Renderer maps to page.getByRole(AriaRole.BUTTON, ...).click()
     */
    CLICK_ROLE("Click by role", false, true),

    /** Hover over an element. Requires: locator. */
    HOVER("Hover element", true, false),

    // ========== Wait ==========

    /** Wait for page load state. No parameters needed. */
    WAIT_FOR_LOAD("Wait for load", false, false),

    /** Wait for a specific element to appear. Requires: locator. */
    WAIT_FOR_SELECTOR("Wait for selector", true, false),

    /** Wait for URL to match a pattern. Requires: value (URL pattern). */
    WAIT_FOR_URL("Wait for URL", false, true),

    // ========== Assertions ==========

    /** Assert element has specific text. Requires: locator, value (expected text). */
    ASSERT_TEXT("Assert text", true, true),

    /** Assert element is visible. Requires: locator. */
    ASSERT_VISIBLE("Assert visible", true, false),

    /** Assert element is hidden. Requires: locator. */
    ASSERT_HIDDEN("Assert hidden", true, false),

    /** Assert page URL matches pattern. Requires: value (URL pattern/regex). */
    ASSERT_URL("Assert URL", false, true),

    /** Assert page title. Requires: value (expected title). */
    ASSERT_TITLE("Assert title", false, true),

    /** Assert element count. Requires: locator, value (count as string). */
    ASSERT_COUNT("Assert count", true, true),

    /** Assert input has specific value. Requires: locator, value (expected). */
    ASSERT_VALUE("Assert value", true, true),

    /** Assert element is enabled. Requires: locator. */
    ASSERT_ENABLED("Assert enabled", true, false),

    /** Assert element is disabled. Requires: locator. */
    ASSERT_DISABLED("Assert disabled", true, false);

    // ========== Fields ==========

    private final String displayName;
    private final boolean locatorRequired;
    private final boolean valueRequired;

    IntentActionType(String displayName, boolean locatorRequired, boolean valueRequired) {
        this.displayName = displayName;
        this.locatorRequired = locatorRequired;
        this.valueRequired = valueRequired;
    }

    // ========== Getters ==========

    public String getDisplayName() {
        return displayName;
    }

    /**
     * Whether this action requires a locator (element selector).
     */
    public boolean requiresLocator() {
        return locatorRequired;
    }

    /**
     * Whether this action requires a value (text, URL, key name, etc.).
     */
    public boolean requiresValue() {
        return valueRequired;
    }

    /**
     * Whether this is an assertion action (ASSERT_*).
     */
    public boolean isAssertion() {
        return name().startsWith("ASSERT_");
    }

    /**
     * Whether this is a navigation action.
     */
    public boolean isNavigation() {
        return this == NAVIGATE || this == RELOAD || this == GO_BACK;
    }

    /**
     * Whether this is a wait action.
     */
    public boolean isWait() {
        return this == WAIT_FOR_LOAD || this == WAIT_FOR_SELECTOR || this == WAIT_FOR_URL;
    }

    /**
     * Whether this is an input/interaction action that modifies page state.
     */
    public boolean isInteraction() {
        return this == FILL || this == CLEAR || this == SELECT_OPTION
                || this == CHECK || this == UNCHECK || this == PRESS_KEY
                || this == CLICK || this == CLICK_ROLE || this == HOVER;
    }

    // ========== Serialization ==========

    /**
     * Jackson serialization: serialize as enum name (e.g., "FILL").
     */
    @JsonValue
    public String toValue() {
        return name();
    }

    /**
     * Jackson deserialization: case-insensitive parse from string.
     * Handles "FILL", "fill", "Fill" etc.
     *
     * @param value action type string from JSON
     * @return IntentActionType enum value
     * @throws IllegalArgumentException if value is unknown
     */
    @JsonCreator
    public static IntentActionType fromString(String value) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException("Action type cannot be null or empty");
        }

        String normalized = value.trim().toUpperCase();

        try {
            return valueOf(normalized);
        } catch (IllegalArgumentException e) {
            // Build helpful error message with all valid values
            String validValues = EnumSet.allOf(IntentActionType.class).stream()
                    .map(Enum::name)
                    .collect(Collectors.joining(", "));

            throw new IllegalArgumentException(
                    "Unknown action type: '" + value + "'. Supported types: " + validValues
            );
        }
    }

    // ========== Static Helpers ==========

    /** All assertion action types. */
    private static final Set<IntentActionType> ASSERTION_TYPES = EnumSet.of(
            ASSERT_TEXT, ASSERT_VISIBLE, ASSERT_HIDDEN, ASSERT_URL,
            ASSERT_TITLE, ASSERT_COUNT, ASSERT_VALUE, ASSERT_ENABLED, ASSERT_DISABLED
    );

    /** All action types requiring a locator. */
    private static final Set<IntentActionType> LOCATOR_REQUIRED_TYPES = EnumSet.allOf(IntentActionType.class)
            .stream()
            .filter(IntentActionType::requiresLocator)
            .collect(Collectors.toCollection(() -> EnumSet.noneOf(IntentActionType.class)));

    /**
     * Get all assertion action types.
     */
    public static Set<IntentActionType> assertionTypes() {
        return ASSERTION_TYPES;
    }

    /**
     * Get all action types that require a locator.
     */
    public static Set<IntentActionType> locatorRequiredTypes() {
        return LOCATOR_REQUIRED_TYPES;
    }
}