package com.company.qa.service.report;

import com.company.qa.model.dto.*;
import com.company.qa.model.entity.TestExecution;
import com.company.qa.model.enums.TestStatus;
import com.company.qa.repository.TestExecutionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.*;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ReportServiceImpl implements ReportService {

    private final TestExecutionRepository testExecutionRepository;

    @Override
    public ExecutionStatsDTO getExecutionStats(LocalDate startDate, LocalDate endDate) {
        log.info("Getting execution stats from {} to {}", startDate, endDate);

        // Convert LocalDate to Instant (your framework uses Instant)
        Instant start = startDate.atStartOfDay(ZoneId.systemDefault()).toInstant();
        Instant end = endDate.atTime(LocalTime.MAX).atZone(ZoneId.systemDefault()).toInstant();

        // Use YOUR existing method: findByStartTimeBetween
        List<TestExecution> executions = testExecutionRepository.findByStartTimeBetween(start, end);

        long total = executions.size();

        // Use YOUR TestStatus enum
        long passed = executions.stream()
                .filter(e -> e.getStatus() == TestStatus.PASSED)
                .count();
        long failed = executions.stream()
                .filter(e -> e.getStatus() == TestStatus.FAILED)
                .count();
        long error = executions.stream()
                .filter(e -> e.getStatus() == TestStatus.ERROR)
                .count();

        double passRate = total > 0 ? (passed * 100.0 / total) : 0.0;
        double failRate = total > 0 ? (failed * 100.0 / total) : 0.0;

        return ExecutionStatsDTO.builder()
                .totalExecutions(total)
                .passedExecutions(passed)
                .failedExecutions(failed)
                .errorExecutions(error)
                .passRate(Math.round(passRate * 100.0) / 100.0)
                .failRate(Math.round(failRate * 100.0) / 100.0)
                .periodStart(start)
                .periodEnd(end)
                .build();
    }

    @Override
    public List<TestTrendDTO> getExecutionTrends(LocalDate startDate, LocalDate endDate) {
        log.info("Getting execution trends from {} to {}", startDate, endDate);

        Instant start = startDate.atStartOfDay(ZoneId.systemDefault()).toInstant();
        Instant end = endDate.atTime(LocalTime.MAX).atZone(ZoneId.systemDefault()).toInstant();

        List<TestExecution> executions = testExecutionRepository.findByStartTimeBetween(start, end);

        // Group executions by date (convert Instant to LocalDate)
        Map<LocalDate, List<TestExecution>> executionsByDate = executions.stream()
                .filter(e -> e.getStartTime() != null)
                .collect(Collectors.groupingBy(e ->
                        e.getStartTime().atZone(ZoneId.systemDefault()).toLocalDate()
                ));

        List<TestTrendDTO> trends = new ArrayList<>();
        LocalDate current = startDate;

        while (!current.isAfter(endDate)) {
            List<TestExecution> dayExecutions = executionsByDate.getOrDefault(current, new ArrayList<>());

            long total = dayExecutions.size();
            long passed = dayExecutions.stream()
                    .filter(e -> e.getStatus() == TestStatus.PASSED)
                    .count();
            long failed = dayExecutions.stream()
                    .filter(e -> e.getStatus() == TestStatus.FAILED)
                    .count();
            double passRate = total > 0 ? (passed * 100.0 / total) : 0.0;

            trends.add(TestTrendDTO.builder()
                    .date(current)
                    .totalExecutions(total)
                    .passed(passed)
                    .failed(failed)
                    .passRate(Math.round(passRate * 100.0) / 100.0)
                    .build());

            current = current.plusDays(1);
        }

        return trends;
    }

    @Override
    public List<BrowserStatsDTO> getBrowserStats(LocalDate startDate, LocalDate endDate) {
        log.info("Getting browser stats from {} to {}", startDate, endDate);

        Instant start = startDate.atStartOfDay(ZoneId.systemDefault()).toInstant();
        Instant end = endDate.atTime(LocalTime.MAX).atZone(ZoneId.systemDefault()).toInstant();

        List<TestExecution> executions = testExecutionRepository.findByStartTimeBetween(start, end);

        // Group by browser (handle null browsers)
        Map<String, List<TestExecution>> executionsByBrowser = executions.stream()
                .collect(Collectors.groupingBy(e ->
                        e.getBrowser() != null ? e.getBrowser() : "Unknown"
                ));

        return executionsByBrowser.entrySet().stream()
                .map(entry -> {
                    String browser = entry.getKey();
                    List<TestExecution> browserExecutions = entry.getValue();

                    long total = browserExecutions.size();
                    long passed = browserExecutions.stream()
                            .filter(e -> e.getStatus() == TestStatus.PASSED)
                            .count();
                    long failed = browserExecutions.stream()
                            .filter(e -> e.getStatus() == TestStatus.FAILED)
                            .count();
                    double passRate = total > 0 ? (passed * 100.0 / total) : 0.0;

                    return BrowserStatsDTO.builder()
                            .browser(browser)
                            .totalExecutions(total)
                            .passed(passed)
                            .failed(failed)
                            .passRate(Math.round(passRate * 100.0) / 100.0)
                            .build();
                })
                .sorted((a, b) -> Long.compare(b.getTotalExecutions(), a.getTotalExecutions()))
                .collect(Collectors.toList());
    }

    @Override
    public DashboardReportDTO getDashboardReport(LocalDate startDate, LocalDate endDate) {
        log.info("Getting complete dashboard report from {} to {}", startDate, endDate);

        return DashboardReportDTO.builder()
                .overallStats(getExecutionStats(startDate, endDate))
                .trends(getExecutionTrends(startDate, endDate))
                .browserStats(getBrowserStats(startDate, endDate))
                .build();
    }
}