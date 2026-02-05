package com.company.qa.controller;

import com.company.qa.model.dto.*;
import com.company.qa.model.enums.ApprovalRequestType;
import com.company.qa.model.enums.ApprovalStatus;
import com.company.qa.service.ApiKeyService;
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

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration test for approval workflow.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("Approval Request Integration Tests")
class ApprovalRequestIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ApiKeyService apiKeyService;

    private String apiKey;
    private UUID userId;
    private UUID reviewerId;

    @BeforeEach
    void setUp() {
        // Create API key
        CreateApiKeyRequest request = CreateApiKeyRequest.builder()
                .name("Integration Test Key")
                .description("API key for integration testing")
                .build();

        ApiKeyDto apiKeyDto = apiKeyService.createApiKey(request);
        apiKey = apiKeyDto.getKeyValue();

        userId = UUID.randomUUID();
        reviewerId = UUID.randomUUID();
    }

    @Test
    @DisplayName("Integration: Complete approval workflow")
    void completeApprovalWorkflow() throws Exception {
        // Step 1: Create approval request
        CreateApprovalRequestDTO createDTO = CreateApprovalRequestDTO.builder()
                .requestType(ApprovalRequestType.TEST_GENERATION)
                .generatedContent("@Test public void testLogin() { /* test code */ }")
                .testName("Test Login Functionality")
                .testFramework("JUnit 5")
                .testLanguage("Java")
                .targetUrl("https://example.com/login")
                .requestedById(userId)
                .requestedByName("John Doe")
                .requestedByEmail("john@example.com")
                .autoExecuteOnApproval(false)
                .build();

        MvcResult createResult = mockMvc.perform(post("/api/v1/approvals")
                        .header("X-API-Key", apiKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createDTO)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("PENDING_APPROVAL"))
                .andReturn();

        String responseJson = createResult.getResponse().getContentAsString();
        ApiResponse<ApprovalRequestDTO> createResponse =
                objectMapper.readValue(responseJson,
                        objectMapper.getTypeFactory().constructParametricType(
                                ApiResponse.class, ApprovalRequestDTO.class));

        UUID requestId = createResponse.getData().getId();
        assertThat(requestId).isNotNull();

        // Step 2: Get pending approvals
        mockMvc.perform(get("/api/v1/approvals/pending")
                        .header("X-API-Key", apiKey))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data[0].id").value(requestId.toString()));

        // Step 3: Get request by ID
        mockMvc.perform(get("/api/v1/approvals/" + requestId)
                        .header("X-API-Key", apiKey))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PENDING_APPROVAL"))
                .andExpect(jsonPath("$.data.isPending").value(true));

        // Step 4: Approve the request
        ApprovalDecisionDTO approvalDecision = ApprovalDecisionDTO.builder()
                .approved(true)
                .reviewerId(reviewerId)
                .reviewerName("Jane Smith")
                .reviewerEmail("jane@example.com")
                .notes("Looks good, approved")
                .build();

        mockMvc.perform(post("/api/v1/approvals/" + requestId + "/approve")
                        .header("X-API-Key", apiKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(approvalDecision)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("APPROVED"))
                .andExpect(jsonPath("$.data.reviewedById").value(reviewerId.toString()));

        // Step 5: Verify status changed
        mockMvc.perform(get("/api/v1/approvals/" + requestId)
                        .header("X-API-Key", apiKey))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("APPROVED"))
                .andExpect(jsonPath("$.data.isPending").value(false));
    }

    @Test
    @DisplayName("Integration: Reject approval workflow")
    void rejectApprovalWorkflow() throws Exception {
        // Create and reject
        CreateApprovalRequestDTO createDTO = CreateApprovalRequestDTO.builder()
                .requestType(ApprovalRequestType.TEST_GENERATION)
                .generatedContent("@Test public void testBadCode() {}")
                .testName("Bad Test")
                .testFramework("JUnit 5")
                .testLanguage("Java")
                .requestedById(userId)
                .requestedByName("John Doe")
                .build();

        MvcResult createResult = mockMvc.perform(post("/api/v1/approvals")
                        .header("X-API-Key", apiKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createDTO)))
                .andExpect(status().isCreated())
                .andReturn();

        String responseJson = createResult.getResponse().getContentAsString();
        ApiResponse<ApprovalRequestDTO> createResponse =
                objectMapper.readValue(responseJson,
                        objectMapper.getTypeFactory().constructParametricType(
                                ApiResponse.class, ApprovalRequestDTO.class));

        UUID requestId = createResponse.getData().getId();

        // Reject
        ApprovalDecisionDTO rejectionDecision = ApprovalDecisionDTO.builder()
                .approved(false)
                .reviewerId(reviewerId)
                .reviewerName("Jane Smith")
                .rejectionReason("Code quality is poor")
                .notes("Please improve test coverage")
                .build();

        mockMvc.perform(post("/api/v1/approvals/" + requestId + "/reject")
                        .header("X-API-Key", apiKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(rejectionDecision)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("REJECTED"))
                .andExpect(jsonPath("$.data.rejectionReason").value("Code quality is poor"));
    }

    @Test
    @DisplayName("Integration: Cancel approval workflow")
    void cancelApprovalWorkflow() throws Exception {
        // Create
        CreateApprovalRequestDTO createDTO = CreateApprovalRequestDTO.builder()
                .requestType(ApprovalRequestType.TEST_GENERATION)
                .generatedContent("@Test public void test() {}")
                .testName("Test")
                .testFramework("JUnit 5")
                .testLanguage("Java")
                .requestedById(userId)
                .requestedByName("John Doe")
                .build();

        MvcResult createResult = mockMvc.perform(post("/api/v1/approvals")
                        .header("X-API-Key", apiKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createDTO)))
                .andExpect(status().isCreated())
                .andReturn();

        String responseJson = createResult.getResponse().getContentAsString();
        ApiResponse<ApprovalRequestDTO> createResponse =
                objectMapper.readValue(responseJson,
                        objectMapper.getTypeFactory().constructParametricType(
                                ApiResponse.class, ApprovalRequestDTO.class));

        UUID requestId = createResponse.getData().getId();

        // Cancel
        mockMvc.perform(delete("/api/v1/approvals/" + requestId)
                        .header("X-API-Key", apiKey)
                        .header("X-User-Id", userId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CANCELLED"));
    }

    @Test
    @DisplayName("Integration: Get statistics")
    void getStatistics() throws Exception {
        mockMvc.perform(get("/api/v1/approvals/statistics")
                        .header("X-API-Key", apiKey))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.totalRequests").exists())
                .andExpect(jsonPath("$.data.approvalRate").exists());
    }
}