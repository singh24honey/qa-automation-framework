package com.company.qa.service.analytics;

import com.company.qa.model.dto.*;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface AnalyticsService {

    /**
     * Detect flaky tests in the given date range
     * @param startDate Start date for analysis
     * @param endDate End date for analysis
     * @return List of flaky tests sorted by flakiness score
     */
    List<FlakyTestDTO> detectFlakyTests(Instant startDate, Instant endDate);

    /**
     * Analyze a single test for flakiness
     * @param testId Test to analyze
     * @param startDate Start date for analysis
     * @param endDate End date for analysis
     * @return Flaky test analysis or null if not found
     */
    FlakyTestDTO analyzeSingleTest(UUID testId, Instant startDate, Instant endDate);

    /**
     * Analyze performance metrics for all tests
     * @param startDate Start date for analysis
     * @param endDate End date for analysis
     * @return List of performance metrics sorted by average duration
     */
    List<PerformanceMetricsDTO> analyzePerformance(Instant startDate, Instant endDate);

    /**
     * Get performance metrics for a single test
     * @param testId Test to analyze
     * @param startDate Start date for analysis
     * @param endDate End date for analysis
     * @return Performance metrics or null if not found
     */
    PerformanceMetricsDTO getTestPerformance(UUID testId, Instant startDate, Instant endDate);

    /**
     * Analyze failure patterns across all tests
     * @param startDate Start date for analysis
     * @param endDate End date for analysis
     * @return List of failure patterns sorted by occurrence
     */
    List<FailurePatternDTO> analyzeFailurePatterns(Instant startDate, Instant endDate);

    /**
     * Calculate overall test suite health
     * @param startDate Start date for analysis
     * @param endDate End date for analysis
     * @return Suite health metrics
     */
    TestSuiteHealthDTO calculateSuiteHealth(Instant startDate, Instant endDate);

    /**
     * Get complete analytics dashboard
     * @param startDate Start date for analysis
     * @param endDate End date for analysis
     * @return Complete analytics dashboard
     */
    AnalyticsDashboardDTO getAnalyticsDashboard(Instant startDate, Instant endDate);

}