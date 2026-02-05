package com.company.qa.analytics.model;

import lombok.Value;

import java.util.Objects;

@Value
public class FailureFingerprint {

    String errorType;
    String normalizedMessage;
    String failingStep;

    public static FailureFingerprint from(
            String errorMessage,
            String failingStep) {

        String type = errorMessage == null
                ? "UNKNOWN"
                : errorMessage.split(":")[0];

        String normalized = errorMessage == null
                ? ""
                : errorMessage.replaceAll("\\d+", "<n>");

        return new FailureFingerprint(
                type,
                normalized,
                failingStep
        );
    }

    @Override
    public int hashCode() {
        return Objects.hash(errorType, normalizedMessage, failingStep);
    }
}