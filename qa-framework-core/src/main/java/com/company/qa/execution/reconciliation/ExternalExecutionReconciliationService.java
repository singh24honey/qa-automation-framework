package com.company.qa.execution.reconciliation;

import com.company.qa.model.entity.TestExecution;
import com.company.qa.model.enums.TestStatus;
import com.company.qa.repository.TestExecutionRepository;
import com.company.qa.service.quality.TestQualityHistoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
@RequiredArgsConstructor
public class ExternalExecutionReconciliationService {

    private final TestExecutionRepository testExecutionRepository;
    private final TestQualityHistoryService qualityHistoryService;

    @Transactional
    public void reconcile(ExternalExecutionResult result) {

        TestExecution execution = testExecutionRepository
                .findByExternalExecutionRef(result.getExternalExecutionRef())
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "Unknown external execution ref: " +
                                        result.getExternalExecutionRef()
                        )
                );

        // Ignore duplicate callbacks
        if (execution.getStatus() != TestStatus.RUNNING) {
            return;
        }

        execution.setEndTime(Instant.now());
        execution.setDuration((int) result.getDurationMs());
        execution.setStatus(result.isSuccess()
                ? TestStatus.PASSED
                : TestStatus.FAILED
        );

        execution.setErrorDetails(result.getErrorMessage());
        execution.setLogUrl(result.getLogUrl());

        if (result.getScreenshotUrls() != null) {
            execution.setScreenshotUrls(
                    result.getScreenshotUrls().toArray(new String[0])
            );
        }

        testExecutionRepository.save(execution);

        // Feed analytics & history
        qualityHistoryService.recordExecutionHistory(execution);
    }
}