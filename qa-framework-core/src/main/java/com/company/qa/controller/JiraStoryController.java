package com.company.qa.controller;

import com.company.qa.exception.JiraIntegrationException;
import com.company.qa.integration.jira.JiraStoryService;
import com.company.qa.model.dto.ParsedAcceptanceCriteria;
import com.company.qa.model.entity.JiraStory;
import com.company.qa.repository.JiraStoryRepository;
import com.company.qa.service.context.JiraContextBuilder;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Week 9 Day 2: JIRA Story REST API Controller
 *
 * Provides endpoints for:
 * - Fetching JIRA stories (single or bulk via JQL)
 * - Viewing stored stories
 * - Parsing acceptance criteria
 * - Building AI context
 *
 * Day 3 will add:
 * - AI test generation endpoints
 * - Approval workflow triggers
 *
 * Security:
 * - Uses Spring Security authentication
 * - User ID extracted from authenticated principal
 * - API key authentication (configured separately)
 *
 * Error Handling:
 * - Consistent error response format
 * - Appropriate HTTP status codes
 * - Detailed error messages for debugging
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/jira/stories")
@RequiredArgsConstructor
@Tag(name = "JIRA Stories", description = "JIRA story fetching and management")
public class JiraStoryController {

    private final JiraStoryService storyService;
    private final JiraStoryRepository storyRepository;
    private final JiraContextBuilder contextBuilder;

    // ==================== Fetch Endpoints ====================

