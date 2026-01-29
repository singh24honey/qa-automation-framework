package com.company.qa.repository;

import com.company.qa.model.entity.JiraApiAuditLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface JiraApiAuditLogRepository extends JpaRepository<JiraApiAuditLog, UUID> {
    Optional<JiraApiAuditLog> findByRequestId(String requestId);

    Page<JiraApiAuditLog> findByConfigIdOrderByCreatedAtDesc(UUID configId, Pageable pageable);

    @Query("SELECT COUNT(a) FROM JiraApiAuditLog a " +
            "WHERE a.config.id = :configId AND a.statusCode >= 400 AND a.createdAt > :since")
    long countFailedRequestsSince(
            @Param("configId") UUID configId,
            @Param("since") LocalDateTime since
    );

    long countByConfigIdAndRateLimitedTrueAndCreatedAtAfter(UUID configId, LocalDateTime since);
}