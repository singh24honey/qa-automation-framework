package com.company.qa.model.enums;

/**
 * Types of approval requests.
 */
public enum ApprovalRequestType {
    /**
     * AI-generated new test code
     */
    TEST_GENERATION,

    /**
     * SelfHealingAgent verified a locator fix (all verification runs passed).
     * On approval: sync fixed rendered Java to playwright-tests/drafts/.
     */
    SELF_HEALING_FIX,

    /**
     * SelfHealingAgent exhausted all fix attempts — flagging for human review.
     * Content is the best-effort fixed INTENT_V1 JSON.
     * On approval: NO file sync — human must manually edit the draft file.
     */
    SELF_HEALING_MANUAL,

    /**
     * FlakyTestAgent verified a flakiness fix and already committed to Git branch.
     * This approval is a PR review gate.
     * On approval: NO file sync — Git branch is the source of truth.
     */
    FLAKY_FIX,

    /**
     * FlakyTestAgent exhausted all fix attempts — flagging for human review.
     * Content is the ORIGINAL broken test captured at analysis start.
     * On approval: NO file sync — writing original content would regress the file.
     */
    FLAKY_MANUAL,

    /**
     * AI-modified existing test code (pre-agent era, kept for backward compatibility).
     * @deprecated Use SELF_HEALING_FIX or FLAKY_FIX instead.
     */
    @Deprecated(since = "Phase 2", forRemoval = false)
    TEST_MODIFICATION,

    /**
     * Request to delete test (pre-agent era, kept for backward compatibility).
     * @deprecated Not used by any active workflow.
     */
    @Deprecated(since = "Phase 2", forRemoval = false)
    TEST_DELETION
}