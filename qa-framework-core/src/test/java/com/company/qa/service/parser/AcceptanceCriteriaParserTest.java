package com.company.qa.service.parser;

import com.company.qa.model.dto.ParsedAcceptanceCriteria;
import com.company.qa.model.dto.ParsedAcceptanceCriteria.ACFormat;
import com.company.qa.model.dto.ParsedAcceptanceCriteria.GherkinScenario;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.*;

/**
 * Week 9 Day 2: Acceptance Criteria Parser Tests
 *
 * Tests all 4 AC formats + edge cases:
 * 1. Gherkin (Given/When/Then)
 * 2. Numbered lists
 * 3. Bullet points
 * 4. Unstructured text
 * 5. Edge cases (empty, null, malformed)
 */
class AcceptanceCriteriaParserTest {

    private AcceptanceCriteriaParser parser;

    @BeforeEach
    void setUp() {
        parser = new AcceptanceCriteriaParser();
    }

    // ==================== Gherkin Format Tests ====================

    @Test
    @DisplayName("Should parse simple Gherkin format")
    void shouldParseSimpleGherkin() {
        String ac = """
            Scenario: User Login
            Given I am on the login page
            When I enter valid credentials
            Then I should be redirected to dashboard
            """;

        ParsedAcceptanceCriteria result = parser.parse(ac);

        assertThat(result.getFormat()).isEqualTo(ACFormat.GHERKIN);
        assertThat(result.getConfidence()).isGreaterThan(0.8);
        assertThat(result.getScenarios()).hasSize(1);

        GherkinScenario scenario = result.getScenarios().get(0);
        assertThat(scenario.getName()).isEqualTo("User Login");
        assertThat(scenario.getGivenSteps()).containsExactly("I am on the login page");
        assertThat(scenario.getWhenSteps()).containsExactly("I enter valid credentials");
        assertThat(scenario.getThenSteps()).containsExactly("I should be redirected to dashboard");
    }

    @Test
    @DisplayName("Should parse Gherkin with multiple scenarios")
    void shouldParseMultipleGherkinScenarios() {
        String ac = """
            Scenario: Valid Login
            Given I am on the login page
            When I enter valid credentials
            Then I should be logged in
            
            Scenario: Invalid Login
            Given I am on the login page
            When I enter invalid credentials
            Then I should see error message
            """;

        ParsedAcceptanceCriteria result = parser.parse(ac);

        assertThat(result.getScenarios()).hasSize(2);
        assertThat(result.getScenarios().get(0).getName()).isEqualTo("Valid Login");
        assertThat(result.getScenarios().get(1).getName()).isEqualTo("Invalid Login");
        assertThat(result.getTestCaseCount()).isEqualTo(2);
    }

    @Test
    @DisplayName("Should parse Gherkin with And steps")
    void shouldParseGherkinWithAndSteps() {
        String ac = """
            Scenario: Shopping Cart
            Given I am on the product page
            And I am logged in
            When I add item to cart
            And I proceed to checkout
            Then I should see order summary
            And I should see payment options
            """;

        ParsedAcceptanceCriteria result = parser.parse(ac);

        GherkinScenario scenario = result.getScenarios().get(0);
        assertThat(scenario.getGivenSteps()).hasSize(2);
        assertThat(scenario.getWhenSteps()).hasSize(2);
        assertThat(scenario.getThenSteps()).hasSize(2);
        assertThat(scenario.isWellFormed()).isTrue();
    }

    @Test
    @DisplayName("Should handle case-insensitive Gherkin keywords")
    void shouldHandleCaseInsensitiveGherkin() {
        String ac = """
            SCENARIO: Login Test
            GIVEN I am on login page
            WHEN I enter credentials
            THEN I am logged in
            """;

        ParsedAcceptanceCriteria result = parser.parse(ac);

        assertThat(result.getFormat()).isEqualTo(ACFormat.GHERKIN);
        assertThat(result.getScenarios()).hasSize(1);
    }

    // ==================== Numbered List Tests ====================

