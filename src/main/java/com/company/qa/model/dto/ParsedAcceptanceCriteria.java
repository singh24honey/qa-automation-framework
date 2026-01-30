package com.company.qa.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * Week 9 Day 2: Parsed Acceptance Criteria DTO
 *
 * Represents acceptance criteria parsed from various formats:
 * - Gherkin (Given/When/Then)
 * - Numbered lists (1. 2. 3.)
 * - Bullet points (- * •)
 * - Unstructured text
 *
 * This DTO provides a normalized structure for AI test generation.
 *
 * Design Decisions:
 * - Immutable after construction (builder pattern)
 * - Self-contained validation logic
 * - Helper methods for common operations
 * - Nested DTO for Gherkin scenarios
 *
 * Example Usage:
 * <pre>
 * ParsedAcceptanceCriteria parsed = parser.parse(rawText);
 * if (parsed.getFormat() == ACFormat.GHERKIN) {
 *     parsed.getScenarios().forEach(scenario -> {
 *         System.out.println(scenario.toGherkinString());
 *     });
 * }
 * </pre>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ParsedAcceptanceCriteria {

    /**
     * Detected format of the acceptance criteria
     */
    private ACFormat format;

    /**
     * Original raw text (preserved for reference)
     */
    private String rawText;

    /**
     * Parsed scenarios (for Gherkin format)
     * Empty list if format is not GHERKIN
     */
    @Builder.Default
    private List<GherkinScenario> scenarios = new ArrayList<>();

    /**
     * Parsed steps (for numbered/bullet/unstructured formats)
     * Empty list for GHERKIN format
     */
    @Builder.Default
    private List<String> steps = new ArrayList<>();

    /**
     * Normalized Gherkin representation (best-effort conversion)
     * Generated for non-Gherkin formats to provide consistent AI input
     */
    private String normalizedGherkin;

    /**
     * Confidence score (0.0 - 1.0) for format detection
     * Higher = more confident in detected format
     */
    @Builder.Default
    private double confidence = 0.0;

    /**
     * Parse warnings (e.g., malformed Gherkin, mixed formats)
     * Used for user feedback and debugging
     */
    @Builder.Default
    private List<String> warnings = new ArrayList<>();

    /**
     * Supported acceptance criteria formats
     */
    public enum ACFormat {
        GHERKIN,           // Given/When/Then - structured scenarios
        NUMBERED_LIST,     // 1. 2. 3. - sequential steps
        BULLET_POINTS,     // - * • - list of requirements
        UNSTRUCTURED,      // Plain paragraphs - free text
        MIXED,             // Multiple formats combined
        EMPTY              // No AC provided
    }

    /**
     * Represents a single Gherkin scenario
     *
     * Standard Gherkin structure:
     * Scenario: <name>
     *   Given <precondition>
     *   When <action>
     *   Then <expected result>
     *   And <additional steps>
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class GherkinScenario {

        /**
         * Scenario name/title
         */
        private String name;

        /**
         * Given steps (preconditions/setup)
         */
        @Builder.Default
        private List<String> givenSteps = new ArrayList<>();

        /**
         * When steps (actions/triggers)
         */
        @Builder.Default
        private List<String> whenSteps = new ArrayList<>();

        /**
         * Then steps (expected outcomes/assertions)
         */
        @Builder.Default
        private List<String> thenSteps = new ArrayList<>();

        /**
         * And steps (additional conditions)
         * Note: And steps are context-dependent and may be parsed
         * into given/when/then based on preceding keyword
         */
        @Builder.Default
        private List<String> andSteps = new ArrayList<>();

        /**
         * Convert to standard Gherkin format string
         *
         * Example output:
         * Scenario: User Login
         *   Given I am on the login page
         *   When I enter valid credentials
         *   Then I should be redirected to dashboard
         */
        public String toGherkinString() {
            StringBuilder sb = new StringBuilder();

            if (name != null && !name.isEmpty()) {
                sb.append("Scenario: ").append(name).append("\n");
            }

            givenSteps.forEach(step -> sb.append("  Given ").append(step).append("\n"));
            whenSteps.forEach(step -> sb.append("  When ").append(step).append("\n"));
            thenSteps.forEach(step -> sb.append("  Then ").append(step).append("\n"));
            andSteps.forEach(step -> sb.append("  And ").append(step).append("\n"));

            return sb.toString();
        }

        /**
         * Check if scenario has content
         * Scenario is valid if it has at least one step
         */
        public boolean hasContent() {
            return !givenSteps.isEmpty() || !whenSteps.isEmpty() ||
                    !thenSteps.isEmpty() || !andSteps.isEmpty();
        }

        /**
         * Get total step count
         * Useful for complexity metrics
         */
        public int getTotalStepCount() {
            return givenSteps.size() + whenSteps.size() +
                    thenSteps.size() + andSteps.size();
        }

        /**
         * Validate scenario structure
         * A well-formed scenario should have:
         * - At least one Given (precondition)
         * - At least one When (action)
         * - At least one Then (assertion)
         */
        public boolean isWellFormed() {
            return !givenSteps.isEmpty() &&
                    !whenSteps.isEmpty() &&
                    !thenSteps.isEmpty();
        }
    }

    /**
     * Check if AC contains meaningful content
     * Content exists if we have scenarios, steps, or raw text
     */
    public boolean hasContent() {
        return !scenarios.isEmpty() || !steps.isEmpty() ||
                (rawText != null && !rawText.trim().isEmpty());
    }

    /**
     * Get total number of test scenarios/steps
     * For Gherkin: number of scenarios
     * For other formats: 1 (all steps = 1 test case)
     */
    public int getTestCaseCount() {
        if (format == ACFormat.GHERKIN) {
            return scenarios.size();
        } else if (!steps.isEmpty()) {
            return 1; // Single test case with multiple steps
        }
        return 0;
    }

    /**
     * Get total number of steps across all scenarios
     * Useful for complexity estimation
     */
    public int getTotalStepCount() {
        if (format == ACFormat.GHERKIN) {
            return scenarios.stream()
                    .mapToInt(GherkinScenario::getTotalStepCount)
                    .sum();
        } else {
            return steps.size();
        }
    }

    /**
     * Add a warning message
     * Thread-safe lazy initialization of warnings list
     */
    public void addWarning(String warning) {
        if (warnings == null) {
            warnings = new ArrayList<>();
        }
        warnings.add(warning);
    }

    /**
     * Check if parsing produced any warnings
     * Warnings indicate potential issues with format detection or parsing
     */
    public boolean hasWarnings() {
        return warnings != null && !warnings.isEmpty();
    }

    /**
     * Get formatted summary for logging/display
     * Example: "GHERKIN (95% confidence): 2 scenarios, 8 steps"
     */
    public String getSummary() {
        return String.format("%s (%.0f%% confidence): %d scenarios, %d steps",
                format,
                confidence * 100,
                scenarios.size(),
                getTotalStepCount()
        );
    }

    /**
     * Check if AC is ready for AI test generation
     * Ready if we have meaningful content and reasonable confidence
     */
    public boolean isReadyForAI() {
        return hasContent() && confidence >= 0.5;
    }
}