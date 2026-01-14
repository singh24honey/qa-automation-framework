package com.company.qa.service.notification.channel;

import com.company.qa.config.NotificationConfig;
import com.company.qa.model.dto.NotificationRequest;
import com.company.qa.model.dto.NotificationResponse;
import com.company.qa.model.enums.NotificationChannel;
import com.company.qa.model.enums.NotificationStatus;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class WebhookNotificationChannel implements NotificationChannelService {

    private final NotificationConfig config;
    private final ObjectMapper objectMapper;

    @Override
    public NotificationResponse send(NotificationRequest request) {
        log.info("Sending webhook notification for event: {}", request.getEvent());

        try {
            String webhookUrl = request.getWebhookUrl() != null ?
                    request.getWebhookUrl() : config.getWebhook().getDefaultUrl();

            Map<String, Object> payload = buildWebhookPayload(request);

            WebClient client = WebClient.create();

            String response = client.post()
                    .uri(webhookUrl)
                    .header("Content-Type", "application/json")
                    .header("X-QA-Framework-Event", request.getEvent().name())
                    .bodyValue(payload)
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(Duration.ofSeconds(config.getWebhook().getTimeoutSeconds()))
                    .block();

            log.info("Webhook notification sent successfully to: {}", webhookUrl);

            return NotificationResponse.builder()
                    .id(UUID.randomUUID())
                    .channel(NotificationChannel.WEBHOOK)
                    .status(NotificationStatus.SENT)
                    .message("Webhook notification sent successfully")
                    .sentAt(Instant.now())
                    .build();

        } catch (Exception e) {
            log.error("Failed to send webhook notification: {}", e.getMessage(), e);

            return NotificationResponse.builder()
                    .id(UUID.randomUUID())
                    .channel(NotificationChannel.WEBHOOK)
                    .status(NotificationStatus.FAILED)
                    .message("Failed to send webhook notification")
                    .errorDetails(e.getMessage())
                    .sentAt(Instant.now())
                    .build();
        }
    }

    @Override
    public boolean isEnabled() {
        return config.isEnabled() && config.getWebhook().isEnabled();
    }

    @Override
    public String getChannelName() {
        return "WEBHOOK";
    }

    private Map<String, Object> buildWebhookPayload(NotificationRequest request) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("event", request.getEvent());
        payload.put("testId", request.getTestId());
        payload.put("testName", request.getTestName());
        payload.put("executionId", request.getExecutionId());
        payload.put("timestamp", Instant.now().toString());

        if (request.getData() != null) {
            payload.put("data", request.getData());
        }

        return payload;
    }
}