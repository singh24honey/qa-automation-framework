package com.company.qa.model.enums;

/**
 * Status for schedule execution history.
 * Note: This is separate from TestStatus which is used for test executions.
 */
public enum ScheduleStatus {
    SCHEDULED,    // Waiting to run
    RUNNING,      // Currently executing
    COMPLETED,    // Finished successfully
    FAILED,       // Execution failed
    SKIPPED,      // Skipped (e.g., previous run still running)
    CANCELLED,    // Manually cancelled
    ERROR         // System error (couldn't start execution)
}