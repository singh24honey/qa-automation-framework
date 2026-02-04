package com.company.qa.model.enums;

/**
 * Status of Pull Requests
 */
public enum PullRequestStatus {
    OPEN("Open"),
    DRAFT("Draft"),
    MERGED("Merged"),
    CLOSED("Closed");

    private final String displayName;

    PullRequestStatus(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}