package com.company.qa.execution.engine;

import com.company.qa.model.dto.FailureAnalysis;
import com.company.qa.model.dto.TestScript;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class ExecutionResult {

    private final boolean success;
    private final long durationMs;

    private final String errorMessage;
    private final String logUrl;

    private final List<String> screenshotUrls;
    private final TestScript testScript;
    private List<FailureAnalysis> failureHistory;
    private final String externalExecutionRef;

}