package com.company.qa.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExecutionStatsDTO {
    private Long totalExecutions;
    private Long passedExecutions;
    private Long failedExecutions;
    private Long errorExecutions;
    private Double passRate;
    private Double failRate;
    private Instant periodStart;
    private Instant periodEnd;
}