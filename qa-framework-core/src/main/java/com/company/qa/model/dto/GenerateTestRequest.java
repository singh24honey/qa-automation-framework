package com.company.qa.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * Request to generate test from OpenAPI endpoint
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GenerateTestRequest {

    /**
     * User's custom instructions
     */
    @NotBlank(message = "User prompt is required")
    private String userPrompt;

    /**
     * Test framework to use
     */
    @NotBlank(message = "Framework is required")
    private String framework;

    /**
     * Programming language
     */
    @NotBlank(message = "Language is required")
    private String language;

    /**
     * Who is requesting this test
     */
    @NotNull(message = "Requester ID is required")
    private UUID requesterId;

    /**
     * Priority for approval
     */
    private String priority; // HIGH, MEDIUM, LOW

    /**
     * Business justification
     */
    private String justification;
}