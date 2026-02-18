package com.company.qa.service.agent;

import com.company.qa.model.agent.entity.AgentExecution;
import com.company.qa.model.enums.AgentStatus;
import com.company.qa.repository.AgentExecutionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * Cleans up orphaned agent executions on startup.
 *
 * Problem: AgentOrchestrator tracks running agents in an in-memory ConcurrentHashMap.
 * When the server restarts, that map is wiped but the DB still has every past
 * execution stuck at RUNNING status forever - causing the "78 running agents" bug.
 *
 * Solution: On every startup, find all RUNNING or WAITING_FOR_APPROVAL executions
 * and mark them STOPPED with an explanatory message. They are unreachable - the
 * CompletableFuture is gone.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class AgentStartupCleanupService {

    private final AgentExecutionRepository executionRepository;

    /**
     * Runs after the application is fully started.
     * Marks all orphaned executions so the UI shows accurate running counts.
     */
    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void cleanupOrphanedExecutions() {
        List<AgentExecution> orphaned = executionRepository.findRunningAgents();

        if (orphaned.isEmpty()) {
            log.info("✅ Agent startup cleanup: no orphaned executions found");
            return;
        }

        log.warn("⚠️ Agent startup cleanup: found {} orphaned execution(s) - marking as STOPPED", orphaned.size());

        for (AgentExecution execution : orphaned) {
            execution.setStatus(AgentStatus.STOPPED);
            execution.setErrorMessage(
                    "Execution interrupted: server restarted while agent was running. " +
                            "Trigger a new execution to retry."
            );
            execution.setCompletedAt(Instant.now());
            executionRepository.save(execution);

            log.info("  → Stopped orphaned execution: {} (type={}, startedAt={})",
                    execution.getId(), execution.getAgentType(), execution.getStartedAt());
        }

        log.info("✅ Agent startup cleanup complete: {} execution(s) marked as STOPPED", orphaned.size());
    }
}