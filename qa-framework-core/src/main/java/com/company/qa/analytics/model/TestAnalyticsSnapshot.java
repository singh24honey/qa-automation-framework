package com.company.qa.analytics.model;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class TestAnalyticsSnapshot {

    private final long totalRuns;
    private final long passCount;
    private final long failCount;

    private final long uniqueFailureCount;
    private final double flakinessScore;
    private final double confidenceScore;
}