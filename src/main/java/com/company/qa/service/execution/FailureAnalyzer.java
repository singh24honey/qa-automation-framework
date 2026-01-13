package com.company.qa.service.execution;

import com.company.qa.model.dto.FailureAnalysis;
import com.company.qa.model.enums.FailureType;
import lombok.extern.slf4j.Slf4j;
import org.openqa.selenium.*;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class FailureAnalyzer {

    public FailureAnalysis analyze(Exception exception, Integer stepNumber) {
        log.debug("Analyzing failure: {}", exception.getClass().getSimpleName());

        FailureType failureType = classifyFailure(exception);
        String suggestion = generateSuggestion(failureType, exception);

        return FailureAnalysis.builder()
                .failureType(failureType)
                .errorMessage(exception.getMessage())
                .suggestion(suggestion)
                .shouldRetry(failureType.shouldRetry())
                .stepNumber(stepNumber)
                .stackTrace(getStackTraceAsString(exception))
                .build();
    }

    private FailureType classifyFailure(Exception exception) {
        // Selenium-specific exceptions
        if (exception instanceof TimeoutException) {
            return FailureType.TIMEOUT;
        }

        if (exception instanceof NoSuchElementException) {
            return FailureType.ELEMENT_NOT_FOUND;
        }

        if (exception instanceof StaleElementReferenceException) {
            return FailureType.STALE_ELEMENT;
        }

        if (exception instanceof ElementNotInteractableException) {
            return FailureType.ELEMENT_NOT_INTERACTABLE;
        }

        if (exception instanceof InvalidSelectorException) {
            return FailureType.INVALID_SELECTOR;
        }

        if (exception instanceof WebDriverException) {
            if (exception.getMessage() != null &&
                    exception.getMessage().contains("net::ERR")) {
                return FailureType.NETWORK_ERROR;
            }
            return FailureType.WEBDRIVER_ERROR;
        }

        // Assertion failures
        /*if (exception instanceof AssertionError) {
            return FailureType.ASSERTION_FAILED;
        }*/

        // Generic errors
        if (exception.getMessage() != null) {
            String msg = exception.getMessage().toLowerCase();

            if (msg.contains("timeout") || msg.contains("timed out")) {
                return FailureType.TIMEOUT;
            }

            if (msg.contains("network") || msg.contains("connection")) {
                return FailureType.NETWORK_ERROR;
            }

            if (msg.contains("grid") || msg.contains("session")) {
                return FailureType.SELENIUM_GRID_ERROR;
            }
        }

        return FailureType.UNKNOWN;
    }

    private String generateSuggestion(FailureType failureType, Exception exception) {
        return switch (failureType) {
            case TIMEOUT ->
                    "Element took too long to appear. Consider increasing timeout or checking if element exists.";

            case NETWORK_ERROR ->
                    "Network connection issue. Check internet connectivity or target URL.";

            case ELEMENT_NOT_FOUND ->
                    "Element not found. Verify the locator is correct and element is present in DOM.";

            case STALE_ELEMENT ->
                    "Element reference became stale. Page may have refreshed. Will retry automatically.";

            case ASSERTION_FAILED ->
                    "Assertion failed: " + exception.getMessage() + ". This indicates a genuine test failure.";

            case ELEMENT_NOT_INTERACTABLE ->
                    "Element is not interactable. It may be hidden, covered, or disabled.";

            case INVALID_SELECTOR ->
                    "Invalid selector syntax. Check your locator format.";

            case WEBDRIVER_ERROR ->
                    "WebDriver error occurred. Check browser and driver compatibility.";

            case SELENIUM_GRID_ERROR ->
                    "Selenium Grid connection issue. Verify Grid is running and accessible.";

            case APPLICATION_ERROR ->
                    "Application error occurred during test execution.";

            case UNKNOWN ->
                    "Unknown error occurred. Check logs for details.";
        };
    }

    private String getStackTraceAsString(Exception exception) {
        if (exception.getStackTrace().length == 0) {
            return exception.toString();
        }

        StringBuilder sb = new StringBuilder();
        sb.append(exception.toString()).append("\n");

        // Include first 5 stack trace elements
        int limit = Math.min(5, exception.getStackTrace().length);
        for (int i = 0; i < limit; i++) {
            sb.append("\tat ").append(exception.getStackTrace()[i]).append("\n");
        }

        return sb.toString();
    }

    public boolean isTransientFailure(Exception exception) {
        FailureType type = classifyFailure(exception);
        return type.isTransient();
    }
}