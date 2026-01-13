package com.company.qa.model.enums;

public enum FailureType {
    // Transient failures (should retry)
    TIMEOUT,
    NETWORK_ERROR,
    ELEMENT_NOT_FOUND,
    STALE_ELEMENT,

    // Permanent failures (should not retry)
    ASSERTION_FAILED,
    ELEMENT_NOT_INTERACTABLE,
    INVALID_SELECTOR,
    APPLICATION_ERROR,

    // Infrastructure failures
    WEBDRIVER_ERROR,
    SELENIUM_GRID_ERROR,

    // Unknown
    UNKNOWN;

    public boolean isTransient() {
        return this == TIMEOUT ||
                this == NETWORK_ERROR ||
                this == ELEMENT_NOT_FOUND ||
                this == STALE_ELEMENT;
    }

    public boolean shouldRetry() {
        return isTransient();
    }
}