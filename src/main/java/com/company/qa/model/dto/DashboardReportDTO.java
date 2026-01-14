package com.company.qa.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DashboardReportDTO {
    private ExecutionStatsDTO overallStats;
    private List<TestTrendDTO> trends;
    private List<BrowserStatsDTO> browserStats;
}