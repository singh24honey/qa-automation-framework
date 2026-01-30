package com.company.qa.service.context;

import com.company.qa.model.dto.ParsedAcceptanceCriteria;
import com.company.qa.model.entity.JiraStory;
import com.company.qa.service.parser.AcceptanceCriteriaParser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.stream.Collectors;

/**
 * Week 9 Day 2: JIRA Context Builder
 *
 * Builds structured AI prompts from JIRA stories.
 * Follows the EXACT pattern of OpenAPIContextBuilder (Week 8).
 *
 * Design Pattern (from OpenAPIContextBuilder):
 * 1. User's request (if provided)
 * 2. Context header (=== Section Name ===)
 * 3. Structured information (key: value)
 * 4. Detailed content (cleaned and formatted)
 * 5. Additional context (labels, metadata)
 * 6. Generation instructions
 *
 * Integration Flow (Day 3):
 * 1. JiraContextBuilder.buildStoryTestPrompt() ← builds context
 * 2. AIGatewayService.generateTest() ← handles sanitization, rate limiting, approval
 *
 * This service:
 * - Cleans JIRA HTML/markup
 * - Structures story information
 * - Formats acceptance criteria
 * - Builds comprehensive context for AI
 *
 * Does NOT:
 * - Call DataSanitizerService (AIGatewayService does this)
 * - Call RateLimiterService (AIGatewayService does this)
 * - Create approval requests (AIGatewayService does this)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class JiraContextBuilder {

    private final AcceptanceCriteriaParser acParser;

    // ==================== Main Context Building Methods ====================

    /**
     * Build comprehensive AI prompt for test generation from JIRA story
     *
     * Pattern follows OpenAPIContextBuilder.buildEndpointTestPrompt()
     *
     * Structure:
     * 1. User's custom prompt (optional)
     * 2. Story context (key, type, status, priority)
     * 3. Summary
     * 4. Description (if present)
     * 5. Acceptance criteria (parsed and structured)
     * 6. Additional context (labels, components, assignee)
     * 7. Test generation instructions
     *
     * @param story JIRA story entity
     * @param userPrompt User's additional instructions (optional)
     * @return Structured prompt for AI test generation
     */
    public String buildStoryTestPrompt(JiraStory story, String userPrompt) {
        log.debug("Building test prompt for JIRA story: {}", story.getJiraKey());

        StringBuilder prompt = new StringBuilder();

        // User's request (if provided) - comes first to set context
        if (userPrompt != null && !userPrompt.trim().isEmpty()) {
            prompt.append(userPrompt).append("\n\n");
        }

        // Story context header
        prompt.append("=== JIRA Story Context ===\n");
        prompt.append("Story Key: ").append(story.getJiraKey()).append("\n");
        prompt.append("Type: ").append(story.getStoryType()).append("\n");
        prompt.append("Status: ").append(story.getStatus()).append("\n");
        prompt.append("Priority: ").append(story.getPriority()).append("\n");
        prompt.append("\n");

        // Summary - always present, most important field
        prompt.append("=== Story Summary ===\n");
        prompt.append(cleanJiraHtml(story.getSummary())).append("\n\n");

        // Description (if present)
        if (story.getDescription() != null && !story.getDescription().trim().isEmpty()) {
            prompt.append("=== Description ===\n");
            prompt.append(cleanJiraHtml(story.getDescription())).append("\n\n");
        }

        // Acceptance Criteria (parsed and structured)
        if (story.hasAcceptanceCriteria()) {
            ParsedAcceptanceCriteria parsedAC = acParser.parse(story.getAcceptanceCriteria());
            prompt.append(buildAcceptanceCriteriaSection(parsedAC));
        }

        // Additional context (labels, components, etc.)
        prompt.append(buildAdditionalContext(story));

        // Instructions for AI (format-specific based on AC format)
        ParsedAcceptanceCriteria parsedAC = story.hasAcceptanceCriteria()
                ? acParser.parse(story.getAcceptanceCriteria())
                : ParsedAcceptanceCriteria.builder().format(ParsedAcceptanceCriteria.ACFormat.EMPTY).build();

        prompt.append(buildTestGenerationInstructions(story, parsedAC.getFormat()));

        String result = prompt.toString();
        log.debug("Built prompt: {} characters, {} sections",
                result.length(),
                countSections(result));
        return result;
    }

    /**
     * Build minimal prompt for quick test generation
     * Useful for simple stories without extensive context
     *
     * Contains only:
     * - Summary
     * - Acceptance criteria OR description
     * - Basic instructions
     */
    public String buildMinimalPrompt(JiraStory story) {
        StringBuilder prompt = new StringBuilder();

        prompt.append("Generate tests for: ").append(cleanJiraHtml(story.getSummary())).append("\n\n");

        if (story.hasAcceptanceCriteria()) {
            prompt.append("Acceptance Criteria:\n");
            prompt.append(cleanJiraHtml(story.getAcceptanceCriteria()));
        } else if (story.getDescription() != null) {
            prompt.append("Description:\n");
            prompt.append(cleanJiraHtml(story.getDescription()));
        }

        return prompt.toString();
    }

    /**
     * Extract key information summary from story
     * Useful for logging, notifications, or UI display
     *
     * Format: "QA-123 | Type: Story | Status: To Do | AC Format: GHERKIN | Scenarios: 2"
     */
    public String extractKeyInformation(JiraStory story) {
        ParsedAcceptanceCriteria parsedAC = story.hasAcceptanceCriteria() ?
                acParser.parse(story.getAcceptanceCriteria()) :
                ParsedAcceptanceCriteria.builder()
                        .format(ParsedAcceptanceCriteria.ACFormat.EMPTY)
                        .build();

        return String.format(
                "%s | Type: %s | Status: %s | AC Format: %s | Scenarios: %d",
                story.getJiraKey(),
                story.getStoryType(),
                story.getStatus(),
                parsedAC.getFormat(),
                parsedAC.getTestCaseCount()
        );
    }

    // ==================== Section Building Methods ====================

    /**
     * Build acceptance criteria section with proper formatting
     *
     * Handles different AC formats:
     * - GHERKIN: Display scenarios with Given/When/Then
     * - NUMBERED_LIST/BULLET_POINTS: Display steps with numbering
     * - UNSTRUCTURED: Display raw text + normalized Gherkin
     * - MIXED: Display raw + warnings
     */
    private String buildAcceptanceCriteriaSection(ParsedAcceptanceCriteria parsedAC) {
        StringBuilder section = new StringBuilder();

        section.append("=== Acceptance Criteria ===\n");
        section.append("Format Detected: ").append(parsedAC.getFormat()).append("\n");
        section.append("Confidence: ").append(String.format("%.0f%%", parsedAC.getConfidence() * 100)).append("\n\n");

        switch (parsedAC.getFormat()) {
            case GHERKIN:
                section.append("Scenarios:\n");
                parsedAC.getScenarios().forEach(scenario -> {
                    section.append(scenario.toGherkinString()).append("\n");
                });
                break;

            case NUMBERED_LIST:
            case BULLET_POINTS:
                section.append("Steps:\n");
                for (int i = 0; i < parsedAC.getSteps().size(); i++) {
                    section.append(i + 1).append(". ")
                            .append(parsedAC.getSteps().get(i)).append("\n");
                }
                section.append("\nNormalized to Gherkin:\n");
                section.append(parsedAC.getNormalizedGherkin()).append("\n");
                break;

            case UNSTRUCTURED:
            case MIXED:
                section.append("Raw Text:\n");
                section.append(parsedAC.getRawText()).append("\n");
                if (parsedAC.getNormalizedGherkin() != null) {
                    section.append("\nNormalized to Gherkin:\n");
                    section.append(parsedAC.getNormalizedGherkin()).append("\n");
                }
                break;

            default:
                section.append("No acceptance criteria provided.\n");
                section.append("Tests should be generated based on story summary and description.\n");
        }

        // Add warnings if any
        if (parsedAC.getWarnings() != null && !parsedAC.getWarnings().isEmpty()) {
            section.append("\nParsing Notes:\n");
            parsedAC.getWarnings().forEach(warning ->
                    section.append("- ").append(warning).append("\n")
            );
        }

        section.append("\n");
        return section.toString();
    }

    /**
     * Build additional context section (labels, components, etc.)
     *
     * Provides supplementary information that may be useful for:
     * - Test categorization (labels)
     * - Component-specific test logic (components)
     * - Context about who's working on it (assignee)
     */
    private String buildAdditionalContext(JiraStory story) {
        StringBuilder context = new StringBuilder();
        context.append("=== Additional Context ===\n");

        boolean hasAdditionalContext = false;

        // Labels
        if (story.getLabels() != null && story.getLabels().length > 0) {
            context.append("Labels: ")
                    .append(Arrays.stream(story.getLabels())
                            .collect(Collectors.joining(", ")))
                    .append("\n");
            hasAdditionalContext = true;
        }

        // Components
        if (story.getComponents() != null && story.getComponents().length > 0) {
            context.append("Components: ")
                    .append(Arrays.stream(story.getComponents())
                            .collect(Collectors.joining(", ")))
                    .append("\n");
            hasAdditionalContext = true;
        }

        // Assignee
        if (story.getAssignee() != null) {
            context.append("Assignee: ").append(story.getAssignee()).append("\n");
            hasAdditionalContext = true;
        }

        if (!hasAdditionalContext) {
            context.append("No additional context available.\n");
        }

        context.append("\n");
        return context.toString();
    }

    /**
     * Build test generation instructions for AI
     *
     * Instructions vary based on AC format:
     * - GHERKIN: Match scenarios exactly
     * - Other formats: Convert to Gherkin
     * - EMPTY: Generate based on summary/description
     */
    private String buildTestGenerationInstructions(JiraStory story,
                                                   ParsedAcceptanceCriteria.ACFormat acFormat) {
        StringBuilder instructions = new StringBuilder();

        instructions.append("=== Test Generation Instructions ===\n");
        instructions.append("Based on the JIRA story above, generate comprehensive test cases that:\n\n");

        instructions.append("1. Cover all acceptance criteria scenarios\n");
        instructions.append("2. Include positive and negative test cases\n");
        instructions.append("3. Follow Cucumber/TestNG/Selenium patterns\n");
        instructions.append("4. Use Page Object Model design pattern\n");
        instructions.append("5. Include proper assertions and validations\n");
        instructions.append("6. Add meaningful test data examples\n\n");

        // Format-specific instructions
        if (acFormat == ParsedAcceptanceCriteria.ACFormat.GHERKIN) {
            instructions.append("Note: Acceptance criteria are in Gherkin format. ");
            instructions.append("Generate feature files that match these scenarios exactly.\n\n");
        } else if (acFormat == ParsedAcceptanceCriteria.ACFormat.EMPTY) {
            instructions.append("Note: No explicit acceptance criteria provided. ");
            instructions.append("Generate test scenarios based on story summary and description. ");
            instructions.append("Infer test cases from the requirements.\n\n");
        } else {
            instructions.append("Note: Acceptance criteria are in ")
                    .append(acFormat.toString().toLowerCase().replace("_", " "))
                    .append(" format. ");
            instructions.append("Convert to proper Gherkin scenarios.\n\n");
        }

        instructions.append("Output Requirements:\n");
        instructions.append("- Cucumber feature file (.feature)\n");
        instructions.append("- Step definition class (.java)\n");
        instructions.append("- Page Object class if needed (.java)\n");
        instructions.append("- Test data class/file if needed\n\n");

        instructions.append("Framework Context:\n");
        instructions.append("- Java 17, Spring Boot 3.2\n");
        instructions.append("- Selenium Grid for browser automation\n");
        instructions.append("- TestNG for test orchestration\n");
        instructions.append("- Cucumber for BDD scenarios\n");
        instructions.append("- Page Object Model for UI structure\n");

        return instructions.toString();
    }

    // ==================== Text Cleaning Methods ====================

    /**
     * Clean JIRA HTML markup and special characters
     *
     * Removes:
     * - HTML tags (<p>, <strong>, etc.)
     * - HTML entities (&nbsp;, &lt;, etc.)
     * - JIRA-specific markup ({code}, {quote}, etc.)
     * - Excess whitespace
     *
     * Preserves:
     * - Line breaks (normalized to \n)
     * - Basic text structure
     * - Meaningful spacing
     */
    public String cleanJiraHtml(String text) {
        if (text == null) return "";

        return text
                // Remove HTML tags
                .replaceAll("<[^>]+>", "")
                // Remove HTML entities
                .replaceAll("&nbsp;", " ")
                .replaceAll("&lt;", "<")
                .replaceAll("&gt;", ">")
                .replaceAll("&amp;", "&")
                .replaceAll("&quot;", "\"")
                .replaceAll("&apos;", "'")
                // Remove JIRA markup
                .replaceAll("\\{code[^}]*\\}", "")
                .replaceAll("\\{quote\\}", "")
                .replaceAll("\\{color[^}]*\\}", "")
                .replaceAll("\\{panel[^}]*\\}", "")
                .replaceAll("\\{noformat\\}", "")
                // Remove wiki-style links
                .replaceAll("\\[([^|\\]]+)\\|[^\\]]+\\]", "$1")  // [text|url] → text
                .replaceAll("\\[([^\\]]+)\\]", "$1")              // [text] → text
                // Normalize whitespace
                .replaceAll("\\r\\n", "\n")
                .replaceAll("\\r", "\n")
                .replaceAll(" +", " ")
                .replaceAll("\n\n\n+", "\n\n")
                .trim();
    }

    // ==================== Helper Methods ====================

    /**
     * Count sections in generated prompt
     * Used for logging/metrics
     */
    private int countSections(String prompt) {
        return (int) prompt.lines()
                .filter(line -> line.startsWith("==="))
                .count();
    }
}