package com.company.qa.controller;

import com.company.qa.model.dto.*;
import com.company.qa.service.approval.ApprovalRequestService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * REST Controller for Human-in-the-Loop approval workflow.
 */
@RestController
@RequestMapping("/api/v1/approvals")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Approval Workflow", description = "Human-in-the-loop approval for AI-generated tests")
public class ApprovalRequestController {

    private final ApprovalRequestService approvalRequestService;

    /**
     * Create a new approval request.
     */
    @PostMapping
    @Operation(summary = "Create approval request",
            description = "Create a new approval request for AI-generated content")
    public ResponseEntity<ApiResponse<ApprovalRequestDTO>> createApprovalRequest(
            @RequestBody CreateApprovalRequestDTO request) {

        log.info("POST /api/v1/approvals - Creating approval request");

        ApprovalRequestDTO created = approvalRequestService.createApprovalRequest(request);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(created, "Approval request created successfully"));
    }

    /**
     * Get approval request by ID.
     */
    @GetMapping("/{id}")
    @Operation(summary = "Get approval request",
            description = "Get approval request by ID")
    public ResponseEntity<ApiResponse<ApprovalRequestDTO>> getApprovalRequest(
            @PathVariable UUID id) {

        log.info("GET /api/v1/approvals/{}", id);

        ApprovalRequestDTO approvalRequest = approvalRequestService.getApprovalRequest(id);

        return ResponseEntity.ok(ApiResponse.success(approvalRequest));
    }

    /**
     * Get all pending approval requests.
     */
    @GetMapping("/pending")
    @Operation(summary = "Get pending approvals",
            description = "Get all approval requests pending review")
    public ResponseEntity<ApiResponse<List<ApprovalRequestDTO>>> getPendingApprovals() {

        log.info("GET /api/v1/approvals/pending");

        List<ApprovalRequestDTO> pending = approvalRequestService.getPendingApprovalRequests();

        return ResponseEntity.ok(ApiResponse.success(pending));
    }

    /**
     * Get approval requests by requester.
     */
    @GetMapping("/requester/{requesterId}")
    @Operation(summary = "Get requests by requester",
            description = "Get all approval requests created by a specific user")
    public ResponseEntity<ApiResponse<List<ApprovalRequestDTO>>> getRequestsByRequester(
            @PathVariable UUID requesterId) {

        log.info("GET /api/v1/approvals/requester/{}", requesterId);

        List<ApprovalRequestDTO> requests =
                approvalRequestService.getRequestsByRequester(requesterId);

        return ResponseEntity.ok(ApiResponse.success(requests));
    }

    /**
     * Get approval requests by reviewer.
     */
    @GetMapping("/reviewer/{reviewerId}")
    @Operation(summary = "Get requests by reviewer",
            description = "Get all approval requests reviewed by a specific user")
    public ResponseEntity<ApiResponse<List<ApprovalRequestDTO>>> getRequestsByReviewer(
            @PathVariable UUID reviewerId) {

        log.info("GET /api/v1/approvals/reviewer/{}", reviewerId);

        List<ApprovalRequestDTO> requests =
                approvalRequestService.getRequestsByReviewer(reviewerId);

        return ResponseEntity.ok(ApiResponse.success(requests));
    }

    /**
     * Approve an approval request.
     */
    @PostMapping("/{id}/approve")
    @Operation(summary = "Approve request",
            description = "Approve an approval request")
    public ResponseEntity<ApiResponse<ApprovalRequestDTO>> approveRequest(
            @PathVariable UUID id,
            @RequestBody ApprovalDecisionDTO decision) {

        log.info("POST /api/v1/approvals/{}/approve", id);

        decision.setApproved(true);
        ApprovalRequestDTO approved = approvalRequestService.approveRequest(id, decision);

        return ResponseEntity.ok(ApiResponse.success(approved, "Request approved successfully"));
    }

    /**
     * Reject an approval request.
     */
    @PostMapping("/{id}/reject")
    @Operation(summary = "Reject request",
            description = "Reject an approval request")
    public ResponseEntity<ApiResponse<ApprovalRequestDTO>> rejectRequest(
            @PathVariable UUID id,
            @RequestBody ApprovalDecisionDTO decision) {

        log.info("POST /api/v1/approvals/{}/reject", id);

        decision.setApproved(false);
        ApprovalRequestDTO rejected = approvalRequestService.rejectRequest(id, decision);

        return ResponseEntity.ok(ApiResponse.success(rejected, "Request rejected"));
    }

    /**
     * Cancel an approval request.
     */
    @DeleteMapping("/{id}")
    @Operation(summary = "Cancel request",
            description = "Cancel a pending approval request")
    public ResponseEntity<ApiResponse<ApprovalRequestDTO>> cancelRequest(
            @PathVariable UUID id,
            @Parameter(description = "Requester ID")
            @RequestHeader("X-User-Id") String requesterId) {

        log.info("DELETE /api/v1/approvals/{}", id);

        UUID requesterUUID = UUID.fromString(requesterId);
        ApprovalRequestDTO cancelled =
                approvalRequestService.cancelRequest(id, requesterUUID);

        return ResponseEntity.ok(ApiResponse.success(cancelled, "Request cancelled"));
    }

    /**
     * Get approval statistics.
     */
    @GetMapping("/statistics")
    @Operation(summary = "Get statistics",
            description = "Get approval request summary statistics")
    public ResponseEntity<ApiResponse<ApprovalRequestSummaryDTO>> getStatistics() {

        log.info("GET /api/v1/approvals/statistics");

        ApprovalRequestSummaryDTO summary = approvalRequestService.getSummaryStatistics();

        return ResponseEntity.ok(ApiResponse.success(summary));
    }
}