package com.company.qa.service.notification;

import com.company.qa.config.NotificationConfig;
import com.company.qa.model.dto.NotificationRequest;
import com.company.qa.model.dto.NotificationResponse;
import com.company.qa.model.entity.Test;
import com.company.qa.model.entity.TestExecution;
import com.company.qa.model.enums.*;
import com.company.qa.repository.NotificationHistoryRepository;
import com.company.qa.repository.TestRepository;
import com.company.qa.service.notification.channel.EmailNotificationChannel;
import com.company.qa.service.notification.channel.SlackNotificationChannel;
import com.company.qa.service.notification.channel.WebhookNotificationChannel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock
    private NotificationConfig config;

    @Mock
    private NotificationHistoryRepository historyRepository;

    @Mock
    private TestRepository testRepository;

    @Mock
    private EmailNotificationChannel emailChannel;

    @Mock
    private SlackNotificationChannel slackChannel;

    @Mock
    private WebhookNotificationChannel webhookChannel;

    private NotificationServiceImpl notificationService;

    @BeforeEach
    void setUp() {
        notificationService = new NotificationServiceImpl(
                config,
                historyRepository,
                testRepository,
                emailChannel,
                slackChannel,
                webhookChannel
        );

        when(config.isEnabled()).thenReturn(true);
    }

    @org.junit.jupiter.api.Test
    @DisplayName("Should send notification to email channel")
    void shouldSendEmailNotification() {
        // Given
        NotificationRequest request = NotificationRequest.builder()
                .event(NotificationEvent.TEST_COMPLETED)
                .channels(Arrays.asList(NotificationChannel.EMAIL))
                .testId(UUID.randomUUID())
                .testName("Test")
                .recipients(Arrays.asList("test@example.com"))
                .build();

        NotificationResponse expectedResponse = NotificationResponse.builder()
                .channel(NotificationChannel.EMAIL)
                .status(NotificationStatus.SENT)
                .sentAt(Instant.now())
                .build();

        when(emailChannel.isEnabled()).thenReturn(true);
        when(emailChannel.send(any())).thenReturn(expectedResponse);

        // When
        List<NotificationResponse> responses = notificationService.sendNotification(request);

        // Then
        assertThat(responses).hasSize(1);
        assertThat(responses.get(0).getChannel()).isEqualTo(NotificationChannel.EMAIL);
        assertThat(responses.get(0).getStatus()).isEqualTo(NotificationStatus.SENT);

        verify(emailChannel).send(any());
        verify(historyRepository).save(any());
    }

    @org.junit.jupiter.api.Test
    @DisplayName("Should notify on execution complete")
    void shouldNotifyExecutionComplete() {
        // Given
        UUID testId = UUID.randomUUID();
        UUID executionId = UUID.randomUUID();

        Test test = Test.builder()
                .id(testId)
                .name("Login Test")
                .notifyOnFailure(true)
                .build();

        TestExecution execution = TestExecution.builder()
                .testId(testId)
                .status(TestStatus.PASSED)
                .startTime(Instant.now())
                .endTime(Instant.now())
                .build();

        when(testRepository.findById(testId)).thenReturn(Optional.of(test));
        when(emailChannel.isEnabled()).thenReturn(true);

        // When
        notificationService.notifyExecutionComplete(execution);

        // Then
        verify(testRepository).findById(testId);
        // Additional verifications based on your implementation
    }

    @org.junit.jupiter.api.Test
    @DisplayName("Should handle notification failure")
    void shouldHandleNotificationFailure() {
        // Given
        NotificationRequest request = NotificationRequest.builder()
                .event(NotificationEvent.TEST_FAILED)
                .channels(Arrays.asList(NotificationChannel.EMAIL))
                .testId(UUID.randomUUID())
                .testName("Test")
                .recipients(Arrays.asList("test@example.com"))
                .build();

        when(emailChannel.isEnabled()).thenReturn(true);
        when(emailChannel.send(any())).thenThrow(new RuntimeException("SMTP error"));

        // When
        List<NotificationResponse> responses = notificationService.sendNotification(request);

        // Then
        assertThat(responses).hasSize(1);
        assertThat(responses.get(0).getStatus()).isEqualTo(NotificationStatus.FAILED);

        verify(historyRepository).save(any());
    }
}