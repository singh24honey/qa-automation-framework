package com.company.qa.model.dto;

import com.company.qa.model.enums.NotificationChannel;
import com.company.qa.model.enums.NotificationEvent;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotificationRequest {

    private NotificationEvent event;
    private List<NotificationChannel> channels;
    private UUID executionId;
    private UUID testId;
    private String testName;
    private Map<String, Object> data;

    // Email specific
    private List<String> recipients;
    private String subject;

    // Slack specific
    private String slackChannel;

    // Webhook specific
    private String webhookUrl;
}