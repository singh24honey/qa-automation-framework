package com.company.qa.model.enums;

public enum TestStability {
    STABLE,        // Pass rate >= 95%
    MOSTLY_STABLE, // Pass rate 80-94%
    FLAKY,         // Pass rate 50-79%
    VERY_FLAKY,    // Pass rate 20-49%
    UNSTABLE, UNRELIABLE     // Pass rate < 20%
}