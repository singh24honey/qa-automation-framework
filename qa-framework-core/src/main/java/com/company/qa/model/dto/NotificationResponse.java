package com.company.qa.model.dto;

import com.company.qa.model.enums.NotificationChannel;
import com.company.qa.model.enums.NotificationStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotificationResponse {

    private UUID id;
    private NotificationChannel channel;
    private NotificationStatus status;
    private String message;
    private Instant sentAt;
    private String errorDetails;
}