    /**
     * Fetch a single JIRA story by key
     *
     * POST /api/v1/jira/stories/fetch/{jiraKey}
     *
     * Example: POST /api/v1/jira/stories/fetch/QA-123
     *
     * Response:
     * {
     *   "success": true,
     *   "story": { ... },
     *   "message": "Successfully fetched JIRA story: QA-123"
     * }
     */
    @PostMapping("/fetch/{jiraKey}")
    @Operation(
            summary = "Fetch JIRA story",
            description = "Fetches a JIRA story from JIRA Cloud API and stores it in database"
    )
    public ResponseEntity<Map<String, Object>> fetchStory(
            @PathVariable
            @Parameter(description = "JIRA issue key (e.g., QA-123)", example = "QA-123")
            String jiraKey,
            @AuthenticationPrincipal UserDetails userDetails) {

        log.info("Fetching JIRA story: {}", jiraKey);

        try {
            String userId = userDetails != null ? userDetails.getUsername() : "anonymous";
            JiraStory story = storyService.fetchStory(jiraKey, userId);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("story", story);
            response.put("message", "Successfully fetched JIRA story: " + jiraKey);

            return ResponseEntity.ok(response);

        } catch (JiraIntegrationException e) {
            log.error("Failed to fetch JIRA story: {}", jiraKey, e);
            return buildErrorResponse(e.getMessage(), HttpStatus.BAD_REQUEST);
        } catch (Exception e) {
            log.error("Unexpected error fetching JIRA story: {}", jiraKey, e);
            return buildErrorResponse("Internal server error", HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    /**
     * Fetch multiple stories using JQL
     *
     * POST /api/v1/jira/stories/fetch-by-jql
     *
     * Request Body:
     * {
     *   "jql": "project = QA AND status = 'To Do'",
     *   "maxResults": 50
     * }
     *
     * JQL Examples:
     * - "project = QA AND status = 'To Do'"
     * - "assignee = currentUser() AND sprint = activeSprint()"
     * - "created >= -7d"
     * - "labels = 'automation'"
     *
     * Response:
     * {
     *   "success": true,
     *   "stories": [ ... ],
     *   "count": 15,
     *   "message": "Fetched 15 stories"
     * }
     */
    @PostMapping("/fetch-by-jql")
    @Operation(
            summary = "Fetch stories by JQL",
            description = "Fetches multiple JIRA stories using JQL query (max 100 results)"
    )
    public ResponseEntity<Map<String, Object>> fetchStoriesByJql(
            @RequestBody Map<String, Object> request,
            @AuthenticationPrincipal UserDetails userDetails) {

        String jql = (String) request.get("jql");
        Integer maxResults = request.get("maxResults") != null
                ? (Integer) request.get("maxResults")
                : 50;

        log.info("Fetching JIRA stories by JQL: {} (max: {})", jql, maxResults);

        try {
            String userId = userDetails != null ? userDetails.getUsername() : "anonymous";
            List<JiraStory> stories = storyService.fetchStoriesByJql(jql, userId, maxResults);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("stories", stories);
            response.put("count", stories.size());
            response.put("message", String.format("Fetched %d stories", stories.size()));

            return ResponseEntity.ok(response);

        } catch (JiraIntegrationException e) {
            log.error("Failed to fetch stories by JQL: {}", jql, e);
            return buildErrorResponse(e.getMessage(), HttpStatus.BAD_REQUEST);
        } catch (Exception e) {
            log.error("Unexpected error fetching stories by JQL: {}", jql, e);
            return buildErrorResponse("Internal server error", HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    // ==================== Query Endpoints ====================

    /**
     * Get all stored stories
     *
     * GET /api/v1/jira/stories
     *
     * Response:
     * {
     *   "success": true,
     *   "stories": [ ... ],
     *   "count": 42
     * }
     */
    @GetMapping
    @Operation(
            summary = "Get all stories",
            description = "Retrieves all stored JIRA stories from database"
    )
    public ResponseEntity<Map<String, Object>> getAllStories() {
        log.info("Retrieving all JIRA stories");

        List<JiraStory> stories = storyRepository.findAll();

        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("stories", stories);
        response.put("count", stories.size());

        return ResponseEntity.ok(response);
    }

    /**
     * Get story by database ID
     *
     * GET /api/v1/jira/stories/{id}
     *
     * Example: GET /api/v1/jira/stories/550e8400-e29b-41d4-a716-446655440000
     */
    @GetMapping("/{id}")
    @Operation(
            summary = "Get story by ID",
            description = "Retrieves a specific JIRA story by database UUID"
    )
    public ResponseEntity<Map<String, Object>> getStoryById(
            @PathVariable
            @Parameter(description = "Story database ID (UUID)")
            UUID id) {

        log.info("Retrieving JIRA story by ID: {}", id);

        return storyRepository.findById(id)
                .map(story -> {
                    Map<String, Object> response = new HashMap<>();
                    response.put("success", true);
                    response.put("story", story);
                    return ResponseEntity.ok(response);
                })
                .orElseGet(() -> buildErrorResponse("Story not found", HttpStatus.NOT_FOUND));
    }

    /**
     * Get story by JIRA key
     *
     * GET /api/v1/jira/stories/by-key/{jiraKey}
     *
     * Example: GET /api/v1/jira/stories/by-key/QA-123
     */
    @GetMapping("/by-key/{jiraKey}")
    @Operation(
            summary = "Get story by JIRA key",
            description = "Retrieves a JIRA story by its JIRA key (e.g., QA-123)"
    )
    public ResponseEntity<Map<String, Object>> getStoryByJiraKey(
            @PathVariable
            @Parameter(description = "JIRA issue key", example = "QA-123")
            String jiraKey) {

        log.info("Retrieving JIRA story by key: {}", jiraKey);

        return storyRepository.findByJiraKey(jiraKey)
                .map(story -> {
                    Map<String, Object> response = new HashMap<>();
                    response.put("success", true);
                    response.put("story", story);
                    return ResponseEntity.ok(response);
                })
                .orElseGet(() -> buildErrorResponse("Story not found: " + jiraKey, HttpStatus.NOT_FOUND));
    }

    // ==================== Analysis Endpoints ====================

    /**
     * Parse acceptance criteria for a story
     *
     * GET /api/v1/jira/stories/{id}/parse-ac
     *
     * Response:
     * {
     *   "success": true,
     *   "jiraKey": "QA-123",
     *   "parsedAC": {
     *     "format": "GHERKIN",
     *     "confidence": 0.95,
     *     "scenarios": [ ... ],
     *     "testCaseCount": 2
     *   }
     * }
     */
    @GetMapping("/{id}/parse-ac")
    @Operation(
            summary = "Parse acceptance criteria",
            description = "Parses acceptance criteria and returns structured format"
    )
    public ResponseEntity<Map<String, Object>> parseAcceptanceCriteria(
            @PathVariable
            @Parameter(description = "Story database ID")
            UUID id) {

        log.info("Parsing AC for story: {}", id);

        return storyRepository.findById(id)
                .map(story -> {
                    ParsedAcceptanceCriteria parsed = storyService.parseAcceptanceCriteria(story);

                    Map<String, Object> response = new HashMap<>();
                    response.put("success", true);
                    response.put("jiraKey", story.getJiraKey());
                    response.put("parsedAC", parsed);

                    return ResponseEntity.ok(response);
                })
                .orElseGet(() -> buildErrorResponse("Story not found", HttpStatus.NOT_FOUND));
    }

    /**
     * Build AI context for a story
     *
     * POST /api/v1/jira/stories/{id}/build-context
     *
     * Request Body (optional):
     * {
     *   "userPrompt": "Generate UI tests with focus on error handling"
     * }
     *
     * Response:
     * {
     *   "success": true,
     *   "jiraKey": "QA-123",
     *   "context": "Generate UI tests...\n\n=== JIRA Story Context ===\n...",
     *   "contextLength": 1245
     * }
     */
    @PostMapping("/{id}/build-context")
    @Operation(
            summary = "Build AI context",
            description = "Builds AI prompt context for test generation"
    )
    public ResponseEntity<Map<String, Object>> buildContext(
            @PathVariable
            @Parameter(description = "Story database ID")
            UUID id,
            @RequestBody(required = false) Map<String, String> request) {

        log.info("Building context for story: {}", id);

        return storyRepository.findById(id)
                .map(story -> {
                    String userPrompt = request != null ? request.get("userPrompt") : null;
                    String context = contextBuilder.buildStoryTestPrompt(story, userPrompt);

                    Map<String, Object> response = new HashMap<>();
                    response.put("success", true);
                    response.put("jiraKey", story.getJiraKey());
                    response.put("context", context);
                    response.put("contextLength", context.length());

                    return ResponseEntity.ok(response);
                })
                .orElseGet(() -> buildErrorResponse("Story not found", HttpStatus.NOT_FOUND));
    }

    // ==================== Utility Endpoints ====================

    /**
     * Get stories ready for test generation
     *
     * GET /api/v1/jira/stories/ready-for-generation
     *
     * Returns stories that have:
     * - Summary (always required)
     * - Description OR acceptance criteria
     *
     * Response:
     * {
     *   "success": true,
     *   "stories": [ ... ],
     *   "count": 8
     * }
     */
    @GetMapping("/ready-for-generation")
    @Operation(
            summary = "Get stories ready for test generation",
            description = "Retrieves stories that have sufficient information for AI test generation"
    )
    public ResponseEntity<Map<String, Object>> getStoriesReadyForGeneration() {
        log.info("Retrieving stories ready for test generation");

        List<JiraStory> stories = storyService.getStoriesReadyForTestGeneration();

        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("stories", stories);
        response.put("count", stories.size());

        return ResponseEntity.ok(response);
    }

    /**
     * Get statistics about stored stories
     *
     * GET /api/v1/jira/stories/stats
     *
     * Response:
     * {
     *   "success": true,
     *   "stats": {
     *     "total": 42,
     *     "withAC": 35,
     *     "readyForGen": 30,
     *     "byStatus": {
     *       "To Do": 15,
     *       "In Progress": 10,
     *       "Done": 17
     *     },
     *     "byType": {
     *       "Story": 25,
     *       "Bug": 10,
     *       "Task": 7
     *     }
     *   }
     * }
     */
    @GetMapping("/stats")
    @Operation(
            summary = "Get story statistics",
            description = "Retrieves statistics about stored JIRA stories"
    )
    public ResponseEntity<Map<String, Object>> getStatistics() {
        log.info("Retrieving story statistics");

        Map<String, Object> stats = new HashMap<>();
        stats.put("total", storyRepository.count());
        stats.put("withAC", storyRepository.findStoriesWithAcceptanceCriteria().size());
        stats.put("readyForGen", storyRepository.findStoriesReadyForTestGeneration().size());

        // Count by status
        Map<String, Long> byStatus = new HashMap<>();
        byStatus.put("To Do", storyRepository.countByStatus("To Do"));
        byStatus.put("In Progress", storyRepository.countByStatus("In Progress"));
        byStatus.put("Done", storyRepository.countByStatus("Done"));
        stats.put("byStatus", byStatus);

        // Count by type
        Map<String, Long> byType = new HashMap<>();
        byType.put("Story", storyRepository.countByStoryType("Story"));
        byType.put("Bug", storyRepository.countByStoryType("Bug"));
        byType.put("Task", storyRepository.countByStoryType("Task"));
        stats.put("byType", byType);

        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("stats", stats);

        return ResponseEntity.ok(response);
    }

    // ==================== Helper Methods ====================

    /**
     * Helper method to build error responses
     * Consistent error format across all endpoints
     */
    private ResponseEntity<Map<String, Object>> buildErrorResponse(String message, HttpStatus status) {
        Map<String, Object> response = new HashMap<>();
        response.put("success", false);
        response.put("error", message);
        return ResponseEntity.status(status).body(response);
    }
}