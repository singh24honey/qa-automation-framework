package com.company.qa.service.notification;

import com.company.qa.config.NotificationConfig;
import com.company.qa.model.dto.NotificationRequest;
import com.company.qa.model.dto.NotificationResponse;
import com.company.qa.model.entity.NotificationHistory;
import com.company.qa.model.entity.Test;
import com.company.qa.model.entity.TestExecution;
import com.company.qa.model.enums.NotificationChannel;
import com.company.qa.model.enums.NotificationEvent;
import com.company.qa.model.enums.NotificationStatus;
import com.company.qa.model.enums.TestStatus;
import com.company.qa.repository.NotificationHistoryRepository;
import com.company.qa.repository.TestRepository;
import com.company.qa.service.notification.channel.EmailNotificationChannel;
import com.company.qa.service.notification.channel.SlackNotificationChannel;
import com.company.qa.service.notification.channel.WebhookNotificationChannel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class NotificationServiceImpl implements NotificationService {

    private final NotificationConfig config;
    private final NotificationHistoryRepository historyRepository;
    private final TestRepository testRepository;
    private final EmailNotificationChannel emailChannel;
    private final SlackNotificationChannel slackChannel;
    private final WebhookNotificationChannel webhookChannel;

    @Override
    public List<NotificationResponse> sendNotification(NotificationRequest request) {
        if (!config.isEnabled()) {
            log.debug("Notifications are disabled globally");
            return Collections.emptyList();
        }

        log.info("Processing notification request for event: {} on channels: {}",
                request.getEvent(), request.getChannels());

        List<NotificationResponse> responses = new ArrayList<>();

        for (NotificationChannel channel : request.getChannels()) {
            NotificationResponse response = sendToChannel(channel, request);
            responses.add(response);

            // Save to history
            saveNotificationHistory(request, response);
        }

        return responses;
    }

    @Override
    public void notifyExecutionComplete(TestExecution execution) {
        log.info("Notifying execution complete: {}", execution.getId());

        Optional<Test> testOpt = testRepository.findById(execution.getTestId());
        if (testOpt.isEmpty()) {
            log.warn("Test not found for execution: {}", execution.getId());
            return;
        }

        Test test = testOpt.get();

        // Check if we should notify based on test preferences
        boolean shouldNotify = (execution.getStatus() == TestStatus.FAILED &&
                (test.getNotifyOnFailure() != null ? test.getNotifyOnFailure() : true)) ||
                (execution.getStatus() == TestStatus.PASSED &&
                        (test.getNotifyOnSuccess() != null ? test.getNotifyOnSuccess() : false));

        if (!shouldNotify) {
            log.debug("Notification not required for test: {}", test.getName());
            return;
        }

        NotificationEvent event = execution.getStatus() == TestStatus.FAILED ?
                NotificationEvent.TEST_FAILED : NotificationEvent.TEST_COMPLETED;

        Map<String, Object> data = buildExecutionData(execution);

        NotificationRequest request = NotificationRequest.builder()
                .event(event)
                .channels(getChannelsForTest(test))
                .executionId(execution.getId())
                .testId(execution.getTestId())
                .testName(test.getName())
                .data(data)
                .recipients(getRecipientsForTest(test))
                .subject(String.format("Test %s: %s",
                        execution.getStatus(), test.getName()))
                .build();

        sendNotification(request);
    }

    @Override
    public void notifyExecutionFailed(TestExecution execution) {
        log.info("Notifying execution failed: {}", execution.getId());

        Optional<Test> testOpt = testRepository.findById(execution.getTestId());
        if (testOpt.isEmpty()) {
            return;
        }

        Test test = testOpt.get();

        Map<String, Object> data = buildExecutionData(execution);
        data.put("errorDetails", execution.getErrorDetails());

        NotificationRequest request = NotificationRequest.builder()
                .event(NotificationEvent.TEST_FAILED)
                .channels(getChannelsForTest(test))
                .executionId(execution.getId())
                .testId(execution.getTestId())
                .testName(test.getName())
                .data(data)
                .recipients(getRecipientsForTest(test))
                .subject(String.format("Test Failed: %s", test.getName()))
                .build();

        sendNotification(request);
    }

    @Override
    public void notifyExecutionStarted(TestExecution execution) {
        log.info("Notifying execution started: {}", execution.getId());

        Optional<Test> testOpt = testRepository.findById(execution.getTestId());
        if (testOpt.isEmpty()) {
            return;
        }

        Test test = testOpt.get();

        Map<String, Object> data = buildExecutionData(execution);

        NotificationRequest request = NotificationRequest.builder()
                .event(NotificationEvent.TEST_STARTED)
                .channels(getChannelsForTest(test))
                .executionId(execution.getId())
                .testId(execution.getTestId())
                .testName(test.getName())
                .data(data)
                .recipients(getRecipientsForTest(test))
                .subject(String.format("Test Started: %s", test.getName()))
                .build();

        sendNotification(request);
    }

    @Override
    @Transactional
    public void retryFailedNotifications() {
        log.info("Retrying failed notifications");

        List<NotificationHistory> failedNotifications =
                historyRepository.findFailedNotificationsForRetry();

        log.info("Found {} failed notifications to retry", failedNotifications.size());

        for (NotificationHistory history : failedNotifications) {
            try {
                NotificationRequest request = buildRequestFromHistory(history);
                NotificationResponse response = sendToChannel(history.getChannel(), request);

                if (response.getStatus() == NotificationStatus.SENT) {
                    history.setStatus(NotificationStatus.SENT);
                    history.setSentAt(Instant.now());
                    history.setErrorDetails(null);
                } else {
                    history.setRetryCount(history.getRetryCount() + 1);
                    history.setErrorDetails(response.getErrorDetails());
                }

                historyRepository.save(history);

            } catch (Exception e) {
                log.error("Failed to retry notification: {}", e.getMessage());
                history.setRetryCount(history.getRetryCount() + 1);
                history.setErrorDetails(e.getMessage());
                historyRepository.save(history);
            }
        }
    }

    @Override
    public List<NotificationResponse> getNotificationHistory(UUID executionId) {
        List<NotificationHistory> history = historyRepository.findByExecutionId(executionId);

        return history.stream()
                .map(h -> NotificationResponse.builder()
                        .id(h.getId())
                        .channel(h.getChannel())
                        .status(h.getStatus())
                        .message(h.getSubject())
                        .sentAt(h.getSentAt())
                        .errorDetails(h.getErrorDetails())
                        .build())
                .collect(Collectors.toList());
    }

    // Private helper methods

    private NotificationResponse sendToChannel(NotificationChannel channel,
                                               NotificationRequest request) {
        try {
            switch (channel) {
                case EMAIL:
                    if (emailChannel.isEnabled()) {
                        return emailChannel.send(request);
                    }
                    break;
                case SLACK:
                    if (slackChannel.isEnabled()) {
                        return slackChannel.send(request);
                    }
                    break;
                case WEBHOOK:
                    if (webhookChannel.isEnabled()) {
                        return webhookChannel.send(request);
                    }
                    break;
            }

            return NotificationResponse.builder()
                    .channel(channel)
                    .status(NotificationStatus.FAILED)
                    .message("Channel is disabled")
                    .sentAt(Instant.now())
                    .build();

        } catch (Exception e) {
            log.error("Error sending to channel {}: {}", channel, e.getMessage(), e);

            return NotificationResponse.builder()
                    .channel(channel)
                    .status(NotificationStatus.FAILED)
                    .message("Error sending notification")
                    .errorDetails(e.getMessage())
                    .sentAt(Instant.now())
                    .build();
        }
    }

    private void saveNotificationHistory(NotificationRequest request,
                                         NotificationResponse response) {
        try {
            NotificationHistory history = NotificationHistory.builder()
                    .event(request.getEvent())
                    .channel(response.getChannel())
                    .status(response.getStatus())
                    .executionId(request.getExecutionId())
                    .testId(request.getTestId())
                    .recipient(request.getRecipients() != null && !request.getRecipients().isEmpty() ?
                            String.join(",", request.getRecipients()) : null)
                    .subject(request.getSubject())
                    .sentAt(response.getSentAt())
                    .errorDetails(response.getErrorDetails())
                    .retryCount(0)
                    .maxRetries(config.getRetry().getMaxAttempts())
                    .build();

            historyRepository.save(history);

        } catch (Exception e) {
            log.error("Failed to save notification history: {}", e.getMessage(), e);
        }
    }

    private Map<String, Object> buildExecutionData(TestExecution execution) {
        Map<String, Object> data = new HashMap<>();
        data.put("status", execution.getStatus());
        data.put("browser", execution.getBrowser());
        data.put("environment", execution.getEnvironment());
        data.put("startTime", execution.getStartTime());
        data.put("endTime", execution.getEndTime());
        data.put("duration", execution.getDuration());
        return data;
    }

    private List<NotificationChannel> getChannelsForTest(Test test) {
        // Default channels: EMAIL
        return Arrays.asList(NotificationChannel.EMAIL, NotificationChannel.SLACK);
    }

    private List<String> getRecipientsForTest(Test test) {
        // Default recipient from config
        return Arrays.asList(config.getEmail().getFrom());
    }

    private NotificationRequest buildRequestFromHistory(NotificationHistory history) {
        return NotificationRequest.builder()
                .event(history.getEvent())
                .channels(Arrays.asList(history.getChannel()))
                .executionId(history.getExecutionId())
                .testId(history.getTestId())
                .recipients(history.getRecipient() != null ?
                        Arrays.asList(history.getRecipient().split(",")) : null)
                .subject(history.getSubject())
                .build();
    }
}