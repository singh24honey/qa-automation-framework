package com.company.qa.service.notification;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(value = "notification.retry.enabled", havingValue = "true", matchIfMissing = true)
public class NotificationRetryScheduler {

    private final NotificationService notificationService;

    // Retry every 5 minutes
    @Scheduled(fixedDelay = 300000)
    public void retryFailedNotifications() {
        log.debug("Running scheduled notification retry");

        try {
            notificationService.retryFailedNotifications();
        } catch (Exception e) {
            log.error("Failed to retry notifications: {}", e.getMessage(), e);
        }
    }
}