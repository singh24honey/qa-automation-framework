package com.company.qa.controller;

import com.company.qa.model.dto.ApiResponse;
import com.company.qa.model.dto.TestDto;
import com.company.qa.service.TestService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/tests")
@RequiredArgsConstructor
@Slf4j
public class TestController {

    private final TestService testService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<TestDto>>> getAllTests() {
        log.info("GET /api/v1/tests - Fetching all tests");
        List<TestDto> tests = testService.getAllActiveTests();
        return ResponseEntity.ok(ApiResponse.success(tests));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<TestDto>> getTestById(@PathVariable UUID id) {
        log.info("GET /api/v1/tests/{} - Fetching test", id);
        TestDto test = testService.getTestById(id);
        return ResponseEntity.ok(ApiResponse.success(test));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<TestDto>> createTest(@RequestBody TestDto testDto) {
        log.info("POST /api/v1/tests - Creating test: {}", testDto.getName());
        TestDto created = testService.createTest(testDto);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(created, "Test created successfully"));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<TestDto>> updateTest(
            @PathVariable UUID id,
            @RequestBody TestDto testDto) {
        log.info("PUT /api/v1/tests/{} - Updating test", id);
        TestDto updated = testService.updateTest(id, testDto);
        return ResponseEntity.ok(ApiResponse.success(updated, "Test updated successfully"));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteTest(@PathVariable UUID id) {
        log.info("DELETE /api/v1/tests/{} - Deleting test", id);
        testService.deleteTest(id);
        return ResponseEntity.ok(ApiResponse.success(null, "Test deleted successfully"));
    }
}