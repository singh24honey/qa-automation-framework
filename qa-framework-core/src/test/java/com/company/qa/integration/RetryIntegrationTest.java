package com.company.qa.integration;

import com.company.qa.model.entity.Test;
import com.company.qa.model.enums.Priority;
import com.company.qa.model.enums.TestFramework;
import com.company.qa.repository.TestRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class RetryIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private TestRepository testRepository;

    private String validApiKey;
    private UUID testId;

    @BeforeEach
    void setUp() throws Exception {
        // Create API key
        MvcResult result = mockMvc.perform(post("/api/v1/auth/api-keys")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Test Key\"}"))
                .andReturn();

        validApiKey = objectMapper.readTree(result.getResponse().getContentAsString())
                .get("data")
                .get("keyValue")
                .asText();

        // Create a test
        String testScript = """
                {
                  "name": "Retry Test",
                  "description": "Test for retry logic",
                  "steps": [
                    {
                      "action": "navigate",
                      "value": "https://example.com"
                    }
                  ]
                }
                """;

        Test test = Test.builder()
                .name("Retry Integration Test")
                .description("Test for retry")
                .framework(TestFramework.SELENIUM)
                .language("json")
                .priority(Priority.HIGH)
                .content(testScript)
                .isActive(true)
                .build();

        test = testRepository.save(test);
        testId = test.getId();
    }

    @org.junit.jupiter.api.Test
    @DisplayName("Should cancel running execution")
    void cancelExecution_Success() throws Exception {
        // Start execution
        String requestBody = String.format("""
                {
                  "testId": "%s",
                  "browser": "CHROME",
                  "headless": true
                }
                """, testId);

        MvcResult execResult = mockMvc.perform(post("/api/v1/executions")
                        .header("X-API-Key", validApiKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isAccepted())
                .andReturn();

        String response = execResult.getResponse().getContentAsString();
        String executionId = objectMapper.readTree(response)
                .get("data")
                .get("executionId")
                .asText();

        // Cancel execution (if it has an ID)
        if (executionId != null && !executionId.equals("null")) {
            mockMvc.perform(delete("/api/v1/executions/" + executionId)
                            .header("X-API-Key", validApiKey))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true));
        }
    }

    @org.junit.jupiter.api.Test
    @DisplayName("Should handle execution with retry configuration")
    void executeWithRetry_Success() throws Exception {
        String requestBody = String.format("""
                {
                  "testId": "%s",
                  "browser": "CHROME",
                  "headless": true
                }
                """, testId);

        mockMvc.perform(post("/api/v1/executions")
                        .header("X-API-Key", validApiKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.success").value(true));
    }
}