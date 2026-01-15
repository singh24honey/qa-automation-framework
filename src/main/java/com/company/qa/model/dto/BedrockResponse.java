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
public class BedrockResponse {

    private String id;
    private String type;
    private String role;
    private List<Content> content;
    private String model;
    private String stop_reason;
    private Usage usage;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Content {
        private String type;
        private String text;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Usage {
        private Integer input_tokens;
        private Integer output_tokens;
    }

    public String getTextContent() {
        if (content == null || content.isEmpty()) {
            return "";
        }
        return content.get(0).getText();
    }

    public Integer getTotalTokens() {
        if (usage == null) {
            return 0;
        }
        return usage.getInput_tokens() + usage.getOutput_tokens();
    }
}