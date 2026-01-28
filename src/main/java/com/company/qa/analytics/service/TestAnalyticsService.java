package com.company.qa.analytics.service;

import com.company.qa.analytics.model.FailureFingerprint;
import com.company.qa.analytics.model.TestAnalyticsSnapshot;
import com.company.qa.model.entity.TestExecution;
import com.company.qa.model.enums.TestStatus;
import com.company.qa.repository.TestExecutionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class TestAnalyticsService {

    private final TestExecutionRepository testExecutionRepository;

    public TestAnalyticsSnapshot updateAnalytics(TestExecution execution) {

        List<TestExecution> history =
                testExecutionRepository.findByTestId(execution.getTestId());

        long total = history.size();
        long passed = history.stream().filter(e -> e.getStatus().equals(TestStatus.PASSED)).count();
        long failed = history.stream().filter(e -> e.getStatus().equals(TestStatus.FAILED)).count();

        Set<FailureFingerprint> uniqueFailures = new HashSet<>();

        history.stream()
                .filter(e -> e.getStatus().equals(TestStatus.FAILED))
                .forEach(e ->
                        uniqueFailures.add(
                                FailureFingerprint.from(
                                        e.getErrorDetails(),
                                        null
                                )
                        )
                );

        double flakiness = total == 0
                ? 0.0
                : (double) uniqueFailures.size() / total;

        double confidence = total == 0
                ? 0.0
                : (double) passed / total;

        return TestAnalyticsSnapshot.builder()
                .totalRuns(total)
                .passCount(passed)
                .failCount(failed)
                .uniqueFailureCount(uniqueFailures.size())
                .flakinessScore(round(flakiness))
                .confidenceScore(round(confidence))
                .build();
    }

    private double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}