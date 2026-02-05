package com.company.qa.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OllamaRequest {

    private String model;
    private String prompt;
    private Boolean stream;
    private OllamaOptions options;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OllamaOptions {
        private Integer num_predict;  // max tokens
        private Double temperature;
        private Integer top_k;
        private Double top_p;
    }

    public static OllamaRequest create(String model, String prompt,
                                       Integer maxTokens, Double temperature) {
        return OllamaRequest.builder()
                .model(model)
                .prompt(prompt)
                .stream(false)
                .options(OllamaOptions.builder()
                        .num_predict(maxTokens)
                        .temperature(temperature)
                        .top_k(40)
                        .top_p(0.9)
                        .build())
                .build();
    }
}