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
 * A single test scenario within a TestIntent.
 *
 * Each scenario becomes one @Test method in the rendered Java class.
 *
 * @author QA Framework
 * @since Zero-Hallucination Pipeline
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class TestScenario {

    /**
     * Test method name. Rendered as the Java method name.
     * Should follow Java naming conventions (camelCase, start with lowercase).
     * Renderer sanitizes if needed (replaces spaces, invalid chars).
     *
     * Example: "testSuccessfulLogin", "testLockedOutUser"
     */
    @JsonProperty("name")
    private String name;

    /**
     * Human-readable description of the scenario.
     * Rendered as @DisplayName annotation value.
     *
     * Example: "Verify standard user can log in and see products page"
     */
    @JsonProperty("description")
    private String description;

    /**
     * Optional tags for grouping and filtering.
     * Future use: JUnit @Tag annotations, test suite selection.
     *
     * Example: ["smoke", "login", "positive"]
     */
    @JsonProperty("tags")
    @Builder.Default
    private List<String> tags = new ArrayList<>();

    /**
     * Ordered list of test steps. At least one step required.
     * Steps execute in order within the rendered @Test method.
     */
    @JsonProperty("steps")
    @Builder.Default
    private List<IntentTestStep> steps = new ArrayList<>();

    // ========== Convenience Methods ==========

    /**
     * Check if this scenario has any assertion steps.
     *
     * @return true if at least one step is an ASSERT_* action
     */
    public boolean hasAssertions() {
        return steps != null && steps.stream()
                .anyMatch(step -> step.getAction() != null && step.getAction().isAssertion());
    }

    /**
     * Count the number of assertion steps.
     *
     * @return number of ASSERT_* steps
     */
    public int getAssertionCount() {
        if (steps == null) return 0;
        return (int) steps.stream()
                .filter(step -> step.getAction() != null && step.getAction().isAssertion())
                .count();
    }

    /**
     * Get the step count.
     *
     * @return number of steps in this scenario
     */
    public int getStepCount() {
        return steps != null ? steps.size() : 0;
    }
}