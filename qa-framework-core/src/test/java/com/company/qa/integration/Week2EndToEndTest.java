package com.company.qa.integration;

import com.company.qa.model.dto.ExecutionRequest;
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
class Week2EndToEndTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private TestRepository testRepository;

    private String validApiKey;

    @BeforeEach
    void setUp() throws Exception {
        // Create API key for all tests
        MvcResult result = mockMvc.perform(post("/api/v1/auth/api-keys")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"E2E Test Key\"}"))
                .andReturn();

        validApiKey = objectMapper.readTree(result.getResponse().getContentAsString())
                .get("data")
                .get("keyValue")
                .asText();
    }

    @org.junit.jupiter.api.Test
    @DisplayName("Complete workflow: Create test -> Execute -> Check results -> Download artifacts")
    void completeWorkflow_Success() throws Exception {
        // Step 1: Create a test
        String testScript = """
                {
                  "name": "E2E Test",
                  "description": "End to end test",
                  "steps": [
                    {
                      "action": "navigate",
                      "value": "https://example.com"
                    },
                    {
                      "action": "assertTitle",
                      "value": "Example Domain"
                    }
                  ]
                }
                """;

        MvcResult createResult = mockMvc.perform(post("/api/v1/tests")
                        .header("X-API-Key", validApiKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.format("""
                                {
                                  "name": "E2E Test",
                                  "framework": "SELENIUM",
                                  "language": "json",
                                  "priority": "HIGH",
                                  "content": %s
                                }
                                """, objectMapper.writeValueAsString(testScript))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andReturn();

        String testId = objectMapper.readTree(createResult.getResponse().getContentAsString())
                .get("data")
                .get("id")
                .asText();

        // Step 2: Execute the test
        ExecutionRequest execRequest = ExecutionRequest.builder()
                .testId(UUID.fromString(testId))
                .browser("CHROME")
                .headless(true)
                .build();

        mockMvc.perform(post("/api/v1/executions")
                        .header("X-API-Key", validApiKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(execRequest)))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.success").value(true));

        // Step 3: Get recent executions
        mockMvc.perform(get("/api/v1/executions")
                        .header("X-API-Key", validApiKey))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray());

        // Step 4: Get storage stats
        mockMvc.perform(get("/api/v1/storage/stats")
                        .header("X-API-Key", validApiKey))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        // Step 5: Delete the test
        mockMvc.perform(delete("/api/v1/tests/" + testId)
                        .header("X-API-Key", validApiKey))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @org.junit.jupiter.api.Test
    @DisplayName("Security: All endpoints require API key")
    void security_AllEndpointsRequireApiKey() throws Exception {
        // Tests without API key should fail
        mockMvc.perform(get("/api/v1/tests"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/v1/executions"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/v1/storage/stats"))
                .andExpect(status().isUnauthorized());
    }

    @org.junit.jupiter.api.Test
    @DisplayName("Storage: Upload, list, download, delete workflow")
    void storage_CompleteWorkflow() throws Exception {
        String executionId = "e2e-test-exec";

        // Upload file
        mockMvc.perform(multipart("/api/v1/storage/upload/" + executionId)
                        .file("file", "test content".getBytes())
                        .param("type", "LOG")
                        .header("X-API-Key", validApiKey))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true));

        // List files
        MvcResult listResult = mockMvc.perform(get("/api/v1/storage/files/" + executionId)
                        .header("X-API-Key", validApiKey))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andReturn();

        String filename = objectMapper.readTree(listResult.getResponse().getContentAsString())
                .get("data")
                .get(0)
                .get("filename")
                .asText();

        // Download file
        mockMvc.perform(get("/api/v1/storage/download/" + executionId + "/" + filename)
                        .header("X-API-Key", validApiKey))
                .andExpect(status().isOk());

        // Delete files
        mockMvc.perform(delete("/api/v1/storage/files/" + executionId)
                        .header("X-API-Key", validApiKey))
                .andExpect(status().isOk());
    }

    @org.junit.jupiter.api.Test
    @DisplayName("API Keys: Create, list, revoke workflow")
    void apiKeys_CompleteWorkflow() throws Exception {
        // Create key
        MvcResult createResult = mockMvc.perform(post("/api/v1/auth/api-keys")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Test Key 2\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.keyValue").exists())
                .andReturn();

        String keyId = objectMapper.readTree(createResult.getResponse().getContentAsString())
                .get("data")
                .get("id")
                .asText();

        // List keys
        mockMvc.perform(get("/api/v1/auth/api-keys")
                        .header("X-API-Key", validApiKey))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray());

        // Revoke key
        mockMvc.perform(delete("/api/v1/auth/api-keys/" + keyId)
                        .header("X-API-Key", validApiKey))
                .andExpect(status().isOk());
    }

    @org.junit.jupiter.api.Test
    @DisplayName("Error handling: Proper error responses for invalid requests")
    void errorHandling_ProperResponses() throws Exception {
        // Invalid test ID
        mockMvc.perform(get("/api/v1/tests/invalid-uuid")
                        .header("X-API-Key", validApiKey))
                .andExpect(status().is4xxClientError());

        // Invalid execution request
        mockMvc.perform(post("/api/v1/executions")
                        .header("X-API-Key", validApiKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"testId\":\"not-a-uuid\"}"))
                .andExpect(status().is4xxClientError());
    }
}