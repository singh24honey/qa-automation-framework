package com.company.qa.model.enums;

/**
 * Supported test actions.
 *
 * @author QA Framework
 * @since Week 11 Day 3
 */
public enum ActionType {

    NAVIGATE,    // Navigate to URL
    CLICK,       // Click element
    TYPE,        // Type text into input
    CLEAR,       // Clear input field
    SELECT,      // Select dropdown option
    CHECK,       // Check checkbox/radio
    UNCHECK,     // Uncheck checkbox
    WAIT,        // Wait for element
    VERIFY_TEXT, // Verify text present
    VERIFY_VISIBLE; // Verify element visible

    /**
     * Parse action type from string.
     *
     * @param value action string (case-insensitive)
     * @return ActionType enum
     */
    public static ActionType fromString(String value) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException("Action type cannot be null or empty");
        }

        try {
            return valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(
                    "Unsupported action type: " + value +
                            ". Supported: navigate, click, type, clear, select, check, uncheck, wait, verify_text, verify_visible"
            );
        }
    }

    /**
     * Check if action requires a locator.
     *
     * @return true if locator needed
     */
    public boolean requiresLocator() {
        return this != NAVIGATE;
    }

    /**
     * Check if action requires input data.
     *
     * @return true if input data needed
     */
    public boolean requiresInputData() {
        return this == TYPE || this == SELECT || this == VERIFY_TEXT;
    }
}