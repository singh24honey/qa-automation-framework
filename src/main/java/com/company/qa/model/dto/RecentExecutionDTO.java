package com.company.qa.model.dto;

import com.company.qa.model.enums.ExecutionStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RecentExecutionDTO {
    private UUID id;
    private String testName;
    private ExecutionStatus status;
    private String browser;
    private LocalDateTime startedAt;
    private LocalDateTime completedAt;
    private Long durationMs;
    private String errorMessage;
}