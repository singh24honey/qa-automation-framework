package com.company.qa.model.dto;

import com.company.qa.model.enums.ApprovalStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Summary statistics for approval requests.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApprovalRequestSummaryDTO {

    private Long totalRequests;
    private Long pendingRequests;
    private Long approvedRequests;
    private Long rejectedRequests;
    private Long expiredRequests;
    private Long cancelledRequests;

    private Double approvalRate; // Percentage
    private Long avgReviewTimeMinutes;
    private Long oldestPendingAgeHours;
}