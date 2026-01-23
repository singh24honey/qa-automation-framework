package com.company.qa.repository;

import com.company.qa.model.entity.QualityTrendAnalysis;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

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
}