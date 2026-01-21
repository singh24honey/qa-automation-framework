package com.company.qa.controller;

import com.company.qa.model.dto.*;
import com.company.qa.model.enums.ApprovalRequestType;
import com.company.qa.model.enums.ApprovalStatus;
import com.company.qa.service.approval.ApprovalRequestService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.Arrays;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ApprovalRequestController.class)
@DisplayName("Approval Request Controller Tests")
class ApprovalRequestControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private ApprovalRequestService approvalRequestService;

    private UUID requestId;
    private UUID userId;
    private ApprovalRequestDTO approvalRequestDTO;

    @BeforeEach
    void setUp() {
        requestId = UUID.randomUUID();
        userId = UUID.randomUUID();

        approvalRequestDTO = ApprovalRequestDTO.builder()
                .id(requestId)
                .requestType(ApprovalRequestType.TEST_GENERATION)
                .status(ApprovalStatus.PENDING_APPROVAL)
                .generatedContent("@Test public void testLogin() {}")
                .testName("Test Login")
                .testFramework("JUnit 5")
                .testLanguage("Java")
                .requestedById(userId)
                .requestedByName("John Doe")
                .createdAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(7 * 24 * 60 * 60))
                .isPending(true)
                .isExpired(false)
                .build();
    }

    @Test
    @DisplayName("Should create approval request")
    void shouldCreateApprovalRequest() throws Exception {
        // Setup
        CreateApprovalRequestDTO createDTO = CreateApprovalRequestDTO.builder()
                .requestType(ApprovalRequestType.TEST_GENERATION)
                .generatedContent("@Test public void testLogin() {}")
                .testName("Test Login")
                .testFramework("JUnit 5")
                .testLanguage("Java")
                .requestedById(userId)
                .requestedByName("John Doe")
                .build();

        when(approvalRequestService.createApprovalRequest(any(CreateApprovalRequestDTO.class)))
                .thenReturn(approvalRequestDTO);

        // Execute & Verify
        mockMvc.perform(post("/api/v1/approvals")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createDTO)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(requestId.toString()))
                .andExpect(jsonPath("$.data.status").value("PENDING_APPROVAL"));
    }

    @Test
    @DisplayName("Should get approval request by ID")
    void shouldGetApprovalRequestById() throws Exception {
        // Setup
        when(approvalRequestService.getApprovalRequest(requestId))
                .thenReturn(approvalRequestDTO);

        // Execute & Verify
        mockMvc.perform(get("/api/v1/approvals/" + requestId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(requestId.toString()));
    }

    @Test
    @DisplayName("Should get pending approvals")
    void shouldGetPendingApprovals() throws Exception {
        // Setup
        when(approvalRequestService.getPendingApprovalRequests())
                .thenReturn(Arrays.asList(approvalRequestDTO));

        // Execute & Verify
        mockMvc.perform(get("/api/v1/approvals/pending"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].status").value("PENDING_APPROVAL"));
    }

    @Test
    @DisplayName("Should approve request")
    void shouldApproveRequest() throws Exception {
        // Setup
        ApprovalDecisionDTO decision = ApprovalDecisionDTO.builder()
                .reviewerId(UUID.randomUUID())
                .reviewerName("Jane Smith")
                .notes("Looks good")
                .build();

        ApprovalRequestDTO approved = ApprovalRequestDTO.builder()
                .id(requestId)
                .status(ApprovalStatus.APPROVED)
                .build();

        when(approvalRequestService.approveRequest(eq(requestId), any(ApprovalDecisionDTO.class)))
                .thenReturn(approved);

        // Execute & Verify
        mockMvc.perform(post("/api/v1/approvals/" + requestId + "/approve")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(decision)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("APPROVED"));
    }

    @Test
    @DisplayName("Should reject request")
    void shouldRejectRequest() throws Exception {
        // Setup
        ApprovalDecisionDTO decision = ApprovalDecisionDTO.builder()
                .reviewerId(UUID.randomUUID())
                .reviewerName("Jane Smith")
                .rejectionReason("Insufficient coverage")
                .build();

        ApprovalRequestDTO rejected = ApprovalRequestDTO.builder()
                .id(requestId)
                .status(ApprovalStatus.REJECTED)
                .rejectionReason("Insufficient coverage")
                .build();

        when(approvalRequestService.rejectRequest(eq(requestId), any(ApprovalDecisionDTO.class)))
                .thenReturn(rejected);

        // Execute & Verify
        mockMvc.perform(post("/api/v1/approvals/" + requestId + "/reject")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(decision)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("REJECTED"));
    }

    @Test
    @DisplayName("Should get statistics")
    void shouldGetStatistics() throws Exception {
        // Setup
        ApprovalRequestSummaryDTO summary = ApprovalRequestSummaryDTO.builder()
                .totalRequests(100L)
                .pendingRequests(20L)
                .approvedRequests(60L)
                .rejectedRequests(15L)
                .approvalRate(80.0)
                .build();

        when(approvalRequestService.getSummaryStatistics())
                .thenReturn(summary);

        // Execute & Verify
        mockMvc.perform(get("/api/v1/approvals/statistics"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.totalRequests").value(100))
                .andExpect(jsonPath("$.data.approvalRate").value(80.0));
    }
}