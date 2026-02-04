package com.company.qa.controller;


import com.company.qa.model.dto.GitCommitHistoryDTO;
import com.company.qa.model.dto.GitStatisticsDTO;
import com.company.qa.model.entity.GitCommitHistory;
import com.company.qa.model.enums.GitOperationStatus;
import com.company.qa.repository.GitCommitHistoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * REST Controller for Git commit history and statistics
 */
@RestController
@RequestMapping("/api/git")
@RequiredArgsConstructor
@Slf4j
@CrossOrigin(origins = "*")
public class GitHistoryController {

    private final GitCommitHistoryRepository gitCommitHistoryRepository;

    /**
     * Get all commit history
     */
    @GetMapping("/history")
    public ResponseEntity<List<GitCommitHistoryDTO>> getAllHistory(
            @RequestParam(required = false) GitOperationStatus status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endDate) {

        log.info("Fetching Git commit history - status: {}, dates: {} to {}", status, startDate, endDate);

        List<GitCommitHistory> history;

        if (startDate != null && endDate != null) {
            history = gitCommitHistoryRepository.findByCreatedAtBetweenOrderByCreatedAtDesc(
                    startDate.toInstant(ZoneOffset.UTC),
                    endDate.toInstant(ZoneOffset.UTC)
            );
        } else if (status != null) {
            history = gitCommitHistoryRepository.findByOperationStatusOrderByCreatedAtDesc(status);
        } else {
            history = gitCommitHistoryRepository.findAll();
        }

        List<GitCommitHistoryDTO> dtos = history.stream()
                .map(this::toDTO)
                .collect(Collectors.toList());

        return ResponseEntity.ok(dtos);
    }

    /**
     * Get commit history for a specific AI test
     */
    @GetMapping("/history/test/{testId}")
    public ResponseEntity<List<GitCommitHistoryDTO>> getHistoryByTest(@PathVariable UUID testId) {
        log.info("Fetching Git history for test: {}", testId);

        List<GitCommitHistoryDTO> history = gitCommitHistoryRepository
                .findByAiGeneratedTestIdOrderByCreatedAtDesc(testId)
                .stream()
                .map(this::toDTO)
                .collect(Collectors.toList());

        return ResponseEntity.ok(history);
    }

    /**
     * Get commit history for a branch
     */
    @GetMapping("/history/branch/{branchName}")
    public ResponseEntity<List<GitCommitHistoryDTO>> getHistoryByBranch(
            @PathVariable String branchName) {

        log.info("Fetching Git history for branch: {}", branchName);

        List<GitCommitHistoryDTO> history = gitCommitHistoryRepository
                .findByBranchNameOrderByCreatedAtDesc(branchName)
                .stream()
                .map(this::toDTO)
                .collect(Collectors.toList());

        return ResponseEntity.ok(history);
    }

    /**
     * Get failed operations for retry
     */
    @GetMapping("/history/failed")
    public ResponseEntity<List<GitCommitHistoryDTO>> getFailedOperations(
            @RequestParam(defaultValue = "3") int maxRetries) {

        log.info("Fetching failed Git operations with max retries: {}", maxRetries);

        List<GitCommitHistoryDTO> history = gitCommitHistoryRepository
                .findFailedCommitsForRetry(maxRetries)
                .stream()
                .map(this::toDTO)
                .collect(Collectors.toList());

        return ResponseEntity.ok(history);
    }

    /**
     * Get Git operation statistics
     */
    @GetMapping("/statistics")
    public ResponseEntity<GitStatisticsDTO> getStatistics() {
        log.info("Fetching Git operation statistics");

        long totalOperations = gitCommitHistoryRepository.count();
        long successCount = gitCommitHistoryRepository.countByOperationStatus(GitOperationStatus.SUCCESS);
        long failedCount = gitCommitHistoryRepository.countByOperationStatus(GitOperationStatus.FAILED);
        long pendingCount = gitCommitHistoryRepository.countByOperationStatus(GitOperationStatus.PENDING);

        double successRate = totalOperations > 0
                ? (successCount * 100.0 / totalOperations)
                : 0.0;

        GitStatisticsDTO stats = GitStatisticsDTO.builder()
                .totalOperations(totalOperations)
                .successfulOperations(successCount)
                .failedOperations(failedCount)
                .pendingOperations(pendingCount)
                .successRate(successRate)
                .build();

        return ResponseEntity.ok(stats);
    }

    private GitCommitHistoryDTO toDTO(GitCommitHistory entity) {
        return GitCommitHistoryDTO.builder()
                .id(entity.getId())
                .aiGeneratedTestId(entity.getAiGeneratedTest() != null
                        ? entity.getAiGeneratedTest().getId() : null)
                .gitConfigurationId(entity.getGitConfiguration().getId())
                .approvalRequestId(entity.getApprovalRequest() != null
                        ? entity.getApprovalRequest().getId() : null)
                .branchName(entity.getBranchName())
                .commitSha(entity.getCommitSha())
                .commitMessage(entity.getCommitMessage())
                .filesCommitted(entity.getFilesCommitted())
                .prNumber(entity.getPrNumber())
                .prUrl(entity.getPrUrl())
                .prStatus(entity.getPrStatus())
                .operationStatus(entity.getOperationStatus())
                .operationType(entity.getOperationType())
                .errorMessage(entity.getErrorMessage())
                .retryCount(entity.getRetryCount())
                .totalLinesAdded(entity.getTotalLinesAdded())
                .totalLinesDeleted(entity.getTotalLinesDeleted())
                .totalFilesCount(entity.getTotalFilesCount())
                .createdAt(entity.getCreatedAt())
                .build();
    }
}