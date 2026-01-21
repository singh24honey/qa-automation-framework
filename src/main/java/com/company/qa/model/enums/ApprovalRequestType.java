package com.company.qa.model.enums;

/**
 * Types of approval requests.
 */
public enum ApprovalRequestType {
    /**
     * AI-generated new test code
     */
    TEST_GENERATION,

    /**
     * AI-modified existing test code
     */
    TEST_MODIFICATION,

    /**
     * Request to delete test
     */
    TEST_DELETION
}