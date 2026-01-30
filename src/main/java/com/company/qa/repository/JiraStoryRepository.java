package com.company.qa.repository;

import com.company.qa.model.entity.JiraConfiguration;
import com.company.qa.model.entity.JiraStory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Week 9 Day 2: JIRA Story Repository
 *
 * Provides data access methods for JIRA stories.
 * Supports queries by key, status, type, and time ranges.
 *
 * Query Strategy:
 * - Derived queries for simple cases (findBy*, countBy*)
 * - @Query JPQL for complex logic
 * - Native SQL for PostgreSQL-specific features (arrays, JSONB)
 */
@Repository
public interface JiraStoryRepository extends JpaRepository<JiraStory, UUID> {

    // ==================== Basic Queries ====================

    /**
     * Find story by JIRA key (e.g., "QA-123")
     * Most common query - used for deduplication and updates
     */
    Optional<JiraStory> findByJiraKey(String jiraKey);

    /**
     * Check if story exists by JIRA key
     * Efficient for existence checks without fetching entity
     */
    boolean existsByJiraKey(String jiraKey);

    // ==================== Configuration-Based Queries ====================

    /**
     * Find all stories for a specific JIRA configuration
     * Useful for per-project reporting
     */
    List<JiraStory> findByConfiguration(JiraConfiguration configuration);

    /**
     * Find stories by configuration and status
     * Example: Get all "To Do" stories for QA project
     */
    List<JiraStory> findByConfigurationAndStatus(
            JiraConfiguration configuration,
            String status
    );

    /**
     * Find stories by configuration and type
     * Example: Get all "Bug" stories for QA project
     */
    List<JiraStory> findByConfigurationAndStoryType(
            JiraConfiguration configuration,
            String storyType
    );

    // ==================== Time-Based Queries ====================

    /**
     * Find stories fetched after a specific date
     * Useful for incremental processing
     */
    List<JiraStory> findByFetchedAtAfter(LocalDateTime after);

    /**
     * Find stories fetched within a date range
     * Useful for daily/weekly reports
     */
    List<JiraStory> findByFetchedAtBetween(LocalDateTime start, LocalDateTime end);

    /**
     * Find recently updated stories from JIRA
     * Uses jira_updated_at timestamp from JIRA API
     */
    List<JiraStory> findByJiraUpdatedAtAfterOrderByJiraUpdatedAtDesc(LocalDateTime after);

    // ==================== Content-Based Queries ====================

    /**
     * Find stories with acceptance criteria
     * JPQL query with explicit null/empty check
     */
    @Query("SELECT s FROM JiraStory s WHERE s.acceptanceCriteria IS NOT NULL AND LENGTH(s.acceptanceCriteria) > 0")
    List<JiraStory> findStoriesWithAcceptanceCriteria();

    /**
     * Find stories without acceptance criteria
     * Useful for identifying incomplete stories
     */
    @Query("SELECT s FROM JiraStory s WHERE s.acceptanceCriteria IS NULL OR LENGTH(s.acceptanceCriteria) = 0")
    List<JiraStory> findStoriesWithoutAcceptanceCriteria();

    /**
     * Find stories ready for test generation
     * Requirements: Has summary AND (description OR AC)
     */
    @Query("SELECT s FROM JiraStory s WHERE " +
            "s.summary IS NOT NULL AND " +
            "(s.description IS NOT NULL OR s.acceptanceCriteria IS NOT NULL)")
    List<JiraStory> findStoriesReadyForTestGeneration();

    // ==================== Array-Based Queries (PostgreSQL) ====================

    /**
     * Find stories by label (array membership)
     * Native query for PostgreSQL array operator
     *
     * Example: findByLabel("authentication")
     */
    @Query(value = "SELECT * FROM jira_stories WHERE :label = ANY(labels)",
            nativeQuery = true)
    List<JiraStory> findByLabel(@Param("label") String label);

    /**
     * Find stories by component (array membership)
     * Native query for PostgreSQL array operator
     *
     * Example: findByComponent("Frontend")
     */
    @Query(value = "SELECT * FROM jira_stories WHERE :component = ANY(components)",
            nativeQuery = true)
    List<JiraStory> findByComponent(@Param("component") String component);

