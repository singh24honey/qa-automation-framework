package com.company.qa.repository;

import com.company.qa.model.entity.AIGeneratedTest;
import com.company.qa.model.entity.AIGeneratedTest.TestGenerationStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface AIGeneratedTestRepository extends JpaRepository<AIGeneratedTest, UUID> {

    // Find by JIRA story
    List<AIGeneratedTest> findByJiraStoryId(UUID jiraStoryId);
    List<AIGeneratedTest> findByJiraStoryKey(String jiraStoryKey);

    // Find by status
    List<AIGeneratedTest> findByStatus(TestGenerationStatus status);
    List<AIGeneratedTest> findByStatusIn(List<TestGenerationStatus> statuses);

    // Pending reviews (for QA dashboard)
    @Query("SELECT t FROM AIGeneratedTest t WHERE t.status IN ('DRAFT', 'PENDING_REVIEW') ORDER BY t.qualityScore DESC NULLS LAST, t.generatedAt DESC")
    List<AIGeneratedTest> findPendingReviews();

    // High quality tests awaiting approval
    @Query("SELECT t FROM AIGeneratedTest t WHERE t.status = 'PENDING_REVIEW' AND t.qualityScore >= :minScore ORDER BY t.qualityScore DESC")
    List<AIGeneratedTest> findHighQualityPendingTests(@Param("minScore") BigDecimal minScore);

    // Find by reviewer
    List<AIGeneratedTest> findByReviewedBy(String reviewer);

    // Find by date range
    List<AIGeneratedTest> findByGeneratedAtBetween(LocalDateTime start, LocalDateTime end);

    // Cost queries (for analytics)
    @Query("SELECT SUM(t.totalCostUsd) FROM AIGeneratedTest t WHERE t.generatedAt >= :startDate")
    BigDecimal calculateTotalCostSince(@Param("startDate") LocalDateTime startDate);

    @Query("SELECT AVG(t.totalCostUsd) FROM AIGeneratedTest t WHERE t.testType = :testType")
    BigDecimal calculateAverageCostByType(@Param("testType") AIGeneratedTest.TestType testType);

    // Quality metrics
    @Query("SELECT AVG(t.qualityScore) FROM AIGeneratedTest t WHERE t.status = 'COMMITTED'")
    BigDecimal calculateAverageQualityScoreForCommittedTests();

    // Find tests needing review older than X days
    @Query("SELECT t FROM AIGeneratedTest t WHERE t.status = 'PENDING_REVIEW' AND t.generatedAt < :cutoffDate")
    List<AIGeneratedTest> findStalePendingReviews(@Param("cutoffDate") LocalDateTime cutoffDate);
}