package com.company.qa.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FailureAnalysisRequest {

    private UUID executionId;
    private String testName;
    private String errorMessage;
    private String stackTrace;
    private String testCode;
    private String screenshotUrl;
}