package com.company.qa.repository;

import com.company.qa.model.entity.ScheduleExecutionHistory;
import com.company.qa.model.enums.ScheduleStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Repository
public interface ScheduleExecutionHistoryRepository extends JpaRepository<ScheduleExecutionHistory, UUID> {

    // Find by schedule with pagination
    Page<ScheduleExecutionHistory> findByScheduleIdOrderByScheduledTimeDesc(
            UUID scheduleId, Pageable pageable);

    // Find recent for a schedule
    List<ScheduleExecutionHistory> findTop10ByScheduleIdOrderByScheduledTimeDesc(UUID scheduleId);

    // Find by time range
    @Query("SELECT h FROM ScheduleExecutionHistory h " +
            "WHERE h.scheduledTime BETWEEN :start AND :end " +
            "ORDER BY h.scheduledTime DESC")
    List<ScheduleExecutionHistory> findByScheduledTimeBetween(
            @Param("start") Instant start,
            @Param("end") Instant end);

    // Count by status for a schedule
    long countByScheduleIdAndStatus(UUID scheduleId, ScheduleStatus status);
}