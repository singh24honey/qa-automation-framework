package com.company.qa.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * DTO for making an approval decision.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApprovalDecisionDTO {

    /**
     * True to approve, false to reject
     */
    private Boolean approved;

    /**
     * Notes from reviewer (optional)
     */
    private String notes;

    /**
     * Reason for rejection (required if rejected)
     */
    private String rejectionReason;

    /**
     * Reviewer details
     */
    private UUID reviewerId;
    private String reviewerName;
    private String reviewerEmail;
}