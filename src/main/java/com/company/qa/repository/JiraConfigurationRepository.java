package com.company.qa.repository;

import com.company.qa.model.entity.JiraConfiguration;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface JiraConfigurationRepository extends JpaRepository<JiraConfiguration, UUID> {
    Optional<JiraConfiguration> findByConfigName(String configName);
    List<JiraConfiguration> findByEnabledTrue();
    List<JiraConfiguration> findByProjectKey(String projectKey);
    boolean existsByConfigName(String configName);

    @Query("SELECT c FROM JiraConfiguration c WHERE c.configName = 'default-dev' OR c.configName = 'default'")
    Optional<JiraConfiguration> findDefaultConfig();
}