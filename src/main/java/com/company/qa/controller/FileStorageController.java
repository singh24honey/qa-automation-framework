package com.company.qa.controller;

import com.company.qa.model.dto.ApiResponse;
import com.company.qa.model.dto.FileMetadata;
import com.company.qa.model.dto.StorageStats;
import com.company.qa.model.enums.FileType;
import com.company.qa.service.storage.FileStorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/v1/storage")
@RequiredArgsConstructor
@Slf4j
public class FileStorageController {

    private final FileStorageService fileStorageService;

    @PostMapping("/upload/{executionId}")
    public ResponseEntity<ApiResponse<FileMetadata>> uploadFile(
            @PathVariable String executionId,
            @RequestParam("file") MultipartFile file,
            @RequestParam("type") FileType fileType) {

        log.info("POST /api/v1/storage/upload/{} - Uploading file: {}", executionId, file.getOriginalFilename());

        FileMetadata metadata = fileStorageService.saveFile(executionId, file, fileType);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(metadata, "File uploaded successfully"));
    }

    @GetMapping("/files/{executionId}")
    public ResponseEntity<ApiResponse<List<FileMetadata>>> listFiles(@PathVariable String executionId) {
        log.info("GET /api/v1/storage/files/{} - Listing files", executionId);

        List<FileMetadata> files = fileStorageService.listFiles(executionId);

        return ResponseEntity.ok(ApiResponse.success(files));
    }

    @GetMapping("/download/{executionId}/{filename:.+}")
    public ResponseEntity<Resource> downloadFile(
            @PathVariable String executionId,
            @PathVariable String filename) {

        log.info("GET /api/v1/storage/download/{}/{} - Downloading file", executionId, filename);

        Resource resource = fileStorageService.loadFileAsResource(executionId, filename);

        String contentType = determineContentType(filename);

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(contentType))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + resource.getFilename() + "\"")
                .body(resource);
    }

    @DeleteMapping("/files/{executionId}")
    public ResponseEntity<ApiResponse<Void>> deleteExecutionFiles(@PathVariable String executionId) {
        log.info("DELETE /api/v1/storage/files/{} - Deleting all files", executionId);

        fileStorageService.deleteExecutionFiles(executionId);

        return ResponseEntity.ok(ApiResponse.success(null, "All files deleted successfully"));
    }

    @GetMapping("/stats")
    public ResponseEntity<ApiResponse<StorageStats>> getStorageStats() {
        log.info("GET /api/v1/storage/stats - Getting storage statistics");

        StorageStats stats = fileStorageService.getStorageStats();

        return ResponseEntity.ok(ApiResponse.success(stats));
    }

    @PostMapping("/cleanup")
    public ResponseEntity<ApiResponse<Integer>> triggerCleanup() {
        log.info("POST /api/v1/storage/cleanup - Triggering manual cleanup");

        int deletedCount = fileStorageService.cleanupOldFiles();

        return ResponseEntity.ok(ApiResponse.success(deletedCount,
                "Cleanup completed. Deleted " + deletedCount + " directories"));
    }

    private String determineContentType(String filename) {
        String extension = filename.substring(filename.lastIndexOf('.') + 1).toLowerCase();

        return switch (extension) {
            case "png" -> "image/png";
            case "jpg", "jpeg" -> "image/jpeg";
            case "txt", "log" -> "text/plain";
            case "html" -> "text/html";
            case "json" -> "application/json";
            case "mp4" -> "video/mp4";
            default -> "application/octet-stream";
        };
    }
}