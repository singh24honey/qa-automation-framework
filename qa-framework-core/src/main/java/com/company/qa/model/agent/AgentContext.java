package com.company.qa.model.agent;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Agent's working memory during execution.
 *
 * This is persisted to Redis via AgentMemoryService and
 * updated after each action.
 *
 * Contains everything the agent needs to make decisions:
 * - What actions have been taken
 * - What results were observed
 * - Current state of work products (generated code, test results, etc.)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentContext {

    /**
     * The goal this agent is working toward.
     */
    private AgentGoal goal;

    /**
     * Current iteration number (0-indexed).
     */
    @Builder.Default
    private int currentIteration = 0;

    /**
     * Maximum iterations allowed before timeout.
     */
    @Builder.Default
    private int maxIterations = 20;

    /**
     * History of all actions taken and their results.
     */
    @Builder.Default
    private List<AgentHistoryEntry> actionHistory = new ArrayList<>();

    /**
     * Work products produced during execution.
     *
     * Examples:
     * - "generatedTestCode": String (Java test code)
     * - "executionResult": ExecutionResult
     * - "failureAnalysis": String
     * - "suggestedFix": String
     */
    @Builder.Default
    private Map<String, Object> workProducts = new HashMap<>();

    /**
     * Agent-specific state variables.
     *
     * Examples for PlaywrightAgent:
     * - "jiraStory": JiraStory object
     * - "testPassed": Boolean
     * - "retryCount": Integer
     */
    @Builder.Default
    private Map<String, Object> state = new HashMap<>();

    /**
     * Total AI cost accumulated so far.
     */
    @Builder.Default
    private Double totalAICost = 0.0;

    /**
     * When execution started.
     */
    private Instant startedAt;

    /**
     * Last update timestamp.
     */
    private Instant lastUpdatedAt;

    /**
     * Add action to history.
     */
    public void addToHistory(AgentHistoryEntry entry) {
        actionHistory.add(entry);
        lastUpdatedAt = Instant.now();
    }

    /**
     * Store work product.
     */
    public void putWorkProduct(String key, Object value) {
        workProducts.put(key, value);
        lastUpdatedAt = Instant.now();
    }

    /**
     * Get work product with type casting.
     */
    @SuppressWarnings("unchecked")
    public <T> T getWorkProduct(String key, Class<T> type) {
        Object value = workProducts.get(key);
        if (value == null) {
            return null;
        }
        return (T) value;
    }

    /**
     * Update state variable.
     */
    public void putState(String key, Object value) {
        state.put(key, value);
        lastUpdatedAt = Instant.now();
    }

    /**
     * Get state variable with type casting.
     */
    @SuppressWarnings("unchecked")
    public <T> T getState(String key, Class<T> type) {
        Object value = state.get(key);
        if (value == null) {
            return null;
        }
        return (T) value;
    }

    /**
     * Increment iteration counter.
     */
    public void incrementIteration() {
        currentIteration++;
        lastUpdatedAt = Instant.now();
    }

    /**
     * Check if max iterations reached.
     */
    @JsonIgnore
    public boolean isMaxIterationsReached() {
        return currentIteration >= maxIterations;
    }

    /**
     * Add to total AI cost.
     */
    public void addAICost(double cost) {
        totalAICost += cost;
        lastUpdatedAt = Instant.now();
    }
}