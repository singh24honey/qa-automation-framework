package com.company.qa.controller;

import com.company.qa.model.dto.ApiResponse;
import com.company.qa.model.dto.GitConfigurationDTO;
import com.company.qa.model.entity.GitConfiguration;
import com.company.qa.repository.GitConfigurationRepository;
import com.company.qa.service.git.GitService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * REST Controller for Git configuration management
 */
@RestController
@RequestMapping("/api/git/configurations")
@RequiredArgsConstructor
@Slf4j
public class GitConfigurationController {

    private final GitConfigurationRepository gitConfigurationRepository;
    private final GitService gitService;

    /**
     * Get all Git configurations
     */
    @GetMapping
    public ResponseEntity<List<GitConfigurationDTO>> getAllConfigurations() {
        log.info("Getting all Git configurations");

        List<GitConfigurationDTO> configs = gitConfigurationRepository.findAll()
                .stream()
                .map(this::toDTO)
                .collect(Collectors.toList());

        return ResponseEntity.ok(configs);
    }

    /**
     * Get active Git configurations only
     */
    @GetMapping("/active")
    public ResponseEntity<List<GitConfigurationDTO>> getActiveConfigurations() {
        log.info("Getting active Git configurations");

        List<GitConfigurationDTO> configs = gitConfigurationRepository.findByIsActiveTrueAndIsValidatedTrue()
                .stream()
                .map(this::toDTO)
                .collect(Collectors.toList());

        return ResponseEntity.ok(configs);
    }

    /**
     * Get default Git configuration
     */
    @GetMapping("/default")
    public ResponseEntity<GitConfigurationDTO> getDefaultConfiguration() {
        log.info("Getting default Git configuration");

        GitConfiguration config = gitService.getDefaultConfiguration();
        return ResponseEntity.ok(toDTO(config));
    }

    /**
     * Get Git configuration by ID
     */
    @GetMapping("/{id}")
    public ResponseEntity<GitConfigurationDTO> getConfigurationById(@PathVariable UUID id) {
        log.info("Getting Git configuration: {}", id);

        GitConfiguration config = gitConfigurationRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Configuration not found: " + id));

        return ResponseEntity.ok(toDTO(config));
    }

