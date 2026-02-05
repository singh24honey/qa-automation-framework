package com.company.qa.model.enums;

/**
 * Supported Git repository types
 */
public enum RepositoryType {
    GITHUB("GitHub", "https://api.github.com"),
    GITLAB("GitLab", "https://gitlab.com/api/v4"),
    BITBUCKET("Bitbucket", "https://api.bitbucket.org/2.0");

    private final String displayName;
    private final String defaultApiUrl;

    RepositoryType(String displayName, String defaultApiUrl) {
        this.displayName = displayName;
        this.defaultApiUrl = defaultApiUrl;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getDefaultApiUrl() {
        return defaultApiUrl;
    }
}