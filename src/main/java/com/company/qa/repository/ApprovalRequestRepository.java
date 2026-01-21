package com.company.qa.repository;

import com.company.qa.model.entity.ApprovalRequest;
import com.company.qa.model.enums.ApprovalStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Repository for ApprovalRequest entities.
 */
@Repository
public interface ApprovalRequestRepository extends JpaRepository<ApprovalRequest, UUID> {

    /**
     * Find all requests by status.
     */
    List<ApprovalRequest> findByStatusOrderByCreatedAtDesc(ApprovalStatus status);

    /**
     * Find all pending requests - CORRECTED METHOD.
     */
    @Query("SELECT ar FROM ApprovalRequest ar WHERE ar.status = 'PENDING_APPROVAL' " +
            "ORDER BY ar.createdAt ASC")
    List<ApprovalRequest> findAllPendingRequestsOrderByCreatedAtAsc();

    /**
     * Find pending requests created before a certain time.
     */
    List<ApprovalRequest> findByStatusAndCreatedAtBefore(
            ApprovalStatus status, Instant createdBefore);

    /**
     * Find requests by requester.
     */
    List<ApprovalRequest> findByRequestedByIdOrderByCreatedAtDesc(UUID requestedById);

    /**
     * Find requests by reviewer.
     */
    List<ApprovalRequest> findByReviewedByIdOrderByReviewedAtDesc(UUID reviewedById);

    /**
     * Find expired pending requests.
     */
    @Query("SELECT ar FROM ApprovalRequest ar WHERE ar.status = 'PENDING_APPROVAL' " +
            "AND ar.expiresAt < :now")
    List<ApprovalRequest> findExpiredPendingRequests(@Param("now") Instant now);

    /**
     * Count requests by status.
     */
    long countByStatus(ApprovalStatus status);

    /**
     * Find all pending requests ordered by creation time.
     */
    @Query("SELECT ar FROM ApprovalRequest ar WHERE ar.status = 'PENDING_APPROVAL' " +
            "ORDER BY ar.createdAt ASC")
    List<ApprovalRequest> findAllPendingRequests();

    /**
     * Find recent requests (last N days).
     */
    @Query("SELECT ar FROM ApprovalRequest ar WHERE ar.createdAt >= :since " +
            "ORDER BY ar.createdAt DESC")
    List<ApprovalRequest> findRecentRequests(@Param("since") Instant since);

    /**
     * Get approval statistics.
     */
    @Query("SELECT COUNT(ar) as total, " +
            "SUM(CASE WHEN ar.status = 'PENDING_APPROVAL' THEN 1 ELSE 0 END) as pending, " +
            "SUM(CASE WHEN ar.status = 'APPROVED' THEN 1 ELSE 0 END) as approved, " +
            "SUM(CASE WHEN ar.status = 'REJECTED' THEN 1 ELSE 0 END) as rejected " +
            "FROM ApprovalRequest ar")
    Object[] getApprovalStatistics();
}