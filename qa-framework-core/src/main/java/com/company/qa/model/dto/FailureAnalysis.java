package com.company.qa.model.dto;

import com.company.qa.model.enums.FailureType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FailureAnalysis {

    private FailureType failureType;
    private String errorMessage;
    private String suggestion;
    private Boolean shouldRetry;
    private Integer stepNumber;
    private String stackTrace;
}