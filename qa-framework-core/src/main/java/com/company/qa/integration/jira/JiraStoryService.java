package com.company.qa.integration.jira;

import com.company.qa.exception.JiraIntegrationException;
import com.company.qa.model.dto.ParsedAcceptanceCriteria;
import com.company.qa.model.entity.JiraConfiguration;
import com.company.qa.model.entity.JiraStory;
import com.company.qa.repository.JiraConfigurationRepository;
import com.company.qa.repository.JiraStoryRepository;
import com.company.qa.service.parser.AcceptanceCriteriaParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import java.util.*;

/**
 * Week 9 Day 2: JIRA Story Service
 *
 * Fetches JIRA stories from JIRA Cloud API and stores them in database.
 *
 * Integration Points:
 * - JiraRestClient (Week 9 Day 1): API calls with retry logic
 * - AcceptanceCriteriaParser (Week 9 Day 2): Parse AC from stories
 * - JiraStoryRepository (Week 9 Day 2): Persist stories
 *
 * Day 3 Integration:
 * - JiraContextBuilder: Build AI prompts from stories
 * - AIGatewayService: Generate tests (handles sanitization/rate limiting/approval)
 *
 * Transaction Management:
 * - @Transactional ensures atomic save/update operations
 * - Rollback on exceptions prevents partial data
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class JiraStoryService {

    private final JiraRestClient jiraRestClient;
    private final JiraConfigurationRepository configRepository;
    private final JiraStoryRepository storyRepository;
    private final AcceptanceCriteriaParser acParser;
    private final ObjectMapper objectMapper;

    // ==================== Public API Methods ====================

    /**
     * Fetch a single JIRA story by key
     * <p>
     * Flow:
     * 1. Get default JIRA configuration
     * 2. Check if story already exists (for update vs create)
     * 3. Call JIRA API via JiraRestClient (retry logic included)
     * 4. Parse JSON response to JiraStory entity
     * 5. Save/update in database
     *
     * @param jiraKey JIRA issue key (e.g., "QA-123")
     * @param userId  User ID for audit trail
     * @return Fetched and parsed JIRA story
     * @throws JiraIntegrationException if fetch fails
     */
    @Transactional
    public JiraStory fetchStory(String jiraKey, String userId) {
        log.info("Fetching JIRA story: {} (userId: {})", jiraKey, userId);

        // Get default JIRA configuration
        JiraConfiguration config = configRepository.findByConfigName("default-dev")
                .orElseThrow(() -> new JiraIntegrationException(
                        "Default JIRA configuration not found. Please configure JIRA integration first."
                ));

        // Check if story already exists (update if so)
        // 🔎 AUTH SANITY CHECK (temporary but recommended)
        try {
            verifyJiraAuthentication(config,userId);
        } catch (Exception e) {
            log.warn("Skipping Jira health check due to transient error: {}", e.getMessage());
        }
// Check if story already exists (update if so)
        Optional<JiraStory> existing = storyRepository.findByJiraKey(jiraKey);

// Fetch from JIRA API
        String endpoint = "/rest/api/3/issue/" + jiraKey;
        Map<String, String> queryParams = new HashMap<>();
        queryParams.put("fields", buildFieldsQuery());

        String jsonResponse = jiraRestClient.get(config, endpoint, queryParams, userId);

        // Parse and save
        JiraStory story = parseStoryFromJson(jsonResponse, config);

        if (existing.isPresent()) {
            // Update existing story
            JiraStory existingStory = existing.get();
            updateStoryFields(existingStory, story);
            story = storyRepository.save(existingStory);
            log.info("Updated existing JIRA story: {}", jiraKey);
        } else {
            story = storyRepository.save(story);
            log.info("Saved new JIRA story: {}", jiraKey);
        }

        return story;
    }

    /**
     * Fetch multiple stories using JQL query
     * <p>
     * JQL Examples:
     * - "project = QA AND status = 'To Do'"
     * - "assignee = currentUser() AND sprint = activeSprint()"
     * - "created >= -7d"
     *
     * @param jql        JIRA Query Language query
     * @param userId     User ID for audit trail
     * @param maxResults Maximum number of results (default: 50, max: 100)
     * @return List of fetched stories
     */
    @Transactional
    public List<JiraStory> fetchStoriesByJql(String jql, String userId, int maxResults) {
        log.info("Fetching JIRA stories by JQL: {} (max: {})", jql, maxResults);

        // Validate maxResults
        if (maxResults > 100) {
            log.warn("maxResults {} exceeds JIRA API limit, capping at 100", maxResults);
            maxResults = 100;
        }

        JiraConfiguration config = configRepository.findByConfigName("default")
                .orElseThrow(() -> new JiraIntegrationException(
                        "Default JIRA configuration not found"
                ));

        // Build search endpoint
        String endpoint = "/rest/api/3/search";
        Map<String, String> queryParams = new HashMap<>();
        queryParams.put("jql", jql);
        queryParams.put("maxResults", String.valueOf(maxResults));
        queryParams.put("fields", buildFieldsQuery());

        String jsonResponse = jiraRestClient.get(config, endpoint, queryParams, userId);

        // Parse search results
        List<JiraStory> stories = parseSearchResults(jsonResponse, config);

        // Save all stories (upsert logic handled in saveAll)
        stories = storyRepository.saveAll(stories);

        log.info("Fetched and saved {} JIRA stories", stories.size());
        return stories;
    }

    /**
     * Get JIRA story by key (from database or fetch if not exists).
     *
     * This is the method used by AgentTool implementations.
     *
     * @param jiraKey JIRA issue key (e.g., "PROJ-123")
     * @return JIRA story entity
     * @throws JiraIntegrationException if story not found and fetch fails
     */
    public JiraStory getStoryByKey(String jiraKey) {
        log.debug("Getting JIRA story: {}", jiraKey);

        // First check database
        Optional<JiraStory> existing = storyRepository.findByJiraKey(jiraKey);

        if (existing.isPresent()) {
            log.debug("Found JIRA story in database: {}", jiraKey);
            return existing.get();
        }

        // Not in database - fetch from JIRA
        log.info("JIRA story not in database, fetching from JIRA: {}", jiraKey);
        return fetchStory(jiraKey, "system");
    }

    /**
     * Parse acceptance criteria for a story
     * Useful for immediate AC analysis without AI generation
     *
     * @param story JIRA story entity
     * @return Parsed acceptance criteria
     */
    public ParsedAcceptanceCriteria parseAcceptanceCriteria(JiraStory story) {
        if (!story.hasAcceptanceCriteria()) {
            return ParsedAcceptanceCriteria.builder()
                    .format(ParsedAcceptanceCriteria.ACFormat.EMPTY)
                    .rawText("")
                    .confidence(1.0)
                    .build();
        }

        return acParser.parse(story.getAcceptanceCriteria());
    }

    /**
     * Get stories ready for test generation
     * These have summary and either description or AC
     *
     * @return List of stories ready for AI processing
     */
    public List<JiraStory> getStoriesReadyForTestGeneration() {
        return storyRepository.findStoriesReadyForTestGeneration();
    }

    // ==================== JSON Parsing Methods ====================

    /**
     * Parse JIRA API response to JiraStory entity
     * <p>
     * Handles:
     * - Standard fields (summary, description, status, etc.)
     * - Custom fields (acceptance criteria, etc.)
     * - Arrays (labels, components)
     * - Nested objects (assignee, reporter, etc.)
     * - Atlassian Document Format (ADF) for description
     */
    private JiraStory parseStoryFromJson(String jsonResponse, JiraConfiguration config) {
        try {
            JsonNode root = objectMapper.readTree(jsonResponse);
            JsonNode fields = root.path("fields");

            // Extract acceptance criteria from custom field or description
            String acceptanceCriteria = extractAcceptanceCriteria(fields);

            // Parse labels
            String[] labels = parseLabels(fields.path("labels"));

            // Parse components
            String[] components = parseComponents(fields.path("components"));

            // Build entity
            JiraStory story = JiraStory.builder()
                    .configuration(config)
                    .jiraKey(root.path("key").asText())
                    .jiraId(root.path("id").asText())
                    .summary(fields.path("summary").asText())
                    .description(extractDescription(fields))
                    .acceptanceCriteria(acceptanceCriteria)
                    .storyType(extractStoryType(fields))
                    .status(extractStatus(fields))
                    .priority(extractPriority(fields))
                    .assignee(extractAssignee(fields))
                    .reporter(extractReporter(fields))
                    .jiraCreatedAt(parseDateTime(fields.path("created").asText()))
                    .jiraUpdatedAt(parseDateTime(fields.path("updated").asText()))
                    .rawJson(objectMapper.convertValue(root, Map.class))
                    .customFields(extractCustomFields(fields))
                    .build();


            story.setLabels(labels);
            story.setComponents(components);
            story.setFetchedAt(LocalDateTime.now());

            log.debug("Parsed JIRA story: {} | Type: {} | Status: {} | AC: {}",
                    story.getJiraKey(), story.getStoryType(), story.getStatus(),
                    story.hasAcceptanceCriteria() ? "Yes" : "No");

            return story;

        } catch (JsonProcessingException e) {
            log.error("Failed to parse JIRA response", e);
            throw new JiraIntegrationException("Failed to parse JIRA response: " + e.getMessage(), e);
        }
    }


    @Transactional
    public JiraStory fetchAndSaveFromJira(String jiraKey) {
        return fetchStory(jiraKey, "system");
    }
    /**
     * Parse search results containing multiple issues
     */
    private List<JiraStory> parseSearchResults(String jsonResponse, JiraConfiguration config) {
        try {
            JsonNode root = objectMapper.readTree(jsonResponse);
            JsonNode issues = root.path("issues");
            int total = root.path("total").asInt(0);

            log.debug("Parsing {} JIRA issues from search results (total available: {})",
                    issues.size(), total);

            List<JiraStory> stories = new ArrayList<>();
            for (JsonNode issue : issues) {
                String issueJson = objectMapper.writeValueAsString(issue);
                JiraStory story = parseStoryFromJson(issueJson, config);
                stories.add(story);
            }

            return stories;

        } catch (JsonProcessingException e) {
            log.error("Failed to parse JIRA search results", e);
            throw new JiraIntegrationException("Failed to parse JIRA search results: " + e.getMessage(), e);
        }
    }

    // ==================== Field Extraction Methods ====================

    /**
     * Extract acceptance criteria from JIRA fields
     * <p>
     * Extraction Strategy (in order of precedence):
     * 1. Check common AC custom fields (customfield_10001, etc.)
     * 2. Check description for AC section markers
     * 3. Return null if not found
     * <p>
     * Note: Custom field IDs vary by JIRA instance.
     * Production deployment should configure AC field ID via properties.
     */
    private String extractAcceptanceCriteria(JsonNode fields) {
        // Try common AC custom field names
        String[] acFieldCandidates = {
                "customfield_10001",  // Often AC field in Jira Cloud
                "customfield_10002",
                "customfield_10100",
                "customfield_11000",
                "acceptanceCriteria",
                "acceptance_criteria"
        };

        for (String fieldName : acFieldCandidates) {
            JsonNode field = fields.path(fieldName);
            if (!field.isMissingNode() && !field.isNull()) {
                String value = field.isTextual() ? field.asText() : field.toString();
                if (!value.isEmpty() && !value.equals("null")) {
                    log.debug("Found AC in custom field: {}", fieldName);
                    return value;
                }
            }
        }

        // Check description for AC section
        String description = fields.path("description").asText("");
        if (description.contains("Acceptance Criteria") ||
                description.contains("AC:") ||
                (description.contains("Given") && description.contains("When"))) {

            String extracted = extractAcFromDescription(description);
            if (!extracted.isEmpty()) {
                log.debug("Extracted AC from description");
                return extracted;
            }
        }

        return null;
    }

    /**
     * Extract AC section from description text
     * Looks for section markers and extracts content
     */
    private String extractAcFromDescription(String description) {
        // Look for AC section markers
        String[] markers = {
                "Acceptance Criteria:", "AC:", "Acceptance:",
                "Given", "Scenario:"
        };

        for (String marker : markers) {
            int index = description.indexOf(marker);
            if (index != -1) {
                // Extract from marker to end or next section
                String remaining = description.substring(index);

                // Try to find section end (next header or empty lines)
                String[] endMarkers = {
                        "\n\n---", "\n\n##", "\n\nImplementation",
                        "\n\nNotes:", "\n\nTechnical Details:"
                };

                for (String endMarker : endMarkers) {
                    int endIndex = remaining.indexOf(endMarker);
                    if (endIndex != -1) {
                        return remaining.substring(0, endIndex).trim();
                    }
                }

                // No end marker found, return remaining text
                return remaining.trim();
            }
        }

        return "";
    }

    /**
     * Extract description from JIRA field (handle ADF format)
     * <p>
     * JIRA Cloud uses Atlassian Document Format (ADF) - a JSON structure.
     * Legacy JIRA uses plain text.
     */
    private String extractDescription(JsonNode fields) {
        JsonNode desc = fields.path("description");

        // Plain text description
        if (desc.isTextual()) {
            return desc.asText();
        }

        // Atlassian Document Format (ADF)
        if (desc.isObject() && desc.has("content")) {
            return extractTextFromAdf(desc);
        }

        return null;
    }

    /**
     * Extract plain text from Atlassian Document Format (ADF)
     * ADF is a nested JSON structure representing rich text
     */
    private String extractTextFromAdf(JsonNode adf) {
        StringBuilder text = new StringBuilder();
        extractTextRecursive(adf, text);
        return text.toString().trim();
    }

    private void extractTextRecursive(JsonNode node, StringBuilder text) {
        // Extract text content
        if (node.has("text")) {
            text.append(node.get("text").asText()).append(" ");
        }

        // Recurse into content array
        if (node.has("content")) {
            for (JsonNode child : node.get("content")) {
                extractTextRecursive(child, text);
            }
        }
    }


    // ==================== Helper Methods (Missing from Part 3) ====================

    /**
     * Build fields query parameter for JIRA API
     * Specifies which fields to retrieve from JIRA
     */
    private String buildFieldsQuery() {
        return String.join(",", Arrays.asList(
                "summary", "description", "issuetype", "status", "priority",
                "assignee", "reporter", "labels", "components",
                "created", "updated",
                "customfield_10001", "customfield_10002", "customfield_10100"
        ));
    }

    /**
     * Extract story type from JIRA fields
     */
    private String extractStoryType(JsonNode fields) {
        return fields.path("issuetype").path("name").asText("Story");
    }

    /**
     * Extract status from JIRA fields
     */
    private String extractStatus(JsonNode fields) {
        return fields.path("status").path("name").asText("Unknown");
    }

    /**
     * Extract priority from JIRA fields
     */
    private String extractPriority(JsonNode fields) {
        JsonNode priority = fields.path("priority");
        if (!priority.isMissingNode() && !priority.isNull()) {
            return priority.path("name").asText("Medium");
        }
        return "Medium";
    }

    /**
     * Extract assignee from JIRA fields
     */
    private String extractAssignee(JsonNode fields) {
        JsonNode assignee = fields.path("assignee");
        if (!assignee.isMissingNode() && !assignee.isNull()) {
            return assignee.path("displayName").asText("");
        }
        return null;
    }

    /**
     * Extract reporter from JIRA fields
     */
    private String extractReporter(JsonNode fields) {
        JsonNode reporter = fields.path("reporter");
        if (!reporter.isMissingNode() && !reporter.isNull()) {
            return reporter.path("displayName").asText("");
        }
        return null;
    }

    /**
     * Parse labels array from JIRA response
     */
    private String[] parseLabels(JsonNode labelsNode) {
        if (labelsNode.isArray() && labelsNode.size() > 0) {
            List<String> labels = new ArrayList<>();
            labelsNode.forEach(node -> labels.add(node.asText()));
            return labels.toArray(new String[0]);
        }
        return new String[0];
    }

    /**
     * Parse components array from JIRA response
     */
    private String[] parseComponents(JsonNode componentsNode) {
        if (componentsNode.isArray() && componentsNode.size() > 0) {
            List<String> components = new ArrayList<>();
            componentsNode.forEach(node ->
                    components.add(node.path("name").asText())
            );
            return components.toArray(new String[0]);
        }
        return new String[0];
    }

    /**
     * Extract custom fields for flexible storage
     */
    private Map<String, Object> extractCustomFields(JsonNode fields) {
        Map<String, Object> customFields = new HashMap<>();

        fields.fieldNames().forEachRemaining(fieldName -> {
            if (fieldName.startsWith("customfield_")) {
                JsonNode value = fields.get(fieldName);
                if (!value.isNull() && !value.isMissingNode()) {
                    customFields.put(fieldName, value);
                }
            }
        });

        return customFields.isEmpty() ? null : customFields;
    }

    /**
     * Parse JIRA datetime string to LocalDateTime
     */
    private LocalDateTime parseDateTime(String dateTimeStr) {
        if (dateTimeStr == null || dateTimeStr.isEmpty()) {
            return null;
        }
        try {
            return ZonedDateTime.parse(dateTimeStr).toLocalDateTime();
        } catch (Exception e) {
            log.warn("Failed to parse datetime: {}", dateTimeStr);
            return null;
        }
    }

    /**
     * Update existing story with new data
     * Used when re-fetching a story that already exists
     */
    private void updateStoryFields(JiraStory existing, JiraStory updated) {
        existing.setSummary(updated.getSummary());
        existing.setDescription(updated.getDescription());
        existing.setAcceptanceCriteria(updated.getAcceptanceCriteria());
        existing.setStoryType(updated.getStoryType());
        existing.setStatus(updated.getStatus());
        existing.setPriority(updated.getPriority());
        existing.setAssignee(updated.getAssignee());
        existing.setReporter(updated.getReporter());
        existing.setLabels(updated.getLabels());
        existing.setComponents(updated.getComponents());
        existing.setJiraUpdatedAt(updated.getJiraUpdatedAt());
        existing.setRawJson(updated.getRawJson());
        existing.setCustomFields(updated.getCustomFields());
        existing.setFetchedAt(LocalDateTime.now());
    }


    /**
     * Sanity check Jira authentication using /myself
     * This confirms API token + email + base URL are valid
     */
    private void verifyJiraAuthentication(JiraConfiguration config, String userId) {
        try {
            String response = jiraRestClient.get(
                    config,
                    "/rest/api/3/myself",
                    Collections.emptyMap(),
                    userId
            );

            JsonNode root = objectMapper.readTree(response);
            String displayName = root.path("displayName").asText("UNKNOWN");
            String email = root.path("emailAddress").asText("UNKNOWN");

            log.info("✅ Jira auth verified. User: {} ({})", displayName, email);

        } catch (Exception e) {
            log.error("❌ Jira authentication failed using /myself", e);
            throw new JiraIntegrationException(
                    "Jira authentication failed. Check API token, email, and base URL.",
                    e
            );
        }
    }


}

// ... (continue in Part 4) ...