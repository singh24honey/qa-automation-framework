package com.company.qa.integration;

import com.company.qa.service.storage.FileStorageService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
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
class FileStorageIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private FileStorageService fileStorageService;

    private String validApiKey;

    @BeforeEach
    void setUp() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/api-keys")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Test Key\"}"))
                .andReturn();

        validApiKey = objectMapper.readTree(result.getResponse().getContentAsString())
                .get("data")
                .get("keyValue")
                .asText();
    }

    @Test
    @DisplayName("Should upload file successfully")
    void uploadFile_Success() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "test.png",
                "image/png",
                "test-image-data".getBytes()
        );

        mockMvc.perform(multipart("/api/v1/storage/upload/exec-123")
                        .file(file)
                        .param("type", "SCREENSHOT")
                        .header("X-API-Key", validApiKey))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.filename").exists())
                .andExpect(jsonPath("$.data.type").value("SCREENSHOT"));
    }

    @Test
    @DisplayName("Should list files for execution")
    void listFiles_Success() throws Exception {
        String executionId = "exec-456";
        fileStorageService.saveScreenshot(executionId, "img1".getBytes(), "step1");
        fileStorageService.saveLog(executionId, "log content", "execution");

        mockMvc.perform(get("/api/v1/storage/files/" + executionId)
                        .header("X-API-Key", validApiKey))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data.length()").value(2));
    }

    @Test
    @DisplayName("Should download file")
    void downloadFile_Success() throws Exception {
        String executionId = "exec-789";
        var metadata = fileStorageService.saveScreenshot(executionId, "test-image".getBytes(), "step1");

        mockMvc.perform(get("/api/v1/storage/download/" + executionId + "/" + metadata.getFilename())
                        .header("X-API-Key", validApiKey))
                .andExpect(status().isOk())
                .andExpect(header().exists("Content-Disposition"))
                .andExpect(content().bytes("test-image".getBytes()));
    }

    @Test
    @DisplayName("Should delete execution files")
    void deleteExecutionFiles_Success() throws Exception {
        String executionId = "exec-delete";
        fileStorageService.saveScreenshot(executionId, "img".getBytes(), "step1");

        mockMvc.perform(delete("/api/v1/storage/files/" + executionId)
                        .header("X-API-Key", validApiKey))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        var files = fileStorageService.listFiles(executionId);
        assert files.isEmpty();
    }

    @Test
    @DisplayName("Should get storage statistics")
    void getStorageStats_Success() throws Exception {
        mockMvc.perform(get("/api/v1/storage/stats")
                        .header("X-API-Key", validApiKey))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.totalFiles").exists())
                .andExpect(jsonPath("$.data.totalSizeBytes").exists());
    }

    @Test
    @DisplayName("Should trigger cleanup")
    void triggerCleanup_Success() throws Exception {
        mockMvc.perform(post("/api/v1/storage/cleanup")
                        .header("X-API-Key", validApiKey))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isNumber());
    }
}