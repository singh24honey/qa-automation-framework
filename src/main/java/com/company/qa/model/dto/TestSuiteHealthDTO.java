package com.company.qa.model.dto;

import com.company.qa.model.enums.TrendDirection;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TestSuiteHealthDTO {

    private Double overallHealthScore; // 0-100
    private Integer totalTests;
    private Integer activeTests;
    private Integer stableTests;
    private Integer flakyTests;
    private TrendDirection trend;
    private Double averagePassRate;
    private Double averageExecutionTime;
    private List<String> topIssues;
    private List<String> recommendations;
    private Instant analyzedAt;
}