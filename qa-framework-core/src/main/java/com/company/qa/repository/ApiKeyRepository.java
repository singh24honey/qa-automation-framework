package com.company.qa.repository;

import com.company.qa.model.entity.ApiKey;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ApiKeyRepository extends JpaRepository<ApiKey, UUID> {

    Optional<ApiKey> findByKeyValue(String keyValue);

    List<ApiKey> findByIsActiveTrue();

    boolean existsByKeyValue(String keyValue);

    /**
     * Direct SQL UPDATE for lastUsedAt — bypasses JPA optimistic locking (@Version check).
     *
     * Using findById → setField → save causes ObjectOptimisticLockingFailureException
     * when many concurrent requests update the same API key (e.g. UI polling + agent actions).
     * lastUsedAt is a non-critical tracking field; we don't need version protection here.
     *
     * Also skips the update when the key was used within the last 30 seconds to reduce
     * unnecessary DB writes under high request rates.
     */
    @Modifying
    @Transactional
    @Query("UPDATE ApiKey a SET a.lastUsedAt = :now " +
            "WHERE a.id = :id " +
            "AND (a.lastUsedAt IS NULL OR a.lastUsedAt < :threshold)")
    int updateLastUsedAt(@Param("id") UUID id,
                         @Param("now") Instant now,
                         @Param("threshold") Instant threshold);
}