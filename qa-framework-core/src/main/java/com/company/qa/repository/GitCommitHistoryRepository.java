package com.company.qa.repository;


import com.company.qa.model.entity.GitCommitHistory;
import com.company.qa.model.enums.GitOperationStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for GitCommitHistory entity
 */
@Repository
public interface GitCommitHistoryRepository extends JpaRepository<GitCommitHistory, UUID> {

    /**
     * Find all commits for a specific AI generated test
     */
    List<GitCommitHistory> findByAiGeneratedTestIdOrderByCreatedAtDesc(UUID aiGeneratedTestId);

    /**
     * Find commits by branch name
     */
    List<GitCommitHistory> findByBranchNameOrderByCreatedAtDesc(String branchName);

    /**
     * Find commits by operation status
     */
    List<GitCommitHistory> findByOperationStatusOrderByCreatedAtDesc(GitOperationStatus status);

    /**
     * Find failed commits for retry
     */
    @Query("SELECT g FROM GitCommitHistory g WHERE g.operationStatus = 'FAILED' " +
            "AND g.retryCount < :maxRetries ORDER BY g.createdAt DESC")
    List<GitCommitHistory> findFailedCommitsForRetry(@Param("maxRetries") int maxRetries);

    /**
     * Find commits by approval request
     */
    List<GitCommitHistory> findByApprovalRequestIdOrderByCreatedAtDesc(UUID approvalRequestId);

    /**
     * Find commits by Git configuration
     */
    List<GitCommitHistory> findByGitConfigurationIdOrderByCreatedAtDesc(UUID gitConfigurationId);

    /**
     * Find commits in date range
     */
    List<GitCommitHistory> findByCreatedAtBetweenOrderByCreatedAtDesc(Instant startDate, Instant endDate);

    /**
     * Find latest commit for a branch
     */
    Optional<GitCommitHistory> findFirstByBranchNameOrderByCreatedAtDesc(String branchName);

    /**
     * Count commits by status
     */
    long countByOperationStatus(GitOperationStatus status);

    /**
     * Get statistics by operation type
     */
    @Query("SELECT g.operationType, g.operationStatus, COUNT(g) FROM GitCommitHistory g " +
            "GROUP BY g.operationType, g.operationStatus")
    List<Object[]> getOperationStatistics();
}