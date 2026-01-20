package com.company.qa.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.eventbridge.EventBridgeClient;

@Configuration
public class EventBridgeConfig {

    @Bean
    /*@ConditionalOnProperty(
            name = "aws.event-bridge.enabled",
            havingValue = "true"
    )*/
    public EventBridgeClient eventBridgeClient() {
        return EventBridgeClient.builder()
                .region(Region.US_EAST_1)
                .build();
    }
}