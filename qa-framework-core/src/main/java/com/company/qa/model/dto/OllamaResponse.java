package com.company.qa.model.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class OllamaResponse {

    private String model;
    private String response;
    private Boolean done;
    private List<Integer> context;
    private Long total_duration;
    private Long load_duration;
    private Integer prompt_eval_count;
    private Long prompt_eval_duration;
    private Integer eval_count;
    private Long eval_duration;

    public String getGeneratedText() {
        return response != null ? response : "";
    }

    public Integer getTotalTokens() {
        int promptTokens = prompt_eval_count != null ? prompt_eval_count : 0;
        int outputTokens = eval_count != null ? eval_count : 0;
        return promptTokens + outputTokens;
    }
}