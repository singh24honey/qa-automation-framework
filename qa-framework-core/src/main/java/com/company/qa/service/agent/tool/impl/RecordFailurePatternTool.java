package com.company.qa.service.agent.tool.impl;

import com.company.qa.model.agent.StabilityAnalysisResult;
import com.company.qa.model.entity.TestFailurePattern;
import com.company.qa.model.enums.AgentActionType;
import com.company.qa.repository.TestFailurePatternRepository;
import com.company.qa.service.agent.tool.AgentTool;
import com.company.qa.service.agent.tool.AgentToolRegistry;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Agent tool to record failure patterns in the database.
 *
 * Input parameters:
 * - stabilityResult: StabilityAnalysisResult JSON with root cause analysis
 *
 * Output:
 * - success: true/false
 * - patternId: ID of saved pattern
 * - error: error message if failed
 *
 * @author QA Framework
 * @since Week 16 Day 1
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RecordFailurePatternTool implements AgentTool {

    private final TestFailurePatternRepository failurePatternRepository;
    private final ObjectMapper objectMapper;
    private final AgentToolRegistry toolRegistry;

    @PostConstruct
    public void register() {
        toolRegistry.registerTool(this);
    }

    @Override
    public AgentActionType getActionType() {
        return AgentActionType.WRITE_FILE;
    }

    @Override
    public String getName() {
        return "Failure Pattern Recorder";
    }

    @Override
    public String getDescription() {
        return "Records the analyzed failure pattern in the database for tracking and fix generation. " +
                "Updates existing pattern if already recorded.";
    }

    @Override
    public Map<String, Object> execute(Map<String, Object> parameters) {
        log.info("💾 Recording failure pattern: {}", parameters);

        try {
            // Extract parameters
            String stabilityResultJson = (String) parameters.get("stabilityResult");
            StabilityAnalysisResult result = objectMapper.readValue(
                    stabilityResultJson, StabilityAnalysisResult.class
            );

            // Create error signature
            String errorSignature = createErrorSignature(result);

            // Check if pattern already exists
            Optional<TestFailurePattern> existingPattern =
                    failurePatternRepository.findByTestNameAndErrorSignature(
                            result.getTestName(),
                            errorSignature
                    );

            TestFailurePattern pattern;

            if (existingPattern.isPresent()) {
                // Update existing pattern
                pattern = existingPattern.get();
                pattern.setOccurrences(pattern.getOccurrences() + 1);
                pattern.setLastDetectedAt(Instant.now());

                log.info("Updating existing pattern: occurrences now {}", pattern.getOccurrences());

            } else {
                // Create new pattern
                pattern = TestFailurePattern.builder()
                        .testName(result.getTestName())
                        .patternType(result.getRootCause().name())
                        .errorSignature(errorSignature)
                        // ❌ REMOVED: .errorMessage() - field doesn't exist in entity
                        // ❌ REMOVED: .stackTrace() - field doesn't exist in entity
                        // ❌ REMOVED: .suggestedFix() - field doesn't exist in entity
                        .occurrences(1)
                        .impactScore(BigDecimal.valueOf(calculateImpactScore(result)))  // ✅ Convert to BigDecimal
                        .isResolved(false)
                        .firstDetectedAt(Instant.now())
                        .lastDetectedAt(Instant.now())
                        .build();

                log.info("Creating new failure pattern for: {}", result.getTestName());
            }
            // Save pattern
            pattern = failurePatternRepository.save(pattern);

            log.info("✅ Failure pattern recorded: ID={}, Test={}",
                    pattern.getId(), pattern.getTestName());

            Map<String, Object> result2 = new HashMap<>();
            result2.put("success", true);
            result2.put("patternId", pattern.getId());
            result2.put("testName", pattern.getTestName());
            result2.put("occurrences", pattern.getOccurrences());

            return result2;

        } catch (Exception e) {
            log.error("❌ Failed to record pattern: {}", e.getMessage(), e);

            Map<String, Object> result = new HashMap<>();
            result.put("success", false);
            result.put("error", e.getMessage());
            return result;
        }
    }

    @Override
    public boolean validateParameters(Map<String, Object> parameters) {
        if (parameters == null || !parameters.containsKey("stabilityResult")) {
            return false;
        }

        Object stabilityResult = parameters.get("stabilityResult");
        return stabilityResult instanceof String && !((String) stabilityResult).isBlank();
    }

    @Override
    public Map<String, String> getParameterSchema() {
        Map<String, String> schema = new HashMap<>();
        schema.put("stabilityResult", "string (required) - JSON string of StabilityAnalysisResult with root cause");
        return schema;
    }

    /**
     * Create unique signature for error pattern.
     */
    private String createErrorSignature(StabilityAnalysisResult result) {
        String firstError = result.getErrorMessages().isEmpty()
                ? "no-error"
                : result.getErrorMessages().get(0);

        // Normalize error message
        String normalizedError = firstError
                .replaceAll("\\d+", "N")
                .replaceAll("\".*?\"", "STR")
                .substring(0, Math.min(100, firstError.length()));

        return String.format("%s_%s_%s",
                result.getRootCause(),
                result.getPattern(),
                normalizedError.hashCode()
        );
    }

    /**
     * Calculate impact score.
     */
    private long calculateImpactScore(StabilityAnalysisResult result) {
        return (long) (result.getFlakinessScore() * 100.0);
    }
}