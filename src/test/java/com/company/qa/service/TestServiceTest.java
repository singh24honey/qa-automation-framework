package com.company.qa.service;

import com.company.qa.exception.ResourceNotFoundException;
import com.company.qa.model.dto.TestDto;
import com.company.qa.model.entity.Test;
import com.company.qa.model.enums.Priority;
import com.company.qa.model.enums.TestFramework;
import com.company.qa.repository.TestRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
//import org.junit.jupiter.api.Test as JUnitTest;

import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TestServiceTest {

    @Mock
    private TestRepository testRepository;

    @InjectMocks
    private TestService testService;

    private Test sampleTest;
    private UUID testId;

    @BeforeEach
    void setUp() {
        testId = UUID.randomUUID();
        sampleTest = Test.builder()
                .id(testId)
                .name("Sample Test")
                .description("Test description")
                .framework(TestFramework.SELENIUM)
                .language("java")
                .priority(Priority.HIGH)
                .isActive(true)
                .content("test content")
                .build();
    }

    @org.junit.jupiter.api.Test
    @DisplayName("Should return all active tests")
    void getAllActiveTests_ReturnsActiveTests() {
        // Given
        List<Test> tests = Arrays.asList(sampleTest);
        when(testRepository.findByIsActiveTrue()).thenReturn(tests);

        // When
        List<TestDto> result = testService.getAllActiveTests();

        // Then
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getName()).isEqualTo("Sample Test");
        verify(testRepository).findByIsActiveTrue();
    }

    @org.junit.jupiter.api.Test
    @DisplayName("Should return test by ID")
    void getTestById_WithValidId_ReturnsTest() {
        // Given
        when(testRepository.findById(testId)).thenReturn(Optional.of(sampleTest));

        // When
        TestDto result = testService.getTestById(testId);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo(testId);
        assertThat(result.getName()).isEqualTo("Sample Test");
    }

    @org.junit.jupiter.api.Test
    @DisplayName("Should throw exception when test not found")
    void getTestById_WithInvalidId_ThrowsException() {
        // Given
        UUID invalidId = UUID.randomUUID();
        when(testRepository.findById(invalidId)).thenReturn(Optional.empty());

        // When/Then
        assertThatThrownBy(() -> testService.getTestById(invalidId))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Test not found");
    }

    @org.junit.jupiter.api.Test
    @DisplayName("Should create new test")
    void createTest_WithValidData_ReturnsCreatedTest() {
        // Given
        TestDto inputDto = TestDto.builder()
                .name("New Test")
                .framework(TestFramework.PLAYWRIGHT)
                .language("java")
                .build();

        when(testRepository.save(any(Test.class))).thenReturn(sampleTest);

        // When
        TestDto result = testService.createTest(inputDto);

        // Then
        assertThat(result).isNotNull();
        verify(testRepository).save(any(Test.class));
    }

    @org.junit.jupiter.api.Test
    @DisplayName("Should soft delete test")
    void deleteTest_WithValidId_SoftDeletesTest() {
        // Given
        when(testRepository.findById(testId)).thenReturn(Optional.of(sampleTest));
        when(testRepository.save(any(Test.class))).thenReturn(sampleTest);

        // When
        testService.deleteTest(testId);

        // Then
        verify(testRepository).save(argThat(test -> !test.getIsActive()));
    }
}