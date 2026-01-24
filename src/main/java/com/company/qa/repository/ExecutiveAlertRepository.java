package com.company.qa.repository;

import com.company.qa.model.entity.ExecutiveAlert;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

@Repository
public interface ExecutiveAlertRepository extends JpaRepository<ExecutiveAlert, Long> {

    List<ExecutiveAlert> findByStatusOrderByDetectedAtDesc(String status);

    List<ExecutiveAlert> findByStatusAndSeverityOrderByDetectedAtDesc(
            String status, String severity);

    List<ExecutiveAlert> findByAlertTypeAndStatusOrderByDetectedAtDesc(
            String alertType, String status);

    @Query("SELECT a FROM ExecutiveAlert a WHERE a.status = :status " +
            "AND a.severity IN :severities ORDER BY a.detectedAt DESC")
    List<ExecutiveAlert> findByStatusAndSeverities(
            @Param("status") String status,
            @Param("severities") List<String> severities);

    @Query("SELECT a FROM ExecutiveAlert a WHERE a.detectedAt >= :since " +
            "AND a.status = :status ORDER BY a.severity, a.detectedAt DESC")
    List<ExecutiveAlert> findRecentActiveAlerts(
            @Param("since") Instant since,
            @Param("status") String status);

    @Query("SELECT COUNT(a) FROM ExecutiveAlert a WHERE a.status = :status " +
            "AND a.severity = :severity")
    Long countByStatusAndSeverity(
            @Param("status") String status,
            @Param("severity") String severity);


    long countByAcknowledgedAtIsNotNull();

    /**
     * Delete old resolved alerts
     */
    @Modifying
    @Query("DELETE FROM ExecutiveAlert a WHERE a.status = :status AND a.resolvedAt < :cutoff")
    int deleteByStatusAndResolvedAtBefore(@Param("status") String status, @Param("cutoff") Instant cutoff);
}