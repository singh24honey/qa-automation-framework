package com.company.qa.repository;

import com.company.qa.model.entity.TestExecutionHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Repository
public interface TestExecutionHistoryRepository extends JpaRepository<TestExecutionHistory, Long> {

    /**
     * Find all history records for a specific test
     */
    List<TestExecutionHistory> findByTestNameOrderByExecutedAtDesc(String testName);

    /**
     * Find history by execution ID
     */
    List<TestExecutionHistory> findByExecutionId(UUID executionId);

    /**
     * Find history within date range
     */
    List<TestExecutionHistory> findByExecutedAtBetween(Instant start, Instant end);

    /**
     * Count total executions for a test
     */
    long countByTestName(String testName);

    /**
     * Delete old history (for cleanup)
     */
    void deleteByExecutedAtBefore(Instant cutoffDate);

    /**
     * Get execution count by status
     */
    @Query("SELECT h.status, COUNT(h) FROM TestExecutionHistory h " +
            "WHERE h.executedAt BETWEEN :start AND :end " +
            "GROUP BY h.status")
    List<Object[]> getExecutionCountByStatus(@Param("start") Instant start,
                                             @Param("end") Instant end);
}