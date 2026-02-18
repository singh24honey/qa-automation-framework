package com.company.qa.model.intent;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Single test step within a TestScenario.
 *
 * Represents one atomic action the test should perform.
 * AI generates these; PlaywrightJavaRenderer converts each to a Java statement.
 *
 * Locator format uses the same strategy=value convention as
 * {@link com.company.qa.model.enums.LocatorStrategy}:
 *   testid=username
 *   role=button[name='Login']
 *   css=[data-test="login-button"]
 *   label=Email Address
 *   #user-name           (no prefix = CSS default)
 *
 * @author QA Framework
 * @since Zero-Hallucination Pipeline
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class IntentTestStep {

    /**
     * The action to perform.
     * Deserialized from string via {@link IntentActionType#fromString(String)}.
     */
    @JsonProperty("action")
    private IntentActionType action;

    /**
     * Element selector in strategy=value format.
     * Required for actions where {@link IntentActionType#requiresLocator()} is true.
     * Null or absent for page-level actions (NAVIGATE, RELOAD, ASSERT_URL, etc.).
     *
     * Examples:
     *   "testid=username"
     *   "role=button[name='Login']"
     *   "css=[data-test=\"login-button\"]"
     *   "[data-test=\"login-button\"]"   (no prefix = CSS default)
     */
    @JsonProperty("locator")
    private String locator;

    /**
     * Text value, URL, key name, or expected assertion value.
     * Required for actions where {@link IntentActionType#requiresValue()} is true.
     *
     * Usage by action type:
     *   NAVIGATE    → URL to navigate to
     *   FILL        → text to enter
     *   SELECT_OPTION → option value or text
     *   PRESS_KEY   → key name ("Enter", "Tab", "Escape")
     *   CLICK_ROLE  → role spec ("button[name='Login']")
     *   WAIT_FOR_URL → URL pattern
     *   ASSERT_TEXT → expected text
     *   ASSERT_URL  → expected URL pattern (regex)
     *   ASSERT_TITLE → expected page title
     *   ASSERT_COUNT → expected count (as string, e.g., "3")
     *   ASSERT_VALUE → expected input value
     */
    @JsonProperty("value")
    private String value;

    /**
     * Override timeout in milliseconds.
     * When null, Playwright's auto-wait applies (default behavior).
     * When set, renderer generates explicit waitFor with this timeout
     * before the action.
     */
    @JsonProperty("timeout")
    private Integer timeout;

    /**
     * Human-readable description of this step.
     * Rendered as a Java comment above the statement.
     * Shown in the approval UI for QA review.
     *
     * Example: "Enter username for standard user"
     */
    @JsonProperty("description")
    private String description;
}