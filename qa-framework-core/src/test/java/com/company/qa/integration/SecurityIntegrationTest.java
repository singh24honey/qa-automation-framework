package com.company.qa.integration;

import com.company.qa.model.dto.CreateApiKeyRequest;
import com.company.qa.repository.ApiKeyRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class SecurityIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ApiKeyRepository apiKeyRepository;

    private String validApiKey;

    @BeforeEach
    void setUp() throws Exception {
        apiKeyRepository.deleteAll();

        // Create an API key for testing
        CreateApiKeyRequest request = CreateApiKeyRequest.builder()
                .name("Test Key")
                .description("For testing")
                .build();

        MvcResult result = mockMvc.perform(post("/api/v1/auth/api-keys")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();

        String responseJson = result.getResponse().getContentAsString();
        validApiKey = objectMapper.readTree(responseJson)
                .get("data")
                .get("keyValue")
                .asText();
    }

    @Test
    @DisplayName("Should reject request without API key")
    void requestWithoutApiKey_Returns401() throws Exception {
        mockMvc.perform(get("/api/v1/tests"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Should reject request with invalid API key")
    void requestWithInvalidApiKey_Returns401() throws Exception {
        mockMvc.perform(get("/api/v1/tests")
                        .header("X-API-Key", "invalid-key"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Should accept request with valid API key")
    void requestWithValidApiKey_Returns200() throws Exception {
        mockMvc.perform(get("/api/v1/tests")
                        .header("X-API-Key", validApiKey))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Should allow access to health endpoint without API key")
    void healthEndpoint_NoAuthRequired() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Should create API key successfully")
    void createApiKey_Success() throws Exception {
        CreateApiKeyRequest request = CreateApiKeyRequest.builder()
                .name("New Test Key")
                .description("Another test key")
                .expiresInDays(30)
                .build();

        mockMvc.perform(post("/api/v1/auth/api-keys")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.name").value("New Test Key"))
                .andExpect(jsonPath("$.data.keyValue").isNotEmpty());
    }

    @Test
    @DisplayName("Should list API keys with valid authentication")
    void listApiKeys_WithAuth_Success() throws Exception {
        mockMvc.perform(get("/api/v1/auth/api-keys")
                        .header("X-API-Key", validApiKey))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray());
    }

    @Test
    @DisplayName("Should revoke API key")
    void revokeApiKey_Success() throws Exception {
        // Get the API key ID
        MvcResult result = mockMvc.perform(get("/api/v1/auth/api-keys")
                        .header("X-API-Key", validApiKey))
                .andReturn();

        String responseJson = result.getResponse().getContentAsString();
        String apiKeyId = objectMapper.readTree(responseJson)
                .get("data")
                .get(0)
                .get("id")
                .asText();

        // Revoke it
        mockMvc.perform(delete("/api/v1/auth/api-keys/" + apiKeyId)
                        .header("X-API-Key", validApiKey))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }
}