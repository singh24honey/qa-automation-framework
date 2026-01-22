package com.company.qa.model.enums;

/**
 * AI Provider types with pricing information.
 */
public enum AIProvider {
    BEDROCK("AWS Bedrock", true),
    OLLAMA("Ollama (Local)", false),
    MOCK("Mock AI (Testing)", false);

    private final String displayName;
    private final boolean hasCost;

    AIProvider(String displayName, boolean hasCost) {
        this.displayName = displayName;
        this.hasCost = hasCost;
    }

    public String getDisplayName() {
        return displayName;
    }

    public boolean hasCost() {
        return hasCost;
    }

    /**
     * Check if this provider has associated costs.
     */
    public boolean isFree() {
        return !hasCost;
    }
}