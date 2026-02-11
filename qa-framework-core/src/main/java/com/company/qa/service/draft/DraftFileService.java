package com.company.qa.service.draft;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Draft File Service - manages test files in UAT submodule workflow.
 *
 * Integrates with existing WriteTestFileTool and GitService.
 *
 * Folder Structure:
 * uat-test-runner/
 * ├── drafts/           # AI-generated Playwright tests awaiting approval
 * ├── approved/         # Approved tests ready for execution
 * └── rejected/         # Rejected tests (audit trail)
 *
 * Workflow:
 * 1. PlaywrightAgent generates test → saves to drafts/
 * 2. QA Manager reviews in UI → approves/rejects
 * 3. On approval → moves to approved/ → triggers Git workflow
 * 4. On rejection → moves to rejected/ with metadata
 */
@Slf4j
@Service
public class DraftFileService {

    @Value("${uat.submodule.path:../playwright-tests}")
    private String uatSubmodulePath;

    private Path draftsDir;
    private Path approvedDir;
    private Path rejectedDir;

    @PostConstruct
    public void init() {
        try {
            // Initialize folder structure
            Path uatRoot = Paths.get(uatSubmodulePath).toAbsolutePath().normalize();

            log.info("🔧 Initializing UAT submodule draft folders");
            log.info("   UAT Root: {}", uatRoot);

            draftsDir = uatRoot.resolve("drafts");
            approvedDir = uatRoot.resolve("approved");
            rejectedDir = uatRoot.resolve("rejected");

            // Create directories if they don't exist
            Files.createDirectories(draftsDir);
            Files.createDirectories(approvedDir);
            Files.createDirectories(rejectedDir);

            log.info("✅ Draft folder structure initialized");
            log.info("   Drafts:   {}", draftsDir);
            log.info("   Approved: {}", approvedDir);
            log.info("   Rejected: {}", rejectedDir);

        } catch (IOException e) {
            log.error("❌ Failed to initialize draft folder structure", e);
            throw new RuntimeException("Failed to initialize draft folders", e);
        }
    }

    /**
     * Saves a generated Playwright test file to the drafts folder
     */
    public Path saveToDrafts(String fileName, String content) throws IOException {
        log.info("💾 Saving generated Playwright test to drafts: {}", fileName);

        Path draftFile = draftsDir.resolve(fileName);
        Files.writeString(draftFile, content,
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING);

        log.info("✅ Draft saved successfully: {}", draftFile.toAbsolutePath());
        return draftFile;
    }

    /**
     * Moves a draft file to approved folder after QA Manager approval
     */
    public Path moveDraftToApproved(String fileName) throws IOException {
        log.info("✅ Moving draft to approved: {}", fileName);

        Path sourcePath = draftsDir.resolve(fileName);
        Path targetPath = approvedDir.resolve(fileName);

        if (!Files.exists(sourcePath)) {
            throw new IOException("Draft file not found: " + fileName);
        }

        Files.move(sourcePath, targetPath, StandardCopyOption.REPLACE_EXISTING);
        log.info("✅ File moved to approved: {}", targetPath.toAbsolutePath());

        return targetPath;
    }

    /**
     * Moves a draft file to rejected folder after QA Manager rejection
     */
    public Path moveDraftToRejected(String fileName, String rejectionReason) throws IOException {
        log.info("❌ Moving draft to rejected: {}", fileName);

        Path sourcePath = draftsDir.resolve(fileName);
        String timestamp = LocalDateTime.now()
                .format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        String rejectedFileName = fileName.replace(".java",
                "_rejected_" + timestamp + ".java");
        Path targetPath = rejectedDir.resolve(rejectedFileName);

        if (!Files.exists(sourcePath)) {
            throw new IOException("Draft file not found: " + fileName);
        }

        Files.move(sourcePath, targetPath, StandardCopyOption.REPLACE_EXISTING);
        createRejectionMetadata(targetPath, rejectionReason);

        log.info("✅ File moved to rejected: {}", targetPath.toAbsolutePath());
        return targetPath;
    }

    public List<Path> listDrafts() throws IOException {
        try (Stream<Path> paths = Files.list(draftsDir)) {
            return paths
                    .filter(Files::isRegularFile)
                    .filter(p -> p.toString().endsWith(".java"))
                    .sorted()
                    .collect(Collectors.toList());
        }
    }

    public List<Path> listApproved() throws IOException {
        try (Stream<Path> paths = Files.list(approvedDir)) {
            return paths
                    .filter(Files::isRegularFile)
                    .filter(p -> p.toString().endsWith(".java"))
                    .sorted()
                    .collect(Collectors.toList());
        }
    }

    public List<Path> listRejected() throws IOException {
        try (Stream<Path> paths = Files.list(rejectedDir)) {
            return paths
                    .filter(Files::isRegularFile)
                    .filter(p -> p.toString().endsWith(".java"))
                    .sorted()
                    .collect(Collectors.toList());
        }
    }

    public String readDraft(String fileName) throws IOException {
        Path draftFile = draftsDir.resolve(fileName);
        if (!Files.exists(draftFile)) {
            throw new IOException("Draft file not found: " + fileName);
        }
        return Files.readString(draftFile);
    }

    public void deleteDraft(String fileName) throws IOException {
        Path draftFile = draftsDir.resolve(fileName);
        if (Files.exists(draftFile)) {
            Files.delete(draftFile);
            log.info("✅ Draft deleted: {}", fileName);
        }
    }

    public Path getDraftsDirectory() {
        return draftsDir;
    }

    public Path getApprovedDirectory() {
        return approvedDir;
    }

    public Path getRejectedDirectory() {
        return rejectedDir;
    }

    public boolean isSubmoduleAccessible() {
        Path uatRoot = Paths.get(uatSubmodulePath);
        return Files.exists(uatRoot) && Files.isDirectory(uatRoot);
    }

    private void createRejectionMetadata(Path rejectedFile, String reason) throws IOException {
        String metadataFileName = rejectedFile.getFileName().toString()
                .replace(".java", ".meta.txt");
        Path metadataFile = rejectedFile.getParent().resolve(metadataFileName);

        String metadata = String.format(
                "Rejected At: %s%nRejection Reason: %s%n",
                LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME),
                reason
        );

        Files.writeString(metadataFile, metadata);
    }
}