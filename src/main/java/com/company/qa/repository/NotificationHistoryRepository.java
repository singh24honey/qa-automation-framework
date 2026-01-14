package com.company.qa.repository;

import com.company.qa.model.entity.NotificationHistory;
import com.company.qa.model.enums.NotificationChannel;
import com.company.qa.model.enums.NotificationEvent;
import com.company.qa.model.enums.NotificationStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Repository
public interface NotificationHistoryRepository extends JpaRepository<NotificationHistory, UUID> {

    List<NotificationHistory> findByExecutionId(UUID executionId);

    List<NotificationHistory> findByTestId(UUID testId);

    List<NotificationHistory> findByStatus(NotificationStatus status);

    List<NotificationHistory> findByChannel(NotificationChannel channel);

    List<NotificationHistory> findByEvent(NotificationEvent event);

    Page<NotificationHistory> findAllByOrderBySentAtDesc(Pageable pageable);

    List<NotificationHistory> findBySentAtBetween(Instant start, Instant end);

    @Query("SELECT n FROM NotificationHistory n WHERE n.status = 'FAILED' AND n.retryCount < n.maxRetries")
    List<NotificationHistory> findFailedNotificationsForRetry();

    long countByStatusAndSentAtAfter(NotificationStatus status, Instant after);
}