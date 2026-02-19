package com.company.qa.service.playwright;

import com.company.qa.model.intent.TestIntent;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Parses AI JSON response into a validated {@link TestIntent} object.
 *
 * Responsibilities:
 * 1. Extract the "scenarios" JSON block from the AI response (which may contain
 *    extra fields like "testName", "jiraStory", etc. that the AI appended)
 * 2. Deserialize into {@link TestIntent} via Jackson
 * 3. Run {@link TestIntentValidator} post-parse
 * 4. Return a typed {@link ParseResult} — never throws, always returns structured outcome
 *
 * This is the entry point from {@link com.company.qa.service.ai.AITestGenerationService}
 * when format detection identifies an INTENT_V1 response (contains "scenarios" key).
 *
 * @author QA Framework
 * @since Zero-Hallucination Pipeline
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TestIntentParser {

    private final ObjectMapper objectMapper;
    private final TestIntentValidator validator;

    // ========== Public API ==========

    /**
     * Parse an AI JSON response string into a validated TestIntent.
     *
     * The response JSON may be:
     *   1. A bare TestIntent object: {"testClassName": "...", "scenarios": [...]}
     *   2. Wrapped in an outer object the AI invented: {"testCode": {"testClassName": "...", "scenarios": [...]}}
     *   3. The "scenarios" key at top level, "testClassName" at top level
     *
     * The parser handles all three cases by looking for the "scenarios" key,
     * finding the node that contains it (root or nested), and deserializing
     * that node as TestIntent.
     *
     * @param rawJson the raw JSON string from the AI response
     * @return ParseResult with either a validated TestIntent or structured errors
     */
    public ParseResult parse(String rawJson) {
        if (rawJson == null || rawJson.isBlank()) {
            return ParseResult.failure("AI response is empty — cannot parse TestIntent");
        }

        log.debug("Parsing TestIntent from AI response ({} chars)", rawJson.length());

        // Step 1: Find the JSON node that contains "scenarios"
        JsonNode intentNode;
        try {
            intentNode = findIntentNode(rawJson);
        } catch (JsonProcessingException e) {
            log.warn("Failed to parse AI response as JSON: {}", e.getMessage());
            return ParseResult.failure("AI response is not valid JSON: " + e.getMessage());
        }

        if (intentNode == null) {
            log.warn("AI response does not contain 'scenarios' key — not an intent response");
            return ParseResult.notIntent();
        }

        // Step 2: Deserialize to TestIntent
        TestIntent intent;
        try {
            intent = objectMapper.treeToValue(intentNode, TestIntent.class);
        } catch (JsonProcessingException e) {
            log.warn("Failed to deserialize TestIntent: {}", e.getMessage());
            return ParseResult.failure("Failed to deserialize TestIntent: " + e.getMessage());
        }

        log.debug("Deserialized TestIntent: className={}, scenarios={}",
                intent.getTestClassName(), intent.getScenarioCount());

        // Step 3: Validate the deserialized intent
        ValidationResult validation = validator.validate(intent);

        if (!validation.isValid()) {
            log.warn("TestIntent validation failed: {} errors", validation.getErrors().size());
            return ParseResult.validationFailure(intent, validation);
        }

        if (validation.hasWarnings()) {
            log.debug("TestIntent parsed with {} warnings: {}", validation.getWarnings().size(), validation.getWarnings());
        }

        log.info("TestIntent parsed successfully: className={}, scenarios={}, totalSteps={}",
                intent.getTestClassName(), intent.getScenarioCount(), intent.getTotalStepCount());

        return ParseResult.success(intent, validation);
    }

    /**
     * Quick check: does this JSON string look like a TestIntent response?
     * Used by AITestGenerationService for format detection before full parsing.
     *
     * Returns true if the JSON contains a "scenarios" key at any level.
     * Does NOT validate the intent — use {@link #parse(String)} for full validation.
     *
     * @param rawJson JSON string to check
     * @return true if this looks like a TestIntent response
     */
    public boolean isIntentFormat(String rawJson) {
        if (rawJson == null || rawJson.isBlank()) return false;
        // Fast path: string contains literal "scenarios" before full JSON parse
        if (!rawJson.contains("\"scenarios\"")) return false;

        try {
            JsonNode root = objectMapper.readTree(rawJson);
            return findIntentNode(root) != null;
        } catch (JsonProcessingException e) {
            return false;
        }
    }

    // ========== Private Helpers ==========

    /**
     * Find the JSON node that represents a TestIntent.
     * Looks for the node containing both "testClassName" and "scenarios".
     *
     * Search order:
     * 1. Root node has "scenarios" → use root
     * 2. Root has a child with "scenarios" → use first such child
     *
     * @param rawJson raw JSON string
     * @return JsonNode containing the intent, or null if not found
     */
    private JsonNode findIntentNode(String rawJson) throws JsonProcessingException {
        JsonNode root = objectMapper.readTree(rawJson);
        return findIntentNode(root);
    }

    private JsonNode findIntentNode(JsonNode root) {
        // Case 1: root is the intent
        if (root.has("scenarios")) {
            return root;
        }

        // Case 2: root has a child that is the intent (AI wrapped it)
        if (root.isObject()) {
            for (JsonNode child : root) {
                if (child.isObject() && child.has("scenarios")) {
                    log.debug("Found intent in nested node");
                    return child;
                }
            }
        }

        return null;
    }

    // ========== Result Type ==========

    /**
     * Typed result of a parse attempt.
     * Avoids exception-driven flow for expected failure cases (validation errors, wrong format).
     */
    public static class ParseResult {

        public enum Status {
            /** Successfully parsed and validated. */
            SUCCESS,
            /** JSON is valid but validation failed — use errors for feedback. */
            VALIDATION_FAILURE,
            /** JSON does not contain "scenarios" — not an intent response (use legacy path). */
            NOT_INTENT,
            /** JSON is malformed or deserialization failed. */
            PARSE_ERROR
        }

        private final Status status;
        private final TestIntent intent;
        private final ValidationResult validation;
        private final String errorMessage;

        private ParseResult(Status status, TestIntent intent, ValidationResult validation, String errorMessage) {
            this.status = status;
            this.intent = intent;
            this.validation = validation;
            this.errorMessage = errorMessage;
        }

        // ---- Factory methods ----

        public static ParseResult success(TestIntent intent, ValidationResult validation) {
            return new ParseResult(Status.SUCCESS, intent, validation, null);
        }

        public static ParseResult validationFailure(TestIntent intent, ValidationResult validation) {
            return new ParseResult(Status.VALIDATION_FAILURE, intent, validation, null);
        }

        public static ParseResult notIntent() {
            return new ParseResult(Status.NOT_INTENT, null, null, null);
        }

        public static ParseResult failure(String message) {
            return new ParseResult(Status.PARSE_ERROR, null, null, message);
        }

        // ---- Accessors ----

        public boolean isSuccess() { return status == Status.SUCCESS; }
        public boolean isNotIntent() { return status == Status.NOT_INTENT; }
        public boolean isValidationFailure() { return status == Status.VALIDATION_FAILURE; }
        public boolean isParseError() { return status == Status.PARSE_ERROR; }

        public Status getStatus() { return status; }
        public TestIntent getIntent() { return intent; }
        public ValidationResult getValidation() { return validation; }
        public String getErrorMessage() { return errorMessage; }

        /**
         * Combined error summary for logging / failure storage.
         */
        public String getSummary() {
            return switch (status) {
                case SUCCESS -> "OK (" + (intent != null ? intent.getScenarioCount() + " scenarios" : "") + ")";
                case NOT_INTENT -> "Not a TestIntent response — use legacy path";
                case PARSE_ERROR -> "Parse error: " + errorMessage;
                case VALIDATION_FAILURE -> "Validation failed: " +
                        (validation != null ? String.join("; ", validation.getErrors()) : "unknown");
            };
        }
    }
}