package com.company.qa.service.parser;

import com.company.qa.model.dto.ParsedAcceptanceCriteria;
import com.company.qa.model.dto.ParsedAcceptanceCriteria.ACFormat;
import com.company.qa.model.dto.ParsedAcceptanceCriteria.GherkinScenario;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Week 9 Day 2: Acceptance Criteria Parser
 *
 * Parses acceptance criteria from multiple formats:
 * 1. Gherkin (Given/When/Then) - BDD format
 * 2. Numbered lists (1. 2. 3.) - Sequential steps
 * 3. Bullet points (- * •) - Requirement lists
 * 4. Unstructured text - Free-form descriptions
 *
 * Design:
 * - Stateless service (thread-safe)
 * - Pattern-based detection with confidence scoring
 * - Best-effort conversion to normalized Gherkin
 * - Comprehensive error handling
 *
 * Thread-safe: Yes (no instance state)
 * Performance: O(n) where n = length of input text
 */
@Slf4j
@Service
public class AcceptanceCriteriaParser {

    // ==================== Regex Patterns ====================

    // Gherkin keyword patterns (case-insensitive)
    private static final Pattern SCENARIO_PATTERN = Pattern.compile(
            "^\\s*Scenario:\\s*(.+)$",
            Pattern.CASE_INSENSITIVE | Pattern.MULTILINE
    );

    private static final Pattern GIVEN_PATTERN = Pattern.compile(
            "^\\s*Given\\s+(.+)$",
            Pattern.CASE_INSENSITIVE | Pattern.MULTILINE
    );

    private static final Pattern WHEN_PATTERN = Pattern.compile(
            "^\\s*When\\s+(.+)$",
            Pattern.CASE_INSENSITIVE | Pattern.MULTILINE
    );

    private static final Pattern THEN_PATTERN = Pattern.compile(
            "^\\s*Then\\s+(.+)$",
            Pattern.CASE_INSENSITIVE | Pattern.MULTILINE
    );

    private static final Pattern AND_PATTERN = Pattern.compile(
            "^\\s*And\\s+(.+)$",
            Pattern.CASE_INSENSITIVE | Pattern.MULTILINE
    );

    private static final Pattern BUT_PATTERN = Pattern.compile(
            "^\\s*But\\s+(.+)$",
            Pattern.CASE_INSENSITIVE | Pattern.MULTILINE
    );

    // Numbered list pattern (supports different separators)
    // Matches: "1. ", "2) ", "3: ", "4 - "
    private static final Pattern NUMBERED_PATTERN = Pattern.compile(
            "^\\s*\\d+[.):\\-]\\s+(.+)$",
            Pattern.MULTILINE
    );

    // Bullet point pattern (supports multiple markers)
    // Matches: "- ", "* ", "• ", "+ "
    private static final Pattern BULLET_PATTERN = Pattern.compile(
            "^\\s*[-*•+]\\s+(.+)$",
            Pattern.MULTILINE
    );

    // ==================== Main Parse Method ====================

    /**
     * Parse acceptance criteria from raw text
     *
     * Algorithm:
     * 1. Clean input (remove HTML, normalize whitespace)
     * 2. Detect format based on pattern analysis
     * 3. Parse using format-specific logic
     * 4. Generate normalized Gherkin if needed
     * 5. Calculate confidence score
     *
     * @param rawText Raw acceptance criteria text
     * @return Parsed and normalized acceptance criteria
     */
    public ParsedAcceptanceCriteria parse(String rawText) {
        log.debug("Parsing acceptance criteria: {} characters",
                rawText != null ? rawText.length() : 0);

        // Handle null/empty input
        if (rawText == null || rawText.trim().isEmpty()) {
            return ParsedAcceptanceCriteria.builder()
                    .format(ACFormat.EMPTY)
                    .rawText("")
                    .confidence(1.0)
                    .build();
        }

        // Clean input text
        String cleaned = cleanText(rawText);

        // Detect format
        ACFormat format = detectFormat(cleaned);

        // Build result
        ParsedAcceptanceCriteria.ParsedAcceptanceCriteriaBuilder builder =
                ParsedAcceptanceCriteria.builder()
                        .format(format)
                        .rawText(rawText);

        // Parse based on detected format
        switch (format) {
            case GHERKIN:
                parseGherkin(cleaned, builder);
                break;
            case NUMBERED_LIST:
                parseNumberedList(cleaned, builder);
                break;
            case BULLET_POINTS:
                parseBulletPoints(cleaned, builder);
                break;
            case MIXED:
                parseMixed(cleaned, builder);
                break;
            default:
                parseUnstructured(cleaned, builder);
        }

        ParsedAcceptanceCriteria result = builder.build();

        // Generate normalized Gherkin if not already Gherkin
        if (format != ACFormat.GHERKIN && result.hasContent()) {
            result.setNormalizedGherkin(convertToGherkin(result));
        } else if (format == ACFormat.GHERKIN) {
            // For Gherkin, set normalized as concatenated scenarios
            result.setNormalizedGherkin(
                    result.getScenarios().stream()
                            .map(GherkinScenario::toGherkinString)
                            .reduce((a, b) -> a + "\n\n" + b)
                            .orElse("")
            );
        }

        log.debug("Parsed AC: format={}, scenarios={}, steps={}, confidence={}",
                format, result.getScenarios().size(),
                result.getSteps().size(), result.getConfidence());

        return result;
    }

