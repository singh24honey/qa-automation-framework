package com.company.qa.model.enums;

/**
 * Root cause categories for flaky test failures.
 *
 * Used by FlakyTestAgent to categorize failures and suggest appropriate fixes.
 *
 * @author QA Framework
 * @since Week 16 Day 1
 */
public enum FlakyRootCause {

    /**
     * Timing/race condition issue.
     * Fix: Add explicit waits, increase timeouts, use stable wait conditions.
     */
    TIMING_ISSUE("Timing/Race Condition"),

    /**
     * Test data dependency issue.
     * Fix: Add proper test data setup/teardown, use unique test data.
     */
    DATA_DEPENDENCY("Data Dependency"),

    /**
     * Environment/external service dependency.
     * Fix: Add environment checks, mock external services, add retries.
     */
    ENVIRONMENT_DEPENDENCY("Environment Dependency"),

    /**
     * Element locator brittleness.
     * Fix: Use more stable locator strategy, add fallback locators.
     */
    LOCATOR_BRITTLENESS("Locator Brittleness"),

    /**
     * Unknown or multiple root causes.
     * Fix: Manual investigation required.
     */
    UNKNOWN("Unknown");

    private final String displayName;

    FlakyRootCause(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}