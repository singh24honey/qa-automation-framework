package com.company.qa.integration;

import com.company.qa.model.dto.TestDto;
import com.company.qa.model.enums.Priority;
import com.company.qa.model.enums.TestFramework;
import com.company.qa.repository.TestRepository;
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
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class TestControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private TestRepository testRepository;

    @BeforeEach
    void setUp() {
        testRepository.deleteAll();
    }

    @Test
    @DisplayName("Should create test successfully")
    void createTest_WithValidData_Returns201() throws Exception {
        // Given
        TestDto testDto = TestDto.builder()
                .name("Integration Test")
                .description("Test description")
                .framework(TestFramework.SELENIUM)
                .language("java")
                .priority(Priority.HIGH)
                .build();

        // When/Then
        mockMvc.perform(post("/api/v1/tests")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(testDto)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.name").value("Integration Test"));
    }

    @Test
    @DisplayName("Should get all tests")
    void getAllTests_ReturnsTestList() throws Exception {
        mockMvc.perform(get("/api/v1/tests"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray());
    }
}