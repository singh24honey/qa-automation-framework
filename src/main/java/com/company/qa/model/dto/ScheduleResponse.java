package com.company.qa.model.dto;

import com.company.qa.model.enums.ScheduleStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ScheduleResponse {

    private UUID id;
    private String name;
    private String description;
    private UUID testId;
    private String testName;
    private String cronExpression;
    private String cronDescription;  // Human-readable
    private String timezone;
    private String browser;
    private String environment;
    private Boolean headless;
    private Boolean isEnabled;
    private Boolean isRunning;
    private Instant lastRunTime;
    private ScheduleStatus lastRunStatus;
    private Instant nextRunTime;
    private Integer totalRuns;
    private Integer successfulRuns;
    private Integer failedRuns;
    private Double successRate;
    private String createdBy;
    private Instant createdAt;
    private Instant updatedAt;
}