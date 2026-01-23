package com.company.qa.repository;

import com.company.qa.model.entity.TestFailurePattern;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public interface TestFailurePatternRepository extends JpaRepository<TestFailurePattern, Long> {

    /**
     * Find all patterns for a test
     */
    List<TestFailurePattern> findByTestNameOrderByOccurrencesDesc(String testName);

    /**
     * Find unresolved patterns for a test
     */
    List<TestFailurePattern> findByTestNameAndIsResolvedFalseOrderByOccurrencesDesc(String testName);

    /**
     * Find patterns by type
     */
    List<TestFailurePattern> findByPatternTypeOrderByOccurrencesDesc(String patternType);

    /**
     * Find specific pattern by test and signature
     */
    Optional<TestFailurePattern> findByTestNameAndErrorSignature(
            String testName, String errorSignature);

    /**
     * Find high-impact unresolved patterns
     */
    @Query("SELECT p FROM TestFailurePattern p " +
            "WHERE p.isResolved = false AND p.impactScore > :minScore " +
            "ORDER BY p.impactScore DESC, p.occurrences DESC")
    List<TestFailurePattern> findHighImpactPatterns(@Param("minScore") Double minScore);

    /**
     * Find all unresolved patterns
     */
    List<TestFailurePattern> findByIsResolvedFalseOrderByLastDetectedAtDesc();

    /**
     * Count unresolved patterns
     */
    long countByIsResolvedFalse();

    /**
     * Delete resolved patterns older than cutoff
     */
    void deleteByIsResolvedTrueAndResolvedAtBefore(Instant cutoffDate);
}