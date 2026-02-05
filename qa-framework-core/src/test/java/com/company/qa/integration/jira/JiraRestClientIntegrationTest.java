package com.company.qa.integration.jira;

import com.company.qa.config.JiraProperties;
import com.company.qa.config.TestConfig;
import com.company.qa.integration.secrets.JiraCredentials;
import com.company.qa.integration.secrets.JiraSecretsManager;
import com.company.qa.model.entity.JiraConfiguration;
import com.company.qa.repository.JiraApiAuditLogRepository;
import com.company.qa.service.execution.RetryService;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.client.RestTemplate;

import java.util.UUID;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * Integration tests for JiraRestClient using WireMock.
 * FIXED: Uses TestConfig to provide mock schedulers.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("test")
@Import(TestConfig.class)
class JiraRestClientIntegrationTest {

    @Autowired
    private RestTemplate restTemplate;

    @MockBean
    private JiraSecretsManager secretsManager;

    @Autowired
    private RetryService retryService;

    @Autowired
    private JiraProperties jiraProperties;

    @MockBean
    private JiraApiAuditLogRepository auditLogRepository;

    private WireMockServer wireMockServer;
    private JiraRestClient jiraRestClient;
    private JiraConfiguration testConfig;
    private JiraApiAuditLogger apiAuditLogger;

    @BeforeEach
    void setUp() {
        // Start WireMock server
        wireMockServer = new WireMockServer(WireMockConfiguration.options().dynamicPort());
        wireMockServer.start();

        // Configure test config to point to WireMock
        testConfig = JiraConfiguration.builder()
                .id(UUID.randomUUID())
                .configName("integration-test")
                .jiraUrl("http://localhost:" + wireMockServer.port())
                .projectKey("TEST")
                .maxRequestsPerMinute(60)
                .secretArn("test-secret")
                .build();

        // Mock credentials
        JiraCredentials testCredentials = JiraCredentials.builder()
                .email("test@example.com")
                .apiToken("test-token")
                .build();
        when(secretsManager.getCredentials(anyString())).thenReturn(testCredentials);

        // Create audit logger
        apiAuditLogger = new JiraApiAuditLogger(auditLogRepository);

        // Create client with REAL RetryService
        jiraRestClient = new JiraRestClient(
                restTemplate,
                secretsManager,
                retryService,
                jiraProperties,
                apiAuditLogger
        );
    }

    @AfterEach
    void tearDown() {
        if (wireMockServer != null) {
            wireMockServer.stop();
        }
    }

    @Test
    void get_RealJiraIssue_Success() {
        wireMockServer.stubFor(get(urlEqualTo("/rest/api/3/issue/TEST-123"))
                .withHeader("Authorization", matching("Basic .*"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"key\":\"TEST-123\",\"fields\":{\"summary\":\"Test Issue\"}}")));

        String response = jiraRestClient.get(testConfig, "/rest/api/3/issue/TEST-123", null, "test-user");

        assertThat(response).contains("TEST-123").contains("Test Issue");
        wireMockServer.verify(getRequestedFor(urlEqualTo("/rest/api/3/issue/TEST-123")));
    }

    @Test
    void get_ServerError_RetriesAndEventuallyFails() {
        wireMockServer.stubFor(get(urlEqualTo("/rest/api/3/issue/TEST-999"))
                .willReturn(aResponse().withStatus(500).withBody("Internal Server Error")));

        assertThatThrownBy(() ->
                jiraRestClient.get(testConfig, "/rest/api/3/issue/TEST-999", null, "test-user")
        )
                .isInstanceOf(com.company.qa.exception.QaFrameworkException.class)
                .hasMessageContaining("JIRA API call failed");

        wireMockServer.verify(exactly(3), getRequestedFor(urlEqualTo("/rest/api/3/issue/TEST-999")));
    }

    @Test
    void get_TransientError_RetriesAndSucceeds() {
        wireMockServer.stubFor(get(urlEqualTo("/rest/api/3/issue/TEST-456"))
                .inScenario("Retry").whenScenarioStateIs("Started")
                .willReturn(aResponse().withStatus(500))
                .willSetStateTo("First Retry"));

        wireMockServer.stubFor(get(urlEqualTo("/rest/api/3/issue/TEST-456"))
                .inScenario("Retry").whenScenarioStateIs("First Retry")
                .willReturn(aResponse().withStatus(500))
                .willSetStateTo("Second Retry"));

        wireMockServer.stubFor(get(urlEqualTo("/rest/api/3/issue/TEST-456"))
                .inScenario("Retry").whenScenarioStateIs("Second Retry")
                .willReturn(aResponse().withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"key\":\"TEST-456\"}")));

        String response = jiraRestClient.get(testConfig, "/rest/api/3/issue/TEST-456", null, "test-user");

        assertThat(response).contains("TEST-456");
        wireMockServer.verify(exactly(3), getRequestedFor(urlEqualTo("/rest/api/3/issue/TEST-456")));
    }

    @Test
    void checkHealth_Success() {
        wireMockServer.stubFor(get(urlEqualTo("/rest/api/3/serverInfo"))
                .willReturn(aResponse().withStatus(200)
                        .withBody("{\"serverTitle\":\"Test JIRA\"}")));

        boolean isHealthy = jiraRestClient.checkHealth(testConfig);

        assertThat(isHealthy).isTrue();
    }

    @Test
    void checkHealth_Failure() {
        wireMockServer.stubFor(get(urlEqualTo("/rest/api/3/serverInfo"))
                .willReturn(aResponse().withStatus(503)));

        boolean isHealthy = jiraRestClient.checkHealth(testConfig);

        assertThat(isHealthy).isFalse();
    }
}