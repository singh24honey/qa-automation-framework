package com.company.qa.controller;

import com.company.qa.model.dto.*;
import com.company.qa.service.schedule.CronService;
import com.company.qa.service.schedule.ScheduleService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/schedules")
@RequiredArgsConstructor
@Slf4j
public class ScheduleController {

    private final ScheduleService scheduleService;
    private final CronService cronService;

    @PostMapping
    public ResponseEntity<ApiResponse<ScheduleResponse>> createSchedule(
            @Valid @RequestBody ScheduleRequest request) {

        log.info("POST /api/v1/schedules - Creating: {}", request.getName());

        ScheduleResponse response = scheduleService.createSchedule(request);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(response, "Schedule created successfully"));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<ScheduleResponse>> getSchedule(@PathVariable UUID id) {
        log.info("GET /api/v1/schedules/{}", id);

        ScheduleResponse response = scheduleService.getSchedule(id);

        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<ScheduleResponse>> updateSchedule(
            @PathVariable UUID id,
            @RequestBody ScheduleUpdateRequest request) {

        log.info("PUT /api/v1/schedules/{}", id);

        ScheduleResponse response = scheduleService.updateSchedule(id, request);

        return ResponseEntity.ok(ApiResponse.success(response, "Schedule updated"));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteSchedule(@PathVariable UUID id) {
        log.info("DELETE /api/v1/schedules/{}", id);

        scheduleService.deleteSchedule(id);

        return ResponseEntity.ok(ApiResponse.success(null, "Schedule deleted"));
    }

    @PostMapping("/{id}/enable")
    public ResponseEntity<ApiResponse<Void>> enableSchedule(@PathVariable UUID id) {
        log.info("POST /api/v1/schedules/{}/enable", id);

        scheduleService.enableSchedule(id);

        return ResponseEntity.ok(ApiResponse.success(null, "Schedule enabled"));
    }

    @PostMapping("/{id}/disable")
    public ResponseEntity<ApiResponse<Void>> disableSchedule(@PathVariable UUID id) {
        log.info("POST /api/v1/schedules/{}/disable", id);

        scheduleService.disableSchedule(id);

        return ResponseEntity.ok(ApiResponse.success(null, "Schedule disabled"));
    }

    @PostMapping("/{id}/trigger")
    public ResponseEntity<ApiResponse<Void>> triggerScheduleNow(@PathVariable UUID id) {
        log.info("POST /api/v1/schedules/{}/trigger", id);

        scheduleService.triggerScheduleNow(id);

        return ResponseEntity.ok(ApiResponse.success(null, "Schedule triggered"));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<Page<ScheduleResponse>>> getAllSchedules(
            @PageableDefault(size = 20) Pageable pageable) {

        log.info("GET /api/v1/schedules - Page: {}", pageable.getPageNumber());

        Page<ScheduleResponse> schedules = scheduleService.getAllSchedules(pageable);

        return ResponseEntity.ok(ApiResponse.success(schedules));
    }

    @GetMapping("/test/{testId}")
    public ResponseEntity<ApiResponse<List<ScheduleResponse>>> getSchedulesByTest(
            @PathVariable UUID testId) {

        log.info("GET /api/v1/schedules/test/{}", testId);

        List<ScheduleResponse> schedules = scheduleService.getSchedulesByTestId(testId);

        return ResponseEntity.ok(ApiResponse.success(schedules));
    }

    @GetMapping("/{id}/history")
    public ResponseEntity<ApiResponse<Page<ScheduleHistoryResponse>>> getScheduleHistory(
            @PathVariable UUID id,
            @PageableDefault(size = 20) Pageable pageable) {

        log.info("GET /api/v1/schedules/{}/history", id);

        Page<ScheduleHistoryResponse> history = scheduleService.getScheduleHistory(id, pageable);

        return ResponseEntity.ok(ApiResponse.success(history));
    }

    @PostMapping("/validate-cron")
    public ResponseEntity<ApiResponse<Map<String, Object>>> validateCronExpression(
            @RequestBody Map<String, String> request) {

        String cronExpression = request.get("cronExpression");
        String timezone = request.getOrDefault("timezone", "UTC");

        log.info("POST /api/v1/schedules/validate-cron - {}", cronExpression);

        boolean isValid = cronService.isValidCronExpression(cronExpression);
        String description = isValid ? cronService.describeCron(cronExpression) : "Invalid";

        var nextRun = isValid ?
                cronService.getNextExecutionTime(cronExpression, timezone).orElse(null) : null;

        Map<String, Object> result = Map.of(
                "valid", isValid,
                "description", description,
                "nextRun", nextRun != null ? nextRun.toString() : "N/A"
        );

        return ResponseEntity.ok(ApiResponse.success(result));
    }

    @GetMapping("/cron-examples")
    public ResponseEntity<ApiResponse<Map<String, String>>> getCronExamples() {
        log.info("GET /api/v1/schedules/cron-examples");

        Map<String, String> examples = Map.of(
                "Every minute", CronService.CronExamples.EVERY_MINUTE,
                "Every hour", CronService.CronExamples.EVERY_HOUR,
                "Every day at 9 AM", CronService.CronExamples.EVERY_DAY_AT_9AM,
                "Every day at midnight", CronService.CronExamples.EVERY_DAY_AT_MIDNIGHT,
                "Weekdays at 9 AM", CronService.CronExamples.WEEKDAYS_AT_9AM,
                "Every Monday at 9 AM", CronService.CronExamples.EVERY_MONDAY_AT_9AM,
                "First day of month", CronService.CronExamples.FIRST_DAY_OF_MONTH,
                "Every 15 minutes", CronService.CronExamples.EVERY_15_MINUTES,
                "Every 30 minutes", CronService.CronExamples.EVERY_30_MINUTES
        );

        return ResponseEntity.ok(ApiResponse.success(examples));
    }
}