    @Test
    @DisplayName("Should parse numbered list format")
    void shouldParseNumberedList() {
        String ac = """
            1. User navigates to login page
            2. User enters username and password
            3. System validates credentials
            4. User is redirected to dashboard
            """;

        ParsedAcceptanceCriteria result = parser.parse(ac);

        assertThat(result.getFormat()).isEqualTo(ACFormat.NUMBERED_LIST);
        assertThat(result.getSteps()).hasSize(4);
        assertThat(result.getSteps().get(0)).isEqualTo("User navigates to login page");
        assertThat(result.getSteps().get(3)).isEqualTo("User is redirected to dashboard");
        assertThat(result.getConfidence()).isGreaterThanOrEqualTo(0.9);
    }

    @ParameterizedTest
    @CsvSource({
            "'1. First step', 'First step'",
            "'2) Second step', 'Second step'",
            "'3: Third step', 'Third step'",
            "'4 - Fourth step', 'Fourth step'"
    })
    @DisplayName("Should parse numbered list with different separators")
    void shouldParseNumberedListWithDifferentSeparators(String input, String expected) {
        ParsedAcceptanceCriteria result = parser.parse(input);

        assertThat(result.getFormat()).isIn(ACFormat.NUMBERED_LIST, ACFormat.UNSTRUCTURED);
        if (result.getFormat() == ACFormat.NUMBERED_LIST) {
            assertThat(result.getSteps()).contains(expected);
        }
    }

    // ==================== Bullet Points Tests ====================

    @Test
    @DisplayName("Should parse bullet points format")
    void shouldParseBulletPoints() {
        String ac = """
            - User can view product details
            - User can add product to cart
            - Cart total updates automatically
            - User can proceed to checkout
            """;

        ParsedAcceptanceCriteria result = parser.parse(ac);

        assertThat(result.getFormat()).isEqualTo(ACFormat.BULLET_POINTS);
        assertThat(result.getSteps()).hasSize(4);
        assertThat(result.getSteps().get(0)).isEqualTo("User can view product details");
    }

    @Test
    @DisplayName("Should parse bullet points with different markers")
    void shouldParseBulletPointsWithDifferentMarkers() {
        String ac = """
            - First point
            * Second point
            • Third point
            + Fourth point
            """;

        ParsedAcceptanceCriteria result = parser.parse(ac);

        assertThat(result.getFormat()).isEqualTo(ACFormat.BULLET_POINTS);
        assertThat(result.getSteps()).hasSize(4);
    }

    // ==================== Unstructured Text Tests ====================

    @Test
    @DisplayName("Should parse unstructured text")
    void shouldParseUnstructuredText() {
        String ac = """
            The system should allow users to login with valid credentials.
            After successful login, users should be redirected to the dashboard.
            Invalid credentials should display an error message.
            """;

        ParsedAcceptanceCriteria result = parser.parse(ac);

        assertThat(result.getFormat()).isEqualTo(ACFormat.UNSTRUCTURED);
        assertThat(result.getSteps()).isNotEmpty();
        assertThat(result.getConfidence()).isLessThanOrEqualTo(0.6);
        assertThat(result.hasWarnings()).isTrue();
    }

    // ==================== Edge Cases ====================

    @Test
    @DisplayName("Should handle empty acceptance criteria")
    void shouldHandleEmptyAC() {
        ParsedAcceptanceCriteria result = parser.parse("");

        assertThat(result.getFormat()).isEqualTo(ACFormat.EMPTY);
        assertThat(result.hasContent()).isFalse();
        assertThat(result.getConfidence()).isEqualTo(1.0);
    }

    @Test
    @DisplayName("Should handle null acceptance criteria")
    void shouldHandleNullAC() {
        ParsedAcceptanceCriteria result = parser.parse(null);

        assertThat(result.getFormat()).isEqualTo(ACFormat.EMPTY);
        assertThat(result.hasContent()).isFalse();
    }

    @Test
    @DisplayName("Should detect mixed format")
    void shouldDetectMixedFormat() {
        String ac = """
            Given I am logged in
            1. Navigate to settings
            - Update profile picture
            When I save changes
            Then I should see success message
            """;

        ParsedAcceptanceCriteria result = parser.parse(ac);

        assertThat(result.getFormat()).isEqualTo(ACFormat.MIXED);
        assertThat(result.hasWarnings()).isTrue();
        assertThat(result.getWarnings()).contains("Mixed format detected - may require manual review");
    }

