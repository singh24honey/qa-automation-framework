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
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class SlackNotificationChannel implements NotificationChannelService {

    private final NotificationConfig config;
    private final ObjectMapper objectMapper;

    @Override
    public NotificationResponse send(NotificationRequest request) {
        log.info("Sending Slack notification for event: {}", request.getEvent());

        try {
            String webhookUrl = request.getWebhookUrl() != null ?
                    request.getWebhookUrl() : config.getSlack().getWebhookUrl();

            Map<String, Object> slackMessage = buildSlackMessage(request);

            WebClient client = WebClient.create();

            String response = client.post()
                    .uri(webhookUrl)
                    .header("Content-Type", "application/json")
                    .bodyValue(slackMessage)
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(Duration.ofSeconds(10))
                    .block();

            log.info("Slack notification sent successfully: {}", response);

            return NotificationResponse.builder()
                    .id(UUID.randomUUID())
                    .channel(NotificationChannel.SLACK)
                    .status(NotificationStatus.SENT)
                    .message("Slack notification sent successfully")
                    .sentAt(Instant.now())
                    .build();

        } catch (Exception e) {
            log.error("Failed to send Slack notification: {}", e.getMessage(), e);

            return NotificationResponse.builder()
                    .id(UUID.randomUUID())
                    .channel(NotificationChannel.SLACK)
                    .status(NotificationStatus.FAILED)
                    .message("Failed to send Slack notification")
                    .errorDetails(e.getMessage())
                    .sentAt(Instant.now())
                    .build();
        }
    }

    @Override
    public boolean isEnabled() {
        return config.isEnabled() && config.getSlack().isEnabled();
    }

    @Override
    public String getChannelName() {
        return "SLACK";
    }

    private Map<String, Object> buildSlackMessage(NotificationRequest request) {
        Map<String, Object> message = new HashMap<>();

        // Use provided channel or default
        String channel = request.getSlackChannel() != null ?
                request.getSlackChannel() : config.getSlack().getChannel();

        message.put("channel", channel);
        message.put("username", config.getSlack().getUsername());
        message.put("icon_emoji", config.getSlack().getIconEmoji());

        // Build attachment with color based on event
        Map<String, Object> attachment = new HashMap<>();
        attachment.put("color", getColorForEvent(request.getEvent().name()));
        attachment.put("title", String.format("%s: %s", request.getEvent(), request.getTestName()));
        attachment.put("text", buildSlackText(request));
        attachment.put("footer", "QA Framework");
        attachment.put("ts", Instant.now().getEpochSecond());

        message.put("attachments", new Object[]{attachment});

        return message;
    }

    private String buildSlackText(NotificationRequest request) {
        StringBuilder text = new StringBuilder();
        text.append("*Test ID:* ").append(request.getTestId()).append("\n");
        text.append("*Execution ID:* ").append(request.getExecutionId()).append("\n");

        if (request.getData() != null && !request.getData().isEmpty()) {
            text.append("\n*Details:*\n");
            request.getData().forEach((key, value) ->
                    text.append("• ").append(key).append(": `").append(value).append("`\n")
            );
        }

        return text.toString();
    }

    private String getColorForEvent(String event) {
        if (event.contains("FAIL") || event.contains("ERROR")) {
            return "danger";
        } else if (event.contains("RECOVER")) {
            return "good";
        } else if (event.contains("START")) {
            return "warning";
        }
        return "#36a64f";
    }
}