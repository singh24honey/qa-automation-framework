package com.company.qa.model.dto;

import com.company.qa.model.enums.AuthType;
import com.company.qa.model.enums.RepositoryType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * DTO for Git configuration
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GitConfigurationDTO {

    private UUID id;
    private String name;
    private String repositoryUrl;
    private RepositoryType repositoryType;
    private String defaultTargetBranch;
    private String branchPrefix;

    private AuthType authType;
    private String authTokenSecretKey;
    private String sshKeyPath;

    private String committerName;
    private String committerEmail;

    private Boolean autoCreatePr;
    private List<String> prReviewerUsernames;
    private List<String> prLabels;

    private Boolean isActive;
    private Boolean isValidated;
    private Instant lastValidationAt;
    private String validationError;

    private Instant createdAt;
    private Instant updatedAt;
}