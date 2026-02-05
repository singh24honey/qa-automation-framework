package com.company.qa.execution.engine;

import com.company.qa.execution.context.ExecutionContext;

public interface ExecutionEngine {

    ExecutionResult execute(ExecutionContext context) throws Exception;

}