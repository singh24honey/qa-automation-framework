package com.company.qa.repository;

import com.company.qa.model.entity.ApiKey;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ApiKeyRepository extends JpaRepository<ApiKey, UUID> {

    Optional<ApiKey> findByKeyValue(String keyValue);

    List<ApiKey> findByIsActiveTrue();

    boolean existsByKeyValue(String keyValue);
}