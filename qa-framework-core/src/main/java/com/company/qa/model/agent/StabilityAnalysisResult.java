package com.company.qa.model.agent;

import com.company.qa.model.enums.FlakyRootCause;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Result of running a test multiple times to analyze stability.
 *
 * @author QA Framework
 * @since Week 16 Day 1
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StabilityAnalysisResult {

    /**
     * Test ID being analyzed.
     */
    private String testId;

    /**
     * Test name.
     */
    private String testName;

    /**
     * Total number of runs performed.
     */
    private int totalRuns;

    /**
     * Number of successful runs.
     */
    private int passedRuns;

    /**
     * Number of failed runs.
     */
    private int failedRuns;

    /**
     * Pass/fail pattern as string (e.g., "PFPPF" = Pass, Fail, Pass, Pass, Fail).
     */
    private String pattern;

    /**
     * Is this test flaky based on the pattern?
     */
    private boolean isFlaky;

    /**
     * Flakiness score (0.0 to 1.0).
     */
    private double flakinessScore;

    /**
     * Error messages from failed runs.
     */
    private List<String> errorMessages;

    /**
     * Execution IDs for each run.
     */
    private List<String> executionIds;

    /**
     * Root cause category (set by AnalyzeFailurePatternTool).
     */
    private FlakyRootCause rootCause;

    /**
     * AI-generated explanation of the root cause.
     */
    private String rootCauseExplanation;

    /**
     * Recommended fix strategy.
     */
    private String recommendedFix;
}