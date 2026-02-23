package com.company.qa.service.approval;

import com.company.qa.event.TestApprovedEvent;
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
import com.company.qa.service.playwright.PlaywrightJavaRenderer;
import com.company.qa.service.playwright.TestIntentParser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
// Add import at top of file
import com.company.qa.model.entity.Test;
import com.company.qa.repository.TestRepository;
import com.company.qa.service.draft.DraftFileService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;

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
    private final TestRepository testRepository;
    private final DraftFileService draftFileService;
    private final TestIntentParser testIntentParser;
    private final PlaywrightJavaRenderer playwrightJavaRenderer;
    private final ApplicationEventPublisher eventPublisher;
    private final TestPromotionService testPromotionService;






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
                .autoCommitOnApproval(dto.getAutoCommitOnApproval() != null ?
                        dto.getAutoCommitOnApproval() : false)
                .build();

        // Set expiration
        int expirationDays = dto.getExpirationDays() != null ? dto.getExpirationDays() : 7;
        request.setExpiresAt(Instant.now().plus(Duration.ofDays(expirationDays)));

        if(dto.getAiGeneratedTestId()!=null){
            request.setAiGeneratedTestId(dto.getAiGeneratedTestId());
        }
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
    /**
     * Approve an approval request.
     * Called by: ApprovalRequestController POST /{id}/approve
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

        // Check if expired (was in approveRequest, missing from approve)
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

// ✅ Promote in its OWN transaction (REQUIRES_NEW) → commits immediately
// so publishTestApprovedEvent can find the row even while this outer txn is open
        Test promotedTest = testPromotionService.promoteAndCommit(saved);

        syncDraftFile(saved);


        // ✅ Trigger Git commit if configured (was in approve(), missing from approveRequest())
        // ✅ FIXED: Only trigger Git if this approval has an aiGeneratedTestId
// Agent-created approvals (from CreateApprovalRequestTool) don't set aiGeneratedTestId
// and should not attempt Git commit — they handle Git themselves via separate agent steps
        boolean shouldCommit = saved.getAiGeneratedTestId() != null          // ← ADD THIS CHECK
                && saved.getAutoCommitOnApproval() != null
                && saved.getAutoCommitOnApproval()
                && !Boolean.TRUE.equals(decision.getSkipGitCommit());

        if (shouldCommit) {
            log.info("Auto-commit enabled, triggering Git workflow for approval: {}", requestId);
            triggerGitCommit(saved);
        } else if (saved.getAiGeneratedTestId() == null) {
            log.info("Agent-managed approval — Git commit handled by agent workflow, skipping auto-commit");
        } else {
            log.info("Git commit deferred - will be triggered manually");
        }

        autoExecutePromotedTest(saved);

        //publishTestApprovedEvent(saved);

       // publishTestApprovedEvent(saved, promotedTest);

        notifyRequester(saved, true);



        log.info("✅ Approved request: {}", requestId);
        return toDTO(saved);
    }


    private void publishTestApprovedEvent(ApprovalRequest saved, Test promotedTest) {
        try {
            if (promotedTest == null) {
                log.warn("⚠️ publishTestApprovedEvent: promotedTest is null for approval {} — skipping",
                        saved.getId());
                return;
            }
            eventPublisher.publishEvent(new TestApprovedEvent(promotedTest.getId(), promotedTest.getName()));
            log.info("📢 Published TestApprovedEvent for: {} (id={})",
                    promotedTest.getName(), promotedTest.getId());
        } catch (Exception e) {
            log.warn("Failed to publish TestApprovedEvent: {}", e.getMessage());
        }
    }

    private void publishTestApprovedEvent(ApprovalRequest saved) {
        try {
            Test test = null;

            // PRIMARY: find via aiGeneratedTestId linkage (reliable, no name matching)
            if (saved.getAiGeneratedTestId() != null) {

                String uuid = saved.getId().toString().toLowerCase();

                test = testRepository.findAll().stream()
                        .filter(t -> {
                            String desc = t.getDescription();

                            System.out.println("UUID = [" + uuid + "]");
                            System.out.println("DESC = [" + desc + "]");

                            return desc != null &&
                                    desc.toLowerCase().contains(uuid);
                        })
                        .findFirst()
                        .orElse(null);
            }

            // FALLBACK: name search if no description match
            if (test == null && saved.getTestName() != null) {
                List<Test> matches = testRepository.findByNameContainingIgnoreCase(saved.getTestName());
                if (!matches.isEmpty()) test = matches.get(0);
            }

            if (test == null) {
                log.warn("⚠️ publishTestApprovedEvent: no Test found for approval {} — skipping auto-execution",
                        saved.getId());
                return;
            }

            eventPublisher.publishEvent(new TestApprovedEvent(test.getId(), test.getName()));
            log.info("📢 Published TestApprovedEvent for: {} (id={})", test.getName(), test.getId());

        } catch (Exception e) {
            log.warn("Failed to publish TestApprovedEvent: {}", e.getMessage());
        }
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

    private void autoExecutePromotedTest(ApprovalRequest request) {
        try {
            // Find the test by name — it was just promoted (or already existed)
           /* String testName = request.getTestName();
            if (testName == null) return;

            List<Test> matches = testRepository.findByNameContainingIgnoreCase(testName);
            if (matches.isEmpty()) {
                log.warn("⚠️ Cannot auto-execute — no test found with name: {}", testName);
                return;
            }

            Test test = matches.get(0);
            log.info("🚀 Auto-executing promoted test: {} (id: {})", test.getName(), test.getId());*/
           Test test = null;

            if (request.getAiGeneratedTestId() != null) {

                String uuid = request.getId().toString().toLowerCase();

                test = testRepository.findAll().stream()
                        .filter(t -> {
                            String desc = t.getDescription();
                            return desc != null &&
                                    desc.toLowerCase().contains(uuid);
                        })
                        .findFirst()
                        .orElse(null);
            }

            ExecutionRequest executionRequest = ExecutionRequest.builder()
                    .testId(test.getId())
                    .browser("FIREFOX") // Default browser for auto-execution, can be made configurable
                    .environment("production")
                    .headless(false)
                    .build();

            ExecutionResponse execution = testExecutionService.startExecution(executionRequest);
            log.info("✅ Execution started: {} for test: {}", execution.getExecutionId(), test.getName());

        } catch (Exception e) {
            // Non-fatal — approval already succeeded
            log.error("❌ Failed to auto-execute promoted test: {}", e.getMessage(), e);
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
                    .testName(request.getTestName())
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
                    .testName(request.getTestName())
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

    /**
     * After approval, create a Test entity row from the AIGeneratedTest so that
     * SelfHealingAgent and FlakyTestAgent (which read from the 'tests' table) can
     * discover and work with this test.
     *
     * The Test.content column stores the intent JSON — the same format as
     * AIGeneratedTest.testCodeJsonRaw — which PlaywrightTestExecutor can run directly.
     */
    private void promoteToTestsTable(ApprovalRequest request) {

        // ── 1. DB promotion (your existing logic — unchanged) ────────────────────
        if (request.getAiGeneratedTestId() == null) {
            log.debug("No aiGeneratedTestId on approval {} — skipping test table promotion",
                    request.getId());
            // Still attempt file sync for SELF_HEALING_FIX which has no aiGeneratedTestId
            syncDraftFile(request);
            return;
        }

        try {
            AIGeneratedTest aiTest = aiGeneratedTestRepository
                    .findById(request.getAiGeneratedTestId())
                    .orElse(null);

            if (aiTest == null) {
                log.warn("⚠️ AIGeneratedTest {} not found — cannot promote to tests table",
                        request.getAiGeneratedTestId());
                return;
            }

            // Avoid creating a duplicate row if already promoted (e.g. retry scenario)
            boolean alreadyExists = !testRepository
                    .findByNameAndFramework(aiTest.getTestName(), TestFramework.PLAYWRIGHT)
                    .isEmpty();

            if (alreadyExists) {
                log.info("ℹ️ Test '{}' already exists in tests table — skipping promotion",
                        aiTest.getTestName());
                syncDraftFile(request);   // Still sync the file even if DB row exists
                return;
            }

            // intent JSON stored in testCodeJsonRaw is exactly what PlaywrightTestExecutor
            // and AnalyzeTestStabilityTool expect in Test.content
            String intentJson = aiTest.getTestCodeJsonRaw();
            if (intentJson == null || intentJson.isBlank()) {
                log.warn("⚠️ AIGeneratedTest {} has no testCodeJsonRaw — cannot promote",
                        aiTest.getId());
                return;
            }

            Test test = Test.builder()
                    .name(aiTest.getTestName())
                    .description("AI-generated from JIRA: " + aiTest.getJiraStoryKey()
                            + " | Approval: " + request.getId())
                    .framework(TestFramework.PLAYWRIGHT)
                    .language("Java")
                    .content(intentJson)
                    .priority(Priority.MEDIUM)
                    .isActive(true)
                    .notifyOnFailure(true)
                    .notifyOnSuccess(false)
                    .build();

            Test saved = testRepository.save(test);

            log.info("✅ Promoted AIGeneratedTest {} → tests table as Test {} (name: {})",
                    aiTest.getId(), saved.getId(), saved.getName());

        } catch (Exception e) {
            // Non-fatal — approval succeeds even if promotion fails
            log.error("❌ Failed to promote AIGeneratedTest to tests table for approval {}: {}",
                    request.getId(), e.getMessage(), e);
        }

        // ── 2. File sync (new) ────────────────────────────────────────────────────
        syncDraftFile(request);
    }

    /**
     * Render the approved INTENT_V1 content to Java and write it to
     * {@code playwright-tests/drafts/} so the QA team's local run is always in sync.
     *
     * <p>Uses the same Zero-Hallucination pipeline ({@link TestIntentParser} →
     * {@link PlaywrightJavaRenderer}) as generation — no bespoke key extraction.
     */
    private void syncDraftFile(ApprovalRequest request) {
        ApprovalRequestType type = request.getRequestType();

        // Determine the INTENT_V1 JSON source based on approval type
        String intentJson;
        if (type == ApprovalRequestType.TEST_GENERATION) {
            // Source: AIGeneratedTest.testCodeJsonRaw (most reliable — full pipeline output)
            if (request.getAiGeneratedTestId() == null) return;
            AIGeneratedTest aiTest = aiGeneratedTestRepository
                    .findById(request.getAiGeneratedTestId()).orElse(null);
            if (aiTest == null) return;
            intentJson = aiTest.getTestCodeJsonRaw();

        } else if (type == ApprovalRequestType.SELF_HEALING_FIX) {
            // Source: ApprovalRequest.generatedContent (INTENT_V1 JSON from SelfHealingAgent)
            intentJson = request.getGeneratedContent();

        } else {
            // FLAKY_FIX    — Git branch already has the file, no sync needed
            // FLAKY_MANUAL — content is original broken version, writing would regress
            // *_MANUAL     — human must decide, do not auto-write
            log.debug("Skipping file sync for approval type {}", type);
            return;
        }

        if (intentJson == null || intentJson.isBlank()) {
            log.warn("⚠️ No INTENT_V1 content available for file sync on approval {}", request.getId());
            return;
        }

        try {
            // Parse → validate → render using the canonical Zero-Hallucination pipeline
            TestIntentParser.ParseResult result = testIntentParser.parse(intentJson);

            if (!result.isSuccess()) {
                log.warn("⚠️ Could not parse INTENT_V1 for approval {} (type={}) — skipping file sync: {}",
                        request.getId(), type, result.getErrorMessage());
                return;
            }

            String renderedJava   = playwrightJavaRenderer.render(result.getIntent());
            String testClassName  = result.getIntent().getTestClassName();
            String fileName       = testClassName + ".java";

            draftFileService.saveToDrafts(fileName, renderedJava);
            log.info("✅ [{}] Synced rendered Java to playwright-tests/drafts/{}", type, fileName);

        } catch (Exception e) {
            // Non-fatal — DB promotion already succeeded, file sync is best-effort
            log.warn("⚠️ File sync failed for {} approval {}: {}",
                    type, request.getId(), e.getMessage());
        }
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