    // ==================== Format Detection ====================

    /**
     * Detect the format of acceptance criteria
     *
     * Scoring algorithm:
     * - Count Gherkin keywords (Given, When, Then)
     * - Count numbered list items
     * - Count bullet points
     * - Determine primary format based on highest score
     * - Detect mixed formats
     *
     * @param text Cleaned acceptance criteria text
     * @return Detected format
     */
    private ACFormat detectFormat(String text) {
        int gherkinScore = countGherkinKeywords(text);
        int numberedScore = countMatches(NUMBERED_PATTERN, text);
        int bulletScore = countMatches(BULLET_PATTERN, text);

        log.debug("Format detection scores - Gherkin: {}, Numbered: {}, Bullet: {}",
                gherkinScore, numberedScore, bulletScore);

        // Gherkin has priority if at least 2 keywords found
        // (minimum viable Gherkin = Given + Then)
        if (gherkinScore >= 2) {
            return ACFormat.GHERKIN;
        }

        // Check for mixed format (multiple structured formats present)
        int totalStructured = numberedScore + bulletScore;
        if (totalStructured > 0 && gherkinScore > 0) {
            return ACFormat.MIXED;
        }
        if (numberedScore > 0 && bulletScore > 0) {
            return ACFormat.MIXED;
        }

        // Pure numbered or bullet format (need at least 2 items)
        if (numberedScore >= 2) {
            return ACFormat.NUMBERED_LIST;
        }
        if (bulletScore >= 2) {
            return ACFormat.BULLET_POINTS;
        }

        // Default to unstructured
        return ACFormat.UNSTRUCTURED;
    }

    // ==================== Gherkin Parsing ====================

    /**
     * Parse Gherkin format acceptance criteria
     *
     * Algorithm:
     * 1. Split text by "Scenario:" keyword
     * 2. Parse each scenario block independently
     * 3. Extract Given/When/Then/And steps
     * 4. Calculate confidence based on structure quality
     */
    private void parseGherkin(String text,
                              ParsedAcceptanceCriteria.ParsedAcceptanceCriteriaBuilder builder) {
        List<GherkinScenario> scenarios = new ArrayList<>();

        // Split by Scenario keyword (case-insensitive)
        String[] blocks = text.split("(?i)(?=Scenario:)");

        for (String block : blocks) {
            if (block.trim().isEmpty()) continue;

            GherkinScenario scenario = parseGherkinScenario(block);
            if (scenario.hasContent()) {
                scenarios.add(scenario);
            }
        }

        builder.scenarios(scenarios)
                .confidence(calculateGherkinConfidence(text, scenarios.size()));

        // Add warnings for incomplete scenarios
        scenarios.stream()
                .filter(s -> !s.isWellFormed())
                .forEach(s -> builder.warnings(new ArrayList<>()));

        if (scenarios.stream().anyMatch(s -> !s.isWellFormed())) {
            List<String> warnings = new ArrayList<>();
            warnings.add("Some scenarios are incomplete (missing Given/When/Then)");
            builder.warnings(warnings);
        }
    }

