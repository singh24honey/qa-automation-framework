package com.company.qa.model.enums;

/**
 * Status of approval request.
 */
public enum ApprovalStatus {
    /**
     * Waiting for approval
     */
    PENDING_APPROVAL,

    /**
     * Approved by reviewer
     */
    APPROVED,

    /**
     * Rejected by reviewer
     */
    REJECTED,

    /**
     * Expired (not reviewed in time)
     */
    EXPIRED,

    /**
     * Cancelled by requester
     */
    CANCELLED
}