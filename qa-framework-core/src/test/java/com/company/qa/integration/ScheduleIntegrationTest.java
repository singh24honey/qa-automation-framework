package com.company.qa.integration;

import com.company.qa.model.entity.Test;
import com.company.qa.model.enums.Priority;
import com.company.qa.model.enums.TestFramework;
import com.company.qa.repository.TestRepository;
import com.company.qa.repository.TestScheduleRepository;
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
class ScheduleIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private TestRepository testRepository;

    @Autowired
    private TestScheduleRepository scheduleRepository;

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
        Test test = Test.builder()
                .name("Schedule Test")
                .description("Test for scheduling")
                .framework(TestFramework.SELENIUM)
                .language("json")
                .priority(Priority.HIGH)
                .content("{\"steps\":[]}")
                .isActive(true)
                .build();

        test = testRepository.save(test);
        testId = test.getId();
    }

    @org.junit.jupiter.api.Test
    @DisplayName("Should create schedule successfully")
    void createSchedule_Success() throws Exception {
        String requestBody = String.format("""
                {
                  "name": "Daily Test",
                  "description": "Runs daily at 9 AM",
                  "testId": "%s",
                  "cronExpression": "0 0 9 * * *",
                  "timezone": "UTC",
                  "browser": "CHROME",
                  "headless": true,
                  "enabled": true
                }
                """, testId);

        mockMvc.perform(post("/api/v1/schedules")
                        .header("X-API-Key", validApiKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.name").value("Daily Test"))
                .andExpect(jsonPath("$.data.cronExpression").value("0 0 9 * * *"))
                .andExpect(jsonPath("$.data.nextRunTime").exists());
    }

    @org.junit.jupiter.api.Test
    @DisplayName("Should reject invalid cron expression")
    void createSchedule_InvalidCron_ReturnsError() throws Exception {
        String requestBody = String.format("""
                {
                  "name": "Bad Schedule",
                  "testId": "%s",
                  "cronExpression": "invalid-cron"
                }
                """, testId);

        mockMvc.perform(post("/api/v1/schedules")
                        .header("X-API-Key", validApiKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isInternalServerError());
    }

    @org.junit.jupiter.api.Test
    @DisplayName("Should validate cron expression")
    void validateCronExpression_Success() throws Exception {
        mockMvc.perform(post("/api/v1/schedules/validate-cron")
                        .header("X-API-Key", validApiKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"cronExpression\": \"0 0 9 * * MON-FRI\", \"timezone\": \"UTC\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.valid").value(true))
                .andExpect(jsonPath("$.data.description").exists());
    }
}