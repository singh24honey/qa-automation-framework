package com.company.qa.model.enums;

/**
 * User roles with different rate limits and permissions.
 */
public enum UserRole {
    /**
     * Developer - Limited access for development/testing
     * Rate Limit: 50 requests/hour, 100K tokens/month
     */
    DEVELOPER,

    /**
     * QA Engineer - Standard access for test automation
     * Rate Limit: 200 requests/hour, 500K tokens/month
     */
    QA_ENGINEER,

    /**
     * QA Lead - Higher limits for test management
     * Rate Limit: 500 requests/hour, 1M tokens/month
     */
    QA_LEAD,

    /**
     * Admin - Highest limits for system administration
     * Rate Limit: 1000 requests/hour, 2M tokens/month
     */
    ADMIN
}