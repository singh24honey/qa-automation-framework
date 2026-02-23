package com.company.qa.model.dto;

import com.company.qa.model.enums.ApprovalRequestType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;
import java.util.UUID;

/**
 * DTO for creating a new approval request.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateApprovalRequestDTO {

    private ApprovalRequestType requestType;
    private String generatedContent;
    private Map<String, Object> aiResponseMetadata;

    // Test details
    private String testName;
    private String testFramework;
    private String testLanguage;
    private String targetUrl;

    // Requester details
    private UUID requestedById;
    private String requestedByName;
    private String requestedByEmail;

    // Configuration
    private Boolean autoExecuteOnApproval;
    private Integer expirationDays; // Optional, default 7
    private Boolean autoCommitOnApproval;  // ADD THIS


    // ADD THIS BLOCK before sanitization fields:
    private UUID aiGeneratedTestId;

    // Sanitization info
    private Boolean sanitizationApplied;
    private Integer redactionCount;

}