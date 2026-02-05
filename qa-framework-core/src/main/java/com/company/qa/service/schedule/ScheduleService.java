package com.company.qa.service.schedule;

import com.company.qa.model.dto.*;
import com.company.qa.model.entity.TestSchedule;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.UUID;

public interface ScheduleService {

    ScheduleResponse createSchedule(ScheduleRequest request);

    ScheduleResponse getSchedule(UUID id);

    ScheduleResponse updateSchedule(UUID id, ScheduleUpdateRequest request);

    void deleteSchedule(UUID id);

    void enableSchedule(UUID id);

    void disableSchedule(UUID id);

    void triggerScheduleNow(UUID id);

    Page<ScheduleResponse> getAllSchedules(Pageable pageable);

    List<ScheduleResponse> getSchedulesByTestId(UUID testId);

    Page<ScheduleHistoryResponse> getScheduleHistory(UUID scheduleId, Pageable pageable);

    List<TestSchedule> getSchedulesDueForExecution();

    void processScheduledExecution(TestSchedule schedule);
}