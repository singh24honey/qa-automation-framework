package com.company.qa.service.execution;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
@Slf4j
public class ExecutionCancellationService {

    private final Set<UUID> cancelledExecutions = ConcurrentHashMap.newKeySet();

    public void cancelExecution(UUID executionId) {
        log.info("Marking execution for cancellation: {}", executionId);
        cancelledExecutions.add(executionId);
    }

    public boolean isCancelled(UUID executionId) {
        return cancelledExecutions.contains(executionId);
    }

    public void clearCancellation(UUID executionId) {
        log.debug("Clearing cancellation flag for: {}", executionId);
        cancelledExecutions.remove(executionId);
    }

    public int getCancelledCount() {
        return cancelledExecutions.size();
    }
}