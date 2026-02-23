package com.company.qa.service.approval;

import com.company.qa.model.entity.AIGeneratedTest;
import com.company.qa.model.entity.ApprovalRequest;
import com.company.qa.model.entity.Test;
import com.company.qa.model.enums.Priority;
import com.company.qa.model.enums.TestFramework;
import com.company.qa.repository.AIGeneratedTestRepository;
import com.company.qa.repository.TestRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * Promotes AIGeneratedTest → tests table in its own REQUIRES_NEW transaction.
 *
 * WHY A SEPARATE SERVICE?
 * ─────────────────────────────────────────────────────────────────────────
 * promoteToTestsTable() was previously a private method on ApprovalRequestService,
 * called inside the @Transactional approveRequest() method.
 *
 * The problem: the outer approveRequest() transaction can remain open for a long
 * time (e.g. while a debugger is paused, or while syncDraftFile renders the .java
 * file). During that window, the Test row is written to Hibernate's first-level
 * cache but NOT yet committed to PostgreSQL.
 *
 * publishTestApprovedEvent() — also called inside the same transaction — does a
 * fresh DB lookup to find the Test by approvalId. Because the row hasn't committed,
 * PostgreSQL (READ COMMITTED isolation) returns nothing, and the event is silently
 * skipped.
 *
 * FIX: extract the Test INSERT into REQUIRES_NEW so it commits immediately, before
 * publishTestApprovedEvent() runs. The outer approveRequest() transaction continues
 * independently and commits its own changes (ApprovalRequest status update etc.)
 * separately.
 *
 * USAGE in ApprovalRequestService.approveRequest():
 *
 *   // 1. Commit Test row NOW in its own transaction
 *   Test promotedTest = testPromotionService.promoteAndCommit(saved);
 *
 *   // 2. Now safe to look up — row is already in DB
 *   publishTestApprovedEvent(saved, promotedTest);
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class TestPromotionService {

    private final TestRepository testRepository;
    private final AIGeneratedTestRepository aiGeneratedTestRepository;

    /**
     * Promote AIGeneratedTest → tests table and commit immediately.
     *
     * Uses REQUIRES_NEW so this transaction commits independently of the caller.
     * After this method returns, the Test row is visible to all DB connections.
     *
     * @return the saved Test, or null if promotion was skipped or failed
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Test promoteAndCommit(ApprovalRequest request) {

        if (request.getAiGeneratedTestId() == null) {
            log.debug("No aiGeneratedTestId on approval {} — skipping test table promotion",
                    request.getId());
            return null;
        }

        try {
            AIGeneratedTest aiTest = aiGeneratedTestRepository
                    .findById(request.getAiGeneratedTestId())
                    .orElse(null);

            if (aiTest == null) {
                log.warn("⚠️ AIGeneratedTest {} not found — cannot promote to tests table",
                        request.getAiGeneratedTestId());
                return null;
            }

            // Duplicate guard — use List to avoid NonUniqueResultException if
            // previous debug runs created duplicate rows
            boolean alreadyExists = !testRepository
                    .findByNameAndFramework(aiTest.getTestName(), TestFramework.PLAYWRIGHT)
                    .isEmpty();

            if (alreadyExists) {
                log.info("ℹ️ Test '{}' already exists in tests table — skipping promotion",
                        aiTest.getTestName());
                // Return the existing test so publishTestApprovedEvent can still fire
                return testRepository
                        .findByNameAndFramework(aiTest.getTestName(), TestFramework.PLAYWRIGHT)
                        .get(0);
            }

            String intentJson = aiTest.getTestCodeJsonRaw();
            if (intentJson == null || intentJson.isBlank()) {
                log.warn("⚠️ AIGeneratedTest {} has no testCodeJsonRaw — cannot promote",
                        aiTest.getId());
                return null;
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

            log.info("✅ Promoted AIGeneratedTest {} → tests table as Test {} (name: {}) [committed in own txn]",
                    aiTest.getId(), saved.getId(), saved.getName());

            return saved;

        } catch (Exception e) {
            // Non-fatal — approval succeeds even if promotion fails
            log.error("❌ Failed to promote AIGeneratedTest to tests table for approval {}: {}",
                    request.getId(), e.getMessage(), e);
            return null;
        }
    }
}