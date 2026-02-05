package com.company.qa.model.enums;

/**
 * Budget alert severity levels.
 */
public enum BudgetAlertType {
    WARNING("Warning - approaching budget limit"),
    CRITICAL("Critical - near budget limit"),
    EXCEEDED("Budget exceeded");

    private final String description;

    BudgetAlertType(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}