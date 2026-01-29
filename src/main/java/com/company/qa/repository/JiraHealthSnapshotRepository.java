package com.company.qa.repository;

import com.company.qa.model.entity.JiraHealthSnapshot;
import com.company.qa.model.enums.HealthStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface JiraHealthSnapshotRepository extends JpaRepository<JiraHealthSnapshot, UUID> {
    Optional<JiraHealthSnapshot> findTopByConfigIdOrderByCheckedAtDesc(UUID configId);

    List<JiraHealthSnapshot> findByConfigIdAndCheckedAtBetweenOrderByCheckedAtDesc(
            UUID configId, LocalDateTime start, LocalDateTime end
    );

    @Query("SELECT COUNT(h) FROM JiraHealthSnapshot h " +
            "WHERE h.config.id = :configId AND h.status = :status AND h.checkedAt > :since")
    long countByConfigAndStatusSince(
            @Param("configId") UUID configId,
            @Param("status") HealthStatus status,
            @Param("since") LocalDateTime since
    );
}