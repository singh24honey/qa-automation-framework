package com.company.qa.execution.engine;

import com.company.qa.execution.context.ExecutionContext;

public interface CiTriggerClient {

    /**
     * Triggers an external CI execution.
     *
     * @return external execution reference (job id, run id, etc.)
     */
    String triggerExecution(ExecutionContext context);
}