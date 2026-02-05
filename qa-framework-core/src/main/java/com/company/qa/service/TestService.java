package com.company.qa.service;

import com.company.qa.exception.ResourceNotFoundException;
import com.company.qa.model.dto.TestDto;
import com.company.qa.model.entity.Test;
import com.company.qa.repository.TestRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class TestService {

    private final TestRepository testRepository;

    @Transactional(readOnly = true)
    public List<TestDto> getAllActiveTests() {
        log.debug("Fetching all active tests");
        return testRepository.findByIsActiveTrue().stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public TestDto getTestById(UUID id) {
        log.debug("Fetching test with id: {}", id);
        Test test = testRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Test", id.toString()));
        return toDto(test);
    }

    @Transactional
    public TestDto createTest(TestDto testDto) {
        log.info("Creating new test: {}", testDto.getName());
        Test test = toEntity(testDto);
        Test saved = testRepository.save(test);
        log.info("Created test with id: {}", saved.getId());
        return toDto(saved);
    }

    @Transactional
    public TestDto updateTest(UUID id, TestDto testDto) {
        log.info("Updating test with id: {}", id);
        Test existing = testRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Test", id.toString()));

        existing.setName(testDto.getName());
        existing.setDescription(testDto.getDescription());
        existing.setFramework(testDto.getFramework());
        existing.setLanguage(testDto.getLanguage());
        existing.setPriority(testDto.getPriority());
        existing.setEstimatedDuration(testDto.getEstimatedDuration());
        existing.setIsActive(testDto.getIsActive());

        // ✅ IMPORTANT: update content if provided
        if (testDto.getContent() != null) {
            existing.setContent(testDto.getContent());
        }

        Test updated = testRepository.save(existing);
        return toDto(updated);
    }

    @Transactional
    public void deleteTest(UUID id) {
        log.info("Soft deleting test with id: {}", id);
        Test test = testRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Test", id.toString()));

        test.setIsActive(false);
        testRepository.save(test);
    }

    private TestDto toDto(Test test) {
        return TestDto.builder()
                .id(test.getId())
                .name(test.getName())
                .description(test.getDescription())
                .framework(test.getFramework())
                .language(test.getLanguage())
                .priority(test.getPriority())
                .estimatedDuration(test.getEstimatedDuration())
                .isActive(test.getIsActive())
                .content(test.getContent())
                .build();
    }

    private Test toEntity(TestDto dto) {
        return Test.builder()
                .name(dto.getName())
                .description(dto.getDescription())
                .framework(dto.getFramework())
                .language(dto.getLanguage())
                .priority(dto.getPriority())
                .estimatedDuration(dto.getEstimatedDuration())
                .isActive(true)
                .content(dto.getContent())
                .build();
    }
}