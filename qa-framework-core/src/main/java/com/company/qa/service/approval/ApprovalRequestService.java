package com.company.qa.service.approval;

import com.company.qa.exception.ResourceNotFoundException;
import com.company.qa.model.dto.*;
import com.company.qa.model.entity.AIGeneratedTest;
import com.company.qa.model.entity.ApprovalRequest;
import com.company.qa.model.enums.*;
import com.company.qa.repository.AIGeneratedTestRepository;
import com.company.qa.repository.ApprovalRequestRepository;
import com.company.qa.service.git.GitService;
import com.company.qa.service.notification.NotificationService;
import com.company.qa.service.TestService;
import com.company.qa.service.execution.TestExecutionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Service for managing approval requests in HITL workflow.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class ApprovalRequestService {

    private final ApprovalRequestRepository approvalRequestRepository;
    private final TestService testService;
    private final TestExecutionService testExecutionService;
    private final NotificationService notificationService;
    private final GitService gitService;
    private final AIGeneratedTestRepository aiGeneratedTestRepository;


    /**
     * Create a new approval request.
     */
    @Transactional
    public ApprovalRequestDTO createApprovalRequest(CreateApprovalRequestDTO dto) {
        log.info("Creating approval request for user: {}", dto.getRequestedById());

        ApprovalRequest request = ApprovalRequest.builder()
                .requestType(dto.getRequestType() != null ?
                        dto.getRequestType() : ApprovalRequestType.TEST_GENERATION)
                .status(ApprovalStatus.PENDING_APPROVAL)
                .generatedContent(dto.getGeneratedContent())
                .aiResponseMetadata(dto.getAiResponseMetadata())
                .testName(dto.getTestName())
                .testFramework(dto.getTestFramework())
                .testLanguage(dto.getTestLanguage())
                .targetUrl(dto.getTargetUrl())
                .requestedById(dto.getRequestedById())
                .requestedByName(dto.getRequestedByName())
                .requestedByEmail(dto.getRequestedByEmail())
                .autoExecuteOnApproval(dto.getAutoExecuteOnApproval() != null ?
                        dto.getAutoExecuteOnApproval() : false)
                .sanitizationApplied(dto.getSanitizationApplied())
                .redactionCount(dto.getRedactionCount())
                .build();

        // Set expiration
        int expirationDays = dto.getExpirationDays() != null ? dto.getExpirationDays() : 7;
        request.setExpiresAt(Instant.now().plus(Duration.ofDays(expirationDays)));

        ApprovalRequest saved = approvalRequestRepository.save(request);

        // TODO: Notify approvers
        notifyApprovers(saved);

        log.info("Created approval request: {}", saved.getId());
        return toDTO(saved);
    }

    /**
     * Get approval request by ID.
     */
    public ApprovalRequestDTO getApprovalRequest(UUID id) {
        log.info("Getting approval request: {}", id);
        ApprovalRequest request = approvalRequestRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("ApprovalRequest", id.toString()));
        return toDTO(request);
    }

    /**
     * Get all pending approval requests.
     */
    public List<ApprovalRequestDTO> getPendingApprovalRequests() {
        log.info("Getting all pending approval requests");
        List<ApprovalRequest> requests = approvalRequestRepository.findAllPendingRequests();
        return requests.stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    /**
     * Get approval requests by requester.
     */
    public List<ApprovalRequestDTO> getRequestsByRequester(UUID requesterId) {
        log.info("Getting approval requests for requester: {}", requesterId);
        List<ApprovalRequest> requests =
                approvalRequestRepository.findByRequestedByIdOrderByCreatedAtDesc(requesterId);
        return requests.stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    /**
     * Get approval requests by reviewer.
     */
    public List<ApprovalRequestDTO> getRequestsByReviewer(UUID reviewerId) {
        log.info("Getting approval requests reviewed by: {}", reviewerId);
        List<ApprovalRequest> requests =
                approvalRequestRepository.findByReviewedByIdOrderByReviewedAtDesc(reviewerId);
        return requests.stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    /**
     * Approve an approval request.
     */
    @Transactional
    public ApprovalRequestDTO approveRequest(UUID requestId, ApprovalDecisionDTO decision) {
        log.info("Approving request: {} by reviewer: {}", requestId, decision.getReviewerId());

        ApprovalRequest request = approvalRequestRepository.findById(requestId)
                .orElseThrow(() -> new ResourceNotFoundException("ApprovalRequest", requestId.toString()));

        // Validate request is pending
        if (!request.isPending()) {
            throw new IllegalStateException(
                    "Cannot approve request in status: " + request.getStatus());
        }

        // Check if expired
        if (request.isExpired()) {
            request.setStatus(ApprovalStatus.EXPIRED);
            approvalRequestRepository.save(request);
            throw new IllegalStateException("Cannot approve expired request");
        }

        // Update request
        request.setStatus(ApprovalStatus.APPROVED);
        request.setReviewedById(decision.getReviewerId());
        request.setReviewedByName(decision.getReviewerName());
        request.setReviewedByEmail(decision.getReviewerEmail());
        request.setApprovalDecisionNotes(decision.getNotes());
        request.setReviewedAt(Instant.now());

        ApprovalRequest saved = approvalRequestRepository.save(request);

        // Notify requester
        notifyRequester(saved, true);

        // Auto-execute if configured
        if (Boolean.TRUE.equals(saved.getAutoExecuteOnApproval())) {
            executeApprovedTest(saved);
        }

        log.info("Approved request: {}", requestId);
        return toDTO(saved);
    }

    /**
     * Reject an approval request.
     */
    @Transactional
    public ApprovalRequestDTO rejectRequest(UUID requestId, ApprovalDecisionDTO decision) {
        log.info("Rejecting request: {} by reviewer: {}", requestId, decision.getReviewerId());

        ApprovalRequest request = approvalRequestRepository.findById(requestId)
                .orElseThrow(() -> new ResourceNotFoundException("ApprovalRequest", requestId.toString()));

        // Validate request is pending
        if (!request.isPending()) {
            throw new IllegalStateException(
                    "Cannot reject request in status: " + request.getStatus());
        }

        // Validate rejection reason provided
        if (decision.getRejectionReason() == null || decision.getRejectionReason().isBlank()) {
            throw new IllegalArgumentException("Rejection reason is required");
        }

        // Update request
        request.setStatus(ApprovalStatus.REJECTED);
        request.setReviewedById(decision.getReviewerId());
        request.setReviewedByName(decision.getReviewerName());
        request.setReviewedByEmail(decision.getReviewerEmail());
        request.setRejectionReason(decision.getRejectionReason());
        request.setApprovalDecisionNotes(decision.getNotes());
        request.setReviewedAt(Instant.now());

        ApprovalRequest saved = approvalRequestRepository.save(request);

        // Notify requester
        notifyRequester(saved, false);

        log.info("Rejected request: {}", requestId);
        return toDTO(saved);
    }

    /**
     * Cancel an approval request (by requester).
     */
    @Transactional
    public ApprovalRequestDTO cancelRequest(UUID requestId, UUID requesterId) {
        log.info("Cancelling request: {} by requester: {}", requestId, requesterId);

        ApprovalRequest request = approvalRequestRepository.findById(requestId)
                .orElseThrow(() -> new ResourceNotFoundException("ApprovalRequest", requestId.toString()));

        // Validate requester
        if (!request.getRequestedById().equals(requesterId)) {
            throw new IllegalStateException("Only requester can cancel approval request");
        }

        // Validate request is pending
        if (!request.isPending()) {
            throw new IllegalStateException(
                    "Cannot cancel request in status: " + request.getStatus());
        }

        request.setStatus(ApprovalStatus.CANCELLED);
        ApprovalRequest saved = approvalRequestRepository.save(request);

        log.info("Cancelled request: {}", requestId);
        return toDTO(saved);
    }

    /**
     * Get approval request summary statistics.
     */
    public ApprovalRequestSummaryDTO getSummaryStatistics() {
        log.info("Getting approval request summary statistics");

        long total = approvalRequestRepository.count();
        long pending = approvalRequestRepository.countByStatus(ApprovalStatus.PENDING_APPROVAL);
        long approved = approvalRequestRepository.countByStatus(ApprovalStatus.APPROVED);
        long rejected = approvalRequestRepository.countByStatus(ApprovalStatus.REJECTED);
        long expired = approvalRequestRepository.countByStatus(ApprovalStatus.EXPIRED);
        long cancelled = approvalRequestRepository.countByStatus(ApprovalStatus.CANCELLED);

        double approvalRate = (approved + rejected) > 0 ?
                (double) approved / (approved + rejected) * 100 : 0.0;

        // Calculate average review time
        List<ApprovalRequest> reviewed = approvalRequestRepository
                .findByStatusOrderByCreatedAtDesc(ApprovalStatus.APPROVED);
        long avgReviewTimeMinutes = (long) reviewed.stream()
                .filter(r -> r.getReviewedAt() != null)
                .mapToLong(r -> Duration.between(r.getCreatedAt(), r.getReviewedAt()).toMinutes())
                .average()
                .orElse(0);

        // Find oldest pending
        List<ApprovalRequest> pendingRequests = approvalRequestRepository.findAllPendingRequests();
        long oldestPendingAgeHours = pendingRequests.isEmpty() ? 0 :
                Duration.between(pendingRequests.get(0).getCreatedAt(), Instant.now()).toHours();

        return ApprovalRequestSummaryDTO.builder()
                .totalRequests(total)
                .pendingRequests(pending)
                .approvedRequests(approved)
                .rejectedRequests(rejected)
                .expiredRequests(expired)
                .cancelledRequests(cancelled)
                .approvalRate(approvalRate)
                .avgReviewTimeMinutes(avgReviewTimeMinutes)
                .oldestPendingAgeHours(oldestPendingAgeHours)
                .build();
    }

    /**
     * Scheduled task to mark expired requests.
     * Runs every hour.
     */
    @Scheduled(cron = "0 0 * * * *") // Every hour
    @Transactional
    public void markExpiredRequests() {
        log.info("Checking for expired approval requests");

        List<ApprovalRequest> expired =
                approvalRequestRepository.findExpiredPendingRequests(Instant.now());

        for (ApprovalRequest request : expired) {
            request.setStatus(ApprovalStatus.EXPIRED);
            approvalRequestRepository.save(request);
            log.info("Marked approval request as expired: {}", request.getId());

            // Notify requester
            notifyRequesterOfExpiration(request);
        }

        log.info("Marked {} requests as expired", expired.size());
    }

    // ========== Private Helper Methods (CORRECTED) ==========

    private void executeApprovedTest(ApprovalRequest request) {
        try {
            log.info("Auto-executing approved test: {}", request.getId());

            // Create test from approved content
            TestDto testDto = TestDto.builder()
                    .name(request.getTestName())
                    .description("AI-generated test - Approved by " + request.getReviewedByName())
                    .content(request.getGeneratedContent())
                    .framework(com.company.qa.model.enums.TestFramework.valueOf(
                            request.getTestFramework()))
                    .language(request.getTestLanguage())
                    .build();

            TestDto createdTest = testService.createTest(testDto);
            request.setExecutedTestId(createdTest.getId());

            // Execute the test - FIXED: removed triggeredBy field
            ExecutionRequest executionRequest = ExecutionRequest.builder()
                    .testId(createdTest.getId())
                    .browser(com.company.qa.model.enums.BrowserType.CHROME.name())
                    .environment("production")
                    .headless(false)
                    .build();

            ExecutionResponse execution = testExecutionService.startExecution(executionRequest);
            request.setExecutionId(execution.getExecutionId());

            approvalRequestRepository.save(request);

            log.info("Auto-executed test: {} with execution: {}",
                    createdTest.getId(), execution.getExecutionId());

        } catch (Exception e) {
            log.error("Failed to auto-execute approved test: {}", e.getMessage(), e);
        }
    }

    private void notifyApprovers(ApprovalRequest request) {
        try {
            String subject = "New Approval Request: " + request.getTestName();
            String content = String.format(
                    "A new approval request requires your review.\n\n" +
                            "Request ID: %s\n" +
                            "Type: %s\n" +
                            "Requested by: %s\n" +
                            "Expires at: %s\n\n" +
                            "Please review at your earliest convenience.",
                    request.getId(),
                    request.getRequestType(),
                    request.getRequestedByName(),
                    request.getExpiresAt()
            );

            // TODO: Get approver emails from configuration or database
            // For now, just log
            log.info("Would notify approvers about request: {}", request.getId());
            log.info("Notification content: {}", content);

            // If you want to actually send notifications, configure approver emails first
            // Then uncomment this:
        /*
        NotificationRequest notificationRequest = NotificationRequest.builder()
                .channel(NotificationChannel.EMAIL)
                .recipient("approvers@example.com") // TODO: Get from config
                .subject(subject)
                .content(content)
                .event(NotificationEvent.SCHEDULED_TEST_START) // Or create new event type
                .testId(request.getExecutedTestId())
                .executionId(request.getExecutionId())
                .build();

        notificationService.sendNotification(notificationRequest);
        */

        } catch (Exception e) {
            log.error("Failed to notify approvers: {}", e.getMessage(), e);
        }
    }

    private void notifyRequester(ApprovalRequest request, boolean approved) {
        try {
            if (request.getRequestedByEmail() == null) {
                log.warn("Cannot notify requester - email not provided for request: {}",
                        request.getId());
                return;
            }

            String subject = approved ?
                    "Test Approved: " + request.getTestName() :
                    "Test Rejected: " + request.getTestName();

            String content = approved ?
                    String.format("Your test '%s' has been approved by %s.\n\n" +
                                    "Approval notes: %s",
                            request.getTestName(),
                            request.getReviewedByName(),
                            request.getApprovalDecisionNotes() != null ?
                                    request.getApprovalDecisionNotes() : "None") :
                    String.format("Your test '%s' was rejected by %s.\n\n" +
                                    "Reason: %s\n\n" +
                                    "Notes: %s",
                            request.getTestName(),
                            request.getReviewedByName(),
                            request.getRejectionReason(),
                            request.getApprovalDecisionNotes() != null ?
                                    request.getApprovalDecisionNotes() : "None");

            // Use your actual NotificationService interface - FIXED
            NotificationRequest notificationRequest = NotificationRequest.builder()
                    .channels(Collections.singletonList(NotificationChannel.EMAIL))
                    .recipients(Collections.singletonList(request.getRequestedByEmail()))
                    .subject(subject)
                    .content(content)
                    .event(approved ? NotificationEvent.TEST_COMPLETED : NotificationEvent.TEST_FAILED)
                    .testId(request.getExecutedTestId())
                    .executionId(request.getExecutionId())
                    .build();

            notificationService.sendNotification(notificationRequest);

            log.info("Sent {} notification to requester: {}",
                    approved ? "approval" : "rejection", request.getRequestedByEmail());

        } catch (Exception e) {
            log.error("Failed to notify requester: {}", e.getMessage(), e);
        }
    }

    private void notifyRequesterOfExpiration(ApprovalRequest request) {
        try {
            if (request.getRequestedByEmail() == null) {
                log.warn("Cannot notify requester of expiration - email not provided for request: {}",
                        request.getId());
                return;
            }

            String subject = "Approval Request Expired: " + request.getTestName();
            String content = String.format(
                    "Your approval request for '%s' has expired without review.\n\n" +
                            "Request ID: %s\n" +
                            "Created at: %s\n" +
                            "Expired at: %s\n\n" +
                            "You can create a new approval request if still needed.",
                    request.getTestName(),
                    request.getId(),
                    request.getCreatedAt(),
                    request.getExpiresAt()
            );

            // Use your actual NotificationService interface - FIXED
            NotificationRequest notificationRequest = NotificationRequest.builder()
                    .channels(Collections.singletonList(NotificationChannel.EMAIL))
                    .recipients(Collections.singletonList(request.getRequestedByEmail()))
                    .subject(subject)
                    .content(content)
                    .event(request.getStatus().equals(ApprovalStatus.APPROVED) ? NotificationEvent.TEST_COMPLETED : NotificationEvent.TEST_FAILED)
                    .testId(request.getExecutedTestId())
                    .executionId(request.getExecutionId())
                    .build();

            notificationService.sendNotification(notificationRequest);

            log.info("Sent expiration notification to requester: {}", request.getRequestedByEmail());

        } catch (Exception e) {
            log.error("Failed to notify requester of expiration: {}", e.getMessage(), e);
        }
    }

    private ApprovalRequestDTO toDTO(ApprovalRequest entity) {
        long daysUntilExpiration = entity.getExpiresAt() != null ?
                Duration.between(Instant.now(), entity.getExpiresAt()).toDays() : 0;

        return ApprovalRequestDTO.builder()
                .id(entity.getId())
                .requestType(entity.getRequestType())
                .status(entity.getStatus())
                .generatedContent(entity.getGeneratedContent())
                .aiResponseMetadata(entity.getAiResponseMetadata())
                .testName(entity.getTestName())
                .testFramework(entity.getTestFramework())
                .testLanguage(entity.getTestLanguage())
                .targetUrl(entity.getTargetUrl())
                .requestedById(entity.getRequestedById())
                .requestedByName(entity.getRequestedByName())
                .requestedByEmail(entity.getRequestedByEmail())
                .reviewedById(entity.getReviewedById())
                .reviewedByName(entity.getReviewedByName())
                .reviewedByEmail(entity.getReviewedByEmail())
                .approvalDecisionNotes(entity.getApprovalDecisionNotes())
                .rejectionReason(entity.getRejectionReason())
                .createdAt(entity.getCreatedAt())
                .expiresAt(entity.getExpiresAt())
                .reviewedAt(entity.getReviewedAt())
                .autoExecuteOnApproval(entity.getAutoExecuteOnApproval())
                .executedTestId(entity.getExecutedTestId())
                .executionId(entity.getExecutionId())
                .sanitizationApplied(entity.getSanitizationApplied())
                .redactionCount(entity.getRedactionCount())
                .isPending(entity.isPending())
                .isExpired(entity.isExpired())
                .daysUntilExpiration(daysUntilExpiration)
                .autoCommitOnApproval(entity.getAutoCommitOnApproval())
                .gitOperationTriggered(entity.getGitOperationTriggered())
                .gitOperationSuccess(entity.getGitOperationSuccess())
                .gitErrorMessage(entity.getGitErrorMessage())
                .aiGeneratedTestId(entity.getAiGeneratedTestId())
                .gitBranch(entity.getGitBranch())
                .gitCommitSha(entity.getGitCommitSha())
                .gitPrUrl(entity.getGitPrUrl())
                .gitCommittedAt(entity.getGitCommittedAt())

                // Computed fields
                .isPending(entity.isPending())
                .isExpired(entity.isExpired())
                //.daysUntilExpiration(calculateDaysUntilExpiration(entity.getExpiresAt()))
                .build();
    }

    @Transactional
    public ApprovalRequestDTO approve(UUID id, ApprovalDecisionDTO decision) {
        log.info("Approving request: {}", id);

        ApprovalRequest request = approvalRequestRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Approval request", id.toString()));

        // Validate current status
        if (request.getStatus() != ApprovalStatus.PENDING_APPROVAL) {
            throw new IllegalStateException(
                    "Cannot approve request in status: " + request.getStatus()
            );
        }

        // Update approval status
        request.setStatus(ApprovalStatus.APPROVED);
        request.setReviewedById(decision.getReviewerId());
        request.setReviewedByName(decision.getReviewerName());
        request.setReviewedByEmail(decision.getReviewerEmail());
        request.setReviewedAt(Instant.now());
        request.setApprovalDecisionNotes(decision.getNotes());

        ApprovalRequest savedRequest = approvalRequestRepository.save(request);

        // Trigger Git commit if configured and not skipped
        boolean shouldCommit = request.getAutoCommitOnApproval() != null &&
                request.getAutoCommitOnApproval() &&
                !Boolean.TRUE.equals(decision.getSkipGitCommit());

        if (shouldCommit) {
            log.info("Auto-commit enabled, triggering Git workflow");
            triggerGitCommit(savedRequest);
        } else {
            log.info("Git commit deferred - will be triggered manually");
        }

        // Notify stakeholders
        notifyApprovers(savedRequest);

        // Auto-execute test if configured
        if (request.getAutoExecuteOnApproval() && request.getExecutedTestId() != null) {
            executeApprovedTest(savedRequest);
        }

        return toDTO(savedRequest);
    }

    /**
     * Manually trigger Git commit for an approved request
     * This allows flexibility to commit later even if auto-commit was disabled
     *
     * @param approvalId Approval request ID
     * @return Git operation result
     */
    @Transactional
    public GitOperationResult manuallyTriggerGitCommit(UUID approvalId) {
        log.info("Manually triggering Git commit for approval: {}", approvalId);

        ApprovalRequest request = approvalRequestRepository.findById(approvalId)
                .orElseThrow(() -> new ResourceNotFoundException("Approval request", approvalId.toString()));

        // Validate that request is approved
        if (request.getStatus() != ApprovalStatus.APPROVED) {
            throw new IllegalStateException(
                    "Cannot commit unapproved request. Status: " + request.getStatus()
            );
        }

        // Check if already committed
        if (Boolean.TRUE.equals(request.getGitOperationTriggered()) &&
                Boolean.TRUE.equals(request.getGitOperationSuccess())) {
            throw new IllegalStateException(
                    "Git commit already completed successfully for this approval"
            );
        }

        return triggerGitCommit(request);
    }

    /**
     * Retry a failed Git operation
     *
     * @param approvalId Approval request ID
     * @return Git operation result
     */
    @Transactional
    public GitOperationResult retryGitOperation(UUID approvalId) {
        log.info("Retrying Git operation for approval: {}", approvalId);

        ApprovalRequest request = approvalRequestRepository.findById(approvalId)
                .orElseThrow(() -> new ResourceNotFoundException("Approval request", approvalId.toString()));

        // Validate that previous attempt failed
        if (!Boolean.TRUE.equals(request.getGitOperationTriggered())) {
            throw new IllegalStateException("No Git operation has been triggered yet");
        }

        if (Boolean.TRUE.equals(request.getGitOperationSuccess())) {
            throw new IllegalStateException("Previous Git operation succeeded, no retry needed");
        }

        log.info("Retrying failed Git operation. Previous error: {}", request.getGitErrorMessage());

        return triggerGitCommit(request);
    }

    /**
     * Trigger Git commit workflow
     * This is the core method that orchestrates the Git integration
     */
    private GitOperationResult triggerGitCommit(ApprovalRequest request) {
        log.info("Triggering Git commit for approval: {}", request.getId());

        try {
            // Mark Git operation as triggered
            request.setGitOperationTriggered(true);
            approvalRequestRepository.save(request);

            // Get AI generated test
            AIGeneratedTest aiTest = getAIGeneratedTest(request);

            // Extract story key from JIRA story or metadata
            String storyKey = extractStoryKey(aiTest);

            // Find draft files for this test
            List<String> filePaths = findDraftFiles(aiTest);

            if (filePaths.isEmpty()) {
                throw new IllegalStateException(
                        "No draft files found for AI test: " + aiTest.getId()
                );
            }

            // Build commit message
            String commitMessage = buildCommitMessage(aiTest, request);

            // Build PR title and description
            String prTitle = buildPRTitle(storyKey, aiTest);
            String prDescription = buildPRDescription(aiTest, request);

            // Create Git operation request
            GitOperationRequest gitRequest = GitOperationRequest.builder()
                    .aiGeneratedTestId(aiTest.getId())
                    .approvalRequestId(request.getId())
                    .storyKey(storyKey)
                    .filePaths(filePaths)
                    .commitMessage(commitMessage)
                    .prTitle(prTitle)
                    .prDescription(prDescription)
                    .build();

            // Execute Git workflow (creates branch, commits files, creates PR)
            GitOperationResult result = gitService.executeCompleteWorkflow(gitRequest, null);

            // Update approval request with Git details
            if (result.isSuccess()) {
                request.setGitOperationSuccess(true);
                request.setGitBranch(result.getBranchName());
                request.setGitCommitSha(result.getCommitSha());
                request.setGitPrUrl(result.getPrUrl());
                request.setGitCommittedAt(Instant.now());
                request.setGitErrorMessage(null);

                log.info("Git commit successful - Branch: {}, PR: {}",
                        result.getBranchName(), result.getPrUrl());
            } else {
                request.setGitOperationSuccess(false);
                request.setGitErrorMessage(result.getErrorMessage());

                log.error("Git commit failed: {}", result.getErrorMessage());
            }

            approvalRequestRepository.save(request);

            return result;

        } catch (Exception e) {
            log.error("Failed to trigger Git commit", e);

            request.setGitOperationSuccess(false);
            request.setGitErrorMessage(e.getMessage());
            approvalRequestRepository.save(request);

            return GitOperationResult.failure(
                    com.company.qa.model.enums.GitOperationType.COMMIT,
                    "Failed to trigger Git commit: " + e.getMessage()
            );
        }
    }

    /**
     * Get AI generated test for the approval request
     */
    private AIGeneratedTest getAIGeneratedTest(ApprovalRequest request) {
        if (request.getAiGeneratedTestId() == null) {
            throw new IllegalStateException(
                    "No AI generated test associated with this approval request"
            );
        }

        return aiGeneratedTestRepository.findById(request.getAiGeneratedTestId())
                .orElseThrow(() -> new IllegalStateException(
                        "AI generated test not found: " + request.getAiGeneratedTestId()
                ));
    }

    /**
     * Extract story key from AI test
     */
    private String extractStoryKey(AIGeneratedTest aiTest) {
        // Try JIRA story key first
        if (aiTest.getJiraStoryKey() != null) {
            return aiTest.getJiraStoryKey();
        }

        // Try extracting from metadata
        /*if (aiTest.getMetadata() != null) {
            // Assuming metadata is stored as JSON
            // You might need to adjust this based on your actual metadata structure
            return "TEST-" + aiTest.getId().toString().substring(0, 8);
        }*/
        if (aiTest.getDraftFolderPath() != null) {
            // e.g., /tmp/qa-framework/drafts/SCRUM-7/
            String path = aiTest.getDraftFolderPath();
            if (path.contains("/drafts/")) {
                String[] parts = path.split("/drafts/");
                if (parts.length > 1) {
                    String keyPart = parts[1].split("/")[0];
                    if (keyPart.matches("[A-Z]+-\\d+")) {
                        return keyPart;
                    }
                }
            }
        }

        // Fallback: use test ID
        return "AI-" + aiTest.getId().toString().substring(0, 8);
    }

    /**
     * Find draft files for the test
     */
    private List<String> findDraftFiles(AIGeneratedTest aiTest) throws IOException {
        List<String> files = new ArrayList<>();

        // Get draft folder path from AI test
        String draftFolder = aiTest.getDraftFolderPath();
        if (draftFolder == null) {
            log.warn("No draft folder path set for AI test: {}", aiTest.getId());
            return files;
        }

        Path draftPath = Paths.get(draftFolder);
        if (!Files.exists(draftPath)) {
            log.warn("Draft folder does not exist: {}", draftFolder);
            return files;
        }

        // Find all files in draft folder
        try (Stream<Path> paths = Files.walk(draftPath)) {
            paths.filter(Files::isRegularFile)
                    .forEach(path -> files.add(path.toAbsolutePath().toString()));
        }

        log.info("Found {} draft files for AI test {}", files.size(), aiTest.getId());
        return files;
    }

    /**
     * Build commit message
     */
    private String buildCommitMessage(AIGeneratedTest aiTest, ApprovalRequest request) {
        StringBuilder message = new StringBuilder();
        message.append("feat: Add AI-generated tests for ").append(extractStoryKey(aiTest)).append("\n\n");
        message.append("Test Name: ").append(aiTest.getTestName()).append("\n");
        message.append("Test Type: ").append(aiTest.getTestType()).append("\n");
        message.append("Framework: ").append(aiTest.getTestFramework()).append("\n");

        if (aiTest.getQualityScore() != null) {
            message.append("Quality Score: ").append(aiTest.getQualityScore()).append("\n");
        }

        message.append("Generated: ").append(aiTest.getGeneratedAt()).append("\n");
        message.append("AI Provider: ").append(aiTest.getAiProvider()).append("\n");
        message.append("Approved by: ").append(request.getReviewedByName()).append("\n");
        message.append("\n[AI-Generated] [Approved]");

        return message.toString();
    }

    /**
     * Build PR title
     */
    private String buildPRTitle(String storyKey, AIGeneratedTest aiTest) {
        return String.format("Add AI-generated tests for %s: %s",
                storyKey, aiTest.getTestName());
    }

    /**
     * Build PR description
     */
    private String buildPRDescription(AIGeneratedTest aiTest, ApprovalRequest request) {
        StringBuilder description = new StringBuilder();
        description.append("## AI-Generated Test\n\n");
        description.append("**Story**: ").append(extractStoryKey(aiTest)).append("\n");
        description.append("**Test Name**: ").append(aiTest.getTestName()).append("\n");
        description.append("**Test Type**: ").append(aiTest.getTestType()).append("\n");
        description.append("**Framework**: ").append(aiTest.getTestFramework()).append("\n\n");

        if (aiTest.getQualityScore() != null) {
            description.append("**Quality Score**: ").append(aiTest.getQualityScore()).append("/100\n\n");
        }

        description.append("### AI Generation Details\n\n");
        description.append("- **Provider**: ").append(aiTest.getAiProvider()).append("\n");
        description.append("- **Model**: ").append(aiTest.getAiModel()).append("\n");
        description.append("- **Generated**: ").append(aiTest.getGeneratedAt()).append("\n\n");

        description.append("### Approval Details\n\n");
        description.append("- **Approved by**: ").append(request.getReviewedByName()).append("\n");
        description.append("- **Approved at**: ").append(request.getReviewedAt()).append("\n");

        if (request.getApprovalDecisionNotes() != null) {
            description.append("- **Notes**: ").append(request.getApprovalDecisionNotes()).append("\n");
        }

        description.append("\n### Review Checklist\n\n");
        description.append("- [ ] Test logic is correct\n");
        description.append("- [ ] Follows coding standards\n");
        description.append("- [ ] No hardcoded values\n");
        description.append("- [ ] Proper error handling\n");
        description.append("- [ ] Test is executable\n");

        return description.toString();
    }

    // ... rest of existing methods ...
}
