package com.company.qa.repository;

import com.company.qa.model.entity.ExecutiveKPICache;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public interface ExecutiveKPICacheRepository extends JpaRepository<ExecutiveKPICache, Long> {

    Optional<ExecutiveKPICache> findByPeriodTypeAndPeriodStartAndPeriodEnd(
            String periodType, Instant periodStart, Instant periodEnd);

    List<ExecutiveKPICache> findByPeriodTypeOrderByPeriodStartDesc(String periodType);

    List<ExecutiveKPICache> findByPeriodTypeAndPeriodStartBetweenOrderByPeriodStart(
            String periodType, Instant start, Instant end);

    @Query("SELECT k FROM ExecutiveKPICache k WHERE k.periodType = :periodType " +
            "AND k.isFinal = true ORDER BY k.periodStart DESC")
    List<ExecutiveKPICache> findFinalizedByPeriodType(@Param("periodType") String periodType);

    @Query("SELECT k FROM ExecutiveKPICache k WHERE k.periodStart >= :since " +
            "ORDER BY k.periodStart DESC")
    List<ExecutiveKPICache> findRecentKPIs(@Param("since") Instant since);

    /**
     * Count KPIs generated after a certain time
     */
    long countByCacheGeneratedAtAfter(Instant timestamp);

    /**
     * Count KPIs by period type
     */
    long countByPeriodType(String periodType);

    /**
     * Delete old KPI records
     */
    @Modifying
    @Query("DELETE FROM ExecutiveKPICache k WHERE k.periodStart < :cutoff")
    int deleteByPeriodStartBefore(@Param("cutoff") Instant cutoff);
}