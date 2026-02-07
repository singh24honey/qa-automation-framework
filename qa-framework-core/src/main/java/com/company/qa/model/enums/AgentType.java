package com.company.qa.model.enums;

/**
 * Types of autonomous agents in the QA Framework.
 *
 * Each agent type has a specific purpose and uses
 * different combinations of existing services.
 */
public enum AgentType {

    /**
     * Generates Playwright tests from JIRA stories.
     * Uses: JiraService, PlaywrightContextBuilder, AIGateway,
     *       PlaywrightExecutor, GitService
     */
    PLAYWRIGHT_TEST_GENERATOR,

    /**
     * Detects and fixes flaky tests.
     * Uses: AnalyticsService, PlaywrightExecutor, AIGateway, GitService
     */
    FLAKY_TEST_FIXER,

    /**
     * Self-heals broken tests by finding alternative locators.
     * Uses: ElementRegistry, PlaywrightExecutor, AIGateway, GitService
     */
    SELF_HEALING_TEST_FIXER,

    /**
     * Analyzes test failures and suggests improvements.
     * Uses: AnalyticsService, AIGateway, QualityAssessment
     */
    TEST_FAILURE_ANALYZER,

    /**
     * Monitors test quality trends and generates reports.
     * Uses: AnalyticsService, AIGateway, ExecutiveAnalytics
     */
    QUALITY_MONITOR
}