package com.company.qa.model.enums;

/**
 * Agent execution status.
 */
public enum AgentStatus {

    /**
     * Agent is currently running and executing actions.
     */
    RUNNING,

    /**
     * Agent is waiting for human approval before continuing.
     */
    WAITING_FOR_APPROVAL,

    /**
     * Agent successfully achieved its goal.
     */
    SUCCEEDED,

    /**
     * Agent failed to achieve its goal.
     */
    FAILED,

    /**
     * Agent was stopped by user or system.
     */
    STOPPED,

    /**
     * Agent exceeded max iterations without achieving goal.
     */
    TIMEOUT,

    /**
     * Agent exceeded budget limits.
     */
    BUDGET_EXCEEDED
}