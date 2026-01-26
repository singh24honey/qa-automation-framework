package com.company.qa.service.eventbridge;

import com.company.qa.event.RequestLoggedEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.eventbridge.EventBridgeClient;
import software.amazon.awssdk.services.eventbridge.model.PutEventsRequest;
import software.amazon.awssdk.services.eventbridge.model.PutEventsRequestEntry;

@Service
@RequiredArgsConstructor
@Slf4j
public class EventBridgePublisher {

    private final EventBridgeClient eventBridgeClient;
    private final ObjectMapper objectMapper;


    @Value("${aws.eventbridge.bus-name:qa-framework-audit-bus}")
    private String eventBusName;

    public void publish(RequestLoggedEvent event) {
        try {

            String payload = objectMapper.writeValueAsString(event);

            PutEventsRequestEntry entry = PutEventsRequestEntry.builder()
                    .eventBusName(eventBusName)
                    .source("qa-framework.request")
                    .detailType("RequestLoggedEvent")
                    .detail(payload) // simple ObjectMapper wrapper
                    .build();

            eventBridgeClient.putEvents(
                    PutEventsRequest.builder()
                            .entries(entry)
                            .build()
            );

            log.debug("Published RequestLoggedEvent to EventBridge");

        } catch (Exception e) {
            log.error("Failed to publish event to EventBridge", e);
        }
    }

    @PostConstruct
    public void logBusConfig() {
        log.error(">>> EventBridge bus resolved to = [{}]", eventBusName);
    }


}