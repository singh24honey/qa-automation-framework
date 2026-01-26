package com.company.qa.model.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;

/**
 * Request to generate tests for multiple endpoints
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BatchGenerateTestRequest {

    @NotEmpty(message = "At least one endpoint ID required")
    private List<Long> endpointIds;

    @NotNull(message = "Requester ID is required")
    private UUID requesterId;

    private String framework;
    private String language;
    private String priority;
    private String justification;

    // Batch settings
    private Boolean autoApprove; // For admin users
    private Integer maxConcurrent; // Process N at a time
}