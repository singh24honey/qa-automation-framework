package com.company.qa.repository;

import com.company.qa.model.entity.TestQualitySnapshot;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface TestQualitySnapshotRepository extends JpaRepository<TestQualitySnapshot, Long> {

    /**
     * Find snapshot by date
     */
    Optional<TestQualitySnapshot> findBySnapshotDate(LocalDate date);

    /**
     * Check if snapshot exists for date
     */
    boolean existsBySnapshotDate(LocalDate date);

    /**
     * Find snapshots in date range
     */
    List<TestQualitySnapshot> findBySnapshotDateBetweenOrderBySnapshotDateAsc(
            LocalDate start, LocalDate end);

    /**
     * Get recent snapshots (last N days)
     */
    @Query("SELECT s FROM TestQualitySnapshot s " +
            "WHERE s.snapshotDate >= :startDate " +
            "ORDER BY s.snapshotDate ASC")
    List<TestQualitySnapshot> findRecentSnapshots(@Param("startDate") LocalDate startDate);

    /**
     * Get latest snapshot
     */
    @Query("SELECT s FROM TestQualitySnapshot s " +
            "ORDER BY s.snapshotDate DESC")
    List<TestQualitySnapshot> findLatestSnapshot();

    /**
     * Delete snapshots older than date
     */
    void deleteBySnapshotDateBefore(LocalDate cutoffDate);
}