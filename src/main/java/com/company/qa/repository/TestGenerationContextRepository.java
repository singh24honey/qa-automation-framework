package com.company.qa.repository;

import com.company.qa.model.entity.TestGenerationContext;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface TestGenerationContextRepository extends JpaRepository<TestGenerationContext, Long> {

    /**
     * Find context by test ID
     */
    Optional<TestGenerationContext> findByTestId(UUID testId);

    /**
     * Find all contexts for a specification
     */
    List<TestGenerationContext> findBySpecificationIdOrderByGeneratedAtDesc(Long specId);

    /**
     * Find all contexts for an endpoint
     */
    List<TestGenerationContext> findByEndpointIdOrderByGeneratedAtDesc(Long endpointId);

    /**
     * Find approved contexts
     */
    List<TestGenerationContext> findByApprovedTrueOrderByGeneratedAtDesc();

    /**
     * Find rejected contexts
     */
    List<TestGenerationContext> findByApprovedFalseOrderByGeneratedAtDesc();

    /**
     * Find by context type
     */
    List<TestGenerationContext> findByContextTypeOrderByGeneratedAtDesc(String contextType);

    /**
     * Find recent generations
     */
    @Query("SELECT t FROM TestGenerationContext t WHERE t.generatedAt >= :since " +
            "ORDER BY t.generatedAt DESC")
    List<TestGenerationContext> findRecentGenerations(@Param("since") Instant since);

    /**
     * Calculate total AI cost
     */
    @Query("SELECT COALESCE(SUM(t.aiCost), 0) FROM TestGenerationContext t")
    BigDecimal calculateTotalAICost();

    /**
     * Calculate AI cost for specification
     */
    @Query("SELECT COALESCE(SUM(t.aiCost), 0) FROM TestGenerationContext t " +
            "WHERE t.specification.id = :specId")
    BigDecimal calculateAICostForSpec(@Param("specId") Long specId);

    /**
     * Count generations by approval status
     */
    long countByApproved(Boolean approved);

    /**
     * Get approval rate
     */
    @Query("SELECT CAST(COUNT(CASE WHEN t.approved = true THEN 1 END) AS double) / " +
            "CAST(COUNT(*) AS double) FROM TestGenerationContext t " +
            "WHERE t.approved IS NOT NULL")
    Double getApprovalRate();
}