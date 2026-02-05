package com.company.qa.model.enums;

public enum NotificationEvent {
    TEST_STARTED,
    TEST_COMPLETED,
    TEST_FAILED,
    TEST_RECOVERED,  // Success after previous failure
    TEST_CANCELLED,
    TEST_TIMEOUT,
    SYSTEM_ERROR,
    SCHEDULED_TEST_START
}