    /**
     * Find stories with any of the given labels
     * PostgreSQL array overlap operator (&&)
     *
     * Example: findByAnyLabel(new String[]{"security", "authentication"})
     */
    @Query(value = "SELECT * FROM jira_stories WHERE labels && CAST(:labels AS text[])",
            nativeQuery = true)
    List<JiraStory> findByAnyLabel(@Param("labels") String[] labels);

    // ==================== JSONB-Based Queries (PostgreSQL) ====================

    /**
     * Find stories by custom field value
     * Uses JSONB -> operator for nested field access
     *
     * Example: findByCustomFieldValue("customfield_10001", "High Priority")
     */
    @Query(value = "SELECT * FROM jira_stories WHERE custom_fields->>:fieldName = :fieldValue",
            nativeQuery = true)
    List<JiraStory> findByCustomFieldValue(
            @Param("fieldName") String fieldName,
            @Param("fieldValue") String fieldValue
    );

    /**
     * Find stories that have a specific custom field
     * Uses jsonb_exists function instead of ? operator
     */
    @Query(value = "SELECT * FROM jira_stories WHERE jsonb_exists(custom_fields, :fieldName)",
            nativeQuery = true)
    List<JiraStory> findByCustomFieldExists(@Param("fieldName") String fieldName);
    // ==================== Aggregation Queries ====================

    /**
     * Count stories by status
     * Useful for dashboard metrics
     */
    long countByStatus(String status);

    /**
     * Count stories by type
     * Useful for type distribution reporting
     */
    long countByStoryType(String storyType);

    /**
     * Count stories by configuration
     * Useful for per-project statistics
     */
    long countByConfiguration(JiraConfiguration configuration);

    /**
     * Get story count by status - all statuses
     * Custom query returning grouped results
     */
    @Query("SELECT s.status, COUNT(s) FROM JiraStory s GROUP BY s.status")
    List<Object[]> countByStatusGrouped();

    /**
     * Get story count by type - all types
     * Custom query returning grouped results
     */
    @Query("SELECT s.storyType, COUNT(s) FROM JiraStory s GROUP BY s.storyType")
    List<Object[]> countByTypeGrouped();

    // ==================== Priority-Based Queries ====================

    /**
     * Find high priority stories
     * Useful for prioritizing test generation
     */
    List<JiraStory> findByPriorityOrderByJiraUpdatedAtDesc(String priority);

    /**
     * Find stories by priority and status
     * Example: High priority stories in "To Do" status
     */
    List<JiraStory> findByPriorityAndStatusOrderByJiraUpdatedAtDesc(
            String priority,
            String status
    );

    // ==================== Complex Queries ====================

    /**
     * Find actionable stories for test generation
     * Criteria:
     * - Status is "To Do" or "In Progress"
     * - Has AC or description
     * - High or Medium priority
     * - Ordered by priority and update date
     */
    @Query("SELECT s FROM JiraStory s WHERE " +
            "s.status IN ('To Do', 'In Progress') AND " +
            "(s.acceptanceCriteria IS NOT NULL OR s.description IS NOT NULL) AND " +
            "s.priority IN ('High', 'Medium') " +
            "ORDER BY " +
            "CASE s.priority WHEN 'High' THEN 1 WHEN 'Medium' THEN 2 ELSE 3 END, " +
            "s.jiraUpdatedAt DESC")
    List<JiraStory> findActionableStories();

    /**
     * Search stories by text (summary or description)
     * Case-insensitive partial match
     *
     * Example: searchByText("login") finds "User Login", "Login Feature", etc.
     */
    @Query("SELECT s FROM JiraStory s WHERE " +
            "LOWER(s.summary) LIKE LOWER(CONCAT('%', :searchText, '%')) OR " +
            "LOWER(s.description) LIKE LOWER(CONCAT('%', :searchText, '%'))")
    List<JiraStory> searchByText(@Param("searchText") String searchText);

    /**
     * Find stories assigned to a user
     * Useful for user-specific dashboards
     */
    List<JiraStory> findByAssigneeOrderByPriorityAscJiraUpdatedAtDesc(String assignee);

    /**
     * Find recently fetched stories (last N days)
     * Useful for monitoring recent activity
     */
    @Query("SELECT s FROM JiraStory s WHERE s.fetchedAt >= :since ORDER BY s.fetchedAt DESC")
    List<JiraStory> findRecentlyFetched(@Param("since") LocalDateTime since);
}