package com.company.qa.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "logging.request")
public class RequestLoggingProperties {

    private String mode = "hybrid"; // Default to 'postgres'
}