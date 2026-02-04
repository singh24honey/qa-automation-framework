package com.company.qa.model.enums;

/**
 * Git authentication types
 */
public enum AuthType {
    TOKEN("Personal Access Token"),
    SSH_KEY("SSH Key");

    private final String displayName;

    AuthType(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}