    /**
     * Create new Git configuration
     */
    @PostMapping
    public ResponseEntity<ApiResponse<GitConfigurationDTO>> createConfiguration(
            @RequestBody GitConfigurationDTO dto) {
        log.info("Creating new Git configuration: {}", dto.getName());

        // Check for duplicate name
        if (gitConfigurationRepository.existsByName(dto.getName())) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("Configuration with name already exists: " + dto.getName()));
        }

        GitConfiguration config = GitConfiguration.builder()
                .name(dto.getName())
                .repositoryUrl(dto.getRepositoryUrl())
                .repositoryType(dto.getRepositoryType())
                .defaultTargetBranch(dto.getDefaultTargetBranch() != null ?
                        dto.getDefaultTargetBranch() : "main")
                .branchPrefix(dto.getBranchPrefix() != null ?
                        dto.getBranchPrefix() : "AiDraft")
                .authType(dto.getAuthType())
                .authTokenSecretKey(dto.getAuthTokenSecretKey())
                .sshKeyPath(dto.getSshKeyPath())
                .committerName(dto.getCommitterName())
                .committerEmail(dto.getCommitterEmail())
                .autoCreatePr(dto.getAutoCreatePr() != null ?
                        dto.getAutoCreatePr() : true)
                .prReviewerUsernames(dto.getPrReviewerUsernames())
                .prLabels(dto.getPrLabels())
                .isActive(dto.getIsActive() != null ?
                        dto.getIsActive() : false)
                .isValidated(false)
                .build();

        GitConfiguration saved = gitConfigurationRepository.save(config);

        log.info("Created Git configuration: {} (ID: {})", saved.getName(), saved.getId());

        return ResponseEntity.ok(ApiResponse.success(
                toDTO(saved),
                "Git configuration created successfully"
        ));
    }

    /**
     * Update Git configuration
     */
    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<GitConfigurationDTO>> updateConfiguration(
            @PathVariable UUID id,
            @RequestBody GitConfigurationDTO dto) {
        log.info("Updating Git configuration: {}", id);

        GitConfiguration config = gitConfigurationRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Configuration not found: " + id));

        // Update fields
        config.setName(dto.getName());
        config.setRepositoryUrl(dto.getRepositoryUrl());
        config.setRepositoryType(dto.getRepositoryType());
        config.setDefaultTargetBranch(dto.getDefaultTargetBranch());
        config.setBranchPrefix(dto.getBranchPrefix());
        config.setAuthType(dto.getAuthType());
        config.setAuthTokenSecretKey(dto.getAuthTokenSecretKey());
        config.setSshKeyPath(dto.getSshKeyPath());
        config.setCommitterName(dto.getCommitterName());
        config.setCommitterEmail(dto.getCommitterEmail());
        config.setAutoCreatePr(dto.getAutoCreatePr());
        config.setPrReviewerUsernames(dto.getPrReviewerUsernames());
        config.setPrLabels(dto.getPrLabels());
        config.setIsActive(dto.getIsActive());

        // Mark as not validated after update
        config.setIsValidated(false);

        GitConfiguration updated = gitConfigurationRepository.save(config);

        return ResponseEntity.ok(ApiResponse.success(
                toDTO(updated),
                "Git configuration updated successfully"
        ));
    }

    /**
     * Delete Git configuration
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteConfiguration(@PathVariable UUID id) {
        log.info("Deleting Git configuration: {}", id);

        GitConfiguration config = gitConfigurationRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Configuration not found: " + id));

        gitConfigurationRepository.delete(config);

        return ResponseEntity.ok(ApiResponse.success(
                null,
                "Git configuration deleted successfully"
        ));
    }

    /**
     * Validate Git configuration
     * Tests connectivity and permissions
     */
    @PostMapping("/{id}/validate")
    public ResponseEntity<ApiResponse<GitConfigurationDTO>> validateConfiguration(@PathVariable UUID id) {
        log.info("Validating Git configuration: {}", id);

        boolean isValid = gitService.validateConfiguration(id);

        GitConfiguration config = gitConfigurationRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Configuration not found: " + id));

        String message = isValid ?
                "Git configuration validated successfully" :
                "Git configuration validation failed: " + config.getValidationError();

        return ResponseEntity.ok(ApiResponse.success(
                toDTO(config),
                message
        ));
    }

    /**
     * Toggle active status
     */
    @PatchMapping("/{id}/active")
    public ResponseEntity<ApiResponse<GitConfigurationDTO>> toggleActive(
            @PathVariable UUID id,
            @RequestParam boolean active) {
        log.info("Setting Git configuration {} active status to: {}", id, active);

        GitConfiguration config = gitConfigurationRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Configuration not found: " + id));

        config.setIsActive(active);
        GitConfiguration updated = gitConfigurationRepository.save(config);

        return ResponseEntity.ok(ApiResponse.success(
                toDTO(updated),
                "Active status updated successfully"
        ));
    }

    // ==================== HELPER METHODS ====================

    private GitConfigurationDTO toDTO(GitConfiguration config) {
        return GitConfigurationDTO.builder()
                .id(config.getId())
                .name(config.getName())
                .repositoryUrl(config.getRepositoryUrl())
                .repositoryType(config.getRepositoryType())
                .defaultTargetBranch(config.getDefaultTargetBranch())
                .branchPrefix(config.getBranchPrefix())
                .authType(config.getAuthType())
                .authTokenSecretKey(config.getAuthTokenSecretKey())
                .sshKeyPath(config.getSshKeyPath())
                .committerName(config.getCommitterName())
                .committerEmail(config.getCommitterEmail())
                .autoCreatePr(config.getAutoCreatePr())
                .prReviewerUsernames(config.getPrReviewerUsernames())
                .prLabels(config.getPrLabels())
                .isActive(config.getIsActive())
                .isValidated(config.getIsValidated())
                .lastValidationAt(config.getLastValidationAt())
                .validationError(config.getValidationError())
                .createdAt(config.getCreatedAt())
                .updatedAt(config.getUpdatedAt())
                .build();
    }
}