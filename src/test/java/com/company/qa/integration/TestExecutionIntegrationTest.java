package com.company.qa.integration;

import com.company.qa.model.dto.ExecutionRequest;
import com.company.qa.model.entity.Test;
import com.company.qa.model.enums.Priority;
import com.company.qa.model.enums.TestFramework;
import com.company.qa.repository.TestRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.JsonPath;
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
@ActiveProfiles("dev")
@Transactional
class TestExecutionIntegrationTest {

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
                  "name": "Simple Test",
                  "description": "Test description",
                  "steps": [
                    {
                      "action": "navigate",
                      "value": "https://example.com"
                    }
                  ]
                }
                """;

        Test test = Test.builder()
                .name("Integration Test")
                .description("Test for execution")
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
    @DisplayName("Should start test execution")
    void executeTest_StartsExecution() throws Exception {
        ExecutionRequest request = ExecutionRequest.builder()
                .testId(testId)
                .browser("CHROME")
                .environment("test")
                .headless(true)
                .build();

        mockMvc.perform(post("/api/v1/executions")
                        .header("X-API-Key", validApiKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Test execution started"));
    }

    @org.junit.jupiter.api.Test
    void getExecutionStatus_ReturnsStatus() throws Exception {
        // First create an execution
        ExecutionRequest request = ExecutionRequest.builder()
                .testId(testId)
                .browser("CHROME")
                .environment("test")
                .headless(true)
                .build();

        MvcResult execResult = mockMvc.perform(post("/api/v1/executions")
                        .header("X-API-Key", validApiKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.executionId").exists())
                .andReturn();

        // Extract the execution ID from the response
        String responseBody = execResult.getResponse().getContentAsString();
        String executionId = JsonPath.read(responseBody, "$.data.executionId");

        // Wait a moment for async execution to start
        Thread.sleep(1000);

        // Get the execution status
        mockMvc.perform(get("/api/v1/executions/" + executionId)
                        .header("X-API-Key", validApiKey))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.executionId").value(executionId))
                .andExpect(jsonPath("$.data.status").exists())
                .andExpect(jsonPath("$.data.testId").value(testId.toString()));
    }

    @org.junit.jupiter.api.Test
    @DisplayName("Should get recent executions")
    void getRecentExecutions_ReturnsList() throws Exception {
        mockMvc.perform(get("/api/v1/executions")
                        .header("X-API-Key", validApiKey)
                        .param("limit", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray());
    }
}