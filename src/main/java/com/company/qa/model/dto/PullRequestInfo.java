package com.company.qa.model.dto;

import com.company.qa.model.enums.PullRequestStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;

/**
 * Pull Request information DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PullRequestInfo {

    private Integer number;
    private String url;
    private String title;
    private String description;
    private PullRequestStatus status;
    private String sourceBranch;
    private String targetBranch;
    private List<String> reviewers;
    private List<String> labels;
    private Instant createdAt;
    private Instant mergedAt;
}