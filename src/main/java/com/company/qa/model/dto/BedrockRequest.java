package com.company.qa.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BedrockRequest {

    private String anthropic_version;
    private Integer max_tokens;
    private List<Message> messages;
    private Double temperature;
    private List<String> stop_sequences;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Message {
        private String role; // "user" or "assistant"
        private String content;
    }

    public static BedrockRequest createUserMessage(String prompt, Integer maxTokens, Double temperature) {
        Message message = Message.builder()
                .role("user")
                .content(prompt)
                .build();

        return BedrockRequest.builder()
                .anthropic_version("bedrock-2023-05-31")
                .max_tokens(maxTokens)
                .messages(List.of(message))
                .temperature(temperature)
                .build();
    }
}