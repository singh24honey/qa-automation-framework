package com.company.qa.model.enums;

/**
 * Budget scope types.
 */
public enum BudgetScopeType {
    USER("Per User"),
    TEAM("Per Team"),
    GLOBAL("Global/Organization");

    private final String description;

    BudgetScopeType(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}