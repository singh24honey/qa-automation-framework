package com.company.qa.service.execution;

import com.company.qa.event.TestApprovedEvent;
import com.company.qa.model.dto.ExecutionRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
@Slf4j
public class TestApprovedEventListener {

    private final TestExecutionService testExecutionService;

    @Async("taskExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onTestApproved(TestApprovedEvent event) {
        log.info("🚀 Auto-executing test after approval: {}", event.testName());
        try {
            ExecutionRequest request = ExecutionRequest.builder()
                    .testId(event.testId())
                    .browser("FIREFOX")
                    .environment("production")
                    .headless(true)
                    .build();
            testExecutionService.startExecution(request);
        } catch (Exception e) {
            log.error("❌ Auto-execution after approval failed for '{}': {}",
                    event.testName(), e.getMessage());
        }
    }
}