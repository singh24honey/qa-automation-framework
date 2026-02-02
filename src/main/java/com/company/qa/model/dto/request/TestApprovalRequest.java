package com.company.qa.model.dto.request;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/**
 * Request DTO for test approval/rejection.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TestApprovalRequest {

    @NotNull(message = "Test ID is required")
    private UUID testId;

    @NotNull(message = "Approval decision is required")
    private ApprovalDecision decision;

    @NotBlank(message = "Reviewer name is required")
    private String reviewedBy;

    private String comments;

    public enum ApprovalDecision {
        APPROVE,
        REJECT,
        REQUEST_CHANGES
    }
}