package com.company.qa.model.intent;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * Top-level model for the Zero-Hallucination TestIntent pipeline.
 *
 * Represents a complete test class that AI generates as structured intent.
 * PlaywrightJavaRenderer converts this to a compilable Java test class.
 *
 * AI Output Contract:
 * {
 *   "testClassName": "PROJ123_LoginTest",
 *   "baseUrl": "https://www.saucedemo.com",      // optional
 *   "scenarios": [
 *     {
 *       "name": "testSuccessfulLogin",
 *       "description": "Standard user logs in successfully",
 *       "steps": [
 *         {"action": "NAVIGATE", "value": "https://www.saucedemo.com"},
 *         {"action": "FILL", "locator": "testid=username", "value": "standard_user"},
 *         {"action": "CLICK", "locator": "testid=login-button"},
 *         {"action": "ASSERT_URL", "value": ".*inventory.*"}
 *       ]
 *     }
 *   ]
 * }
 *
 * @author QA Framework
 * @since Zero-Hallucination Pipeline
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class TestIntent {

    /**
     * Java class name for the generated test.
     * Must be a valid Java identifier, typically ending with "Test".
     *
     * Example: "PROJ123_LoginTest"
     */
    @JsonProperty("testClassName")
    private String testClassName;

    /**
     * Optional application base URL.
     * If null, falls back to application.yml config.
     * If set, renderer may use it for NAVIGATE steps with relative URLs.
     */
    @JsonProperty("baseUrl")
    private String baseUrl;

    /**
     * Test scenarios. Each becomes one @Test method.
     * At least one scenario is required.
     */
    @JsonProperty("scenarios")
    @Builder.Default
    private List<TestScenario> scenarios = new ArrayList<>();

    // ========== Convenience Methods ==========

    /**
     * Total step count across all scenarios.
     * Used by quality metrics and cost estimation.
     *
     * @return sum of steps across all scenarios
     */
    public int getTotalStepCount() {
        if (scenarios == null) return 0;
        return scenarios.stream()
                .mapToInt(TestScenario::getStepCount)
                .sum();
    }

    /**
     * Check if any scenario contains assertion steps.
     * Used by TestIntentValidator (rule: every test must assert something).
     *
     * @return true if at least one scenario has an assertion step
     */
    public boolean hasAssertions() {
        return scenarios != null && scenarios.stream()
                .anyMatch(TestScenario::hasAssertions);
    }

    /**
     * Total assertion count across all scenarios.
     *
     * @return sum of assertion steps
     */
    public int getTotalAssertionCount() {
        if (scenarios == null) return 0;
        return scenarios.stream()
                .mapToInt(TestScenario::getAssertionCount)
                .sum();
    }

    /**
     * Get scenario count.
     *
     * @return number of scenarios
     */
    public int getScenarioCount() {
        return scenarios != null ? scenarios.size() : 0;
    }
}