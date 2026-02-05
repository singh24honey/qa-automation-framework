package com.company.qa.repository;

import com.company.qa.model.entity.TestSchedule;
import com.company.qa.model.enums.ScheduleStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface TestScheduleRepository extends JpaRepository<TestSchedule, UUID> {

    // Find all enabled schedules
    List<TestSchedule> findByIsEnabledTrue();

    // Find schedules by test
    List<TestSchedule> findByTestId(UUID testId);

    // Find enabled schedules due for execution
    @Query("SELECT s FROM TestSchedule s WHERE s.isEnabled = true " +
            "AND s.isRunning = false " +
            "AND (s.nextRunTime IS NULL OR s.nextRunTime <= :now)")
    List<TestSchedule> findSchedulesDueForExecution(@Param("now") Instant now);

    // Find schedule with test details
    @Query("SELECT s FROM TestSchedule s LEFT JOIN FETCH s.test WHERE s.id = :id")
    Optional<TestSchedule> findByIdWithTest(@Param("id") UUID id);

    // Paginated list
    Page<TestSchedule> findAllByOrderByCreatedAtDesc(Pageable pageable);

    // Count active schedules
    long countByIsEnabledTrue();

    long countByIsRunningTrue();

    // Check duplicate name for a test
    boolean existsByTestIdAndName(UUID testId, String name);
}