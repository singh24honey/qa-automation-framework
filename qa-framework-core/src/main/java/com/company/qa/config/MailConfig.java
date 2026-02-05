package com.company.qa.config;

import jakarta.mail.internet.MimeMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessagePreparator;

import java.io.InputStream;

@Configuration
@Slf4j
public class MailConfig {

    @Bean
    @ConditionalOnMissingBean(JavaMailSender.class)
    public JavaMailSender mockJavaMailSender() {
        log.warn("=".repeat(60));
        log.warn("Creating MOCK JavaMailSender");
        log.warn("Emails will be logged to console but NOT actually sent");
        log.warn("Configure spring.mail properties for production email");
        log.warn("=".repeat(60));

        return new JavaMailSender() {
            @Override
            public MimeMessage createMimeMessage() {
                log.debug("Mock: createMimeMessage called");
                return null;
            }

            @Override
            public MimeMessage createMimeMessage(InputStream contentStream) {
                log.debug("Mock: createMimeMessage with InputStream called");
                return null;
            }

            @Override
            public void send(MimeMessage mimeMessage) {
                log.info("Mock: Email would be sent - {}", mimeMessage);
            }

            @Override
            public void send(MimeMessage... mimeMessages) {
                log.info("Mock: {} emails would be sent", mimeMessages.length);
            }

            @Override
            public void send(MimeMessagePreparator mimeMessagePreparator) {
                log.info("Mock: Email with MimeMessagePreparator would be sent");
            }

            @Override
            public void send(MimeMessagePreparator... mimeMessagePreparators) {
                log.info("Mock: {} emails with MimeMessagePreparator would be sent",
                        mimeMessagePreparators.length);
            }

            @Override
            public void send(SimpleMailMessage simpleMessage) {
                log.info("\n" + "=".repeat(80));
                log.info("📧 MOCK EMAIL NOTIFICATION");
                log.info("=".repeat(80));
                log.info("From:    {}", simpleMessage.getFrom());
                log.info("To:      {}", simpleMessage.getTo() != null ?
                        String.join(", ", simpleMessage.getTo()) : "N/A");
                log.info("Subject: {}", simpleMessage.getSubject());
                log.info("-".repeat(80));
                log.info("Body:\n{}", simpleMessage.getText());
                log.info("=".repeat(80) + "\n");
            }

            @Override
            public void send(SimpleMailMessage... simpleMessages) {
                log.info("Mock: Sending {} emails", simpleMessages.length);
                for (SimpleMailMessage msg : simpleMessages) {
                    send(msg);
                }
            }
        };
    }
}