package com.company.qa.repository;

import com.company.qa.model.entity.QualityTrendAnalysis;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface QualityTrendAnalysisRepository extends JpaRepository<QualityTrendAnalysis, Long> {

    Optional<QualityTrendAnalysis> findByAnalysisDateAndSuiteId(
            LocalDate analysisDate, Long suiteId);

    List<QualityTrendAnalysis> findByAnalysisDateBetweenOrderByAnalysisDate(
            LocalDate start, LocalDate end);

    List<QualityTrendAnalysis> findBySuiteIdAndAnalysisDateBetweenOrderByAnalysisDate(
            Long suiteId, LocalDate start, LocalDate end);

    @Query("SELECT t FROM QualityTrendAnalysis t WHERE t.analysisDate >= :since " +
            "ORDER BY t.analysisDate DESC")
    List<QualityTrendAnalysis> findRecentTrends(@Param("since") LocalDate since);

    @Query("SELECT t FROM QualityTrendAnalysis t WHERE t.passRateTrend IN :trends " +
            "AND t.analysisDate >= :since ORDER BY t.analysisDate DESC")
    List<QualityTrendAnalysis> findByTrendTypes(
            @Param("trends") List<String> trends,
            @Param("since") LocalDate since);

    @Query("SELECT t FROM QualityTrendAnalysis t " +
            "WHERE t.analysisDate = :analysisDate AND t.suiteId IS NULL")
    Optional<QualityTrendAnalysis> findByAnalysisDateAndSuiteIdIsNull(
            @Param("analysisDate") LocalDate analysisDate);

    /**
     * Find global trends in date range
     */
    @Query("SELECT t FROM QualityTrendAnalysis t " +
            "WHERE t.analysisDate BETWEEN :start AND :end " +
            "AND t.suiteId IS NULL " +
            "ORDER BY t.analysisDate ASC")
    List<QualityTrendAnalysis> findByAnalysisDateBetweenAndSuiteIdIsNull(
            @Param("start") LocalDate start,
            @Param("end") LocalDate end);

    /**
     * Count global trends
     */
    long countBySuiteIdIsNull();

    /**
     * Count suite-specific trends
     */
    long countBySuiteIdIsNotNull();

    /**
     * Delete old trends
     */
    @Modifying
    @Query("DELETE FROM QualityTrendAnalysis t WHERE t.createdAt < :cutoff")
    int deleteByCreatedAtBefore(@Param("cutoff") Instant cutoff);
}