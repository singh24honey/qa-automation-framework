package com.company.qa.exception;

/**
 * Week 9 Day 2: JIRA Integration Exception
 *
 * Custom exception for JIRA integration errors.
 *
 * Used for:
 * - JIRA API communication failures
 * - JSON parsing errors
 * - Configuration errors
 * - Authentication failures
 *
 * Extends RuntimeException for unchecked exception handling.
 */
public class JiraIntegrationException extends RuntimeException {

    /**
     * Construct with message
     */
    public JiraIntegrationException(String message) {
        super(message);
    }

    /**
     * Construct with message and cause
     */
    public JiraIntegrationException(String message, Throwable cause) {
        super(message, cause);
    }

    /**
     * Construct with cause
     */
    public JiraIntegrationException(Throwable cause) {
        super(cause);
    }
}
