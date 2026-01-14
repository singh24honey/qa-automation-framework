package com.company.qa.service.notification.channel;

import com.company.qa.model.dto.NotificationRequest;
import com.company.qa.model.dto.NotificationResponse;

public interface NotificationChannelService {

    NotificationResponse send(NotificationRequest request);

    boolean isEnabled();

    String getChannelName();
}