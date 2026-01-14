package com.company.qa.controller;

import com.company.qa.model.dto.ApiResponse;
import com.company.qa.model.dto.NotificationRequest;
import com.company.qa.model.dto.NotificationResponse;
import com.company.qa.service.notification.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
@Slf4j
public class NotificationController {

    private final NotificationService notificationService;

    @PostMapping("/send")
    public ResponseEntity<ApiResponse<List<NotificationResponse>>> sendNotification(
            @RequestBody NotificationRequest request) {

        log.info("Received notification request for event: {}", request.getEvent());

        List<NotificationResponse> responses = notificationService.sendNotification(request);

        return ResponseEntity.ok(ApiResponse.success(
                responses,
                "Notifications processed"
        ));
    }

    @GetMapping("/history/{executionId}")
    public ResponseEntity<ApiResponse<List<NotificationResponse>>> getNotificationHistory(
            @PathVariable UUID executionId) {

        log.info("Getting notification history for execution: {}", executionId);

        List<NotificationResponse> history =
                notificationService.getNotificationHistory(executionId);

        return ResponseEntity.ok(ApiResponse.success(
                history,
                "Notification history retrieved"
        ));
    }

    @PostMapping("/retry-failed")
    public ResponseEntity<ApiResponse<String>> retryFailedNotifications() {

        log.info("Manually triggering retry of failed notifications");

        notificationService.retryFailedNotifications();

        return ResponseEntity.ok(ApiResponse.success(
                "Retry process initiated",
                "Failed notifications are being retried"
        ));
    }
}