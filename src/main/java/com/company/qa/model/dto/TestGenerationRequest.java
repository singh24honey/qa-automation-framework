package com.company.qa.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TestGenerationRequest {

    private String description;
    private String targetUrl;
    private String framework; // SELENIUM, PLAYWRIGHT, etc.
    private String language;  // java, python, etc.
    private String additionalContext;
}