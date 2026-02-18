package com.company.qa.service.playwright;

import lombok.Builder;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * Result of TestIntent validation.
 */
@Data
@Builder
public class ValidationResult {

    @Builder.Default
    private boolean valid = true;

    @Builder.Default
    private List<String> errors = new ArrayList<>();

    @Builder.Default
    private List<String> warnings = new ArrayList<>();

    public static ValidationResult success() {
        return ValidationResult.builder().valid(true).build();
    }

    public static ValidationResult failure(String error) {
        List<String> errors = new ArrayList<>();
        errors.add(error);
        return ValidationResult.builder().valid(false).errors(errors).build();
    }

    public void addError(String error) {
        this.valid = false;
        this.errors.add(error);
    }

    public void addWarning(String warning) {
        this.warnings.add(warning);
    }

    public boolean hasWarnings() {
        return !warnings.isEmpty();
    }
}