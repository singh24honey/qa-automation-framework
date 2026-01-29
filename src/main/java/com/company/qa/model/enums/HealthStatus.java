package com.company.qa.model.enums;

/**
 * Health status for JIRA API connectivity.
 */
public enum HealthStatus {
    /**
     * JIRA API is fully operational
     */
    UP,

    /**
     * JIRA API is experiencing issues but may still work
     */
    DEGRADED,

    /**
     * JIRA API is not accessible
     */
    DOWN
}