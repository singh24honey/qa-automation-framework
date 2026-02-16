package com.company.qa.model.enums;

/**
 * All possible actions an agent can take.
 *
 * Used for:
 * - Approval rules (which actions need human approval)
 * - Audit logging (what the agent did)
 * - Budget tracking (cost per action type)
 */
public enum AgentActionType {

    // ========== JIRA Actions ==========
    FETCH_JIRA_STORY,
    UPDATE_JIRA_STATUS,
    ADD_JIRA_COMMENT,

    // ========== AI Actions ==========
    GENERATE_TEST_CODE,
    ANALYZE_FAILURE,
    SUGGEST_FIX,
    PLAN_NEXT_STEP,

    DISCOVER_LOCATOR,

    // ========== Test Execution Actions ==========
    EXECUTE_TEST,
    VALIDATE_TEST,
    ANALYZE_TEST_STABILITY,

    // ========== File System Actions ==========
    READ_FILE,
    WRITE_FILE,
    DELETE_FILE,
    MODIFY_FILE,

    // ========== Git Actions ==========
    CREATE_BRANCH,
    COMMIT_CHANGES,
    CREATE_PULL_REQUEST,
    MERGE_PR,

    // ========== Registry Actions ==========
    QUERY_ELEMENT_REGISTRY,
    QUERY_PAGE_OBJECT_REGISTRY,
    UPDATE_ELEMENT_REGISTRY,

    // ========== Approval Actions ==========
    REQUEST_APPROVAL,
    WAIT_FOR_APPROVAL,

    // ========== Analytics Actions ==========
    QUERY_TEST_ANALYTICS,
    GENERATE_REPORT,

    // ========== Meta Actions ==========
    INITIALIZE,
    FINALIZE,
    ABORT,
    COMPLETE, RETRY_ACTION,
    EXTRACT_BROKEN_LOCATOR
}