package com.company.qa.repository;


import com.company.qa.model.entity.GitConfiguration;
import com.company.qa.model.enums.RepositoryType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for GitConfiguration entity
 */
@Repository
public interface GitConfigurationRepository extends JpaRepository<GitConfiguration, UUID> {

    /**
     * Find active Git configuration by name
     */
    Optional<GitConfiguration> findByNameAndIsActiveTrue(String name);

    /**
     * Find all active configurations
     */
    List<GitConfiguration> findByIsActiveTrue();

    /**
     * Find all validated and active configurations
     */
    List<GitConfiguration> findByIsActiveTrueAndIsValidatedTrue();

    /**
     * Find all validated and active configurations
     */
    List<GitConfiguration> findByIsActiveFalse();

    /**
     * Find by repository type
     */
    List<GitConfiguration> findByRepositoryTypeAndIsActiveTrue(RepositoryType repositoryType);

    /**
     * Find default configuration (first active and validated)
     */
    default Optional<GitConfiguration> findDefaultConfiguration() {
        return findByIsActiveTrueAndIsValidatedTrue().stream().findFirst();
    }

    /**
     * Check if a configuration name already exists
     */
    boolean existsByName(String name);


}