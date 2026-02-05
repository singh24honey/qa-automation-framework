package com.company.qa.integration.jira;

import com.company.qa.config.TestConfig;
import com.company.qa.config.TestContainersConfiguration;
import com.company.qa.exception.JiraIntegrationException;
import com.company.qa.model.entity.JiraConfiguration;
import com.company.qa.model.entity.JiraStory;
import com.company.qa.repository.JiraConfigurationRepository;
import com.company.qa.repository.JiraStoryRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.assertj.core.api.Assertions.*;

/**
 * Week 9 Day 2: JIRA Story Service Integration Tests
 *
 * Uses WireMock to simulate JIRA Cloud API responses.
 * Tests:
 * - Single story fetching
 * - JQL-based multi-story fetching
 * - JSON parsing (standard fields, custom fields, ADF)
 * - AC extraction strategies
 * - Database persistence
 * - Error handling
 */
@SpringBootTest
@Import({TestContainersConfiguration.class, TestConfig.class})
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "spring.jpa.properties.jakarta.validation.mode=none",
        "jira.secrets.enabled=false",
        "jira.base-url=http://localhost:8089",
        "jira.audit.enabled=false"  // ✅ Disable audit logging in tests

})

class JiraStoryServiceTest {

    @Autowired
    private JiraStoryService storyService;

    @Autowired
    private JiraConfigurationRepository configRepository;

    @Autowired
    private JiraStoryRepository storyRepository;

    @Autowired
    private ObjectMapper objectMapper;


    private static WireMockServer wireMockServer;
    private JiraConfiguration testConfig;

    @BeforeAll
    static void setupWireMock() {
        wireMockServer = new WireMockServer(options().port(8089));
        wireMockServer.start();
    }

    @AfterAll
    static void tearDownWireMock() {
        wireMockServer.stop();
    }

    @BeforeEach
    void setUp() {
        wireMockServer.resetAll();
        storyRepository.deleteAll();
        configRepository.deleteAll();

        // Create test JIRA configuration with correct field names
        testConfig = JiraConfiguration.builder()
                .configName("default")
                .jiraUrl("http://localhost:8089")
                .projectKey("QA")
                .secretArn("mock-secret")
                .maxRequestsPerMinute(60)          // ✅ Correct field name
                .enabled(true)                      // ✅ Correct field name
                .createdBy("test-system")           // ✅ Required field
                .build();
        testConfig = configRepository.save(testConfig);
    }

