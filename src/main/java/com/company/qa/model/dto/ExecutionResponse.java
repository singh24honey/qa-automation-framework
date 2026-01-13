package com.company.qa.model.dto;

import com.company.qa.model.enums.TestStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExecutionResponse {

    private UUID executionId;
    private UUID testId;
    private TestStatus status;
    private Instant startTime;
    private Instant endTime;
    private Integer durationMs;
    private String errorMessage;
    private List<String> screenshotUrls;
    private String logUrl;
}