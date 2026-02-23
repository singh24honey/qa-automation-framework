package com.company.qa.model.intent;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.IOException;

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
@JsonDeserialize(using = IntentTestStep.Deserializer.class)
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

    /**
     * Lenient deserializer: sets action=null when AI uses an unknown action type
     * (e.g. Gherkin keywords like EXAMPLES, BACKGROUND, SCENARIO).
     *
     * TestIntentParser filters out steps where action==null after deserialization,
     * so a single bad step doesn't kill the entire generation.
     */
    public static class Deserializer extends StdDeserializer<IntentTestStep> {

        public Deserializer() {
            super(IntentTestStep.class);
        }

        @Override
        public IntentTestStep deserialize(JsonParser p, DeserializationContext ctx) throws IOException {
            JsonNode node = p.getCodec().readTree(p);

            String actionStr = node.has("action") ? node.get("action").asText(null) : null;
            IntentActionType action = IntentActionType.fromStringOrNull(actionStr);

            if (action == null && actionStr != null) {
                // Log via standard output — Slf4j not available in static context
                System.out.printf("[WARN] IntentTestStep: unknown action type '%s' — step will be skipped%n",
                        actionStr);
            }

            return IntentTestStep.builder()
                    .action(action)
                    .locator(node.has("locator")     ? node.get("locator").asText(null)     : null)
                    .value(node.has("value")         ? node.get("value").asText(null)       : null)
                    .timeout(node.has("timeout")     ? node.get("timeout").asInt(0)         : null)
                    .description(node.has("description") ? node.get("description").asText(null) : null)
                    .build();
        }
    }
}