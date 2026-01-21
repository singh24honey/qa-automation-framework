package com.company.qa.model.dto;

import com.company.qa.model.enums.ViolationType;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Result of response validation.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ValidationResult {

    /**
     * Is the response valid?
     */
    private boolean valid;

    /**
     * Should the response be blocked from use?
     */
    private boolean shouldBlock;

    /**
     * List of violations found
     */
    private List<Violation> violations = new ArrayList<>();

    /**
     * List of warnings (non-blocking issues)
     */
    private List<String> warnings = new ArrayList<>();

    /**
     * Timestamp of validation
     */
    private Instant timestamp;

    /**
     * Individual violation details
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Violation {
        private ViolationType type;
        private String message;
        private String detectedPattern;
    }

    /**
     * Builder for ValidationResult
     */
    public static ValidationResultBuilder builder() {
        return new ValidationResultBuilder();
    }

    /**
     * Custom builder class
     */
    public static class ValidationResultBuilder {
        private boolean valid = true;
        private boolean shouldBlock = false;
        private List<Violation> violations = new ArrayList<>();
        private List<String> warnings = new ArrayList<>();
        private Instant timestamp = Instant.now();

        public ValidationResultBuilder valid(boolean valid) {
            this.valid = valid;
            return this;
        }

        public ValidationResultBuilder shouldBlock(boolean shouldBlock) {
            this.shouldBlock = shouldBlock;
            return this;
        }

        public ValidationResultBuilder addViolation(ViolationType type, String message) {
            this.violations.add(new Violation(type, message, null));
            this.valid = false;
            return this;
        }

        public ValidationResultBuilder addViolation(ViolationType type, String message, String pattern) {
            this.violations.add(new Violation(type, message, pattern));
            this.valid = false;
            return this;
        }

        public ValidationResultBuilder addWarning(String warning) {
            this.warnings.add(warning);
            return this;
        }

        public ValidationResultBuilder timestamp(Instant timestamp) {
            this.timestamp = timestamp;
            return this;
        }

        public ValidationResult build() {
            return new ValidationResult(valid, shouldBlock, violations, warnings, timestamp);
        }
    }
}