    /**
     * Parse a single Gherkin scenario
     *
     * Handles:
     * - Scenario name extraction
     * - Given/When/Then/And/But step parsing
     * - Contextual "And" step assignment (added to previous keyword section)
     */
    /**
     * Parse a single Gherkin scenario
     *
     * Handles:
     * - Scenario name extraction
     * - Given/When/Then/And/But step parsing
     * - Contextual "And" step assignment (added to previous keyword section)
     */
    private GherkinScenario parseGherkinScenario(String block) {
        // Use lists instead of builder fields
        List<String> givenSteps = new ArrayList<>();
        List<String> whenSteps = new ArrayList<>();
        List<String> thenSteps = new ArrayList<>();
        List<String> andSteps = new ArrayList<>();
        String scenarioName = null;

        // Extract scenario name
        Matcher scenarioMatcher = SCENARIO_PATTERN.matcher(block);
        if (scenarioMatcher.find()) {
            scenarioName = scenarioMatcher.group(1).trim();
        }

        // Parse steps line by line
        String[] lines = block.split("\n");
        String currentKeyword = null;

        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.isEmpty() || trimmed.toLowerCase().startsWith("scenario:")) {
                continue;
            }

            // Match Gherkin keywords
            if (GIVEN_PATTERN.matcher(trimmed).matches()) {
                currentKeyword = "GIVEN";
                givenSteps.add(extractStepText(trimmed, "Given"));

            } else if (WHEN_PATTERN.matcher(trimmed).matches()) {
                currentKeyword = "WHEN";
                whenSteps.add(extractStepText(trimmed, "When"));

            } else if (THEN_PATTERN.matcher(trimmed).matches()) {
                currentKeyword = "THEN";
                thenSteps.add(extractStepText(trimmed, "Then"));

            } else if (AND_PATTERN.matcher(trimmed).matches() ||
                    BUT_PATTERN.matcher(trimmed).matches()) {
                String keyword = trimmed.toLowerCase().startsWith("and") ? "And" : "But";
                String stepText = extractStepText(trimmed, keyword);

                // Append to current keyword section
                if (currentKeyword == null) {
                    // Orphan And/But - add to andSteps
                    andSteps.add(stepText);
                } else {
                    switch (currentKeyword) {
                        case "GIVEN":
                            givenSteps.add(stepText);
                            break;
                        case "WHEN":
                            whenSteps.add(stepText);
                            break;
                        case "THEN":
                            thenSteps.add(stepText);
                            break;
                    }
                }
            }
        }

        // Build scenario using collected lists
        return GherkinScenario.builder()
                .name(scenarioName)
                .givenSteps(givenSteps)
                .whenSteps(whenSteps)
                .thenSteps(thenSteps)
                .andSteps(andSteps)
                .build();
    }

    /**
     * Extract step text from Gherkin line
     * Removes keyword prefix and trims whitespace
     */
    private String extractStepText(String line, String keyword) {
        return line.replaceFirst("(?i)^\\s*" + keyword + "\\s+", "").trim();
    }
    /**
     * Extract step text from Gherkin line
     * Removes keyword prefix and trims whitespace
     */


    /**
     * Append "And/But" step to the current keyword section
     * And/But steps inherit the context of the previous Given/When/Then
     */

    // ==================== Other Format Parsing ====================

    /**
     * Parse numbered list format
     * Example: "1. Step one\n2. Step two"
     */
    private void parseNumberedList(String text,
                                   ParsedAcceptanceCriteria.ParsedAcceptanceCriteriaBuilder builder) {
        List<String> steps = extractMatches(NUMBERED_PATTERN, text);
        builder.steps(steps)
                .confidence(steps.size() >= 2 ? 0.9 : 0.7);
    }

    /**
     * Parse bullet points format
     * Example: "- Point one\n- Point two"
     */
    private void parseBulletPoints(String text,
                                   ParsedAcceptanceCriteria.ParsedAcceptanceCriteriaBuilder builder) {
        List<String> steps = extractMatches(BULLET_PATTERN, text);
        builder.steps(steps)
                .confidence(steps.size() >= 2 ? 0.9 : 0.7);
    }

    /**
     * Parse mixed format (combination of different styles)
     * Best-effort extraction of all structured content
     */
    private void parseMixed(String text,
                            ParsedAcceptanceCriteria.ParsedAcceptanceCriteriaBuilder builder) {
        // Try to extract any structured content
        List<String> steps = new ArrayList<>();
        steps.addAll(extractMatches(NUMBERED_PATTERN, text));
        steps.addAll(extractMatches(BULLET_PATTERN, text));

        List<String> warnings = new ArrayList<>();
        warnings.add("Mixed format detected - may require manual review");

        builder.steps(steps)
                .confidence(0.6)
                .warnings(warnings);
    }

    /**
     * Parse unstructured text
     * Fallback: split by sentences as steps
     */
    private void parseUnstructured(String text,
                                   ParsedAcceptanceCriteria.ParsedAcceptanceCriteriaBuilder builder) {
        // Split by sentences (. ! ?)
        String[] sentences = text.split("[.!?]+");
        List<String> steps = new ArrayList<>();

        for (String sentence : sentences) {
            String trimmed = sentence.trim();
            if (!trimmed.isEmpty() && trimmed.length() > 5) { // Skip very short fragments
                steps.add(trimmed);
            }
        }

        List<String> warnings = new ArrayList<>();
        warnings.add("Unstructured format - AI will do best-effort interpretation");

        builder.steps(steps)
                .confidence(0.5)
                .warnings(warnings);
    }

    // ==================== Gherkin Conversion ====================

    /**
     * Convert parsed AC to Gherkin format
     * Best-effort conversion for non-Gherkin formats
     */
    private String convertToGherkin(ParsedAcceptanceCriteria parsed) {
        if (parsed.getFormat() == ACFormat.GHERKIN) {
            return parsed.getScenarios().stream()
                    .map(GherkinScenario::toGherkinString)
                    .reduce((a, b) -> a + "\n\n" + b)
                    .orElse("");
        }

        // Convert steps to simple Gherkin
        StringBuilder sb = new StringBuilder();
        sb.append("Scenario: Acceptance Criteria\n");

        List<String> steps = parsed.getSteps();
        if (steps.isEmpty()) {
            return sb.toString();
        }

        // Heuristic conversion:
        // First step → Given (setup/precondition)
        // Middle steps → When (actions)
        // Last step → Then (expected result)
        for (int i = 0; i < steps.size(); i++) {
            String step = steps.get(i);

            if (i == 0) {
                sb.append("  Given ").append(step).append("\n");
            } else if (i == steps.size() - 1) {
                sb.append("  Then ").append(step).append("\n");
            } else {
                sb.append("  When ").append(step).append("\n");
            }
        }

        return sb.toString();
    }

    // ==================== Helper Methods ====================

    /**
     * Clean text by removing HTML, normalizing whitespace
     */
    private String cleanText(String text) {
        return text
                // Remove HTML tags
                .replaceAll("<[^>]+>", "")
                // Remove HTML entities
                .replaceAll("&nbsp;", " ")
                .replaceAll("&lt;", "<")
                .replaceAll("&gt;", ">")
                .replaceAll("&amp;", "&")
                .replaceAll("&quot;", "\"")
                // Normalize line breaks
                .replaceAll("\\r\\n", "\n")
                .replaceAll("\\r", "\n")
                // Remove excess whitespace
                .replaceAll(" +", " ")
                .replaceAll("\n\n\n+", "\n\n")
                .trim();
    }

    /**
     * Count Gherkin keywords in text
     */
    private int countGherkinKeywords(String text) {
        int count = 0;
        String lower = text.toLowerCase();

        // Count unique keywords (not total occurrences)
        if (lower.contains("given")) count++;
        if (lower.contains("when")) count++;
        if (lower.contains("then")) count++;
        if (lower.contains("scenario")) count++;

        return count;
    }

    /**
     * Count pattern matches in text
     */
    private int countMatches(Pattern pattern, String text) {
        Matcher matcher = pattern.matcher(text);
        int count = 0;
        while (matcher.find()) count++;
        return count;
    }

    /**
     * Extract all pattern matches from text
     */
    private List<String> extractMatches(Pattern pattern, String text) {
        List<String> matches = new ArrayList<>();
        Matcher matcher = pattern.matcher(text);
        while (matcher.find()) {
            matches.add(matcher.group(1).trim());
        }
        return matches;
    }

    /**
     * Calculate confidence score for Gherkin parsing
     *
     * Factors:
     * - Keyword diversity (Given + When + Then = high confidence)
     * - Number of scenarios found
     * - Well-formed scenarios
     */
    private double calculateGherkinConfidence(String text, int scenariosFound) {
        int keywords = countGherkinKeywords(text);

        // Perfect Gherkin: scenarios found + all 3 keywords
        if (scenariosFound > 0 && keywords >= 3) return 0.95;

        // Good Gherkin: at least 2 keywords
        if (keywords >= 2) return 0.8;

        // Weak Gherkin: only 1 keyword
        return 0.6;
    }
}