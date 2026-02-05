package com.company.qa.execution.context;

import com.company.qa.model.dto.TestScript;
import com.company.qa.model.enums.TestFramework;
import com.company.qa.model.dto.RetryConfig;
import lombok.Builder;
import lombok.Getter;

import java.time.Instant;
import java.util.UUID;

@Getter
@Builder
public class ExecutionContext {

    // Execution identity
    private final UUID executionId;
    private final UUID testId;

    // Test definition
    private final TestFramework framework;
    private final String selector; // tags, suite name, feature path

    // Runtime configuration
    private final String environment;
    private final String browser;
    private final boolean headless;

    // Execution behavior
    private final RetryConfig retryConfig;

    // Trigger metadata
    private final String triggeredBy;
    private final Instant triggeredAt;
    private final TestScript testScript;


}