package com.company.qa.service.storage;

import com.company.qa.config.StorageConfig;
import com.company.qa.exception.FileNotFoundException;
import com.company.qa.exception.StorageException;
import com.company.qa.model.dto.FileMetadata;
import com.company.qa.model.dto.StorageStats;
import com.company.qa.model.enums.FileType;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.io.Resource;
import org.springframework.mock.web.MockMultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FileStorageServiceTest {

    @TempDir
    Path tempDir;

    private FileStorageService fileStorageService;
    private StorageConfig storageConfig;

    @BeforeEach
    void setUp() {
        storageConfig = new StorageConfig();
        storageConfig.setType("local");
        storageConfig.setBasePath(tempDir.toString());
        storageConfig.setScreenshots(tempDir.resolve("screenshots").toString());
        storageConfig.setVideos(tempDir.resolve("videos").toString());
        storageConfig.setLogs(tempDir.resolve("logs").toString());
        storageConfig.setReports(tempDir.resolve("reports").toString());
        storageConfig.setRetentionDays(30);
        storageConfig.setMaxFileSizeMb(10);
        storageConfig.setAllowedExtensions(Arrays.asList("png", "jpg", "txt", "log", "html", "json"));

        storageConfig.init();

        fileStorageService = new FileStorageService(storageConfig);
    }

    @Test
    @DisplayName("Should save screenshot successfully")
    void saveScreenshot_Success() {
        String executionId = "exec-123";
        byte[] imageData = "fake-image-data".getBytes();
        String stepName = "login-step";

        FileMetadata metadata = fileStorageService.saveScreenshot(executionId, imageData, stepName);

        assertThat(metadata).isNotNull();
        assertThat(metadata.getFilename()).contains(stepName);
        assertThat(metadata.getType()).isEqualTo(FileType.SCREENSHOT);
        assertThat(metadata.getSizeBytes()).isEqualTo(imageData.length);
        assertThat(Files.exists(Path.of(metadata.getPath()))).isTrue();
    }

    @Test
    @DisplayName("Should save log file successfully")
    void saveLog_Success() {
        String executionId = "exec-123";
        String content = "Test log content\nLine 2\nLine 3";
        String logName = "execution";

        FileMetadata metadata = fileStorageService.saveLog(executionId, content, logName);

        assertThat(metadata).isNotNull();
        assertThat(metadata.getFilename()).contains(logName);
        assertThat(metadata.getType()).isEqualTo(FileType.LOG);

        try {
            String savedContent = Files.readString(Path.of(metadata.getPath()));
            assertThat(savedContent).isEqualTo(content);
        } catch (IOException e) {
            Assertions.fail("Failed to read saved file");
        }
    }

    @Test
    @DisplayName("Should save multipart file successfully")
    void saveFile_Success() {
        String executionId = "exec-123";
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "test.png",
                "image/png",
                "test-data".getBytes()
        );

        FileMetadata metadata = fileStorageService.saveFile(executionId, file, FileType.SCREENSHOT);

        assertThat(metadata).isNotNull();
        assertThat(metadata.getType()).isEqualTo(FileType.SCREENSHOT);
        assertThat(Files.exists(Path.of(metadata.getPath()))).isTrue();
    }

    @Test
    @DisplayName("Should reject empty file")
    void saveFile_EmptyFile_ThrowsException() {
        String executionId = "exec-123";
        MockMultipartFile emptyFile = new MockMultipartFile(
                "file",
                "test.png",
                "image/png",
                new byte[0]
        );

        assertThatThrownBy(() -> fileStorageService.saveFile(executionId, emptyFile, FileType.SCREENSHOT))
                .isInstanceOf(StorageException.class)
                .hasMessageContaining("Cannot store empty file");
    }

    @Test
    @DisplayName("Should reject file with invalid extension")
    void saveFile_InvalidExtension_ThrowsException() {
        String executionId = "exec-123";
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "test.exe",
                "application/x-msdownload",
                "test-data".getBytes()
        );

        assertThatThrownBy(() -> fileStorageService.saveFile(executionId, file, FileType.SCREENSHOT))
                .isInstanceOf(StorageException.class)
                .hasMessageContaining("File type not allowed");
    }

    @Test
    @DisplayName("Should list files for execution")
    void listFiles_ReturnsAllFiles() {
        String executionId = "exec-123";
        fileStorageService.saveScreenshot(executionId, "img1".getBytes(), "step1");
        fileStorageService.saveScreenshot(executionId, "img2".getBytes(), "step2");
        fileStorageService.saveLog(executionId, "log content", "execution");

        List<FileMetadata> files = fileStorageService.listFiles(executionId);

        assertThat(files).hasSize(3);
        assertThat(files).extracting(FileMetadata::getType)
                .containsExactlyInAnyOrder(FileType.SCREENSHOT, FileType.SCREENSHOT, FileType.LOG);
    }

    @Test
    @DisplayName("Should load file as resource")
    void loadFileAsResource_Success() {
        String executionId = "exec-123";
        FileMetadata metadata = fileStorageService.saveScreenshot(executionId, "test-data".getBytes(), "test");

        Resource resource = fileStorageService.loadFileAsResource(executionId, metadata.getFilename());

        assertThat(resource.exists()).isTrue();
        assertThat(resource.isReadable()).isTrue();
    }

    @Test
    @DisplayName("Should throw exception for non-existent file")
    void loadFileAsResource_FileNotFound_ThrowsException() {
        assertThatThrownBy(() -> fileStorageService.loadFileAsResource("exec-123", "nonexistent.png"))
                .isInstanceOf(FileNotFoundException.class);
    }

    @Test
    @DisplayName("Should delete all execution files")
    void deleteExecutionFiles_Success() {
        String executionId = "exec-123";
        fileStorageService.saveScreenshot(executionId, "img".getBytes(), "step1");
        fileStorageService.saveLog(executionId, "log", "execution");

        fileStorageService.deleteExecutionFiles(executionId);

        List<FileMetadata> files = fileStorageService.listFiles(executionId);
        assertThat(files).isEmpty();
    }

    @Test
    @DisplayName("Should get storage statistics")
    void getStorageStats_ReturnsCorrectStats() {
        fileStorageService.saveScreenshot("exec-1", "img1".getBytes(), "step1");
        fileStorageService.saveScreenshot("exec-2", "img2".getBytes(), "step2");
        fileStorageService.saveLog("exec-1", "log content", "execution");

        StorageStats stats = fileStorageService.getStorageStats();

        assertThat(stats.getTotalFiles()).isEqualTo(3);
        assertThat(stats.getTotalSizeBytes()).isGreaterThan(0);
        assertThat(stats.getFilesByType()).containsKeys("SCREENSHOT", "LOG");
    }

    @Test
    @DisplayName("Should cleanup old files")
    void cleanupOldFiles_DeletesOldDirectories() throws InterruptedException {
        String oldExecutionId = "old-exec";
        fileStorageService.saveScreenshot(oldExecutionId, "img".getBytes(), "step1");

        storageConfig.setRetentionDays(0);
        Thread.sleep(100);

        int deletedCount = fileStorageService.cleanupOldFiles();

        assertThat(deletedCount).isGreaterThan(0);
        List<FileMetadata> files = fileStorageService.listFiles(oldExecutionId);
        assertThat(files).isEmpty();
    }
}