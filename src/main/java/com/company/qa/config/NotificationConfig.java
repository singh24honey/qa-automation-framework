package com.company.qa.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "notification")
@Getter
@Setter
public class NotificationConfig {

    private boolean enabled = true;
    private boolean async = true;
    private RetryConfig retry = new RetryConfig();
    private RateLimitConfig rateLimit = new RateLimitConfig();
    private EmailConfig email = new EmailConfig();
    private SlackConfig slack = new SlackConfig();
    private WebhookConfig webhook = new WebhookConfig();

    @Getter
    @Setter
    public static class RetryConfig {
        private boolean enabled = true;
        private int maxAttempts = 3;
        private int delaySeconds = 10;
    }

    @Getter
    @Setter
    public static class RateLimitConfig {
        private boolean enabled = true;
        private int maxPerMinute = 10;
    }

    @Getter
    @Setter
    public static class EmailConfig {
        private boolean enabled = true;
        private String from;
        private SmtpConfig smtp = new SmtpConfig();
    }

    @Getter
    @Setter
    public static class SmtpConfig {
        private String host;
        private int port;
        private String username;
        private String password;
        private boolean auth;
        private boolean starttls;
    }

    @Getter
    @Setter
    public static class SlackConfig {
        private boolean enabled = true;
        private String webhookUrl;
        private String channel;
        private String username;
        private String iconEmoji;
    }

    @Getter
    @Setter
    public static class WebhookConfig {
        private boolean enabled = true;
        private String defaultUrl;
        private int timeoutSeconds = 10;
    }
}