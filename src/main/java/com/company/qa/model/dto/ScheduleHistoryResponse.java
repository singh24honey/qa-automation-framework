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
public class ScheduleHistoryResponse {

    private UUID id;
    private UUID scheduleId;
    private String scheduleName;
    private UUID executionId;
    private Instant scheduledTime;
    private Instant actualStartTime;
    private Instant endTime;
    private ScheduleStatus status;
    private String errorMessage;
    private Integer durationMs;
    private Instant createdAt;
}