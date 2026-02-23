package com.company.qa.service.approval;

import com.company.qa.model.dto.request.TestApprovalRequest;
import com.company.qa.model.dto.request.TestApprovalRequest.ApprovalDecision;
import com.company.qa.model.dto.response.TestApprovalResponse;
import com.company.qa.model.entity.AIGeneratedTest;
import com.company.qa.model.entity.AIGeneratedTest.TestGenerationStatus;
import com.company.qa.exception.ApprovalException;
import com.company.qa.repository.AIGeneratedTestRepository;
import com.company.qa.service.TestFileWriterService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Service for managing test approval workflow.
 *
 * Workflow:
 * 1. QA reviews generated tests in DRAFT or PENDING_REVIEW status
 * 2. QA approves/rejects via approval endpoint
 * 3. Approved tests are committed to final folder
 * 4. Rejected tests are marked for regeneration
 */
/**
 * Approval service for the pre-agent-era {@code AIGeneratedTest} workflow.
 *
 * @deprecated System 1 (pre-agent era). Superseded by
 *             {@link com.company.qa.service.approval.ApprovalRequestService}
 *             which handles all agent-generated approvals, Git integration,
 *             and {@code promoteToTestsTable()} file sync.
 *             Only called from deprecated {@code AITestGenerationController}.
 *             Will be removed in a future release.
 */
@Deprecated(since = "Phase 2", forRemoval = false)
@Service
@Slf4j
@RequiredArgsConstructor
public class TestApprovalService {

    private final AIGeneratedTestRepository testRepository;
    private final TestFileWriterService fileWriterService;

    /**
     * Process approval/rejection decision.
     */
    @Transactional
    public TestApprovalResponse processApproval(TestApprovalRequest request) {
        log.info("Processing approval for test ID: {} by {}",
                request.getTestId(), request.getReviewedBy());

        // Fetch test
        AIGeneratedTest test = testRepository.findById(request.getTestId())
                .orElseThrow(() -> new ApprovalException("Test not found: " + request.getTestId()));

        // Validate test is in reviewable state
        if (!test.isPendingReview()) {
            throw new ApprovalException(
                    "Test is not in reviewable state. Current status: " + test.getStatus());
        }

        // Process based on decision
        switch (request.getDecision()) {
            case APPROVE:
                return approveTest(test, request);
            case REJECT:
                return rejectTest(test, request);
            case REQUEST_CHANGES:
                return requestChanges(test, request);
            default:
                throw new ApprovalException("Unknown approval decision: " + request.getDecision());
        }
    }

    /**
     * Approve test and commit to final folder.
     */
    private TestApprovalResponse approveTest(AIGeneratedTest test, TestApprovalRequest request) {
        log.info("Approving test: {}", test.getTestName());

        try {
            // Mark as approved
            test.markAsApproved(request.getReviewedBy(), request.getComments());
            testRepository.save(test);

            // Commit files to final location
            String committedPath = fileWriterService.commitApprovedTest(test);

            // Update status to COMMITTED
            test.markAsCommitted(committedPath);
            testRepository.save(test);

            log.info("Test approved and committed: {} -> {}", test.getTestName(), committedPath);

            return TestApprovalResponse.builder()
                    .testId(test.getId())
                    .jiraStoryKey(test.getJiraStoryKey())
                    .testName(test.getTestName())
                    .status(test.getStatus())
                    .reviewedBy(test.getReviewedBy())
                    .reviewedAt(test.getReviewedAt())
                    .reviewComments(test.getReviewComments())
                    .committedFolderPath(committedPath)
                    .success(true)
                    .message("Test approved and committed successfully")
                    .build();

        } catch (Exception e) {
            throw new ApprovalException("Failed to approve test: " + e.getMessage(), e);
        }
    }

    /**
     * Reject test and optionally delete draft files.
     */
    private TestApprovalResponse rejectTest(AIGeneratedTest test, TestApprovalRequest request) {
        log.info("Rejecting test: {}", test.getTestName());

        // Mark as rejected
        test.markAsRejected(request.getReviewedBy(), request.getComments());
        testRepository.save(test);

        // Delete draft files
        fileWriterService.deleteDraftFiles(test);

        log.info("Test rejected: {}", test.getTestName());

        return TestApprovalResponse.builder()
                .testId(test.getId())
                .jiraStoryKey(test.getJiraStoryKey())
                .testName(test.getTestName())
                .status(test.getStatus())
                .reviewedBy(test.getReviewedBy())
                .reviewedAt(test.getReviewedAt())
                .reviewComments(test.getReviewComments())
                .success(true)
                .message("Test rejected")
                .build();
    }

    /**
     * Request changes - mark for regeneration.
     */
    private TestApprovalResponse requestChanges(AIGeneratedTest test, TestApprovalRequest request) {
        log.info("Requesting changes for test: {}", test.getTestName());

        // Mark status back to DRAFT for regeneration
        test.setStatus(TestGenerationStatus.DRAFT);
        test.setReviewedBy(request.getReviewedBy());
        test.setReviewedAt(LocalDateTime.now());
        test.setReviewComments(request.getComments());
        testRepository.save(test);

        return TestApprovalResponse.builder()
                .testId(test.getId())
                .jiraStoryKey(test.getJiraStoryKey())
                .testName(test.getTestName())
                .status(test.getStatus())
                .reviewedBy(test.getReviewedBy())
                .reviewedAt(test.getReviewedAt())
                .reviewComments(test.getReviewComments())
                .success(true)
                .message("Changes requested - test marked for regeneration")
                .build();
    }

    /**
     * Get all tests pending review.
     */
    @Transactional(readOnly = true)
    public List<AIGeneratedTest> getPendingReviews() {
        return testRepository.findPendingReviews();
    }

    /**
     * Get tests by reviewer.
     */
    @Transactional(readOnly = true)
    public List<AIGeneratedTest> getTestsByReviewer(String reviewer) {
        return testRepository.findByReviewedBy(reviewer);
    }

    /**
     * Batch approve multiple tests.
     */
    @Transactional
    public List<TestApprovalResponse> batchApprove(List<TestApprovalRequest> requests) {
        log.info("Processing batch approval for {} tests", requests.size());

        return requests.stream()
                .map(this::processApproval)
                .toList();
    }
}