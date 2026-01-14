package com.company.qa.service.schedule;

import com.company.qa.exception.QaFrameworkException;
import com.company.qa.exception.ResourceNotFoundException;
import com.company.qa.model.dto.*;
import com.company.qa.model.entity.ScheduleExecutionHistory;
import com.company.qa.model.entity.Test;
import com.company.qa.model.entity.TestSchedule;
import com.company.qa.model.enums.ScheduleStatus;
import com.company.qa.model.enums.TestStatus;
import com.company.qa.repository.ScheduleExecutionHistoryRepository;
import com.company.qa.repository.TestRepository;
import com.company.qa.repository.TestScheduleRepository;
import com.company.qa.service.execution.TestExecutionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ScheduleServiceImpl implements ScheduleService {

    private final TestScheduleRepository scheduleRepository;
    private final ScheduleExecutionHistoryRepository historyRepository;
    private final TestRepository testRepository;
    private final TestExecutionService executionService;
    private final CronService cronService;

    @Override
    @Transactional
    public ScheduleResponse createSchedule(ScheduleRequest request) {
        log.info("Creating schedule: {} for test: {}", request.getName(), request.getTestId());

        // Validate test exists
        Test test = testRepository.findById(request.getTestId())
                .orElseThrow(() -> new ResourceNotFoundException("Test", request.getTestId().toString()));

        // Validate cron expression
        cronService.validateCronExpression(request.getCronExpression());

        // Check for duplicate name
        if (scheduleRepository.existsByTestIdAndName(request.getTestId(), request.getName())) {
            throw new QaFrameworkException("Schedule with name '" + request.getName() +
                    "' already exists for this test");
        }

        // Calculate next run time
        Optional<Instant> nextRunTime = cronService.getNextExecutionTime(
                request.getCronExpression(),
                request.getTimezone()
        );

        TestSchedule schedule = TestSchedule.builder()
                .name(request.getName())
                .description(request.getDescription())
                .testId(request.getTestId())
                .cronExpression(request.getCronExpression())
                .timezone(request.getTimezone())
                .browser(request.getBrowser())
                .environment(request.getEnvironment())
                .headless(request.getHeadless())
                .isEnabled(request.getEnabled())
                .nextRunTime(nextRunTime.orElse(null))
                .build();

        schedule = scheduleRepository.save(schedule);

        log.info("Created schedule: {} - Next run: {}", schedule.getId(), schedule.getNextRunTime());

        return toResponse(schedule, test.getName());
    }

    @Override
    @Transactional(readOnly = true)
    public ScheduleResponse getSchedule(UUID id) {
        TestSchedule schedule = scheduleRepository.findByIdWithTest(id)
                .orElseThrow(() -> new ResourceNotFoundException("Schedule", id.toString()));

        String testName = schedule.getTest() != null ? schedule.getTest().getName() : "Unknown";
        return toResponse(schedule, testName);
    }

    @Override
    @Transactional
    public ScheduleResponse updateSchedule(UUID id, ScheduleUpdateRequest request) {
        log.info("Updating schedule: {}", id);

        TestSchedule schedule = scheduleRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Schedule", id.toString()));

        if (request.getName() != null) {
            schedule.setName(request.getName());
        }
        if (request.getDescription() != null) {
            schedule.setDescription(request.getDescription());
        }
        if (request.getCronExpression() != null) {
            cronService.validateCronExpression(request.getCronExpression());
            schedule.setCronExpression(request.getCronExpression());

            // Recalculate next run time
            Optional<Instant> nextRunTime = cronService.getNextExecutionTime(
                    request.getCronExpression(),
                    schedule.getTimezone()
            );
            schedule.setNextRunTime(nextRunTime.orElse(null));
        }
        if (request.getTimezone() != null) {
            schedule.setTimezone(request.getTimezone());
            // Recalculate with new timezone
            Optional<Instant> nextRunTime = cronService.getNextExecutionTime(
                    schedule.getCronExpression(),
                    request.getTimezone()
            );
            schedule.setNextRunTime(nextRunTime.orElse(null));
        }
        if (request.getBrowser() != null) {
            schedule.setBrowser(request.getBrowser());
        }
        if (request.getEnvironment() != null) {
            schedule.setEnvironment(request.getEnvironment());
        }
        if (request.getHeadless() != null) {
            schedule.setHeadless(request.getHeadless());
        }
        if (request.getEnabled() != null) {
            schedule.setIsEnabled(request.getEnabled());
        }

        schedule = scheduleRepository.save(schedule);

        Test test = testRepository.findById(schedule.getTestId()).orElse(null);
        String testName = test != null ? test.getName() : "Unknown";

        return toResponse(schedule, testName);
    }

    @Override
    @Transactional
    public void deleteSchedule(UUID id) {
        log.info("Deleting schedule: {}", id);

        if (!scheduleRepository.existsById(id)) {
            throw new ResourceNotFoundException("Schedule", id.toString());
        }

        scheduleRepository.deleteById(id);
    }

    @Override
    @Transactional
    public void enableSchedule(UUID id) {
        log.info("Enabling schedule: {}", id);

        TestSchedule schedule = scheduleRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Schedule", id.toString()));

        schedule.setIsEnabled(true);

        // Recalculate next run time
        Optional<Instant> nextRunTime = cronService.getNextExecutionTime(
                schedule.getCronExpression(),
                schedule.getTimezone()
        );
        schedule.setNextRunTime(nextRunTime.orElse(null));

        scheduleRepository.save(schedule);
    }

    @Override
    @Transactional
    public void disableSchedule(UUID id) {
        log.info("Disabling schedule: {}", id);

        TestSchedule schedule = scheduleRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Schedule", id.toString()));

        schedule.setIsEnabled(false);
        scheduleRepository.save(schedule);
    }

    @Override
    @Transactional
    public void triggerScheduleNow(UUID id) {
        log.info("Triggering schedule manually: {}", id);

        TestSchedule schedule = scheduleRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Schedule", id.toString()));

        if (schedule.getIsRunning()) {
            throw new QaFrameworkException("Schedule is already running");
        }

        processScheduledExecution(schedule);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<ScheduleResponse> getAllSchedules(Pageable pageable) {
        return scheduleRepository.findAllByOrderByCreatedAtDesc(pageable)
                .map(schedule -> {
                    Test test = testRepository.findById(schedule.getTestId()).orElse(null);
                    String testName = test != null ? test.getName() : "Unknown";
                    return toResponse(schedule, testName);
                });
    }

    @Override
    @Transactional(readOnly = true)
    public List<ScheduleResponse> getSchedulesByTestId(UUID testId) {
        return scheduleRepository.findByTestId(testId).stream()
                .map(schedule -> {
                    Test test = testRepository.findById(testId).orElse(null);
                    String testName = test != null ? test.getName() : "Unknown";
                    return toResponse(schedule, testName);
                })
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public Page<ScheduleHistoryResponse> getScheduleHistory(UUID scheduleId, Pageable pageable) {
        if (!scheduleRepository.existsById(scheduleId)) {
            throw new ResourceNotFoundException("Schedule", scheduleId.toString());
        }

        return historyRepository.findByScheduleIdOrderByScheduledTimeDesc(scheduleId, pageable)
                .map(this::toHistoryResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public List<TestSchedule> getSchedulesDueForExecution() {
        return scheduleRepository.findSchedulesDueForExecution(Instant.now());
    }

    @Override
    @Transactional
    public void processScheduledExecution(TestSchedule schedule) {
        UUID scheduleId = schedule.getId();
        log.info("Processing scheduled execution: {} ({})", schedule.getName(), scheduleId);

        // Mark as running
        schedule.setIsRunning(true);
        scheduleRepository.save(schedule);

        // Create history entry
        ScheduleExecutionHistory history = ScheduleExecutionHistory.builder()
                .scheduleId(scheduleId)
                .scheduledTime(Instant.now())
                .status(ScheduleStatus.RUNNING)
                .build();
        history = historyRepository.save(history);

        try {
            // Build execution request using YOUR ExecutionRequest DTO
            ExecutionRequest executionRequest = ExecutionRequest.builder()
                    .testId(schedule.getTestId())
                    .browser(schedule.getBrowser())
                    .environment(schedule.getEnvironment())
                    .headless(schedule.getHeadless())
                    .build();

            // Execute test using YOUR ExecutionService
            history.setActualStartTime(Instant.now());
            historyRepository.save(history);

            ExecutionResponse response = executionService.startExecution(executionRequest);

            // Update history with execution ID
            history.setExecutionId(response.getExecutionId());
            history.setStatus(ScheduleStatus.COMPLETED);
            history.setEndTime(Instant.now());
            if (history.getActualStartTime() != null) {
                history.setDurationMs((int) (Instant.now().toEpochMilli() -
                        history.getActualStartTime().toEpochMilli()));
            }
            historyRepository.save(history);

            // Update schedule stats
            schedule.setIsRunning(false);
            schedule.incrementTotalRuns();
            schedule.incrementSuccessfulRuns();  // Mark as started successfully
            schedule.setLastRunTime(Instant.now());
            schedule.setLastRunStatus(ScheduleStatus.COMPLETED);

            // Calculate next run time
            Optional<Instant> nextRunTime = cronService.getNextExecutionTime(
                    schedule.getCronExpression(),
                    schedule.getTimezone()
            );
            schedule.setNextRunTime(nextRunTime.orElse(null));

            scheduleRepository.save(schedule);

            log.info("Scheduled execution started: {} - Execution ID: {}",
                    scheduleId, response.getExecutionId());

        } catch (Exception e) {
            log.error("Failed scheduled execution: {}", e.getMessage(), e);

            history.setStatus(ScheduleStatus.ERROR);
            history.setEndTime(Instant.now());
            history.setErrorMessage(e.getMessage());
            historyRepository.save(history);

            schedule.setIsRunning(false);
            schedule.incrementTotalRuns();
            schedule.incrementFailedRuns();
            schedule.setLastRunTime(Instant.now());
            schedule.setLastRunStatus(ScheduleStatus.ERROR);

            Optional<Instant> nextRunTime = cronService.getNextExecutionTime(
                    schedule.getCronExpression(),
                    schedule.getTimezone()
            );
            schedule.setNextRunTime(nextRunTime.orElse(null));

            scheduleRepository.save(schedule);
        }
    }

    // ===== Mapper Methods =====

    private ScheduleResponse toResponse(TestSchedule schedule, String testName) {
        return ScheduleResponse.builder()
                .id(schedule.getId())
                .name(schedule.getName())
                .description(schedule.getDescription())
                .testId(schedule.getTestId())
                .testName(testName)
                .cronExpression(schedule.getCronExpression())
                .cronDescription(cronService.describeCron(schedule.getCronExpression()))
                .timezone(schedule.getTimezone())
                .browser(schedule.getBrowser())
                .environment(schedule.getEnvironment())
                .headless(schedule.getHeadless())
                .isEnabled(schedule.getIsEnabled())
                .isRunning(schedule.getIsRunning())
                .lastRunTime(schedule.getLastRunTime())
                .lastRunStatus(schedule.getLastRunStatus())
                .nextRunTime(schedule.getNextRunTime())
                .totalRuns(schedule.getTotalRuns())
                .successfulRuns(schedule.getSuccessfulRuns())
                .failedRuns(schedule.getFailedRuns())
                .successRate(schedule.getSuccessRate())
                .createdBy(schedule.getCreatedBy())
                .createdAt(schedule.getCreatedAt())
                .updatedAt(schedule.getUpdatedAt())
                .build();
    }

    private ScheduleHistoryResponse toHistoryResponse(ScheduleExecutionHistory history) {
        TestSchedule schedule = history.getSchedule();
        return ScheduleHistoryResponse.builder()
                .id(history.getId())
                .scheduleId(history.getScheduleId())
                .scheduleName(schedule != null ? schedule.getName() : "Unknown")
                .executionId(history.getExecutionId())
                .scheduledTime(history.getScheduledTime())
                .actualStartTime(history.getActualStartTime())
                .endTime(history.getEndTime())
                .status(history.getStatus())
                .errorMessage(history.getErrorMessage())
                .durationMs(history.getDurationMs())
                .createdAt(history.getCreatedAt())
                .build();
    }
}