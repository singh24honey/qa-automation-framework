package com.company.qa.model.enums;

/**
 * Locator strategies supported by the framework.
 *
 * Playwright-specific strategies:
 * - ROLE: Accessibility role-based (recommended)
 * - LABEL: Form label text
 * - TEXT: Visible text content
 * - TESTID: data-testid attribute
 *
 * Traditional strategies:
 * - CSS: CSS selectors
 * - XPATH: XPath expressions
 * - ID: Element ID attribute
 * - NAME: Element name attribute
 * - CLASS: Element class name
 *
 * @author QA Framework
 * @since Week 11 Day 3
 */
public enum LocatorStrategy {

    /**
     * Accessibility role-based locator (Playwright recommended).
     * Example: role=button[name="Submit"]
     *
     * Benefits:
     * - Most resilient to UI changes
     * - Enforces accessibility
     * - Semantic meaning
     */
    ROLE("role"),

    /**
     * Form label text.
     * Example: label=Email Address
     *
     * Benefits:
     * - User-friendly
     * - Works for form fields
     */
    LABEL("label"),

    /**
     * Visible text content.
     * Example: text=Click here
     *
     * Benefits:
     * - Simple
     * - What user sees
     */
    TEXT("text"),

    /**
     * Test ID attribute (data-testid).
     * Example: testid=submit-button
     *
     * Benefits:
     * - Stable
     * - Designed for testing
     */
    TESTID("testid"),

    /**
     * CSS selector.
     * Example: css=#submitBtn
     *
     * Benefits:
     * - Powerful
     * - Widely understood
     */
    CSS("css"),

    /**
     * XPath expression.
     * Example: xpath=//button[@id='submit']
     *
     * Benefits:
     * - Very powerful
     * - Can traverse DOM
     */
    XPATH("xpath"),

    /**
     * Element ID attribute.
     * Example: id=submitBtn
     *
     * Benefits:
     * - Fast
     * - Unique
     */
    ID("id"),

    /**
     * Element name attribute.
     * Example: name=username
     *
     * Benefits:
     * - Common in forms
     */
    NAME("name"),

    /**
     * Element class name.
     * Example: class=btn-primary
     *
     * Benefits:
     * - Quick for styling-based selection
     */
    CLASS("class");

    private final String strategy;

    LocatorStrategy(String strategy) {
        this.strategy = strategy;
    }

    public String getStrategy() {
        return strategy;
    }

    /**
     * Parse locator strategy from string.
     * Supports both enum name and strategy value.
     *
     * @param value strategy string (case-insensitive)
     * @return LocatorStrategy enum
     * @throws IllegalArgumentException if invalid
     */
    public static LocatorStrategy fromString(String value) {
        if (value == null || value.trim().isEmpty()) {
            return CSS; // Default fallback
        }

        String normalized = value.trim().toLowerCase();

        // Try matching by strategy value first
        for (LocatorStrategy strategy : values()) {
            if (strategy.strategy.equalsIgnoreCase(normalized)) {
                return strategy;
            }
        }

        // Try matching by enum name
        try {
            return valueOf(normalized.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(
                    "Unsupported locator strategy: " + value +
                            ". Supported: role, label, text, testid, css, xpath, id, name, class"
            );
        }
    }

    /**
     * Check if this is a Playwright-native strategy.
     *
     * @return true if Playwright-specific
     */
    public boolean isPlaywrightNative() {
        return this == ROLE || this == LABEL || this == TEXT || this == TESTID;
    }

    /**
     * Check if this is a traditional web locator.
     *
     * @return true if traditional (CSS, XPath, ID, etc.)
     */
    public boolean isTraditional() {
        return !isPlaywrightNative();
    }
}