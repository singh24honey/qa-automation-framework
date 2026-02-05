package com.company.qa.execution.reconciliation;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class ExternalExecutionResult {

    private String externalExecutionRef;

    private boolean success;
    private long durationMs;

    private String errorMessage;
    private String logUrl;

    private List<String> screenshotUrls;
}