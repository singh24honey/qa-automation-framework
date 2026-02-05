package com.company.qa.service.approval;

import com.company.qa.model.dto.*;
import com.company.qa.model.entity.ApprovalRequest;
import com.company.qa.model.enums.ApprovalRequestType;
import com.company.qa.model.enums.ApprovalStatus;
import com.company.qa.repository.ApprovalRequestRepository;
import com.company.qa.service.notification.NotificationService;
import com.company.qa.service.TestService;
import com.company.qa.service.execution.TestExecutionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("Approval Request Service Tests")
class ApprovalRequestServiceTest {

    @Mock
    private ApprovalRequestRepository approvalRequestRepository;

    @Mock
    private TestService testService;

    @Mock
    private TestExecutionService testExecutionService;

    @Mock
    private NotificationService notificationService;

    @InjectMocks
    private ApprovalRequestService approvalRequestService;

    private UUID userId;
    private UUID reviewerId;
    private CreateApprovalRequestDTO createDTO;
    private ApprovalRequest savedRequest;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        reviewerId = UUID.randomUUID();

        createDTO = CreateApprovalRequestDTO.builder()
                .requestType(ApprovalRequestType.TEST_GENERATION)
                .generatedContent("@Test public void testLogin() {}")
                .testName("Test Login")
                .testFramework("JUnit 5")
                .testLanguage("Java")
                .requestedById(userId)
                .requestedByName("John Doe")
                .requestedByEmail("john@example.com")
                .autoExecuteOnApproval(false)
                .build();

