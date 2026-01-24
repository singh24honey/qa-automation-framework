package com.company.qa.repository;

import com.company.qa.model.entity.TestExecution;
import com.company.qa.model.enums.TestStatus;
import org.springframework.data.domain.Pageable;  // ← CORRECT import
import org.springframework.data.domain.Page;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Repository
public interface TestExecutionRepository extends JpaRepository<TestExecution, UUID> {

    List<TestExecution> findByTestId(UUID testId);

    List<TestExecution> findByStatus(TestStatus status);

    List<TestExecution> findByStartTimeBetween(Instant start, Instant end);

    List<TestExecution> findTop10ByOrderByStartTimeDesc();

    Page<TestExecution> findAllByOrderByStartTimeDesc(Pageable pageable);

    // ===== NEW METHODS FOR WEEK 7 =====

    /**
     * Count executions in date range
     */
    long countByStartTimeBetween(Instant start, Instant end);

    /**
     * Calculate average execution time in date range
     */
    @Query("SELECT AVG(e.duration) FROM TestExecution e " +
            "WHERE e.startTime BETWEEN :start AND :end " +
            "AND e.duration IS NOT NULL")
    Double findAverageExecutionTime(@Param("start") Instant start, @Param("end") Instant end);

    /**
     * Find peak concurrency - count max simultaneous executions
     */
    @Query("SELECT COUNT(e) FROM TestExecution e " +
            "WHERE e.startTime <= :timestamp " +
            "AND (e.endTime IS NULL OR e.endTime >= :timestamp)")
    long countConcurrentAt(@Param("timestamp") Instant timestamp);

}