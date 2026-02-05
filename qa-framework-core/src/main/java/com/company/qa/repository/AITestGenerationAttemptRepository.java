package com.company.qa.repository;

import com.company.qa.model.entity.AITestGenerationAttempt;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Repository
public interface AITestGenerationAttemptRepository extends JpaRepository<AITestGenerationAttempt, UUID> {

    // Find attempts for a story
    List<AITestGenerationAttempt> findByJiraStoryKey(String jiraStoryKey);
    List<AITestGenerationAttempt> findByJiraStoryKeyOrderByAttemptedAtDesc(String jiraStoryKey);

    // Find failed attempts
    List<AITestGenerationAttempt> findBySuccessFalse();
    List<AITestGenerationAttempt> findBySuccessFalseAndErrorType(String errorType);

    // Find recent failures for alerting
    @Query("SELECT a FROM AITestGenerationAttempt a WHERE a.success = false AND a.attemptedAt >= :since ORDER BY a.attemptedAt DESC")
    List<AITestGenerationAttempt> findRecentFailures(@Param("since") LocalDateTime since);

    // Error pattern analysis
    @Query("SELECT a.errorType, COUNT(a) FROM AITestGenerationAttempt a WHERE a.success = false AND a.attemptedAt >= :since GROUP BY a.errorType")
    List<Object[]> countFailuresByErrorType(@Param("since") LocalDateTime since);

    // Performance metrics
    @Query("SELECT AVG(a.durationMs) FROM AITestGenerationAttempt a WHERE a.success = true AND a.aiProvider = :provider")
    Double calculateAverageDurationByProvider(@Param("provider") String provider);

    // Success rate calculation
    @Query("SELECT COUNT(a) * 100.0 / (SELECT COUNT(b) FROM AITestGenerationAttempt b WHERE b.attemptedAt >= :since) " +
            "FROM AITestGenerationAttempt a WHERE a.success = true AND a.attemptedAt >= :since")
    Double calculateSuccessRateSince(@Param("since") LocalDateTime since);
}