        savedRequest = ApprovalRequest.builder()
                .id(UUID.randomUUID())
                .requestType(ApprovalRequestType.TEST_GENERATION)
                .status(ApprovalStatus.PENDING_APPROVAL)
                .generatedContent("@Test public void testLogin() {}")
                .testName("Test Login")
                .testFramework("JUnit 5")
                .testLanguage("Java")
                .requestedById(userId)
                .requestedByName("John Doe")
                .requestedByEmail("john@example.com")
                .createdAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(7 * 24 * 60 * 60))
                .autoExecuteOnApproval(false)
                .build();
    }

    @Test
    @DisplayName("Should create approval request successfully")
    void shouldCreateApprovalRequest() {
        // Setup
        when(approvalRequestRepository.save(any(ApprovalRequest.class)))
                .thenReturn(savedRequest);

        // Execute
        ApprovalRequestDTO result = approvalRequestService.createApprovalRequest(createDTO);

        // Verify
        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo(savedRequest.getId());
        assertThat(result.getStatus()).isEqualTo(ApprovalStatus.PENDING_APPROVAL);
        assertThat(result.getRequestedById()).isEqualTo(userId);

        verify(approvalRequestRepository).save(any(ApprovalRequest.class));
    }

    @Test
    @DisplayName("Should get approval request by ID")
    void shouldGetApprovalRequestById() {
        // Setup
        when(approvalRequestRepository.findById(savedRequest.getId()))
                .thenReturn(Optional.of(savedRequest));

        // Execute
        ApprovalRequestDTO result = approvalRequestService.getApprovalRequest(savedRequest.getId());

        // Verify
        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo(savedRequest.getId());
        assertThat(result.getGeneratedContent()).isEqualTo(savedRequest.getGeneratedContent());
    }

    @Test
    @DisplayName("Should get all pending approval requests")
    void shouldGetAllPendingRequests() {
        // Setup
        List<ApprovalRequest> pending = Arrays.asList(savedRequest);
        when(approvalRequestRepository.findAllPendingRequests())
                .thenReturn(pending);

        // Execute
        List<ApprovalRequestDTO> result = approvalRequestService.getPendingApprovalRequests();

        // Verify
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getStatus()).isEqualTo(ApprovalStatus.PENDING_APPROVAL);
    }

    @Test
    @DisplayName("Should approve request successfully")
    void shouldApproveRequest() {
        // Setup
        when(approvalRequestRepository.findById(savedRequest.getId()))
                .thenReturn(Optional.of(savedRequest));
        when(approvalRequestRepository.save(any(ApprovalRequest.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        ApprovalDecisionDTO decision = ApprovalDecisionDTO.builder()
                .approved(true)
                .reviewerId(reviewerId)
                .reviewerName("Jane Smith")
                .reviewerEmail("jane@example.com")
                .notes("Looks good")
                .build();

        // Execute
        ApprovalRequestDTO result = approvalRequestService.approveRequest(
                savedRequest.getId(), decision);

        // Verify
        assertThat(result).isNotNull();
        assertThat(result.getStatus()).isEqualTo(ApprovalStatus.APPROVED);
        assertThat(result.getReviewedById()).isEqualTo(reviewerId);
        assertThat(result.getReviewedAt()).isNotNull();

        verify(approvalRequestRepository).save(any(ApprovalRequest.class));
    }

    @Test
    @DisplayName("Should reject request successfully")
    void shouldRejectRequest() {
        // Setup
        when(approvalRequestRepository.findById(savedRequest.getId()))
                .thenReturn(Optional.of(savedRequest));
        when(approvalRequestRepository.save(any(ApprovalRequest.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        ApprovalDecisionDTO decision = ApprovalDecisionDTO.builder()
                .approved(false)
                .reviewerId(reviewerId)
                .reviewerName("Jane Smith")
                .reviewerEmail("jane@example.com")
                .rejectionReason("Test coverage insufficient")
                .build();

        // Execute
        ApprovalRequestDTO result = approvalRequestService.rejectRequest(
                savedRequest.getId(), decision);

        // Verify
        assertThat(result).isNotNull();
        assertThat(result.getStatus()).isEqualTo(ApprovalStatus.REJECTED);
        assertThat(result.getRejectionReason()).isEqualTo("Test coverage insufficient");

        verify(approvalRequestRepository).save(any(ApprovalRequest.class));
    }

    @Test
    @DisplayName("Should cancel request by requester")
    void shouldCancelRequest() {
        // Setup
        when(approvalRequestRepository.findById(savedRequest.getId()))
                .thenReturn(Optional.of(savedRequest));
        when(approvalRequestRepository.save(any(ApprovalRequest.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        // Execute
        ApprovalRequestDTO result = approvalRequestService.cancelRequest(
                savedRequest.getId(), userId);

        // Verify
        assertThat(result).isNotNull();
        assertThat(result.getStatus()).isEqualTo(ApprovalStatus.CANCELLED);

        verify(approvalRequestRepository).save(any(ApprovalRequest.class));
    }

    @Test
    @DisplayName("Should not allow non-requester to cancel")
    void shouldNotAllowNonRequesterToCancel() {
        // Setup
        when(approvalRequestRepository.findById(savedRequest.getId()))
                .thenReturn(Optional.of(savedRequest));

        UUID differentUser = UUID.randomUUID();

        // Execute & Verify
        assertThatThrownBy(() ->
                approvalRequestService.cancelRequest(savedRequest.getId(), differentUser))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Only requester can cancel");
    }

    @Test
    @DisplayName("Should require rejection reason when rejecting")
    void shouldRequireRejectionReason() {
        // Setup
        when(approvalRequestRepository.findById(savedRequest.getId()))
                .thenReturn(Optional.of(savedRequest));

        ApprovalDecisionDTO decision = ApprovalDecisionDTO.builder()
                .approved(false)
                .reviewerId(reviewerId)
                .reviewerName("Jane Smith")
                .build();

        // Execute & Verify
        assertThatThrownBy(() ->
                approvalRequestService.rejectRequest(savedRequest.getId(), decision))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Rejection reason is required");
    }

    @Test
    @DisplayName("Should get summary statistics")
    void shouldGetSummaryStatistics() {
        // Setup
        when(approvalRequestRepository.count()).thenReturn(100L);
        when(approvalRequestRepository.countByStatus(ApprovalStatus.PENDING_APPROVAL))
                .thenReturn(20L);
        when(approvalRequestRepository.countByStatus(ApprovalStatus.APPROVED))
                .thenReturn(60L);
        when(approvalRequestRepository.countByStatus(ApprovalStatus.REJECTED))
                .thenReturn(15L);
        when(approvalRequestRepository.countByStatus(ApprovalStatus.EXPIRED))
                .thenReturn(5L);
        when(approvalRequestRepository.findByStatusOrderByCreatedAtDesc(ApprovalStatus.APPROVED))
                .thenReturn(Collections.emptyList());
        when(approvalRequestRepository.findAllPendingRequests())
                .thenReturn(Collections.emptyList());

        // Execute
        ApprovalRequestSummaryDTO summary = approvalRequestService.getSummaryStatistics();

        // Verify
        assertThat(summary).isNotNull();
        assertThat(summary.getTotalRequests()).isEqualTo(100L);
        assertThat(summary.getPendingRequests()).isEqualTo(20L);
        assertThat(summary.getApprovedRequests()).isEqualTo(60L);
        assertThat(summary.getRejectedRequests()).isEqualTo(15L);
        assertThat(summary.getApprovalRate()).isGreaterThan(0);
    }
}