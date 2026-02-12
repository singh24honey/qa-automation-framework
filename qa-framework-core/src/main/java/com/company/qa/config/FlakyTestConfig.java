package com.company.qa.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration for FlakyTestAgent detection and analysis.
 *
 * @author QA Framework
 * @since Week 16 Day 1
 */
@Configuration
@ConfigurationProperties(prefix = "agent.flaky-test")
@Getter
@Setter
public class FlakyTestConfig {

    /**
     * Minimum flakiness score to consider a test as flaky.
     * Range: 0.0 to 1.0
     * Default: 0.2 (20% failure rate with varying errors)
     */
    private double flakinessThreshold = 0.2;

    /**
     * Number of times to run a test to confirm flakiness.
     * Default: 5
     */
    private int stabilityCheckRuns = 5;

    /**
     * Number of times to run a test after applying fix to verify stability.
     * Default: 5
     */
    private int verificationRuns = 5;

    /**
     * Maximum number of fix attempts before giving up.
     * Default: 3
     */
    private int maxFixAttempts = 3;

    /**
     * Minimum number of historical runs required to calculate flakiness.
     * Default: 10
     */
    private int minHistoricalRuns = 10;

    /**
     * Enable/disable automatic flaky test detection.
     * Default: true
     */
    private boolean autoDetectionEnabled = true;

    /**
     * Timeout in seconds for each test run during stability analysis.
     * Default: 300 (5 minutes)
     */
    private int testExecutionTimeout = 300;
}