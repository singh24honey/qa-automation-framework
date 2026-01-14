package com.company.qa.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FailurePatternDTO {

    private String errorType;
    private Integer occurrenceCount;
    private Double percentage;
    private List<String> affectedTests;
    private List<String> affectedBrowsers;
    private String recommendation;
}