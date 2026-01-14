package com.company.qa.service.notification;

import com.company.qa.model.dto.NotificationRequest;
import com.company.qa.model.dto.NotificationResponse;
import com.company.qa.model.entity.TestExecution;

import java.util.List;
import java.util.UUID;

public interface NotificationService {

    // Send notification
    List<NotificationResponse> sendNotification(NotificationRequest request);

    // Notify on execution events
    void notifyExecutionComplete(TestExecution execution);
    void notifyExecutionFailed(TestExecution execution);
    void notifyExecutionStarted(TestExecution execution);

    // Retry failed notifications
    void retryFailedNotifications();

    // Get notification history
    List<NotificationResponse> getNotificationHistory(UUID executionId);
}