package com.company.qa.model.enums;

/**
 * Types of AI tasks with descriptions.
 */
public enum AITaskType {
    TEST_GENERATION("Generate Test Code"),
    FAILURE_ANALYSIS("Analyze Test Failure"),
    FIX_SUGGESTION("Suggest Test Fix"),
    CODE_REVIEW("Review Test Code"),
    TEST_OPTIMIZATION("Optimize Existing Tests"),
    GENERAL("General AI Task"),
    DOCUMENTATION("Generate Documentation");

    private final String description;

    AITaskType(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}