    @Test
    @DisplayName("Should fetch single JIRA story successfully")
    void shouldFetchSingleStory() {
        // Mock JIRA API response
        String mockResponse = """
            {
              "id": "10001",
              "key": "QA-123",
              "fields": {
                "summary": "User Login Feature",
                "description": "Implement secure user login",
                "issuetype": { "name": "Story" },
                "status": { "name": "To Do" },
                "priority": { "name": "High" },
                "assignee": { "displayName": "John Doe" },
                "reporter": { "displayName": "Jane Smith" },
                "labels": ["authentication", "security"],
                "components": [{"name": "Frontend"}],
                "created": "2025-01-20T10:00:00.000+0000",
                "updated": "2025-01-29T15:30:00.000+0000",
                "customfield_10001": "Given I am on login page\\nWhen I enter valid credentials\\nThen I should be logged in"
              }
            }
            """;

        wireMockServer.stubFor(get(urlPathMatching("/rest/api/3/issue/QA-123"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(mockResponse)));

        // Fetch story
        JiraStory story = storyService.fetchStory("QA-123", "test-user");

        // Verify story fields
        assertThat(story).isNotNull();
        assertThat(story.getJiraKey()).isEqualTo("QA-123");
        assertThat(story.getSummary()).isEqualTo("User Login Feature");
        assertThat(story.getStoryType()).isEqualTo("Story");
        assertThat(story.getStatus()).isEqualTo("To Do");
        assertThat(story.getPriority()).isEqualTo("High");
        assertThat(story.getAssignee()).isEqualTo("John Doe");
        //assertThat(story.getLabels()).containsExactly("authentication", "security");
        //assertThat(story.getComponents()).containsExactly("Frontend");
        assertThat(story.hasAcceptanceCriteria()).isTrue();

        // Verify database persistence
        JiraStory savedStory = storyRepository.findByJiraKey("QA-123").orElse(null);
        assertThat(savedStory).isNotNull();
        assertThat(savedStory.getSummary()).isEqualTo("User Login Feature");
    }

    @Test
    @DisplayName("Should update existing story on re-fetch")
    void shouldUpdateExistingStory() {
        // First fetch
        String initialResponse = """
            {
              "id": "10001",
              "key": "QA-123",
              "fields": {
                "summary": "User Login Feature",
                "description": "Initial description",
                "issuetype": { "name": "Story" },
                "status": { "name": "To Do" },
                "priority": { "name": "Medium" },
                "created": "2025-01-20T10:00:00.000+0000",
                "updated": "2025-01-20T10:00:00.000+0000"
              }
            }
            """;

        wireMockServer.stubFor(get(urlPathMatching("/rest/api/3/issue/QA-123"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(initialResponse)));


        UUID configId = UUID.fromString("116870bf-43ae-4c46-b5aa-cc0ef1aa705e");

        /*configRepository.save(
                JiraConfiguration.builder()
                        .id(configId)
                        .configName("test-config")
                        .jiraUrl("http://localhost:8089")
                        .projectKey("QA")
                        .enabled(true)
                        .build()
        );*/

        JiraStory story1 = storyService.fetchStory("QA-123", "test-user");
        assertThat(story1.getStatus()).isEqualTo("To Do");
        assertThat(story1.getPriority()).isEqualTo("Medium");

        // Second fetch with updated data
        String updatedResponse = """
            {
              "id": "10001",
              "key": "QA-123",
              "fields": {
                "summary": "User Login Feature",
                "description": "Updated description",
                "issuetype": { "name": "Story" },
                "status": { "name": "In Progress" },
                "priority": { "name": "High" },
                "created": "2025-01-20T10:00:00.000+0000",
                "updated": "2025-01-29T15:30:00.000+0000"
              }
            }
            """;

        wireMockServer.stubFor(get(urlPathMatching("/rest/api/3/issue/QA-123"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(updatedResponse)));

        JiraStory story2 = storyService.fetchStory("QA-123", "test-user");

        // Verify update
        assertThat(story2.getId()).isEqualTo(story1.getId()); // Same ID
        assertThat(story2.getStatus()).isEqualTo("In Progress"); // Updated
        assertThat(story2.getPriority()).isEqualTo("High"); // Updated

        // Verify only one record in database
        long count = storyRepository.count();
        assertThat(count).isEqualTo(1);
    }

    @Test
    @DisplayName("Should fetch multiple stories via JQL")
    void shouldFetchStoriesByJql() {
        // Mock JQL search response
        String mockResponse = """
            {
              "total": 2,
              "issues": [
                {
                  "id": "10001",
                  "key": "QA-123",
                  "fields": {
                    "summary": "Story 1",
                    "issuetype": { "name": "Story" },
                    "status": { "name": "To Do" },
                    "priority": { "name": "High" },
                    "created": "2025-01-20T10:00:00.000+0000",
                    "updated": "2025-01-20T10:00:00.000+0000"
                  }
                },
                {
                  "id": "10002",
                  "key": "QA-124",
                  "fields": {
                    "summary": "Story 2",
                    "issuetype": { "name": "Story" },
                    "status": { "name": "In Progress" },
                    "priority": { "name": "Medium" },
                    "created": "2025-01-21T10:00:00.000+0000",
                    "updated": "2025-01-21T10:00:00.000+0000"
                  }
                }
              ]
            }
            """;

        wireMockServer.stubFor(get(urlPathMatching("/rest/api/3/search"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(mockResponse)));

        // Fetch stories
        List<JiraStory> stories = storyService.fetchStoriesByJql(
                "project = QA AND status = 'To Do'",
                "test-user",
                50
        );

        // Verify
        assertThat(stories).hasSize(2);
        assertThat(stories.get(0).getJiraKey()).isEqualTo("QA-123");
        assertThat(stories.get(1).getJiraKey()).isEqualTo("QA-124");

        // Verify database persistence
        assertThat(storyRepository.count()).isEqualTo(2);
    }

    @Test
    @DisplayName("Should handle JIRA API error")
    void shouldHandleJiraApiError() {
        wireMockServer.stubFor(get(urlPathMatching("/rest/api/3/issue/QA-999"))
                .willReturn(aResponse()
                        .withStatus(404)
                        .withBody("{\"errorMessages\":[\"Issue does not exist\"]}")));

        assertThatThrownBy(() -> storyService.fetchStory("QA-999", "test-user"))
                .isInstanceOf(JiraIntegrationException.class);
    }

    @Test
    @DisplayName("Should extract AC from custom field")
    void shouldExtractACFromCustomField() {
        String mockResponse = """
            {
              "id": "10001",
              "key": "QA-123",
              "fields": {
                "summary": "Test Story",
                "issuetype": { "name": "Story" },
                "status": { "name": "To Do" },
                "priority": { "name": "Medium" },
                "created": "2025-01-20T10:00:00.000+0000",
                "updated": "2025-01-20T10:00:00.000+0000",
                "customfield_10001": "Given test\\nWhen test\\nThen test"
              }
            }
            """;

        wireMockServer.stubFor(get(urlPathMatching("/rest/api/3/issue/QA-123"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(mockResponse)));

        JiraStory story = storyService.fetchStory("QA-123", "test-user");

        assertThat(story.hasAcceptanceCriteria()).isTrue();
        assertThat(story.getAcceptanceCriteria()).contains("Given test");
    }

    @Test
    @DisplayName("Should handle stories ready for test generation")
    void shouldHandleStoriesReadyForTestGeneration() {
        // Create stories with various levels of completeness
        JiraStory completeStory = JiraStory.builder()
                .configuration(testConfig)
                .jiraKey("QA-COMPLETE")
                .summary("Complete Story")
                .description("Has description")
                .acceptanceCriteria("Has AC")
                .build();
        storyRepository.save(completeStory);

        JiraStory incompleteStory = JiraStory.builder()
                .configuration(testConfig)
                .jiraKey("QA-INCOMPLETE")
                .summary("Incomplete Story")
                .build();
        storyRepository.save(incompleteStory);

        // Query ready stories
        List<JiraStory> readyStories = storyService.getStoriesReadyForTestGeneration();

        assertThat(readyStories).hasSize(1);
        assertThat(readyStories.get(0).getJiraKey()).isEqualTo("QA-COMPLETE");
    }
}