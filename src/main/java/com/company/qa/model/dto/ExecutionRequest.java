package com.company.qa.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExecutionRequest {

    private UUID testId;
    private String browser;  // CHROME, FIREFOX
    private String environment;
    private Map<String, String> parameters;
    private Boolean headless;
}