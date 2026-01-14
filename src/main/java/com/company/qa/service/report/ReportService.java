package com.company.qa.service.report;

import com.company.qa.model.dto.*;

import java.time.LocalDate;
import java.util.List;

public interface ReportService {

    ExecutionStatsDTO getExecutionStats(LocalDate startDate, LocalDate endDate);

    List<TestTrendDTO> getExecutionTrends(LocalDate startDate, LocalDate endDate);

    List<BrowserStatsDTO> getBrowserStats(LocalDate startDate, LocalDate endDate);

    DashboardReportDTO getDashboardReport(LocalDate startDate, LocalDate endDate);
}