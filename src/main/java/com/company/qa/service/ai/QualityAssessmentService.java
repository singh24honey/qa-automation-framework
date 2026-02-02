package com.company.qa.service.ai;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.*;

/**
 * Service for assessing quality of AI-generated test code.
 *
 * This is a RULE-BASED quality assessment (not AI-powered).
 * Checks for:
 * - Presence of assertions
 * - Gherkin format correctness
 * - Step definition completeness
 * - Page object patterns
 */
@Service
@Slf4j
public class QualityAssessmentService {

    /**
     * Assess quality of generated test code.
     * Returns quality metrics and concerns.
     */
    public QualityAssessmentResult assessTestQuality(Map<String, Object> testCode) {
        log.debug("Assessing quality of generated test code");

        List<QualityConcern> concerns = new ArrayList<>();
        int totalChecks = 0;
        int passedChecks = 0;

        // Check 1: Feature file exists and is valid
        if (testCode.containsKey("featureFile")) {
            totalChecks++;
            String featureFile = testCode.get("featureFile").toString();

            if (isValidGherkin(featureFile)) {
                passedChecks++;
            } else {
                concerns.add(new QualityConcern(
                        "INVALID_GHERKIN",
                        "HIGH",
                        "Feature file does not follow Gherkin syntax",
                        "Review and fix Gherkin syntax (Feature, Scenario, Given/When/Then)"
                ));
            }
        } else {
            concerns.add(new QualityConcern(
                    "MISSING_FEATURE_FILE",
                    "CRITICAL",
                    "Feature file is missing",
                    "Generate a valid Cucumber feature file"
            ));
        }

        // Check 2: Step definitions exist
        if (testCode.containsKey("stepDefinitions")) {
            totalChecks++;
            @SuppressWarnings("unchecked")
            List<String> stepDefs = (List<String>) testCode.get("stepDefinitions");

            if (stepDefs != null && !stepDefs.isEmpty()) {
                passedChecks++;

                // Check for assertions in step definitions
                boolean hasAssertions = stepDefs.stream()
                        .anyMatch(step -> step.contains("assert") ||
                                step.contains("assertEquals") ||
                                step.contains("assertTrue"));

                if (!hasAssertions) {
                    concerns.add(new QualityConcern(
                            "MISSING_ASSERTIONS",
                            "MEDIUM",
                            "Step definitions lack assertions",
                            "Add assertion statements to verify expected behavior"
                    ));
                }
            } else {
                concerns.add(new QualityConcern(
                        "MISSING_STEP_DEFINITIONS",
                        "CRITICAL",
                        "Step definitions are missing or empty",
                        "Generate step definition classes"
                ));
            }
        }

        // Check 3: Page objects exist (for UI tests)
        if (testCode.containsKey("pageObjects")) {
            totalChecks++;
            @SuppressWarnings("unchecked")
            List<String> pageObjects = (List<String>) testCode.get("pageObjects");

            if (pageObjects != null && !pageObjects.isEmpty()) {
                passedChecks++;

                // Check for proper POM patterns
                boolean hasSelectors = pageObjects.stream()
                        .anyMatch(po -> po.contains("@FindBy") ||
                                po.contains("findElement"));

                if (!hasSelectors) {
                    concerns.add(new QualityConcern(
                            "WEAK_PAGE_OBJECTS",
                            "LOW",
                            "Page objects lack proper selector annotations",
                            "Use @FindBy annotations for web elements"
                    ));
                }
            }
        }

        // Calculate quality score (0-100)
        BigDecimal qualityScore = totalChecks > 0
                ? BigDecimal.valueOf((passedChecks * 100.0) / totalChecks)
                .setScale(2, java.math.RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

        // Determine confidence level
        String confidenceLevel;
        if (qualityScore.compareTo(BigDecimal.valueOf(80)) >= 0) {
            confidenceLevel = "HIGH";
        } else if (qualityScore.compareTo(BigDecimal.valueOf(60)) >= 0) {
            confidenceLevel = "MEDIUM";
        } else {
            confidenceLevel = "LOW";
        }

        log.info("Quality assessment complete: score={}, confidence={}, concerns={}",
                qualityScore, confidenceLevel, concerns.size());

        return QualityAssessmentResult.builder()
                .qualityScore(qualityScore)
                .confidenceLevel(confidenceLevel)
                .concerns(concerns)
                .checksPerformed(totalChecks)
                .checksPassed(passedChecks)
                .build();
    }

    /**
     * Validate Gherkin syntax.
     */
    private boolean isValidGherkin(String featureFile) {
        if (featureFile == null || featureFile.trim().isEmpty()) {
            return false;
        }

        // Basic Gherkin validation
        boolean hasFeature = featureFile.contains("Feature:");
        boolean hasScenario = featureFile.contains("Scenario:") ||
                featureFile.contains("Scenario Outline:");
        boolean hasSteps = featureFile.contains("Given") ||
                featureFile.contains("When") ||
                featureFile.contains("Then");

        return hasFeature && hasScenario && hasSteps;
    }

    /**
     * Quality assessment result.
     */
    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class QualityAssessmentResult {
        private BigDecimal qualityScore;
        private String confidenceLevel;
        private List<QualityConcern> concerns;
        private Integer checksPerformed;
        private Integer checksPassed;
    }

    /**
     * Quality concern.
     */
    @lombok.Data
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class QualityConcern {
        private String type;
        private String severity;
        private String message;
        private String suggestion;
    }
}