    @Test
    @DisplayName("Should clean HTML from AC text")
    void shouldCleanHtmlFromAC() {
        String ac = """
            <p>Given I am on the <strong>login</strong> page</p>
            <p>When I enter credentials</p>
            <p>Then I should be logged in</p>
            """;

        ParsedAcceptanceCriteria result = parser.parse(ac);

        assertThat(result.getFormat()).isEqualTo(ACFormat.GHERKIN);
        GherkinScenario scenario = result.getScenarios().get(0);
        assertThat(scenario.getGivenSteps().get(0))
                .doesNotContain("<p>", "<strong>", "</p>", "</strong>");
    }

    // ==================== Gherkin Conversion Tests ====================

    @Test
    @DisplayName("Should generate normalized Gherkin for non-Gherkin formats")
    void shouldGenerateNormalizedGherkin() {
        String ac = """
            1. User opens app
            2. User logs in
            3. User sees dashboard
            """;

        ParsedAcceptanceCriteria result = parser.parse(ac);

        assertThat(result.getNormalizedGherkin()).isNotNull();
        assertThat(result.getNormalizedGherkin()).contains("Given", "When", "Then");
        assertThat(result.getNormalizedGherkin()).contains("Scenario");
    }

    @Test
    @DisplayName("Should set normalized Gherkin for Gherkin format")
    void shouldSetNormalizedGherkinForGherkin() {
        String ac = """
            Scenario: Test
            Given step
            When step
            Then step
            """;

        ParsedAcceptanceCriteria result = parser.parse(ac);

        assertThat(result.getNormalizedGherkin()).isNotNull();
        assertThat(result.getNormalizedGherkin()).contains("Scenario: Test");
    }

    // ==================== Metrics Tests ====================

    @Test
    @DisplayName("Should calculate test case count correctly")
    void shouldCalculateTestCaseCount() {
        // Gherkin with 2 scenarios
        String gherkinAC = """
            Scenario: Test 1
            Given step
            
            Scenario: Test 2
            Given step
            """;

        ParsedAcceptanceCriteria gherkinResult = parser.parse(gherkinAC);
        assertThat(gherkinResult.getTestCaseCount()).isEqualTo(2);

        // Numbered list (1 test case)
        String numberedAC = """
            1. Step one
            2. Step two
            """;

        ParsedAcceptanceCriteria numberedResult = parser.parse(numberedAC);
        assertThat(numberedResult.getTestCaseCount()).isEqualTo(1);

        // Empty (0 test cases)
        ParsedAcceptanceCriteria emptyResult = parser.parse("");
        assertThat(emptyResult.getTestCaseCount()).isEqualTo(0);
    }

    @Test
    @DisplayName("Should calculate total step count correctly")
    void shouldCalculateTotalStepCount() {
        String ac = """
            Scenario: Test 1
            Given step 1
            Given step 2
            When step 3
            Then step 4
            
            Scenario: Test 2
            Given step 5
            When step 6
            Then step 7
            """;

        ParsedAcceptanceCriteria result = parser.parse(ac);

        assertThat(result.getTotalStepCount()).isEqualTo(7);
    }

    // ==================== Summary Tests ====================

    @Test
    @DisplayName("Should generate formatted summary")
    void shouldGenerateFormattedSummary() {
        String ac = """
            Scenario: Test
            Given step
            When step
            Then step
            """;

        ParsedAcceptanceCriteria result = parser.parse(ac);

        String summary = result.getSummary();
        assertThat(summary).contains("GHERKIN");
        assertThat(summary).containsPattern("\\d+% confidence");
        assertThat(summary).contains("scenarios");
        assertThat(summary).contains("steps");
    }

    @Test
    @DisplayName("Should check if ready for AI")
    void shouldCheckIfReadyForAI() {
        // High confidence Gherkin - ready
        String goodAC = """
            Scenario: Test
            Given step
            When step
            Then step
            """;
        ParsedAcceptanceCriteria goodResult = parser.parse(goodAC);
        assertThat(goodResult.isReadyForAI()).isTrue();

        // Empty - not ready
        ParsedAcceptanceCriteria emptyResult = parser.parse("");
        assertThat(emptyResult.isReadyForAI()).isFalse();
    }
}