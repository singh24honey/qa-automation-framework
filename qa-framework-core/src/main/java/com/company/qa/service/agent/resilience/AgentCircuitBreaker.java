package com.company.qa.service.agent.resilience;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Circuit Breaker pattern for agent tool execution.
 *
 * Prevents cascading failures by temporarily disabling tools
 * that are consistently failing.
 *
 * States:
 * - CLOSED: Normal operation, requests pass through
 * - OPEN: Tool is failing, requests rejected immediately
 * - HALF_OPEN: Testing if tool has recovered
 */
@Slf4j
@Component
public class AgentCircuitBreaker {

    private static final int FAILURE_THRESHOLD = 5;
    private static final long TIMEOUT_MS = 60_000; // 1 minute

    private final Map<String, CircuitState> circuits = new ConcurrentHashMap<>();

    public enum State {
        CLOSED,
        OPEN,
        HALF_OPEN
    }

    private static class CircuitState {
        State state = State.CLOSED;
        AtomicInteger failureCount = new AtomicInteger(0);
        Instant lastFailureTime;
        Instant openedAt;
    }

    /**
     * Check if request should be allowed through.
     */
    public boolean allowRequest(String toolName) {
        CircuitState circuit = circuits.computeIfAbsent(toolName, k -> new CircuitState());

        synchronized (circuit) {
            switch (circuit.state) {
                case CLOSED:
                    return true;

                case OPEN:
                    // Check if timeout has elapsed
                    if (Instant.now().toEpochMilli() - circuit.openedAt.toEpochMilli() > TIMEOUT_MS) {
                        log.info("Circuit breaker for {} transitioning to HALF_OPEN", toolName);
                        circuit.state = State.HALF_OPEN;
                        return true;
                    }
                    log.warn("Circuit breaker OPEN for {}, rejecting request", toolName);
                    return false;

                case HALF_OPEN:
                    return true;

                default:
                    return true;
            }
        }
    }

    /**
     * Record successful execution.
     */
    public void recordSuccess(String toolName) {
        CircuitState circuit = circuits.get(toolName);
        if (circuit == null) return;

        synchronized (circuit) {
            if (circuit.state == State.HALF_OPEN) {
                log.info("Circuit breaker for {} transitioning to CLOSED (recovered)", toolName);
                circuit.state = State.CLOSED;
                circuit.failureCount.set(0);
            }
        }
    }

    /**
     * Record failed execution.
     */
    public void recordFailure(String toolName) {
        CircuitState circuit = circuits.computeIfAbsent(toolName, k -> new CircuitState());

        synchronized (circuit) {
            circuit.lastFailureTime = Instant.now();
            int failures = circuit.failureCount.incrementAndGet();

            if (circuit.state == State.HALF_OPEN) {
                log.warn("Circuit breaker for {} failed in HALF_OPEN, reopening", toolName);
                circuit.state = State.OPEN;
                circuit.openedAt = Instant.now();
            } else if (failures >= FAILURE_THRESHOLD && circuit.state == State.CLOSED) {
                log.error("Circuit breaker for {} OPENING after {} failures", toolName, failures);
                circuit.state = State.OPEN;
                circuit.openedAt = Instant.now();
            }
        }
    }

    /**
     * Get current state of circuit.
     */
    public State getState(String toolName) {
        CircuitState circuit = circuits.get(toolName);
        return circuit != null ? circuit.state : State.CLOSED;
    }

    /**
     * Reset circuit (for testing).
     */
    public void reset(String toolName) {
        circuits.remove(toolName);
    }
}