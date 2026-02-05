package com.company.qa.model.enums;

/**
 * Types of Git operations performed by the framework
 */
public enum GitOperationType {
    BRANCH_CREATE("Branch Creation"),
    COMMIT("File Commit"),
    PR_CREATE("Pull Request Creation");

    private final String displayName;

    GitOperationType(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}