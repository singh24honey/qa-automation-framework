package com.company.qa.repository;

import com.company.qa.model.entity.RequestLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Repository
public interface RequestLogRepository extends JpaRepository<RequestLog, Long> {

    List<RequestLog> findByApiKeyIdAndCreatedAtAfter(UUID apiKeyId, Instant after);

    long countByApiKeyIdAndCreatedAtAfter(UUID apiKeyId, Instant after);
}