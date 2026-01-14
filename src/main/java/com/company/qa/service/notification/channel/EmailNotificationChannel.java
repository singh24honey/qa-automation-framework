package com.company.qa.service.notification.channel;

import com.company.qa.config.NotificationConfig;
import com.company.qa.model.dto.NotificationRequest;
import com.company.qa.model.dto.NotificationResponse;
import com.company.qa.model.enums.NotificationChannel;
import com.company.qa.model.enums.NotificationStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class EmailNotificationChannel implements NotificationChannelService {

    private final JavaMailSender mailSender;
    private final NotificationConfig config;

    @Override
    public NotificationResponse send(NotificationRequest request) {
        log.info("Sending email notification for event: {}", request.getEvent());

        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(config.getEmail().getFrom());
            message.setTo(request.getRecipients().toArray(new String[0]));
            message.setSubject(request.getSubject() != null ? request.getSubject() : buildSubject(request));
            message.setText(buildEmailBody(request));

            mailSender.send(message);

            log.info("Email sent successfully to: {}", request.getRecipients());

            return NotificationResponse.builder()
                    .id(UUID.randomUUID())
                    .channel(NotificationChannel.EMAIL)
                    .status(NotificationStatus.SENT)
                    .message("Email sent successfully")
                    .sentAt(Instant.now())
                    .build();

        } catch (Exception e) {
            log.error("Failed to send email notification: {}", e.getMessage(), e);

            return NotificationResponse.builder()
                    .id(UUID.randomUUID())
                    .channel(NotificationChannel.EMAIL)
                    .status(NotificationStatus.FAILED)
                    .message("Failed to send email")
                    .errorDetails(e.getMessage())
                    .sentAt(Instant.now())
                    .build();
        }
    }

    @Override
    public boolean isEnabled() {
        return config.isEnabled() && config.getEmail().isEnabled();
    }

    @Override
    public String getChannelName() {
        return "EMAIL";
    }

    private String buildSubject(NotificationRequest request) {
        return String.format("[QA Framework] %s - %s",
                request.getEvent(),
                request.getTestName());
    }

    private String buildEmailBody(NotificationRequest request) {
        StringBuilder body = new StringBuilder();
        body.append("QA Framework Notification\n");
        body.append("========================\n\n");
        body.append("Event: ").append(request.getEvent()).append("\n");
        body.append("Test: ").append(request.getTestName()).append("\n");
        body.append("Test ID: ").append(request.getTestId()).append("\n");
        body.append("Execution ID: ").append(request.getExecutionId()).append("\n\n");

        if (request.getData() != null && !request.getData().isEmpty()) {
            body.append("Additional Details:\n");
            request.getData().forEach((key, value) ->
                    body.append("  ").append(key).append(": ").append(value).append("\n")
            );
        }

        body.append("\n---\n");
        body.append("This is an automated notification from QA Framework\n");
        body.append("Time: ").append(Instant.now()).append("\n");

        return body.toString();
    }
}