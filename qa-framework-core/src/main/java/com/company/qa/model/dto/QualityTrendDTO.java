package com.company.qa.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

/**
 * DTO for quality trend data point
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QualityTrendDTO {

    private LocalDate date;
    private Integer totalTests;
    private Integer flakyTests;
    private Double avgPassRate;
    private Double overallHealthScore;
    private Integer totalExecutions;
}